param(
    [string]$Query = "帮我完整评审南京数据治理中心低优先级小单1期：金额 85,000，客户希望 2026-07-25 上线，付款 20% 首付 + 80% 验收后 90 天，合同要求数据安全承诺。判断是否值得推进，并给下一步动作。",
    [string]$OutputFile = "",
    [string]$DifyApiKey = $env:DIFY_API_KEY,
    [string]$DifyBaseUrl = $env:DIFY_BASE_URL,
    [int]$TimeoutSeconds = 480
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not $OutputFile) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputFile = Join-Path $PSScriptRoot "v3-node-trace-$stamp.json"
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

$inputs = @{
    bound_object_type = "opportunity"
    bound_object_id = "demo_o_064"
    bound_object_name = "南京数据治理中心低优先级小单1期"
    bound_object_source = "codex-node-trace"
    route_customer_id = "demo_c_027"
    route_opportunity_id = "demo_o_064"
    selected_object_type = ""
    selected_object_id = ""
    selected_object_name = ""
    resume_query = ""
    attachments_summary = ""
    attachment_names = ""
}

$bodyObject = @{
    query = $Query
    user = "codex-node-trace"
    response_mode = "streaming"
    inputs = $inputs
}

$bodyFile = New-TemporaryFile
$sseFile = [System.IO.Path]::ChangeExtension($OutputFile, ".sse.log")
$body = $bodyObject | ConvertTo-Json -Depth 30
Set-Content -LiteralPath $bodyFile -Value $body -Encoding utf8

Write-Host "Running Dify node trace..."
Write-Host "Output: $OutputFile"

$start = Get-Date
$curlOutput = & curl.exe --http1.1 -sS -N -m $TimeoutSeconds `
    -w "`nCURL_HTTP_CODE=%{http_code} CURL_TIME_TOTAL=%{time_total}`n" `
    -H "Authorization: Bearer $DifyApiKey" `
    -H "Content-Type: application/json; charset=utf-8" `
    --data-binary "@$bodyFile" `
    "$($DifyBaseUrl.TrimEnd('/'))/chat-messages" `
    -o $sseFile 2>&1
$exitCode = $LASTEXITCODE
$wallClock = ((Get-Date) - $start).TotalSeconds
Remove-Item -LiteralPath $bodyFile -Force -ErrorAction SilentlyContinue

$httpCode = $null
foreach ($line in @($curlOutput)) {
    if ([string]$line -match "CURL_HTTP_CODE=(\d+)") {
        $httpCode = [int]$Matches[1]
    }
}

$events = @()
$nodes = @()
$agentLogs = @()
$messages = @()
$workflow = $null

foreach ($line in @(Get-Content -LiteralPath $sseFile -Encoding utf8)) {
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
    $events += $evt.event

    if ($evt.event -eq "node_finished") {
        $d = $evt.data
        $nodeOutput = $d.outputs
        $answerLen = $null
        if ($nodeOutput -and $nodeOutput.answer) {
            $answerLen = ([string]$nodeOutput.answer).Length
        }
        elseif ($nodeOutput -and $nodeOutput.text) {
            $answerLen = ([string]$nodeOutput.text).Length
        }
        elseif ($nodeOutput -and $nodeOutput.protocol_answer) {
            $answerLen = ([string]$nodeOutput.protocol_answer).Length
        }

        $totalTokens = $null
        $totalPrice = $null
        if ($d.execution_metadata) {
            $totalTokens = $d.execution_metadata.total_tokens
            $totalPrice = $d.execution_metadata.total_price
        }
        elseif ($d.process_data -and $d.process_data.usage) {
            $totalTokens = $d.process_data.usage.total_tokens
            $totalPrice = $d.process_data.usage.total_price
        }

        $nodes += [ordered]@{
            node_id = [string]$d.node_id
            title = [string]$d.title
            node_type = [string]$d.node_type
            status = [string]$d.status
            elapsed_time = $d.elapsed_time
            total_tokens = $totalTokens
            total_price = $totalPrice
            inputs_truncated = [bool]$d.inputs_truncated
            outputs_truncated = [bool]$d.outputs_truncated
            process_data_truncated = [bool]$d.process_data_truncated
            output_length = $answerLen
            error = $d.error
        }
    }
    elseif ($evt.event -eq "agent_log") {
        $d = $evt.data
        $agentLogs += [ordered]@{
            node_id = [string]$d.node_id
            node_execution_id = [string]$d.node_execution_id
            label = [string]$d.label
            status = [string]$d.status
            error = $d.error
        }
    }
    elseif ($evt.event -eq "message") {
        $messages += [string]$evt.answer
    }
    elseif ($evt.event -eq "workflow_finished") {
        $workflow = $evt.data
    }
}

$rawAnswer = ($messages -join "")
$answerText = ""
$turnType = ""
try {
    $protocol = $rawAnswer | ConvertFrom-Json -ErrorAction Stop
    $answerText = [string]$protocol.answerText
    $turnType = [string]$protocol.turnType
}
catch {
    $answerText = $rawAnswer
}

$summary = [ordered]@{
    http_code = $httpCode
    curl_exit_code = $exitCode
    wall_clock_seconds = [math]::Round($wallClock, 2)
    workflow_status = if ($workflow) { [string]$workflow.status } else { $null }
    workflow_elapsed_time = if ($workflow) { $workflow.elapsed_time } else { $null }
    workflow_total_steps = if ($workflow) { $workflow.total_steps } else { $null }
    workflow_total_tokens = if ($workflow) { $workflow.total_tokens } else { $null }
    turn_type = $turnType
    answer_length = $answerText.Length
    node_count_finished = $nodes.Count
    agent_log_count = $agentLogs.Count
    sse_file = $sseFile
}

$report = [ordered]@{
    summary = $summary
    nodes = $nodes
    agent_logs = $agentLogs
    answer_preview = if ($answerText.Length -gt 800) { $answerText.Substring(0, 800) } else { $answerText }
    curl_output = @($curlOutput)
}

$report | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $OutputFile -Encoding utf8

Write-Host ""
Write-Host "Summary:"
$summary.GetEnumerator() | ForEach-Object { Write-Host "  $($_.Key): $($_.Value)" }
Write-Host ""
Write-Host "Node timings:"
$nodes |
    Sort-Object { if ($_.elapsed_time -eq $null) { 0 } else { [double]$_.elapsed_time } } -Descending |
    Select-Object -First 20 |
    ForEach-Object {
        $elapsed = if ($_.elapsed_time -eq $null) { "" } else { "{0:N2}s" -f [double]$_.elapsed_time }
        Write-Host ("  {0,-28} {1,8} {2,8} {3}" -f $_.title, $elapsed, $_.node_type, $_.status)
    }

if ($exitCode -ne 0 -or ($httpCode -and $httpCode -ge 400)) {
    exit 2
}
