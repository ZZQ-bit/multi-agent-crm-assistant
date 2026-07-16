param(
  [string]$BaseUrl = "http://127.0.0.1:8081",
  [string]$Token = "replace-with-your-own-token",
  [string]$CustomerKeyword = "",
  [string]$OpportunityKeyword = "",
  [switch]$CreateWriteback
)

$ErrorActionPreference = "Stop"

function From-Utf8Base64($value) {
  return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value))
}

function Convert-ContentToText($content) {
  if ($content -is [byte[]]) {
    return [System.Text.Encoding]::UTF8.GetString($content)
  }
  return [string]$content
}

function Invoke-DifyTool($toolName, $headers, $payload) {
  $json = $payload | ConvertTo-Json -Depth 30
  $body = [System.Text.Encoding]::UTF8.GetBytes($json)
  $response = Invoke-WebRequest `
    -Method Post `
    -Uri "$BaseUrl/anonymous/ai/deal-desk/dify-tools/$toolName" `
    -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body $body `
    -UseBasicParsing
  $text = Convert-ContentToText $response.Content
  if ([string]::IsNullOrWhiteSpace($text)) {
    throw "$toolName returned an empty response. Restart the backend after compiling the latest Dify Tool API code."
  }
  return $text | ConvertFrom-Json
}

function Assert-ToolCode($name, $response, $expectedCode) {
  if ($response.code -ne $expectedCode) {
    $actual = $response | ConvertTo-Json -Depth 20
    throw "$name expected code $expectedCode but got: $actual"
  }
  Write-Host "PASS $name -> $($response.code)"
}

function Assert-DataKey($name, $response, $key) {
  if (-not ($response.data.PSObject.Properties.Name -contains $key)) {
    $actual = $response | ConvertTo-Json -Depth 20
    throw "$name missing data key: $key. Response: $actual"
  }
}

if ([string]::IsNullOrWhiteSpace($CustomerKeyword)) {
  $CustomerKeyword = From-Utf8Base64 "5Y2O5Lic5pm66YCg6ZuG5Zui"
}
if ([string]::IsNullOrWhiteSpace($OpportunityKeyword)) {
  $OpportunityKeyword = From-Utf8Base64 "5Y2O5Lic5pm66YCg6ZuG5ZuiQUnlrqLmnI3ljYfnuqfpobnnm64="
}

$badHeaders = @{
  "X-DIFY-TOOL-TOKEN" = "wrong-token"
  "Accept-Language" = "zh-CN"
}
$headers = @{
  "X-DIFY-TOOL-TOKEN" = $Token
  "Accept-Language" = "zh-CN"
}

$unauthorized = Invoke-DifyTool "search-customers" $badHeaders @{ keyword = $CustomerKeyword; limit = 5 }
Assert-ToolCode "anonymous bad token" $unauthorized "UNAUTHORIZED"

$customerSearch = Invoke-DifyTool "search-customers" $headers @{ keyword = $CustomerKeyword; limit = 5 }
Assert-ToolCode "anonymous search-customers" $customerSearch "OK"
$customerId = $customerSearch.data.candidates[0].id

$opportunitySearch = Invoke-DifyTool "search-opportunities" $headers @{ keyword = $OpportunityKeyword; limit = 5 }
Assert-ToolCode "anonymous search-opportunities" $opportunitySearch "OK"
$opportunityId = $opportunitySearch.data.candidates[0].id

$resolvedObject = Invoke-DifyTool "resolve-crm-object" $headers @{ objectReference = $OpportunityKeyword; limit = 5 }
Assert-ToolCode "anonymous resolve-crm-object" $resolvedObject "OK"
Assert-DataKey "anonymous resolve-crm-object" $resolvedObject "resolvedObject"
if ($resolvedObject.data.resolvedObject.objectType -ne "opportunity") {
  $actual = $resolvedObject | ConvertTo-Json -Depth 20
  throw "anonymous resolve-crm-object expected opportunity but got: $actual"
}
Write-Host "PASS anonymous resolve-crm-object -> opportunity"

$customerContext = Invoke-DifyTool "get-customer-context" $headers @{ customerId = $customerId }
Assert-ToolCode "anonymous get-customer-context" $customerContext "OK"
Assert-DataKey "anonymous get-customer-context" $customerContext "customer"

$opportunityContext = Invoke-DifyTool "get-opportunity-context" $headers @{ opportunityId = $opportunityId }
Assert-ToolCode "anonymous get-opportunity-context" $opportunityContext "OK"
foreach ($key in @("opportunity", "customer", "contacts", "recentFollowRecords", "openFollowPlans", "businessFacts", "dealTerms", "riskSignals", "missingFields", "sourceRefs")) {
  Assert-DataKey "anonymous get-opportunity-context" $opportunityContext $key
}
Write-Host "PASS anonymous get-opportunity-context package keys"

$invalidRecord = Invoke-DifyTool "create-follow-record" $headers @{ opportunityId = $opportunityId; idempotencyKey = "stage4-dify-smoke-invalid" }
Assert-ToolCode "anonymous create-follow-record validation" $invalidRecord "WRITEBACK_VALIDATION_FAILED"

$invalidPlan = Invoke-DifyTool "create-follow-plan" $headers @{ opportunityId = $opportunityId; idempotencyKey = "stage4-dify-smoke-invalid-plan" }
Assert-ToolCode "anonymous create-follow-plan validation" $invalidPlan "WRITEBACK_VALIDATION_FAILED"

if ($CreateWriteback) {
  $stamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
  $recordKey = "stage4-dify-smoke-record-$stamp"
  $planKey = "stage4-dify-smoke-plan-$stamp"
  $recordPayload = @{
    opportunityId = $opportunityId
    customerId = $customerId
    content = (From-Utf8Base64 "QUkgRGVhbCBEZXNrIOmYtuautSA0IGFub255bW91cyBzbW9rZSDot6/ov5voraHlvZXvvIzlj6/liKDpmaTjgII=")
    followMethod = "2"
    idempotencyKey = $recordKey
  }
  $record1 = Invoke-DifyTool "create-follow-record" $headers $recordPayload
  Assert-ToolCode "anonymous create-follow-record" $record1 "OK"
  $record2 = Invoke-DifyTool "create-follow-record" $headers $recordPayload
  Assert-ToolCode "anonymous create-follow-record idempotency" $record2 "OK"

  $planPayload = @{
    opportunityId = $opportunityId
    customerId = $customerId
    content = (From-Utf8Base64 "QUkgRGVhbCBEZXNrIOmYtuautSA0IGFub255bW91cyBzbW9rZSDot6/ov5vorajliJLkuIDmraTooYzliqjvvIzlj6/liKDpmaTjgII=")
    planMethod = "2"
    planTime = ([DateTimeOffset]::Now.AddDays(1).ToUnixTimeMilliseconds())
    idempotencyKey = $planKey
  }
  $plan1 = Invoke-DifyTool "create-follow-plan" $headers $planPayload
  Assert-ToolCode "anonymous create-follow-plan" $plan1 "OK"
  $plan2 = Invoke-DifyTool "create-follow-plan" $headers $planPayload
  Assert-ToolCode "anonymous create-follow-plan idempotency" $plan2 "OK"
} else {
  Write-Host "SKIP real anonymous writeback creation. Re-run with -CreateWriteback to create smoke follow record and plan."
}

Write-Host "多Agent智能助手 anonymous Dify Tool API smoke test completed."
