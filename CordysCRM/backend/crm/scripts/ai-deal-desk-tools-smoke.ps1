param(
  [string]$BaseUrl = "http://127.0.0.1:8081",
  [string]$Username = "admin",
  [string]$Password = "CordysCRM",
  [string]$OrganizationId = "100001",
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

function Invoke-JsonPost($path, $headers, $payload) {
  $json = $payload | ConvertTo-Json -Depth 30
  $body = [System.Text.Encoding]::UTF8.GetBytes($json)
  $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl$path" -Headers $headers -ContentType "application/json; charset=utf-8" -Body $body -UseBasicParsing
  $text = Convert-ContentToText $response.Content
  if ([string]::IsNullOrWhiteSpace($text)) {
    throw "$path returned an empty response. Restart the backend after compiling the latest Tool API code."
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

if ([string]::IsNullOrWhiteSpace($CustomerKeyword)) {
  $CustomerKeyword = From-Utf8Base64 "5Y2O5Lic5pm66YCg6ZuG5Zui"
}
if ([string]::IsNullOrWhiteSpace($OpportunityKeyword)) {
  $OpportunityKeyword = From-Utf8Base64 "5Y2O5Lic5pm66YCg6ZuG5ZuiQUnlrqLmnI3ljYfnuqfpobnnm64="
}

$loginPayload = @{
  username = $Username
  password = $Password
  platform = "WEB"
  authenticate = "LOCAL"
}
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -ContentType "application/json" -Body ($loginPayload | ConvertTo-Json)
$headers = @{
  "X-AUTH-TOKEN" = $login.data.sessionId
  "CSRF-TOKEN" = $login.data.csrfToken
  "Organization-Id" = $OrganizationId
  "Accept-Language" = "zh-CN"
}

$customerSearch = Invoke-JsonPost "/ai/deal-desk/tools/search-customers" $headers @{ keyword = $CustomerKeyword; limit = 5 }
Assert-ToolCode "search-customers" $customerSearch "OK"
$customerId = $customerSearch.data.candidates[0].id

$opportunitySearch = Invoke-JsonPost "/ai/deal-desk/tools/search-opportunities" $headers @{ keyword = $OpportunityKeyword; limit = 5 }
Assert-ToolCode "search-opportunities" $opportunitySearch "OK"
$opportunityId = $opportunitySearch.data.candidates[0].id

$ambiguousSearch = Invoke-JsonPost "/ai/deal-desk/tools/search-opportunities" $headers @{ keyword = $CustomerKeyword; limit = 5 }
if ($ambiguousSearch.code -notin @("OK", "OBJECT_AMBIGUOUS")) {
  throw "search-opportunities ambiguous probe expected OK or OBJECT_AMBIGUOUS but got $($ambiguousSearch.code)"
}
Write-Host "PASS search-opportunities broad keyword -> $($ambiguousSearch.code)"

$notFound = Invoke-JsonPost "/ai/deal-desk/tools/search-customers" $headers @{ keyword = (From-Utf8Base64 "5LiN5a2Y5Zyo55qE6Zi25q615LiJ5rWL6K+V5a6i5oi3"); limit = 5 }
Assert-ToolCode "search-customers not found" $notFound "OBJECT_NOT_FOUND"

$customerContext = Invoke-JsonPost "/ai/deal-desk/tools/get-customer-context" $headers @{ customerId = $customerId }
Assert-ToolCode "get-customer-context" $customerContext "OK"

$opportunityContext = Invoke-JsonPost "/ai/deal-desk/tools/get-opportunity-context" $headers @{ opportunityId = $opportunityId }
Assert-ToolCode "get-opportunity-context" $opportunityContext "OK"
foreach ($key in @("opportunity", "customer", "contacts", "recentFollowRecords", "openFollowPlans", "riskSignals", "missingFields", "sourceRefs")) {
  if (-not ($opportunityContext.data.PSObject.Properties.Name -contains $key)) {
    throw "get-opportunity-context missing key: $key"
  }
}
Write-Host "PASS get-opportunity-context package keys"

$invalidWriteback = Invoke-JsonPost "/ai/deal-desk/tools/create-follow-record" $headers @{ opportunityId = $opportunityId; idempotencyKey = "stage3-smoke-invalid" }
Assert-ToolCode "create-follow-record validation" $invalidWriteback "WRITEBACK_VALIDATION_FAILED"

if ($CreateWriteback) {
  $stamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
  $recordKey = "stage3-smoke-record-$stamp"
  $planKey = "stage3-smoke-plan-$stamp"
  $recordPayload = @{
    opportunityId = $opportunityId
    customerId = $customerId
    content = (From-Utf8Base64 "QUkgRGVhbCBEZXNrIOmYtuautSAzIHNtb2tlIHRlc3Qg6Lef6L+b6K6w5b2V77yM5Y+v5Yig6Zmk44CC")
    followMethod = "2"
    idempotencyKey = $recordKey
  }
  $record1 = Invoke-JsonPost "/ai/deal-desk/tools/create-follow-record" $headers $recordPayload
  Assert-ToolCode "create-follow-record" $record1 "OK"
  $record2 = Invoke-JsonPost "/ai/deal-desk/tools/create-follow-record" $headers $recordPayload
  Assert-ToolCode "create-follow-record idempotency" $record2 "OK"

  $planPayload = @{
    opportunityId = $opportunityId
    customerId = $customerId
    content = (From-Utf8Base64 "QUkgRGVhbCBEZXNrIOmYtuautSAzIHNtb2tlIHRlc3Qg6Lef6L+b6K6h5YiS77yM5Y+v5Yig6Zmk44CC")
    planMethod = "2"
    planTime = ([DateTimeOffset]::Now.AddDays(1).ToUnixTimeMilliseconds())
    idempotencyKey = $planKey
  }
  $plan1 = Invoke-JsonPost "/ai/deal-desk/tools/create-follow-plan" $headers $planPayload
  Assert-ToolCode "create-follow-plan" $plan1 "OK"
  $plan2 = Invoke-JsonPost "/ai/deal-desk/tools/create-follow-plan" $headers $planPayload
  Assert-ToolCode "create-follow-plan idempotency" $plan2 "OK"
} else {
  Write-Host "SKIP real writeback creation. Re-run with -CreateWriteback to create smoke follow record and plan."
}

Write-Host "多Agent智能助手 Tool API smoke test completed."
