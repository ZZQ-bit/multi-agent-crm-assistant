param(
    [string]$OutputFile = "",
    [string]$DifyApiKey = $env:DIFY_API_KEY,
    [string]$DifyBaseUrl = $env:DIFY_BASE_URL,
    [int]$IntervalSeconds = 2,
    [int]$TimeoutSeconds = 300,
    [string[]]$CaseIds = @()
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not $OutputFile) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputFile = Join-Path $PSScriptRoot "v3-readonly-complex-api-$stamp.json"
}

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
    param(
        [string]$ObjectId,
        [string]$ObjectName,
        [string]$CustomerId = "demo_c_027",
        [string]$CustomerName = "南京数据治理中心"
    )
    return @{
        objectType = "opportunity"
        objectId = $ObjectId
        objectName = $ObjectName
        customerId = $CustomerId
        customerName = $CustomerName
        source = "codex-readonly-complex-api"
    }
}

function New-DifyInputs {
    param([object]$BoundObject)

    $inputs = @{}
    if (-not $BoundObject) {
        return $inputs
    }

    $objectType = [string]$BoundObject.objectType
    $objectId = [string]$BoundObject.objectId
    $objectName = [string]$BoundObject.objectName
    $customerId = [string]$BoundObject.customerId

    $inputs.bound_object_type = $objectType
    $inputs.bound_object_id = $objectId
    $inputs.bound_object_name = $objectName
    $inputs.bound_object_source = [string]$BoundObject.source
    $inputs.route_customer_id = if ($objectType -eq "customer") { $objectId } elseif ($customerId) { $customerId } else { "" }
    $inputs.route_opportunity_id = if ($objectType -eq "opportunity") { $objectId } else { "" }
    $inputs.selected_object_type = ""
    $inputs.selected_object_id = ""
    $inputs.selected_object_name = ""
    $inputs.resume_query = ""
    $inputs.attachments_summary = ""
    $inputs.attachment_names = ""

    return $inputs
}

