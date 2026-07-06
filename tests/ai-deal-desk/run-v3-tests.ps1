# 多Agent智能助手 V3 Test Execution Script

param(
    [string]$TestCasesFile = "./v3-test-cases.json",
    [string]$OutputFile = "./v3-test-results.json",
    [string]$DifyApiKey = $env:DIFY_API_KEY,
    [string]$DifyBaseUrl = $env:DIFY_BASE_URL,
    [int]$IntervalSeconds = 2,
    [int]$MaxRetries = 3,
    [switch]$DryRun
)

# Configuration check
if (-not $DifyApiKey) {
    $propsFile = "../CordysCRM/backend/app/src/main/resources/commons.properties"
    if (Test-Path $propsFile) {
        $props = Get-Content $propsFile | Where-Object { $_ -match "^ai\.deal-desk\.dify\." }
        $DifyApiKey = ($props | Where-Object { $_ -match "api-key=" } | ForEach-Object { $_ -replace "ai\.deal-desk\.dify\.api-key=", "" })
        $DifyBaseUrl = ($props | Where-Object { $_ -match "base-url=" } | ForEach-Object { $_ -replace "ai\.deal-desk\.dify\.base-url=", "" })
    }
}

if (-not $DifyApiKey) {
    Write-Error "Dify API Key not found. Please provide via DIFY_API_KEY environment variable"
    exit 1
}

if (-not $DifyBaseUrl) {
    $DifyBaseUrl = "https://api.dify.ai/v1"
}

Write-Host "=== 多Agent智能助手 V3 Test Execution ===" -ForegroundColor Cyan
Write-Host "Dify Base URL: $DifyBaseUrl"
Write-Host "API Key: $($DifyApiKey.Substring(0,10))..."
Write-Host "Test Cases File: $TestCasesFile"
Write-Host "Output File: $OutputFile"
Write-Host "Request Interval: $IntervalSeconds seconds"
Write-Host ""

# Load test cases
if (-not (Test-Path $TestCasesFile)) {
    Write-Error "Test cases file not found: $TestCasesFile"
    exit 1
}

$testCases = Get-Content $TestCasesFile -Raw | ConvertFrom-Json
$cases = $testCases.test_cases

Write-Host "Total $($cases.Count) test cases" -ForegroundColor Green
Write-Host ""

if ($DryRun) {
    Write-Host "[DryRun] Showing test case list only" -ForegroundColor Yellow
    $cases | ForEach-Object {
        Write-Host "  [$($_.id)] $($_.category) - $($_.subcategory)"
    }
    exit 0
}

# Function: Infer knowledge base usage
function InferKnowledgeBaseUsage($output) {
    $kbIndicators = @(
        "根据规则", "知识库", "政策规定", "公司标准", "折扣规则",
        "交付周期标准", "合同条款标准", "验收标准", "风险阈值"
    )
    foreach ($indicator in $kbIndicators) {
        if ($output -match $indicator) { return $true }
    }
    return $false
}

# Function: Infer CRM tool usage
function InferCRMToolUsage($output) {
    $crmIndicators = @(
        "客户信息", "商机信息", "跟进记录", "跟进计划", "联系人",
        "金额", "阶段", "负责人", "CRM", "查询了", "根据CRM数据"
    )
    foreach ($indicator in $crmIndicators) {
        if ($output -match $indicator) { return $true }
    }
    return $false
}

# Function: Infer writeback draft
function InferWritebackDraft($output) {
    $writebackIndicators = @(
        "草稿", "待确认", "预览", "确认后保存", "确认后写入", "请确认", "是否保存"
    )
    foreach ($indicator in $writebackIndicators) {
        if ($output -match $indicator) { return $true }
    }
    return $false
}

# Function: Infer agents used
function InferAgentsUsed($output) {
    $agents = @()
    if ($output -match "销售评估|销售风险|赢单机会|推进动作") { $agents += "sales_evaluation" }
    if ($output -match "财务风控|财务风险|折扣|付款|回款|现金流") { $agents += "finance_control" }
    if ($output -match "交付可行性|交付风险|上线周期|定制范围|资源") { $agents += "delivery_feasibility" }
    if ($output -match "合同风险|合同条款|验收|赔付|责任边界") { $agents += "contract_risk" }
    if ($output -match "协调总结|汇总|综合|多角度|多视角") { $agents += "coordinator" }
    if ($output -match "轻量问答|泛CRM|一般功能|通用") { $agents += "lightweight_qa" }
    return $agents
}

