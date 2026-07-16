$ErrorActionPreference = 'Stop'
$propertiesPath = Join-Path $PSScriptRoot "..\runtime\cordys\cordys-crm.properties"
$sqlPath = Resolve-Path (Join-Path $PSScriptRoot "..\runtime\mysql\seed-deal-desk-extra-data.sql")
$mysqlCli = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"

if (-not (Test-Path $propertiesPath)) {
    Write-Error "Properties file not found: $propertiesPath"
}
if (-not (Test-Path $sqlPath)) {
    Write-Error "SQL file not found: $sqlPath"
}
if (-not (Test-Path $mysqlCli)) {
    Write-Error "MySQL CLI not found: $mysqlCli"
}

$dbConfig = @{}
$lines = Get-Content $propertiesPath
foreach ($line in $lines) {
    $trimmed = $line.Trim()
    if ($trimmed -and -not $trimmed.StartsWith("#")) {
        $parts = $trimmed.Split("=", 2)
        if ($parts.Length -eq 2) {
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            $dbConfig[$key] = $val
        }
    }
}

$dbUrl = $dbConfig["spring.datasource.url"]
$dbUser = $dbConfig["spring.datasource.username"]
$dbPass = $dbConfig["spring.datasource.password"]

if (-not $dbUrl -or -not $dbUser -or -not $dbPass) {
    Write-Error "Incomplete database configuration in properties file."
}

$hostName = "127.0.0.1"
$port = "3306"
$dbName = "cordys-crm"
if ($dbUrl -match 'jdbc:mysql://([^:/]+)(:(\d+))?/([^?]+)') {
    $hostName = $Matches[1]
    if ($Matches[3]) { $port = $Matches[3] }
    $dbName = $Matches[4]
}

Write-Host "Importing extra demo data into DB [$dbName] (Host ${hostName}:${port}, User $dbUser) ..."

$sqlContent = Get-Content $sqlPath -Encoding UTF8 -Raw
$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = $mysqlCli
$processInfo.Arguments = "-h $hostName -P $port -u $dbUser `"-p$dbPass`" -D $dbName --default-character-set=utf8mb4"
$processInfo.RedirectStandardInput = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $processInfo
$process.Start() | Out-Null

$writer = $process.StandardInput
$writer.Write($sqlContent)
$writer.Close()

$output = $process.StandardOutput.ReadToEnd()
$errorOutput = $process.StandardError.ReadToEnd()
$process.WaitForExit()

if ($process.ExitCode -eq 0) {
    Write-Host "[OK] Extra demo data imported successfully!"
} else {
    Write-Host "[!!] Import failed. Error details:"
    Write-Host $errorOutput
    exit 1
}
