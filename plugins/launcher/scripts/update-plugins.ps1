# Snuffy's Launcher (the friends one): brings the launcher and each plugin up to date from GitHub without git. For each
# repository it asks GitHub for its latest commit (a 304 "not modified" answer is free of GitHub's hourly limit), and
# when there's a new one downloads the repository as a zip and puts it in place: plugins\<folder>, or for the launcher
# its own files. It keeps each repository's commit and recent history (for "what's new") in .plugin-state.json, and
# writes what happened to .update-result.json for the launcher window, which runs this in the background.
param([string]$Root)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$config = Get-Content (Join-Path $Root 'friends.json') -Raw | ConvertFrom-Json
$statePath = Join-Path $Root '.plugin-state.json'
$resultPath = Join-Path $Root '.update-result.json'
$removedPath = Join-Path $Root '.removed-plugins'
$pluginsHome = Join-Path $Root 'plugins'

# What was known: each repository's commit, its answer's tag (ETag) and its recent history
$state = @{}
if (Test-Path $statePath) {
    try {
        $saved = Get-Content $statePath -Raw | ConvertFrom-Json
        foreach ($property in $saved.PSObject.Properties) { $state[$property.Name] = $property.Value }
    } catch { }
}
$removed = if (Test-Path $removedPath) { @(Get-Content $removedPath | Where-Object { $_ }) } else { @() }
$changed = New-Object System.Collections.Generic.List[string]
$problems = New-Object System.Collections.Generic.List[string]
$headers = @{ 'User-Agent' = 'snuffys-launcher'; 'Accept' = 'application/vnd.github+json' }

function OwnerRepo($url) {
    $m = [regex]::Match($url, 'github\.com[/:]([^/]+/[^/.]+)')
    return $m.Groups[1].Value
}

# One repository: up to date, or downloaded again (into $dest; the launcher's own files when $dest is $Root)
function Update-Repo($name, $url, $branch, $dest, $isLauncher) {
    $repo = OwnerRepo $url
    $known = $state[$name]
    $ask = $headers.Clone()
    $present = $isLauncher -or (Test-Path (Join-Path $dest 'build.gradle'))
    if ($known -and $known.etag -and $present) { $ask['If-None-Match'] = $known.etag }
    try {
        $answer = Invoke-WebRequest -UseBasicParsing -Uri "https://api.github.com/repos/$repo/commits/$branch" -Headers $ask
    } catch {
        $response = $_.Exception.Response
        if ($response -and [int]$response.StatusCode -eq 304) { return }
        $problems.Add($name)
        return
    }
    $sha = ($answer.Content | ConvertFrom-Json).sha
    $etag = $answer.Headers['ETag']
    if ($known -and $known.sha -eq $sha -and $present) {
        $state[$name] = [pscustomobject]@{ sha = $sha; etag = $etag; history = $known.history }
        return
    }
    try {
        # Its recent history (each commit and its first line), then the files
        $commits = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$repo/commits?sha=$branch&per_page=30" -Headers $headers
        $history = @($commits | ForEach-Object { [pscustomobject]@{ sha = $_.sha; subject = (($_.commit.message -split "`n")[0]).Trim() } })
        $zip = Join-Path $env:TEMP "snuffys-$name.zip"
        $unzipped = Join-Path $env:TEMP "snuffys-$name"
        Invoke-WebRequest -UseBasicParsing -Uri "https://codeload.github.com/$repo/zip/refs/heads/$branch" -OutFile $zip -Headers @{ 'User-Agent' = 'snuffys-launcher' }
        if (Test-Path $unzipped) { Remove-Item $unzipped -Recurse -Force }
        Expand-Archive -Path $zip -DestinationPath $unzipped -Force
        $inner = Get-ChildItem $unzipped -Directory | Select-Object -First 1
        if ($isLauncher) {
            Copy-Item -Path (Join-Path $inner.FullName '*') -Destination $Root -Recurse -Force
        } else {
            if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
            Move-Item -Path $inner.FullName -Destination $dest
        }
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        Remove-Item $unzipped -Recurse -Force -ErrorAction SilentlyContinue
        $state[$name] = [pscustomobject]@{ sha = $sha; etag = $etag; history = $history }
        $changed.Add($name)
    } catch {
        $problems.Add($name)
    }
}

if (-not (Test-Path $pluginsHome)) { New-Item -ItemType Directory -Path $pluginsHome | Out-Null }
try { (Get-Item $pluginsHome -Force).Attributes = 'Directory, Hidden' } catch { }

if ($config.launcher) {
    Update-Repo 'launcher' $config.launcher.repo $config.launcher.branch $Root $true
}
foreach ($plugin in $config.plugins) {
    if ($removed -contains $plugin.key) { continue }
    Update-Repo $plugin.folder $plugin.repo $plugin.branch (Join-Path $pluginsHome $plugin.folder) $false
}

$state | ConvertTo-Json -Depth 6 | Set-Content -Path $statePath -Encoding UTF8
[pscustomobject]@{ changed = @($changed); problems = @($problems); at = (Get-Date).ToString('o') } |
    ConvertTo-Json | Set-Content -Path $resultPath -Encoding UTF8
