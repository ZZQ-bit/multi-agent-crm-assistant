param(
    [string]$OutputFile = "./v3-response-quality-extended-results.json",
    [string]$DifyApiKey = $env:DIFY_API_KEY,
    [string]$DifyBaseUrl = $env:DIFY_BASE_URL,
    [int]$IntervalSeconds = 2,
    [int]$MaxRetries = 2,
    [string[]]$CaseIds = @()
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

function New-OpportunityBoundObject {
    param([string]$Name)
    return @{
        objectType = "opportunity"
        objectId = "demo_o_064"
        objectName = $Name
        customerId = "demo_c_027"
        customerName = "南京数据治理中心"
        source = "api-extended-smoke"
    }
}

$defaultOpportunity = New-OpportunityBoundObject "南京数据治理中心低优先级小单1期"

$cases = @(
    @{
        id = "AID-029-api"; priority = "P1"; title = "统计查询与图表输出"
        rounds = @("看一下当前销售漏斗各阶段数量，并用图表展示。")
        maxChars = 1800
        requiredAny = @("漏斗", "阶段", "数量", "chart", "图表")
        forbidden = @("请选择客户", "请选择商机", "raw", "payload", "tool_call", "Tavily", "processEvents", "route_label")
        qualityForbidden = @("原始 JSON", "工具字段", "无法统计但")
    },
    @{
        id = "AID-035-api"; priority = "P1"; title = "公开客户背景触发联网搜索"
        rounds = @("从公开信息角度，查一下南京数据治理中心近期新闻、招投标或信用风险，并结合当前商机给出风险提醒。")
        boundObject = $defaultOpportunity
        maxChars = 1600
        requiredAny = @("公开", "检索", "未获取", "来源", "风险", "核实")
        forbidden = @("payload", "tool_call", "Tavily Search", "Tavily Extract", "raw_content", "processEvents", "route_label")
        qualityForbidden = @("已确认发生信用风险", "CRM 已确认公开", "被执行", "法院判决")
    },
    @{
        id = "AID-037-api"; priority = "P1"; title = "外部资料与 CRM 冲突时以 CRM 为主"
        rounds = @("如果公开资料和 CRM 里的客户信息不一致，帮我判断南京数据治理中心这个商机应该以哪个为准，并说明怎么处理。")
        boundObject = $defaultOpportunity
        maxChars = 1400
        requiredAny = @("CRM", "内部记录", "公开资料", "核实", "不能直接覆盖")
        forbidden = @("直接以公开资料为准", "自动修改", "已写入", "payload", "processEvents", "route_label")
        qualityForbidden = @("公开资料覆盖 CRM", "无需人工核实")
    },
    @{
        id = "AID-039-api"; priority = "P0"; title = "确认写回成功路径"
        rounds = @(
            "把这条内容整理成跟进记录，先让我确认，不要直接写回：客户反馈 7 月底前要完成知识库试点上线，付款条件还需要下周和采购确认。",
            "确认写回",
            "确认"
        )
        boundObject = $defaultOpportunity
        maxChars = 2200
        requiredAny = @("确认", "写回", "跟进记录", "已完成", "没有可确认")
        forbidden = @("新草稿如下", "请重新提供商机", "未指定客户", "processEvents", "route_label")
        qualityForbidden = @("重复写入", "再次写入同一草稿", "记录ID: fake")
        carryWriteback = $true
    },
    @{
        id = "AID-040-api"; priority = "P0"; title = "取消写回"
        rounds = @(
            "把这条内容整理成跟进记录，先让我确认，不要直接写回：客户反馈 7 月底前要完成知识库试点上线，付款条件还需要下周和采购确认。",
            "取消，不写回了",
            "确认"
        )
        boundObject = $defaultOpportunity
        maxChars = 2200
        requiredAny = @("取消", "不写回", "没有可确认", "当前没有")
        forbidden = @("已写入", "写入成功", "新草稿如下", "processEvents", "route_label")
        qualityForbidden = @("取消后仍写入", "记录ID")
        carryWriteback = $true
    },
    @{
        id = "AID-041-api"; priority = "P1"; title = "修改待写回草稿"
        rounds = @(
            "把这条内容整理成跟进记录，先让我确认，不要直接写回：客户反馈 7 月底前要完成知识库试点上线，付款条件还需要下周和采购确认。",
            "先别写，把内容改成：客户已确认 7 月底完成试点，但付款节点待采购确认。"
        )
        boundObject = $defaultOpportunity
        maxChars = 1800
        requiredAny = @("已修改", "草稿", "确认", "7 月底", "付款节点")
        forbidden = @("已写入", "写入成功", "未指定客户", "processEvents", "route_label")
        qualityForbidden = @("直接写回", "丢失目标")
        carryWriteback = $true
    },
    @{
        id = "AID-054-api"; priority = "P1"; title = "外部情报不强制 Extract"
        rounds = @("从公开信息角度，快速了解南京数据治理中心最近是否有一两条公开动态，只需要简短判断，不需要展开网页全文。")
        maxChars = 1200
        requiredAny = @("公开", "动态", "未获取", "来源", "简短")
        forbidden = @("payload", "tool_call", "raw_content", "网页全文", "Tavily Extract", "processEvents", "route_label")
        qualityForbidden = @("大段网页", "以下是全文")
    },
    @{
        id = "AID-055-api"; priority = "P1"; title = "外部情报必要时 Search + Extract"
        rounds = @("请核验南京数据治理中心近期招投标、官网公告或公开新闻里是否有与数据治理、CRM、知识库相关的信息，要求给出来源并说明和当前商机的关系。")
        boundObject = $defaultOpportunity
        maxChars = 1800
        requiredAny = @("来源", "公开", "商机", "关系", "核验", "未获取")
        forbidden = @("payload", "tool_call", "raw_content", "processEvents", "route_label")
        qualityForbidden = @("CRM 已确认该公开信息", "编造来源", "example.com")
    }
)

if ($CaseIds.Count -gt 0) {
    $wanted = @{}
    foreach ($caseId in $CaseIds) { $wanted[$caseId] = $true }
    $cases = @($cases | Where-Object { $wanted.ContainsKey($_["id"]) })
}

function Test-ContainsAny([string]$Text, [object[]]$Needles) {
    foreach ($needle in $Needles) {
        if ($Text.IndexOf([string]$needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) { return $true }
    }
    return $false
}

function Test-ContainsForbidden([string]$Text, [object[]]$Needles) {
    $hits = @()
    foreach ($needle in $Needles) {
        if ($Text.IndexOf([string]$needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) { $hits += $needle }
    }
    return $hits
}

$defaultQualityForbidden = @("route_label", "quick_answer", "full_review", "processEvents", "memory_used", "DIFY_TOOL_TOKEN", "FIN-019")

function ConvertFrom-DifyAnswer([string]$RawAnswer) {
    $parsed = @{
        display_answer = $RawAnswer
        raw_protocol_wrapped = $false
        raw_turn_type = $null
        raw_writeback_status = $null
        raw_writeback = $null
    }
    if ([string]::IsNullOrWhiteSpace($RawAnswer)) { return $parsed }
    try {
        $json = $RawAnswer | ConvertFrom-Json -ErrorAction Stop
        if ($json.PSObject.Properties.Name -contains "answerText") {
            $parsed.display_answer = [string]$json.answerText
            $parsed.raw_protocol_wrapped = $true
            $parsed.raw_turn_type = [string]$json.turnType
            if ($json.writeback) {
                $parsed.raw_writeback = $json.writeback
                if ($json.writeback.status) { $parsed.raw_writeback_status = [string]$json.writeback.status }
            }
        }
    }
    catch {}
    return $parsed
}

function New-DifyInputs {
    param(
        [object]$BoundObject,
        [object]$ActiveWriteback
    )
    $inputs = @{}
    if ($BoundObject) {
        $objectType = [string]$BoundObject.objectType
        $objectId = [string]$BoundObject.objectId
        $customerId = [string]$BoundObject.customerId
        $inputs.bound_object_type = $objectType
        $inputs.bound_object_id = $objectId
        $inputs.bound_object_name = [string]$BoundObject.objectName
        $inputs.bound_object_source = [string]$BoundObject.source
        $inputs.route_customer_id = if ($objectType -eq "customer") { $objectId } elseif ($customerId) { $customerId } else { "" }
        $inputs.route_opportunity_id = if ($objectType -eq "opportunity") { $objectId } else { "" }
        $inputs.selected_object_type = ""
        $inputs.selected_object_id = ""
        $inputs.selected_object_name = ""
        $inputs.resume_query = ""
    }
    if ($ActiveWriteback) {
        $inputs.active_writeback_id = [string]$ActiveWriteback.id
        $inputs.active_writeback_status = [string]$ActiveWriteback.status
        $inputs.active_writeback_type = [string]$ActiveWriteback.type
        $inputs.active_writeback_payload_json = ($ActiveWriteback | ConvertTo-Json -Depth 20 -Compress)
    }
    return $inputs
}

function Invoke-DifyChat {
    param(
        [string]$Query,
        [object]$BoundObject,
        [object]$ActiveWriteback,
        [string]$ConversationId
    )
    $headers = @{
        "Authorization" = "Bearer $DifyApiKey"
        "Content-Type" = "application/json; charset=utf-8"
    }
    $bodyObject = @{
        query = $Query
        user = "codex-api-extended-smoke"
        response_mode = "blocking"
        inputs = New-DifyInputs -BoundObject $BoundObject -ActiveWriteback $ActiveWriteback
    }
    if ($ConversationId) { $bodyObject.conversation_id = $ConversationId }
    $body = $bodyObject | ConvertTo-Json -Depth 30
    $attempt = 0
    $lastError = $null
    while ($attempt -lt $MaxRetries) {
        $attempt += 1
        try {
            $start = Get-Date
            $response = Invoke-RestMethod -Uri "$($DifyBaseUrl.TrimEnd('/'))/chat-messages" -Method Post -Headers $headers -Body $body -TimeoutSec 240
            return @{ response = $response; latency = ((Get-Date) - $start).TotalSeconds; attempts = $attempt; error = $null }
        }
        catch {
            $errorParts = @($_.Exception.Message)
            if ($_.Exception.InnerException -and $_.Exception.InnerException.Message) { $errorParts += "Inner: $($_.Exception.InnerException.Message)" }
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $errorParts += "Details: $($_.ErrorDetails.Message)" }
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) { $errorParts += "StatusCode: $([int]$_.Exception.Response.StatusCode) $($_.Exception.Response.ReasonPhrase)" }
            $lastError = ($errorParts | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join " | "
            if ($attempt -lt $MaxRetries) { Start-Sleep -Seconds 3 }
        }
    }
    return @{ response = $null; latency = $null; attempts = $attempt; error = $lastError }
}

function Invoke-Case {
    param([hashtable]$Case)
    [string[]]$rounds = @($Case["rounds"] | ForEach-Object { [string]$_ })
    $conversationId = $null
    $roundResults = @()
    $combinedAnswer = ""
    $totalLatency = 0.0
    $errorMessage = $null
    $activeWriteback = $null

    for ($i = 0; $i -lt $rounds.Count; $i += 1) {
        $boundObject = if ($Case.ContainsKey("boundObject")) { $Case["boundObject"] } else { $null }
        $call = Invoke-DifyChat -Query $rounds[$i] -BoundObject $boundObject -ActiveWriteback $activeWriteback -ConversationId $conversationId
        if ($call.error) {
            $errorMessage = $call.error
            $roundResults += @{ round = $i + 1; query = $rounds[$i]; error = $call.error; attempts = $call.attempts }
            break
        }
        $rawAnswer = [string]$call.response.answer
        $parsed = ConvertFrom-DifyAnswer $rawAnswer
        $displayAnswer = [string]$parsed.display_answer
        $conversationId = $call.response.conversation_id
        $totalLatency += [double]$call.latency
        $combinedAnswer += "`n`n[Round $($i + 1)]`n$displayAnswer"

        if ($Case.ContainsKey("carryWriteback") -and [bool]$Case["carryWriteback"] -and $parsed.raw_writeback) {
            if ($parsed.raw_writeback.status -eq "awaiting_confirm") {
                $activeWriteback = $parsed.raw_writeback
            }
            elseif ($parsed.raw_writeback.status -in @("confirmed", "cancelled", "failed")) {
                $activeWriteback = $null
            }
        }

        $roundResults += @{
            round = $i + 1
            query = $rounds[$i]
            answer = $displayAnswer
            raw_answer = $rawAnswer
            latency_seconds = [math]::Round($call.latency, 2)
            attempts = $call.attempts
            raw_protocol_wrapped = [bool]$parsed.raw_protocol_wrapped
            raw_turn_type = $parsed.raw_turn_type
            raw_writeback_status = $parsed.raw_writeback_status
            conversation_id = $conversationId
            message_id = $call.response.id
        }
        if ($i -lt $rounds.Count - 1) { Start-Sleep -Seconds $IntervalSeconds }
    }

    $forbiddenHits = Test-ContainsForbidden $combinedAnswer $Case["forbidden"]
    $qualityForbidden = @($defaultQualityForbidden)
    if ($Case.ContainsKey("qualityForbidden")) { $qualityForbidden += @($Case["qualityForbidden"]) }
    $qualityForbiddenHits = Test-ContainsForbidden $combinedAnswer $qualityForbidden
    $checks = @{
        has_answer = -not [string]::IsNullOrWhiteSpace($combinedAnswer)
        within_length = $combinedAnswer.Length -le ([int]$Case["maxChars"] * [Math]::Max(1, $rounds.Count))
        required_signal_present = Test-ContainsAny $combinedAnswer $Case["requiredAny"]
        no_forbidden_terms = $forbiddenHits.Count -eq 0
        no_quality_forbidden_terms = $qualityForbiddenHits.Count -eq 0
        no_error = -not $errorMessage
    }
    $passed = $checks.has_answer -and $checks.within_length -and $checks.required_signal_present -and $checks.no_forbidden_terms -and $checks.no_quality_forbidden_terms -and $checks.no_error

    return @{
        id = $Case["id"]
        priority = $Case["priority"]
        title = $Case["title"]
        result = if ($passed) { "通过" } else { "需复核" }
        total_latency_seconds = [math]::Round($totalLatency, 2)
        answer_length = $combinedAnswer.Length
        combined_answer = $combinedAnswer.Trim()
        rounds = $roundResults
        checks = $checks
        forbidden_hits = $forbiddenHits
        quality_forbidden_hits = $qualityForbiddenHits
        error = $errorMessage
        executed_at = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    }
}

$results = @()
Write-Host "多Agent智能助手 V3 extended API response quality"
Write-Host "Dify Base URL: $DifyBaseUrl"
Write-Host "Cases: $($cases.Count)"

foreach ($case in $cases) {
    Write-Host ""
    Write-Host "[$($case["id"])] [$($case["priority"])] $($case["title"])"
    $result = Invoke-Case $case
    $results += $result
    Write-Host "  Result: $($result.result) | Latency: $($result.total_latency_seconds)s | Length: $($result.answer_length)"
    if ($result.error) { Write-Host "  Error: $($result.error)" }
    if ($result.forbidden_hits.Count -gt 0) { Write-Host "  Forbidden hits: $($result.forbidden_hits -join ', ')" }
    if ($result.quality_forbidden_hits.Count -gt 0) { Write-Host "  Quality forbidden hits: $($result.quality_forbidden_hits -join ', ')" }
    $preview = $result.combined_answer -replace "`r?`n", " "
    if ($preview) { Write-Host "  Preview: $($preview.Substring(0, [Math]::Min(240, $preview.Length)))" }
    Start-Sleep -Seconds $IntervalSeconds
}

$latencies = @($results | Where-Object { $_["total_latency_seconds"] } | ForEach-Object { $_["total_latency_seconds"] })
$summary = @{
    total = $results.Count
    passed = @($results | Where-Object { $_["result"] -eq "通过" }).Count
    review = @($results | Where-Object { $_["result"] -ne "通过" }).Count
    avg_latency_seconds = if ($latencies.Count -gt 0) { [math]::Round(($latencies | Measure-Object -Average).Average, 2) } else { $null }
    generated_at = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
}

@{
    version = "v3-response-quality-extended"
    summary = $summary
    results = $results
} | ConvertTo-Json -Depth 40 | Out-File -LiteralPath $OutputFile -Encoding utf8

Write-Host ""
Write-Host "Summary: $($summary.passed)/$($summary.total) passed, $($summary.review) need review"
Write-Host "Saved to: $OutputFile"