# Function: Call Dify Chat API
function CallDifyChat($query, $boundObject, $conversationId) {
    $headers = @{
        "Authorization" = "Bearer $DifyApiKey"
        "Content-Type" = "application/json"
    }
    $body = @{
        query = $query
        user = "test-user-001"
        response_mode = "blocking"
        inputs = @{}
    }
    if ($conversationId) { $body.conversation_id = $conversationId }
    if ($boundObject) { $body.inputs.bound_object = $boundObject }
    $url = "$DifyBaseUrl/chat-messages"
    $startTime = Get-Date
    $retryCount = 0
    $response = $null
    $error = $null
    while ($retryCount -lt $MaxRetries) {
        try {
            $response = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body ($body | ConvertTo-Json -Depth 10) -TimeoutSec 120
            break
        }
        catch {
            $error = $_.Exception.Message
            $retryCount++
            if ($retryCount -lt $MaxRetries) {
                Write-Host "    Retry $retryCount/$MaxRetries..." -ForegroundColor Yellow
                Start-Sleep -Seconds 5
            }
        }
    }
    $endTime = Get-Date
    $latency = ($endTime - $startTime).TotalSeconds
    return @{ response = $response; latency = $latency; error = $error; retryCount = $retryCount }
}

# Function: Get Dify Traces API
function GetDifyMessageTraces($messageId) {
    if (-not $messageId) { return $null }
    $headers = @{
        "Authorization" = "Bearer $DifyApiKey"
        "Content-Type" = "application/json"
    }
    $url = "$DifyBaseUrl/messages/$messageId"
    try {
        $response = Invoke-RestMethod -Uri $url -Method Get -Headers $headers -TimeoutSec 30
        return $response
    }
    catch {
        Write-Host "    [Warning] Cannot get Traces: $($_.Exception.Message)" -ForegroundColor Yellow
        return $null
    }
}

# Function: Parse Dify Traces
function ParseDifyTraces($tracesResponse) {
    if (-not $tracesResponse) {
        return @{ agents_used = @(); tools_called = @(); knowledge_base_queries = @(); node_executions = @(); total_tokens = 0; total_steps = 0 }
    }
    $agentsUsed = @()
    $toolsCalled = @()
    $kbQueries = @()
    $nodeExecutions = @()
    $totalTokens = 0
    $totalSteps = 0
    if ($tracesResponse.metadata) { $totalTokens = $tracesResponse.metadata.usage.total_tokens }
    if ($tracesResponse.workflow_run) {
        $totalSteps = $tracesResponse.workflow_run.total_steps
        if ($tracesResponse.workflow_run.graph.nodes) {
            foreach ($node in $tracesResponse.workflow_run.graph.nodes) {
                if ($node.status -eq "succeeded" -or $node.status -eq "running") {
                    $nodeExecutions += @{ title = $node.title; type = $node.type; status = $node.status; started_at = $node.started_at; finished_at = $node.finished_at }
                    if ($node.title -match "Agent") { $agentsUsed += $node.title }
                    if ($node.type -eq "tool" -or $node.title -match "CRM|查询|搜索") { $toolsCalled += $node.title }
                    if ($node.type -eq "knowledge-retrieval" -or $node.title -match "知识库|检索") { $kbQueries += $node.title }
                }
            }
        }
    }
    if ($tracesResponse.agent_thoughts) {
        foreach ($thought in $tracesResponse.agent_thoughts) {
            if ($thought.tool -and $thought.tool -ne "") { $toolsCalled += $thought.tool }
        }
    }
    return @{
        agents_used = $agentsUsed | Select-Object -Unique
        tools_called = $toolsCalled | Select-Object -Unique
        knowledge_base_queries = $kbQueries | Select-Object -Unique
        node_executions = $nodeExecutions
        total_tokens = $totalTokens
        total_steps = $totalSteps
    }
}

# Execute tests
$results = @()
$successCount = 0
$failCount = 0
$totalCount = $cases.Count

