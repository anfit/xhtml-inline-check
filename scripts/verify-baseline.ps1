Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$smokeOld = Join-Path $projectRoot "fixtures/support/smoke/old/root.xhtml"
$smokeNew = Join-Path $projectRoot "fixtures/support/smoke/new/root.xhtml"
$gradleUserHome = Join-Path $projectRoot ".gradle-user-home"
$tempRoot = Join-Path $projectRoot ".tmp/gradle"

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    Write-Error "gradle is required on PATH to run baseline verification. This repository does not yet ship a Gradle wrapper."
}

New-Item -ItemType Directory -Force -Path $gradleUserHome | Out-Null
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

$env:GRADLE_USER_HOME = $gradleUserHome
$env:TEMP = $tempRoot
$env:TMP = $tempRoot

Write-Host "Running baseline Gradle tests..."
gradle -p $projectRoot test

Write-Host "Installing the facelets-verify distribution..."
gradle -p $projectRoot installDist

Write-Host "Running the smoke CLI invocation..."
gradle -p $projectRoot runFaceletsVerify --args="$smokeOld $smokeNew"
