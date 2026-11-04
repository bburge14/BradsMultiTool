# Snuffy's launcher, Dev tab: what each plugin's own GitHub repository has now, from the repository itself (not this
# PC's push cache), so every PC with an up-to-date copy shows the same: the BradsMultiTool commit it was last pushed
# from (Push to repos writes "From BradsMultiTool <commit>" in each push) and its version (the push's title, "Snuffy's
# Planner 1.4.2"). One GitHub request a repository; git's GitHub login when there is one. The launcher runs this
# hidden in the background and reads .repo-state.json.
param([string]$Root)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$out = Join-Path $Root '.repo-state.json'
$config = Get-Content (Join-Path $Root 'publish.json') -Raw | ConvertFrom-Json
$headers = @{ 'User-Agent' = 'snuffys-launcher'; 'Accept' = 'application/vnd.github+json' }
# (never a sign-in window: only a login git already has)
$env:GCM_INTERACTIVE = 'never'
$env:GIT_TERMINAL_PROMPT = '0'
try {
    $login = ("protocol=https`nhost=github.com`n`n" | git credential fill 2>$null | Out-String)
    if ($login -match 'password=(\S+)') { $headers['Authorization'] = 'Bearer ' + $Matches[1] }
} catch { }

$state = [ordered]@{}
foreach ($folder in $config.plugins.PSObject.Properties.Name) {
    $entry = $config.plugins.$folder
    if (-not $entry.repo -or $entry.repo -notmatch 'github\.com[/:]([^/]+/[^/.]+)') { continue }
    $repo = $Matches[1]
    $branch = if ($entry.branch) { $entry.branch } else { 'master' }
    try {
        $commit = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$repo/commits/$branch" -Headers $headers
        $message = [string]$commit.commit.message
        $title = ($message -split "`n")[0].Trim()
        $from = if ($message -match 'From BradsMultiTool ([0-9a-f]{7,40})') { $Matches[1] } else { $null }
        $version = if ($title -match '(\d+\.\d+\.\d+)\s*$') { $Matches[1] } else { $null }
        $state[$folder] = [pscustomobject]@{ ok = $true; from = $from; version = $version }
    } catch {
        $state[$folder] = [pscustomobject]@{ ok = $false; problem = $_.Exception.Message }
    }
}
[pscustomobject]@{ at = (Get-Date).ToString('o'); repos = $state } | ConvertTo-Json -Depth 4 | Set-Content -Path $out -Encoding UTF8
