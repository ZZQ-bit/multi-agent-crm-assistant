param(
    [string]$OutputFile = "./v3-response-quality-smoke-results.json",
    [string]$DifyApiKey = $env:DIFY_API_KEY,
    [string]$DifyBaseUrl = $env:DIFY_BASE_URL,
    [int]$IntervalSeconds = 2
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if (-not $DifyApiKey) {
    $propsFile = Join-Path $PSScriptRoot "../../CordysCRM/backend/app/src/main/resources/commons.properties"
    if (Test-Path $propsFile) {
        $props = Get-Content -LiteralPath $propsFile -Encoding utf8
        $DifyApiKey = ($props | Where-Object { $_ -match "^ai\.deal-desk\.dify\.api-key=" } | ForEach-Object { $_ -replace "^ai\.deal-desk\.dify\.api-key=", "" })
        $DifyBaseUrl = ($props | Where-Object { $_ -match "^ai\.deal-desk\.dify\.base-url=" } | ForEach-Object { $_ -replace "^ai\.deal-desk\.dify\.base-url=", "" })
    }
}

if (-not $DifyApiKey) {
    throw "Dify API Key not found. Set DIFY_API_KEY or configure commons.properties."
}

if (-not $DifyBaseUrl) {
    $DifyBaseUrl = "https://api.dify.ai/v1"
}

$cases = @(
    @{
        id = "AID-022-api"
        title = "普通问候短答"
        query = "你好"
        expected = @("短答", "不显示判断过程", "不套复杂商机评审模板")
        maxChars = 300
        requiredAny = @("你好", "您好", "可以帮")
        forbidden = @("销售视角", "财务视角", "交付视角", "合同视角", "风险等级", "判断过程")
    },
    @{
        id = "AID-053-api"
        title = "无图片输入不应卡住"
        query = "不带图片，简单说一下 多Agent智能助手 能帮销售做什么"
        expected = @("正常回答", "不等待图片", "不报上传缺失")
        maxChars = 900
        requiredAny = @("商机", "销售", "客户", "跟进")
        forbidden = @("请上传图片", "等待图片", "无法处理，因为没有图片")
    },
    @{
        id = "AID-023-api"
        title = "简单概念解释不套复杂模板"
        query = "CRM系统一般有哪些功能模块"
        expected = @("泛 CRM 解释", "不声称查询 CRM", "不套复杂评审")
        maxChars = 900
        requiredAny = @("客户", "商机", "线索", "跟进")
        forbidden = @("根据CRM数据", "已查询", "销售视角", "财务视角", "合同视角")
    },
    @{
        id = "AID-024-api"
        title = "正文 JSON 不应泄露内部协议"
        query = "请给我一个销售跟进记录的 JSON 示例，只要示例，不要写入系统"
        expected = @("可输出业务 JSON 示例", "不泄露内部 UI 协议", "不触发写回")
        maxChars = 1200
        requiredAny = @("客户", "商机", "跟进", "{")
        forbidden = @("processEvents", "turnType", "writeback", "quick_answer", "full_review", "memory_used")
    },
    @{
        id = "AID-036-api"
        title = "简单 CRM 查询"
        query = "华东智造集团有哪些商机"
        expected = @("查询 CRM 语义", "结果可读", "不泄露工具协议")
        maxChars = 1600
        requiredAny = @("华东智造", "商机", "未找到", "没有")
        forbidden = @("processEvents", "tool_action", "crm_tool_code", "payload", "DIFY_TOOL_TOKEN")
    },
    @{
        id = "AID-034-api"
        title = "无匹配对象不编造"
        query = "不存在的公司XYZ的商机信息"
        expected = @("明确未找到或要求确认", "不编造商机", "给出下一步")
        maxChars = 900
        requiredAny = @("未找到", "没有找到", "确认", "更多条件", "不存在")
        forbidden = @("金额：", "负责人：张", "已成交", "合同编号")
    }
)

function Test-ContainsAny([string]$Text, [object[]]$Needles) {
    foreach ($needle in $Needles) {
        if ($Text -like "*$needle*") { return $true }
    }
    return $false
}

function Test-ContainsForbidden([string]$Text, [object[]]$Needles) {
    $hits = @()
    foreach ($needle in $Needles) {
        if ($Text -like "*$needle*") { $hits += $needle }
    }
    return $hits
}

function ConvertFrom-DifyAnswer([string]$RawAnswer) {
    $parsed = @{
        display_answer = $RawAnswer
        raw_protocol_wrapped = $false
        raw_turn_type = $null
        raw_process_events_count = 0
    }
    if ([string]::IsNullOrWhiteSpace($RawAnswer)) {
        return $parsed
    }
    try {
        $json = $RawAnswer | ConvertFrom-Json -ErrorAction Stop
        if ($json.PSObject.Properties.Name -contains "answerText") {
            $parsed.display_answer = [string]$json.answerText
            $parsed.raw_protocol_wrapped = $true
            $parsed.raw_turn_type = [string]$json.turnType
            if ($json.processEvents) {
                $parsed.raw_process_events_count = @($json.processEvents).Count
            }
        }
    }
    catch {
        # Non-JSON answers are already display-ready.
    }
    return $parsed
}

function Invoke-DifyChat([string]$Query) {
    $headers = @{
        "Authorization" = "Bearer $DifyApiKey"
        "Content-Type" = "application/json; charset=utf-8"
    }
    $body = @{
        query = $Query
        user = "codex-api-quality-smoke"
        response_mode = "blocking"
        inputs = @{}
    } | ConvertTo-Json -Depth 10

    $start = Get-Date
    $response = Invoke-RestMethod -Uri "$($DifyBaseUrl.TrimEnd('/'))/chat-messages" -Method Post -Headers $headers -Body $body -TimeoutSec 180
    $latency = ((Get-Date) - $start).TotalSeconds
    return @{ response = $response; latency = $latency }
}

$results = @()
Write-Host "多Agent智能助手 V3 response quality smoke"
Write-Host "Dify Base URL: $DifyBaseUrl"
Write-Host "Cases: $($cases.Count)"

foreach ($case in $cases) {
    Write-Host ""
    Write-Host "[$($case.id)] $($case.title)"
    $errorMessage = $null
    $answer = ""
    $latency = $null
    $conversationId = $null
    $messageId = $null

    try {
        $call = Invoke-DifyChat $case.query
        $latency = [math]::Round($call.latency, 2)
        $answer = [string]$call.response.answer
        $conversationId = $call.response.conversation_id
        $messageId = $call.response.id
    }
    catch {
        $errorMessage = $_.Exception.Message
    }

    $parsedAnswer = ConvertFrom-DifyAnswer $answer
    $displayAnswer = [string]$parsedAnswer.display_answer
    $forbiddenHits = Test-ContainsForbidden $displayAnswer $case.forbidden
    $checks = @{
        has_answer = -not [string]::IsNullOrWhiteSpace($displayAnswer)
        within_length = $displayAnswer.Length -le $case.maxChars
        required_signal_present = Test-ContainsAny $displayAnswer $case.requiredAny
        no_forbidden_terms = $forbiddenHits.Count -eq 0
        no_protocol_json_leak_in_display_answer = -not ($displayAnswer -match "processEvents|turnType|writeback|memory_used|quick_answer|full_review")
        no_error = -not $errorMessage
        raw_protocol_wrapped = [bool]$parsedAnswer.raw_protocol_wrapped
    }
    $passed = $checks.has_answer -and $checks.within_length -and $checks.required_signal_present -and $checks.no_forbidden_terms -and $checks.no_protocol_json_leak_in_display_answer -and $checks.no_error

    Write-Host "  Result: $(if ($passed) { 'PASS' } else { 'REVIEW' }) | Latency: $latency s | Display length: $($displayAnswer.Length)"
    if ($parsedAnswer.raw_protocol_wrapped) { Write-Host "  Raw answer: protocol JSON wrapper, turnType=$($parsedAnswer.raw_turn_type)" }
    if ($errorMessage) { Write-Host "  Error: $errorMessage" }
    if ($forbiddenHits.Count -gt 0) { Write-Host "  Forbidden hits: $($forbiddenHits -join ', ')" }
    if ($displayAnswer) {
        $preview = $displayAnswer -replace "`r?`n", " "
        Write-Host "  Preview: $($preview.Substring(0, [Math]::Min(180, $preview.Length)))"
    }

    $results += @{
        id = $case.id
        title = $case.title
        query = $case.query
        expected = $case.expected
        result = if ($passed) { "通过" } else { "需复核" }
        latency_seconds = $latency
        answer_length = $displayAnswer.Length
        answer = $displayAnswer
        raw_answer = $answer
        raw_protocol_wrapped = [bool]$parsedAnswer.raw_protocol_wrapped
        raw_turn_type = $parsedAnswer.raw_turn_type
        raw_process_events_count = $parsedAnswer.raw_process_events_count
        checks = $checks
        forbidden_hits = $forbiddenHits
        error = $errorMessage
        conversation_id = $conversationId
        message_id = $messageId
        executed_at = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    }

    Start-Sleep -Seconds $IntervalSeconds
}

$summary = @{
    total = $results.Count
    passed = @($results | Where-Object { $_["result"] -eq "通过" }).Count
    review = @($results | Where-Object { $_["result"] -ne "通过" }).Count
    raw_protocol_wrapped = @($results | Where-Object { $_["raw_protocol_wrapped"] }).Count
    avg_latency_seconds = [math]::Round((($results | Where-Object { $_["latency_seconds"] } | ForEach-Object { $_["latency_seconds"] } | Measure-Object -Average).Average), 2)
    generated_at = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
}

@{
    version = "v3-response-quality-smoke"
    summary = $summary
    results = $results
} | ConvertTo-Json -Depth 20 | Out-File -LiteralPath $OutputFile -Encoding utf8

Write-Host ""
Write-Host "Summary: $($summary.passed)/$($summary.total) passed, $($summary.review) need review"
Write-Host "Saved to: $OutputFile"
