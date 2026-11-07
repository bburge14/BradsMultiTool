# Snuffy's Launcher (the friends one): when Java isn't installed, downloads a portable Java 17 (Eclipse Temurin, from
# Adoptium) into the launcher's java\ folder, once. Nothing is installed in Windows: the launcher points its build at
# this folder. The launcher window runs this in the background; .java-download.txt says how it went.
param([string]$Root)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$status = Join-Path $Root '.java-download.txt'
$target = Join-Path $Root 'java'
$zip = Join-Path $env:TEMP 'snuffys-java.zip'
$unzipped = Join-Path $env:TEMP 'snuffys-java'
try {
    Set-Content -Path $status -Value 'downloading'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' `
        -OutFile $zip -Headers @{ 'User-Agent' = 'snuffys-launcher' }
    if (Test-Path $unzipped) { Remove-Item $unzipped -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $unzipped -Force
    # The zip holds one folder (jdk-17...): it becomes java\
    $inner = Get-ChildItem $unzipped -Directory | Select-Object -First 1
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Move-Item -Path $inner.FullName -Destination $target
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
    Remove-Item $unzipped -Recurse -Force -ErrorAction SilentlyContinue
    Set-Content -Path $status -Value 'done'
} catch {
    Set-Content -Path $status -Value ("failed: " + $_.Exception.Message)
}
