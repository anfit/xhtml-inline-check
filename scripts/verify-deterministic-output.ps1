Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradleUserHome = Join-Path $projectRoot ".gradle-user-home"
$tempRoot = Join-Path $projectRoot ".tmp/gradle"

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    Write-Error "gradle is required on PATH to run deterministic-output verification. This repository does not yet ship a Gradle wrapper."
}

New-Item -ItemType Directory -Force -Path $gradleUserHome | Out-Null
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

$env:GRADLE_USER_HOME = $gradleUserHome
$env:TEMP = $tempRoot
$env:TMP = $tempRoot

Write-Host "Running repeated-execution deterministic CLI verification..."
gradle -p $projectRoot test --tests dev.xhtmlinlinecheck.cli.FaceletsVerifyDeterministicOutputTest
