param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label,
    [string]$JavaHome = 'C:/project/jdk-17.0.10',
    [string]$Database = 'jiki_perf_20260929',
    [ValidateRange(1, 65535)][int]$DatabasePort = 3307,
    [string]$CredentialsFile,
    [string]$K6Path,
    [ValidateRange(1, 300)][int]$Seconds = 10
)
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($Database -notmatch '^jiki_perf_[a-zA-Z0-9_]+$') { throw 'Dedicated jiki_perf_* database required.' }
if ($CredentialsFile) {
    foreach ($line in Get-Content -LiteralPath $CredentialsFile) {
        if ($line -match '^spring.datasource.username=(.*)$') { $env:PERF_DB_USER = $Matches[1].Trim() }
        if ($line -match '^spring.datasource.password=(.*)$') { $env:PERF_DB_PASSWORD = $Matches[1].Trim() }
    }
}
if (-not $env:PERF_DB_USER -or $null -eq $env:PERF_DB_PASSWORD) { throw 'Set PERF_DB_USER and PERF_DB_PASSWORD, or supply CredentialsFile.' }
$env:JAVA_HOME = $JavaHome
if (-not $K6Path) {
    $toolRoot = [IO.Path]::GetFullPath((Join-Path $projectPath '../../.tools/k6'))
    $K6Path = (Get-ChildItem -LiteralPath $toolRoot -Recurse -Filter k6.exe | Select-Object -First 1).FullName
}
if (-not $K6Path -or -not (Test-Path -LiteralPath $K6Path)) { throw 'k6 executable required.' }
$env:PERF_K6 = $K6Path
$env:PERF_DB_URL = "jdbc:mariadb://127.0.0.1:${DatabasePort}/$Database"
$resultsPath = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Path $resultsPath -Force | Out-Null
if (Test-Path -LiteralPath (Join-Path $resultsPath $Label)) { throw 'Label already exists; use a new label to preserve evidence.' }
Push-Location $projectPath
try {
    & ./gradlew.bat performanceTest "-PperfLabel=$Label" "-PperfSeconds=$Seconds" --no-daemon --console=plain 2>&1 |
        Tee-Object -FilePath (Join-Path $resultsPath "$Label-build.log")
    if ($LASTEXITCODE -ne 0) { throw "Benchmark failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
    Remove-Item Env:PERF_DB_PASSWORD -ErrorAction SilentlyContinue
}
