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

# The last commit's message on a repository's branch: GitHub's API (with git's login, then without it: a stale saved
# login is refused even for a public repository), else git itself (a shallow fetch into .repo-state-git, the same
# login git pulls with), so a PC whose login GitHub's API turns down still gets the real answer
function Get-Message($repo, $url, $branch) {
    $problems = @()
    $tries = if ($headers.ContainsKey('Authorization')) { @($headers, $plain) } else { @($plain) }
    foreach ($try in $tries) {
        try {
            $commit = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$repo/commits/$branch" -Headers $try
            return [string]$commit.commit.message
        } catch { $problems += $_.Exception.Message }
    }
    $git = Join-Path $Root '.repo-state-git'
    try {
        if (-not (Test-Path (Join-Path $git 'HEAD'))) { git init --quiet --bare $git 2>&1 | Out-Null }
        $ErrorActionPreference = 'Continue'
        git -C $git fetch --quiet --depth 1 $url "refs/heads/${branch}" 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $message = (git -C $git log -1 --format=%B FETCH_HEAD 2>$null | Out-String)
            if ($LASTEXITCODE -eq 0 -and $message.Trim()) { return $message }
        }
        $problems += "git couldn't fetch $url"
    } catch { $problems += $_.Exception.Message } finally { $ErrorActionPreference = 'Stop' }
    throw ($problems -join '; ')
}

$plain = @{ 'User-Agent' = $headers['User-Agent']; 'Accept' = $headers['Accept'] }
$state = [ordered]@{}
foreach ($folder in $config.plugins.PSObject.Properties.Name) {
    $entry = $config.plugins.$folder
    if (-not $entry.repo -or $entry.repo -notmatch 'github\.com[/:]([^/]+/[^/.]+)') { continue }
    $repo = $Matches[1]
    $branch = if ($entry.branch) { $entry.branch } else { 'master' }
    try {
        $message = Get-Message $repo $entry.repo $branch
        $title = ($message -split "`n")[0].Trim()
        $from = if ($message -match 'From BradsMultiTool ([0-9a-f]{7,40})') { $Matches[1] } else { $null }
        $version = if ($title -match '(\d+\.\d+\.\d+)\s*$') { $Matches[1] } else { $null }
        $state[$folder] = [pscustomobject]@{ ok = $true; from = $from; version = $version }
    } catch {
        $state[$folder] = [pscustomobject]@{ ok = $false; problem = $_.Exception.Message }
    }
}
[pscustomobject]@{ at = (Get-Date).ToString('o'); repos = $state } | ConvertTo-Json -Depth 4 | Set-Content -Path $out -Encoding UTF8
