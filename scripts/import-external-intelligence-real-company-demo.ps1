$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$propertiesPath = Join-Path $PSScriptRoot '..\runtime\cordys\cordys-crm.properties'
$sqlPath = Resolve-Path (Join-Path $PSScriptRoot '..\runtime\mysql\seed-external-intelligence-real-company-demo.sql')
$mysqlCli = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'

if (-not (Test-Path $propertiesPath)) {
    throw "Properties file not found: $propertiesPath"
}
if (-not (Test-Path $sqlPath)) {
    throw "SQL file not found: $sqlPath"
}
if (-not (Test-Path $mysqlCli)) {
    throw "MySQL CLI not found: $mysqlCli"
}

$dbConfig = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath -Encoding utf8) {
    $trimmed = $line.Trim()
    if ($trimmed -and -not $trimmed.StartsWith('#')) {
        $parts = $trimmed.Split('=', 2)
        if ($parts.Length -eq 2) {
            $dbConfig[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
}

$dbUrl = $dbConfig['spring.datasource.url']
$dbUser = $dbConfig['spring.datasource.username']
$dbPass = $dbConfig['spring.datasource.password']
if (-not $dbUrl -or -not $dbUser -or -not $dbPass) {
    throw 'Incomplete database configuration in cordys-crm.properties.'
}

$hostName = '127.0.0.1'
$port = '3306'
$dbName = 'cordys-crm'
if ($dbUrl -match 'jdbc:mysql://([^:/]+)(:(\d+))?/([^?]+)') {
    $hostName = $Matches[1]
    if ($Matches[3]) {
        $port = $Matches[3]
    }
    $dbName = $Matches[4]
}

Write-Host "Importing external intelligence demo data into DB [$dbName] ..."

$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = $mysqlCli
$processInfo.Arguments = "-h $hostName -P $port -u $dbUser `"-p$dbPass`" -D $dbName --default-character-set=utf8mb4 --batch --raw"
$processInfo.RedirectStandardInput = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $processInfo
$process.Start() | Out-Null
$process.StandardInput.Write((Get-Content -LiteralPath $sqlPath -Raw -Encoding utf8))
$process.StandardInput.Close()

$stdout = $process.StandardOutput.ReadToEnd()
$stderr = $process.StandardError.ReadToEnd()
$process.WaitForExit()

if ($process.ExitCode -ne 0) {
    throw "Import failed:`n$stderr"
}

Write-Host '[OK] External intelligence demo data imported.'
if ($stdout.Trim()) {
    Write-Host $stdout
}