foreach ($case in $cases) {
    Write-Host "[$($case.id)] $($case.category) - $($case.subcategory)" -ForegroundColor Cyan
    Write-Host "  Input: $($case.input.query)"
    $boundObject = $case.input.boundObject
    $conversationId = $null
    $result1 = CallDifyChat $case.input.query $boundObject $conversationId
    if ($result1.error) {
        Write-Host "  [FAIL] $($result1.error)" -ForegroundColor Red
        $failCount++
        $results += @{
            id = $case.id; category = $case.category; subcategory = $case.subcategory
            input = $case.input.query; boundObject = $boundObject; output = $null
            error = $result1.error; latency_seconds = $result1.latency; retry_count = $result1.retryCount
            timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
            inferences = @{ used_knowledge_base = $false; used_crm_tools = $false; generated_writeback_draft = $false; agents_used = @() }
            round = 1; conversation_id = $null
        }
        Start-Sleep -Seconds $IntervalSeconds
        continue
    }
    $output1 = $result1.response.answer
    $conversationId = $result1.response.conversation_id
    $messageId1 = $result1.response.id
    Write-Host "  Output: $($output1.Substring(0, [Math]::Min(100, $output1.Length)))..." -ForegroundColor Green
    Write-Host "  Latency: $($result1.latency.ToString('F2')) seconds"
    Write-Host "  [Traces] Getting detailed call records..." -ForegroundColor DarkGray
    $tracesResponse1 = GetDifyMessageTraces $messageId1
    $tracesData1 = ParseDifyTraces $tracesResponse1
    if ($tracesData1.total_steps -gt 0) {
        Write-Host "  [Traces] Steps: $($tracesData1.total_steps) | Tokens: $($tracesData1.total_tokens)" -ForegroundColor DarkGray
        if ($tracesData1.agents_used.Count -gt 0) { Write-Host "  [Traces] Agents: $($tracesData1.agents_used -join ', ')" -ForegroundColor DarkGray }
        if ($tracesData1.tools_called.Count -gt 0) { Write-Host "  [Traces] Tools: $($tracesData1.tools_called -join ', ')" -ForegroundColor DarkGray }
        if ($tracesData1.knowledge_base_queries.Count -gt 0) { Write-Host "  [Traces] KB: $($tracesData1.knowledge_base_queries -join ', ')" -ForegroundColor DarkGray }
    }
    $usedKB = if ($tracesData1.knowledge_base_queries.Count -gt 0) { $true } else { InferKnowledgeBaseUsage $output1 }
    $usedCRM = if ($tracesData1.tools_called.Count -gt 0) { $true } else { InferCRMToolUsage $output1 }
    $hasWritebackDraft = InferWritebackDraft $output1
    $agentsUsed = if ($tracesData1.agents_used.Count -gt 0) { $tracesData1.agents_used } else { InferAgentsUsed $output1 }
    Write-Host "  KB: $usedKB | CRM: $usedCRM | Writeback: $hasWritebackDraft"
    if ($agentsUsed.Count -gt 0) { Write-Host "  Agents: $($agentsUsed -join ', ')" }
    $successCount++
    $results += @{
        id = $case.id; category = $case.category; subcategory = $case.subcategory
        input = $case.input.query; boundObject = $boundObject; output = $output1
        latency_seconds = $result1.latency; retry_count = $result1.retryCount
        timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
        message_id = $messageId1; conversation_id = $conversationId
        inferences = @{ used_knowledge_base = $usedKB; used_crm_tools = $usedCRM; generated_writeback_draft = $hasWritebackDraft; agents_used = $agentsUsed }
        traces = @{ total_steps = $tracesData1.total_steps; total_tokens = $tracesData1.total_tokens; agents_used = $tracesData1.agents_used; tools_called = $tracesData1.tools_called; knowledge_base_queries = $tracesData1.knowledge_base_queries; node_executions = $tracesData1.node_executions }
        round = 1; expected_time_seconds = $case.expected_time_seconds; within_expected_time = $result1.latency -le $case.expected_time_seconds; expected_behavior = $case.expected_behavior
    }
    if ($case.requires_two_rounds -and $case.round_2_input -and $conversationId) {
        Write-Host "  [Round 2] $($case.round_2_input.query)" -ForegroundColor Yellow
        Start-Sleep -Seconds $IntervalSeconds
        $result2 = CallDifyChat $case.round_2_input.query $boundObject $conversationId
        if ($result2.error) {
            Write-Host "  [Round 2 FAIL] $($result2.error)" -ForegroundColor Red
        }
        else {
            $output2 = $result2.response.answer
            Write-Host "  [Round 2 Output] $($output2.Substring(0, [Math]::Min(100, $output2.Length)))..." -ForegroundColor Green
            Write-Host "  [Round 2 Latency] $($result2.latency.ToString('F2')) seconds"
            $usedKB2 = InferKnowledgeBaseUsage $output2
            $usedCRM2 = InferCRMToolUsage $output2
            $hasWritebackDraft2 = InferWritebackDraft $output2
            $agentsUsed2 = InferAgentsUsed $output2
            $results += @{
                id = $case.id; category = $case.category; subcategory = $case.subcategory
                input = $case.round_2_input.query; boundObject = $boundObject; output = $output2
                latency_seconds = $result2.latency; timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
                inferences = @{ used_knowledge_base = $usedKB2; used_crm_tools = $usedCRM2; generated_writeback_draft = $hasWritebackDraft2; agents_used = $agentsUsed2 }
                round = 2; conversation_id = $conversationId; parent_round = 1
            }
        }
    }
    Write-Host ""
    Start-Sleep -Seconds $IntervalSeconds
}