function Test-ContainsAny {
    param([string]$Text, [object[]]$Needles)
    foreach ($needle in $Needles) {
        if ($Text.IndexOf([string]$needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }
    return $false
}

function Test-ContainsAll {
    param([string]$Text, [object[]]$Needles)
    $missing = @()
    foreach ($needle in $Needles) {
        if ($Text.IndexOf([string]$needle, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
            $missing += [string]$needle
        }
    }
    return $missing
}

function Test-ForbiddenHits {
    param([string]$Text, [object[]]$Needles)
    $hits = @()
    foreach ($needle in $Needles) {
        if ($Text.IndexOf([string]$needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            $hits += [string]$needle
        }
    }
    return $hits
}

function ConvertFrom-DifyAnswer {
    param([string]$RawAnswer)

    $parsed = @{
        display_answer = $RawAnswer
        raw_protocol_wrapped = $false
        raw_protocol_version = $null
        raw_turn_type = $null
        raw_process_events_count = 0
        raw_writeback_is_empty = $false
        raw_bound_object_type = $null
        protocol_complete = $false
    }
    if ([string]::IsNullOrWhiteSpace($RawAnswer)) {
        return $parsed
    }

    try {
        $json = $RawAnswer | ConvertFrom-Json -ErrorAction Stop
        $names = @($json.PSObject.Properties.Name)
        if ($names -contains "answerText") {
            $parsed.display_answer = [string]$json.answerText
            $parsed.raw_protocol_wrapped = $true
            $parsed.raw_protocol_version = [string]$json.protocolVersion
            $parsed.raw_turn_type = [string]$json.turnType
            if ($json.processEvents) {
                $parsed.raw_process_events_count = @($json.processEvents).Count
            }
            if ($json.boundObject -and $json.boundObject.objectType) {
                $parsed.raw_bound_object_type = [string]$json.boundObject.objectType
            }
            $writebackNames = if ($json.writeback) { @($json.writeback.PSObject.Properties.Name) } else { @() }
            $parsed.raw_writeback_is_empty = $writebackNames.Count -eq 0
            $parsed.protocol_complete = (
                ($names -contains "protocolVersion") -and
                ($names -contains "turnType") -and
                ($names -contains "answerText") -and
                ($names -contains "processEvents") -and
                ($names -contains "writeback") -and
                ($names -contains "boundObject")
            )
        }
    }
    catch {
        # Non-JSON answers are treated as display-ready answers.
    }
    return $parsed
}

function Invoke-DifyChat {
    param(
        [string]$Query,
        [object]$BoundObject,
        [string]$ConversationId,
        [string]$UserId
    )

    $bodyObject = @{
        query = $Query
        user = $UserId
        response_mode = "streaming"
        inputs = New-DifyInputs -BoundObject $BoundObject
    }
    if ($ConversationId) {
        $bodyObject.conversation_id = $ConversationId
    }

    $body = $bodyObject | ConvertTo-Json -Depth 30
    $bodyFile = New-TemporaryFile
    $outFile = New-TemporaryFile
    $start = Get-Date
    try {
        Set-Content -LiteralPath $bodyFile -Value $body -Encoding utf8
        $curlOutput = & curl.exe --http1.1 -sS -N -m $TimeoutSeconds `
            -w "`nCURL_HTTP_CODE=%{http_code} CURL_TIME_TOTAL=%{time_total}`n" `
            -H "Authorization: Bearer $DifyApiKey" `
            -H "Content-Type: application/json; charset=utf-8" `
            --data-binary "@$bodyFile" `
            "$($DifyBaseUrl.TrimEnd('/'))/chat-messages" `
            -o $outFile 2>&1
        $exitCode = $LASTEXITCODE
        $latency = ((Get-Date) - $start).TotalSeconds
        $bodyLines = if (Test-Path $outFile) { @(Get-Content -LiteralPath $outFile -Encoding utf8) } else { @() }

        $httpCode = $null
        foreach ($line in @($curlOutput)) {
            if ([string]$line -match "CURL_HTTP_CODE=(\d+)") {
                $httpCode = [int]$Matches[1]
            }
        }
        if ($exitCode -ne 0 -or ($httpCode -and $httpCode -ge 400)) {
            $tail = ($bodyLines | Select-Object -Last 8) -join "`n"
            $message = (@($curlOutput) + @($tail)) -join " | "
            throw "Dify streaming request failed. exit=$exitCode http=$httpCode $message"
        }

        $messageAnswers = @()
        $conversation = $null
        $messageId = $null
        foreach ($line in $bodyLines) {
            if (-not ([string]$line).StartsWith("data: ")) {
                continue
            }
            $jsonText = ([string]$line).Substring(6)
            try {
                $evt = $jsonText | ConvertFrom-Json -ErrorAction Stop
            }
            catch {
                continue
            }
            if ($evt.conversation_id) { $conversation = [string]$evt.conversation_id }
            if ($evt.message_id) { $messageId = [string]$evt.message_id }
            if ($evt.id -and -not $messageId) { $messageId = [string]$evt.id }
            if ($evt.event -eq "message" -and $evt.answer) {
                $messageAnswers += [string]$evt.answer
            }
        }

        $answer = ($messageAnswers -join "")
        if ([string]::IsNullOrWhiteSpace($answer)) {
            $tail = ($bodyLines | Select-Object -Last 12) -join "`n"
            throw "Dify streaming response did not contain a message answer. Tail: $tail"
        }

        $response = [pscustomobject]@{
            answer = $answer
            conversation_id = $conversation
            id = $messageId
        }
        return @{ response = $response; latency = $latency }
    }
    finally {
        Remove-Item -LiteralPath $bodyFile, $outFile -Force -ErrorAction SilentlyContinue
    }
}

$activeOpportunity = New-OpportunityBoundObject -ObjectId "demo_o_064" -ObjectName "南京数据治理中心低优先级小单1期"
$failedOpportunity = New-OpportunityBoundObject -ObjectId "demo_o_065" -ObjectName "南京数据治理中心预测重点单2期"

$commonForbidden = @(
    "processEvents",
    "route_label",
    "memory_used",
    "DIFY_TOOL_TOKEN",
    "CRM_TOOL_BASE_URL",
    "Tool API",
    "workflow variable",
    "Prompt",
    "已写入成功",
    "写入成功",
    "已保存到CRM",
    "审批通过",
    "法务已通过",
    "财务审批已通过"
)

$cases = @(
    @{
        id = "RCX-001"
        title = "复杂商机综合评审"
        rounds = @(
            "帮我完整评审南京数据治理中心低优先级小单1期：金额 85,000，客户希望 2026-07-25 上线，付款 20% 首付 + 80% 验收后 90 天，合同要求数据安全承诺。判断是否值得推进，并给下一步动作。"
        )
        boundObject = $activeOpportunity
        requiredAll = @("结论", "销售", "财务", "交付", "合同", "下一步")
        requiredAny = @("风险", "缺失", "待确认", "建议")
        expectedTurnTypes = @("deep_deal_review_brief", "text_analysis")
        minChars = 500
        maxChars = 5000
    },
    @{
        id = "RCX-002"
        title = "外部公开资料与当前商机关联研判"
        rounds = @(
            "从公开资料搜索南京数据治理中心近期是否有招投标、官网公告或新闻动态，并判断这些公开信息和当前商机有没有关系。要求给出来源链接，并区分事实、推断和缺失信息。"
        )
        boundObject = $activeOpportunity
        requiredAll = @("来源", "事实")
        requiredAny = @("http", "链接", "公告", "招投标", "新闻", "未检索到", "未找到", "相关", "挂钩", "直接")
        expectedTurnTypes = @("text_analysis")
        minChars = 450
        maxChars = 6000
    },
    @{
        id = "RCX-003"
        title = "信息缺失下的边界判断"
        rounds = @(
            "只知道南京数据治理中心低优先级小单1期金额 85,000，其他条件暂时没定。请判断折扣、付款、交付、合同四类风险，必须说明哪些不能下结论。"
        )
        boundObject = $activeOpportunity
        requiredAll = @("缺失", "付款", "交付", "合同")
        requiredAny = @("待确认", "无法判断", "需要补齐", "不能断言", "无法下结论")
        expectedTurnTypes = @("deep_deal_review_brief", "text_analysis")
        minChars = 450
        maxChars = 5000
        extraForbidden = @("首付过低", "交付周期过短", "合同条款有问题", "折扣过大")
    },
    @{
        id = "RCX-004"
        title = "经营分析与销售管理建议"
        rounds = @(
            "从销售管理视角看一下当前 CRM 漏斗：哪些阶段容易卡住，南京数据治理中心这类商机应该怎么排优先级？请给出经营判断和动作建议。"
        )
        requiredAll = @("漏斗", "阶段", "优先级", "建议")
        requiredAny = @("金额", "转化", "风险", "推进", "商机")
        expectedTurnTypes = @("stats_query", "text_analysis")
        minChars = 350
        maxChars = 5000
    },
    @{
        id = "RCX-005"
        title = "失败商机复盘与再启动策略"
        rounds = @(
            "复盘南京数据治理中心预测重点单2期为什么会失败。如果现在要重新启动类似商机，销售、财务、交付、合同各自要先补什么证据？"
        )
        boundObject = $failedOpportunity
        requiredAll = @("失败", "销售", "财务", "交付", "合同")
        requiredAny = @("证据", "原因", "复盘", "重新启动", "补齐")
        expectedTurnTypes = @("deep_deal_review_brief", "text_analysis")
        minChars = 450
        maxChars = 5500
    },
    @{
        id = "RCX-006"
        title = "连续追问上下文承接"
        rounds = @(
            "总结南京数据治理中心低优先级小单1期当前推进情况，重点说已确认事实和主要风险。",
            "那如果客户坚持 20% 首付、验收后 90 天尾款，应该怎么谈？",
            "把刚才建议整理成一段可以发给客户的微信话术，不要写入 CRM。"
        )
        boundObject = $activeOpportunity
        requiredAll = @("付款", "风险", "话术")
        requiredAny = @("您好", "建议", "确认", "沟通", "首付")
        expectedTurnTypes = @("text_analysis")
        minChars = 700
        maxChars = 7000
    }
)

if ($CaseIds.Count -gt 0) {
    $wanted = @{}
    foreach ($caseId in $CaseIds) {
        $wanted[$caseId] = $true
    }
    $cases = @($cases | Where-Object { $wanted.ContainsKey($_["id"]) })
}

$results = @()
Write-Host "多Agent智能助手 V3 readonly complex API regression"
Write-Host "Dify Base URL: $DifyBaseUrl"
Write-Host "Cases: $($cases.Count)"
Write-Host "Output: $OutputFile"

foreach ($case in $cases) {
    Write-Host ""
    Write-Host "[$($case.id)] $($case.title)"

    $conversationId = $null
    $roundResults = @()
    $combinedAnswer = ""
    $turnTypes = @()
    $errorMessage = $null
    $totalLatency = 0.0

    [string[]]$rounds = @($case["rounds"] | ForEach-Object { [string]$_ })
    $userId = "codex-readonly-complex-$($case.id.ToLowerInvariant())"

    for ($i = 0; $i -lt $rounds.Count; $i += 1) {
        try {
            $call = Invoke-DifyChat -Query $rounds[$i] -BoundObject $case["boundObject"] -ConversationId $conversationId -UserId $userId
            $rawAnswer = [string]$call.response.answer
            $parsed = ConvertFrom-DifyAnswer -RawAnswer $rawAnswer
            $displayAnswer = [string]$parsed.display_answer
            $conversationId = [string]$call.response.conversation_id
            $latency = [math]::Round([double]$call.latency, 2)
            $totalLatency += [double]$call.latency
            if ($parsed.raw_turn_type) {
                $turnTypes += [string]$parsed.raw_turn_type
            }
            $combinedAnswer += "`n`n[Round $($i + 1)]`n$displayAnswer"
            $roundResults += @{
                round = $i + 1
                query = $rounds[$i]
                answer = $displayAnswer
                raw_answer = $rawAnswer
                latency_seconds = $latency
                raw_protocol_wrapped = [bool]$parsed.raw_protocol_wrapped
                protocol_complete = [bool]$parsed.protocol_complete
                raw_turn_type = $parsed.raw_turn_type
                raw_process_events_count = $parsed.raw_process_events_count
                raw_writeback_is_empty = [bool]$parsed.raw_writeback_is_empty
                raw_bound_object_type = $parsed.raw_bound_object_type
                message_id = [string]$call.response.id
                conversation_id = $conversationId
            }
            Write-Host "  Round $($i + 1): $latency s | turnType=$($parsed.raw_turn_type) | len=$($displayAnswer.Length)"
            $preview = ($displayAnswer -replace "`r?`n", " ").Trim()
            if ($preview) {
                Write-Host "    $($preview.Substring(0, [Math]::Min(180, $preview.Length)))"
            }
        }
        catch {
            $errorParts = @($_.Exception.Message)
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                $errorParts += $_.ErrorDetails.Message
            }
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $errorParts += "StatusCode: $([int]$_.Exception.Response.StatusCode) $($_.Exception.Response.ReasonPhrase)"
            }
            $errorMessage = ($errorParts | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join " | "
            Write-Host "  Error: $errorMessage"
            break
        }

        if ($i -lt $rounds.Count - 1) {
            Start-Sleep -Seconds $IntervalSeconds
        }
    }

    $combinedAnswer = $combinedAnswer.Trim()
    $forbidden = @($commonForbidden)
    if ($case.ContainsKey("extraForbidden")) {
        $forbidden += @($case["extraForbidden"])
    }
    $forbiddenHits = Test-ForbiddenHits -Text $combinedAnswer -Needles $forbidden
    $missingRequired = Test-ContainsAll -Text $combinedAnswer -Needles $case["requiredAll"]
    $expectedTurnTypes = @($case["expectedTurnTypes"])
    $actualTurnTypes = @($turnTypes | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $turnTypeOk = $actualTurnTypes.Count -eq 0 -or @($actualTurnTypes | Where-Object { $expectedTurnTypes -contains $_ }).Count -gt 0
    $protocolRounds = @($roundResults | Where-Object { $_["raw_protocol_wrapped"] })
    $protocolCompleteRounds = @($roundResults | Where-Object { $_["protocol_complete"] })
    $writebackOkRounds = @($roundResults | Where-Object { $_["raw_writeback_is_empty"] })

    $checks = @{
        no_error = -not $errorMessage
        has_answer = -not [string]::IsNullOrWhiteSpace($combinedAnswer)
        within_max_length = $combinedAnswer.Length -le [int]$case["maxChars"]
        meets_min_length = $combinedAnswer.Length -ge [int]$case["minChars"]
        required_all_present = $missingRequired.Count -eq 0
        required_any_present = Test-ContainsAny -Text $combinedAnswer -Needles $case["requiredAny"]
        no_forbidden_terms = $forbiddenHits.Count -eq 0
        expected_turn_type = $turnTypeOk
        protocol_wrapped_all_rounds = $protocolRounds.Count -eq $roundResults.Count
        protocol_complete_all_rounds = $protocolCompleteRounds.Count -eq $roundResults.Count
        writeback_empty_all_rounds = $writebackOkRounds.Count -eq $roundResults.Count
        no_protocol_leak_in_display_answer = -not ($combinedAnswer -match "processEvents|protocolVersion|turnType|memory_used|route_label|DIFY_TOOL_TOKEN")
    }

    $passed = (
        $checks.no_error -and
        $checks.has_answer -and
        $checks.within_max_length -and
        $checks.meets_min_length -and
        $checks.required_all_present -and
        $checks.required_any_present -and
        $checks.no_forbidden_terms -and
        $checks.expected_turn_type -and
        $checks.protocol_wrapped_all_rounds -and
        $checks.protocol_complete_all_rounds -and
        $checks.writeback_empty_all_rounds -and
        $checks.no_protocol_leak_in_display_answer
    )

    $status = if ($passed) { "通过" } else { "需复核" }
    Write-Host "  Result: $status | total latency=$([math]::Round($totalLatency, 2)) s | chars=$($combinedAnswer.Length)"
    if ($missingRequired.Count -gt 0) { Write-Host "  Missing required: $($missingRequired -join ', ')" }
    if ($forbiddenHits.Count -gt 0) { Write-Host "  Forbidden hits: $($forbiddenHits -join ', ')" }
    if (-not $turnTypeOk) { Write-Host "  Turn types: $($actualTurnTypes -join ', ')" }

    $results += @{
        id = $case.id
        title = $case.title
        result = $status
        total_latency_seconds = [math]::Round($totalLatency, 2)
        answer_length = $combinedAnswer.Length
        combined_answer = $combinedAnswer
        turn_types = $actualTurnTypes
        checks = $checks
        missing_required = $missingRequired
        forbidden_hits = $forbiddenHits
        error = $errorMessage
        conversation_id = $conversationId
        rounds = $roundResults
        executed_at = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    }

    Start-Sleep -Seconds $IntervalSeconds
}

$latencies = @($results | Where-Object { $_["total_latency_seconds"] -ne $null } | ForEach-Object { [double]$_["total_latency_seconds"] })
$summary = @{
    total = $results.Count
    passed = @($results | Where-Object { $_["result"] -eq "通过" }).Count
    review = @($results | Where-Object { $_["result"] -ne "通过" }).Count
    avg_latency_seconds = if ($latencies.Count -gt 0) { [math]::Round(($latencies | Measure-Object -Average).Average, 2) } else { $null }
    max_latency_seconds = if ($latencies.Count -gt 0) { [math]::Round(($latencies | Measure-Object -Maximum).Maximum, 2) } else { $null }
    dify_base_url = $DifyBaseUrl
    generated_at = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
}

$report = @{
    summary = $summary
    results = $results
}

$report | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $OutputFile -Encoding utf8

Write-Host ""
Write-Host "Summary: $($summary.passed)/$($summary.total) passed, review=$($summary.review), avg latency=$($summary.avg_latency_seconds) s"
Write-Host "Saved: $OutputFile"

if ($summary.review -gt 0) {
    exit 2
}
