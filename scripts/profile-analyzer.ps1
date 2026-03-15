$ErrorActionPreference = "Stop"

param(
    [int]$Iterations = 5
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-user-home"
$env:TEMP = Join-Path $repoRoot ".tmp"
$env:TMP = $env:TEMP
$env:XHTML_INLINE_CHECK_PROFILE = "1"

$argsLine = 'dummy/old/report.xhtml dummy/new/report-flattened.xhtml --base-old dummy --base-new dummy --format json'

for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
    Write-Host "Iteration $iteration"
    gradle runFaceletsVerify --args="$argsLine" 2>&1 | Select-String '\[profile\]'
}