# Summary output
Write-Host "=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Total cases: $totalCount"
Write-Host "Success: $successCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
$successRate = [math]::Round($successCount * 100 / $totalCount, 1)
Write-Host "Success rate: $successRate%"

$kbUsedCount = ($results | Where-Object { $_.inferences.used_knowledge_base }).Count
$crmUsedCount = ($results | Where-Object { $_.inferences.used_crm_tools }).Count
$writebackDraftCount = ($results | Where-Object { $_.inferences.generated_writeback_draft }).Count
Write-Host "KB used: $kbUsedCount / $totalCount"
Write-Host "CRM used: $crmUsedCount / $totalCount"
Write-Host "Writeback drafts: $writebackDraftCount / $totalCount"

$tracesAvailable = ($results | Where-Object { $_.traces -and $_.traces.total_steps -gt 0 }).Count
if ($tracesAvailable -gt 0) {
    Write-Host ""
    Write-Host "=== Dify Traces Stats ===" -ForegroundColor Magenta
    Write-Host "Traces available: $tracesAvailable / $totalCount"
    $totalTokens = ($results | Where-Object { $_.traces.total_tokens -gt 0 } | ForEach-Object { $_.traces.total_tokens } | Measure-Object -Sum).Sum
    Write-Host "Total tokens: $totalTokens"
    $avgSteps = ($results | Where-Object { $_.traces.total_steps -gt 0 } | ForEach-Object { $_.traces.total_steps } | Measure-Object -Average).Average
    Write-Host "Avg steps: $([math]::Round($avgSteps, 1))"
}

$allAgents = ($results | Where-Object { $_.inferences.agents_used.Count -gt 0 } | ForEach-Object { $_.inferences.agents_used })
$agentCounts = $allAgents | Group-Object | Sort-Object Count -Descending
Write-Host ""
Write-Host "Agent usage:"
foreach ($agentGroup in $agentCounts) { Write-Host "  $($agentGroup.Name): $($agentGroup.Count)" }

$latencies = $results | Where-Object { $_.latency_seconds } | ForEach-Object { $_.latency_seconds }
$avgLatency = ($latencies | Measure-Object -Average).Average
$maxLatency = ($latencies | Measure-Object -Maximum).Maximum
$minLatency = ($latencies | Measure-Object -Minimum).Minimum
Write-Host "Latency distribution:"
Write-Host "  Avg: $([math]::Round($avgLatency, 2)) seconds"
Write-Host "  Max: $([math]::Round($maxLatency, 2)) seconds"
Write-Host "  Min: $([math]::Round($minLatency, 2)) seconds"

$overTimeCount = ($results | Where-Object { $_.within_expected_time -eq $false }).Count
Write-Host "Over expected time: $overTimeCount / $totalCount"

# Save results
$outputJson = @{
    version = "3.0"
    execution_summary = @{
        total_cases = $totalCount
        success_count = $successCount
        fail_count = $failCount
        success_rate = "$successRate%"
        kb_used_count = $kbUsedCount
        crm_used_count = $crmUsedCount
        writeback_draft_count = $writebackDraftCount
        avg_latency_seconds = $avgLatency
        max_latency_seconds = $maxLatency
        min_latency_seconds = $minLatency
        over_expected_time_count = $overTimeCount
        execution_date = (Get-Date -Format "yyyy-MM-dd")
        dify_base_url = $DifyBaseUrl
        traces_available_count = $tracesAvailable
        total_tokens = $totalTokens
        avg_steps = $avgSteps
    }
    results = $results
}
$outputJson | ConvertTo-Json -Depth 15 | Out-File $OutputFile -Encoding UTF8
Write-Host ""
Write-Host "Results saved to: $OutputFile" -ForegroundColor Green