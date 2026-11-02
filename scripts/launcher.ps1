# Snuffy's launcher window (Launch-BradsMultiTool.bat opens it), two tabs. Dev: pushes the plugins to their own
# repositories and publishes them to RuneLite's Plugin Hub (docs/ROLLOUT.md). Play: updates the plugins (on opening, then checked every
# minute in the background while it's open, pulled as soon as there's one), warns (red, a bold Relaunch, a Windows
# notification) when the RuneLite that's open was built from an older version, Relaunch closes and restarts RuneLite with any update, checks the setup (RuneLite
# installed, session saving on in RuneLite (configure), a Jagex session saved, Java, git) with a button to fix
# each, and walks through saving a session; lets you pick which plugins RuneLite loads, and launches it. The Toolbelt has every separate
# plugin in it, so it's either the Toolbelt on its own or any of the separate ones. Launching runs hidden with a
# progress bar (building, starting, running; the log a click away) and the window stays open while RuneLite runs.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
# Finding RuneLite's "Are you sure you want to exit?" box, for Relaunch to answer it
Add-Type -Namespace Snuffys -Name Win -MemberDefinition @'
[DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr FindWindowEx(IntPtr parent, IntPtr after, string cls, string title);
[DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd, int show);
[DllImport("kernel32.dll")] public static extern IntPtr GetConsoleWindow();
'@
# No console window behind the launcher, however it was started (the .bat, the shortcut): only the launcher shows.
# Everything it runs (the build, git, Python) runs hidden too.
$consoleWindow = [Snuffys.Win]::GetConsoleWindow()
if ($consoleWindow -ne [IntPtr]::Zero) { [void][Snuffys.Win]::ShowWindow($consoleWindow, 0) }
[System.Windows.Forms.Application]::EnableVisualStyles()

$root = Split-Path $PSScriptRoot -Parent
$choiceFile = Join-Path $root '.launch-choice'
# The version (commit) RuneLite was last launched with, to tell when the open one is out of date
$runningFile = Join-Path $root '.running-commit'
$creds = Join-Path $env:USERPROFILE '.runelite\credentials.properties'
$green = [System.Drawing.Color]::FromArgb(0, 140, 60)
$red = [System.Drawing.Color]::FromArgb(190, 30, 30)
$gray = [System.Drawing.Color]::Gray

function New-Label($text, $x, $y, $width = 400, $height = 18) {
    $label = New-Object System.Windows.Forms.Label
    $label.Text = $text
    $label.Location = New-Object System.Drawing.Point($x, $y)
    $label.Size = New-Object System.Drawing.Size($width, $height)
    return $label
}

function New-Group($text, $y, $height) {
    $group = New-Object System.Windows.Forms.GroupBox
    $group.Text = $text
    $group.Location = New-Object System.Drawing.Point(12, $y)
    $group.Size = New-Object System.Drawing.Size(436, $height)
    $playTab.Controls.Add($group)
    return $group
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Snuffy's Launcher"
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.StartPosition = 'CenterScreen'
$form.ClientSize = New-Object System.Drawing.Size(468, 700)
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9)
# Two tabs: Play (updates, setup, launching) and Dev (pushing the plugins to their repositories and RuneLite)
$tabs = New-Object System.Windows.Forms.TabControl
$tabs.Dock = 'Fill'
$form.Controls.Add($tabs)
$playTab = New-Object System.Windows.Forms.TabPage
$playTab.Text = 'Play'
$playTab.UseVisualStyleBackColor = $true
$devTab = New-Object System.Windows.Forms.TabPage
$devTab.Text = 'Dev'
$devTab.UseVisualStyleBackColor = $true
$tabs.TabPages.Add($playTab)
$tabs.TabPages.Add($devTab)
$icon = Join-Path $root 'assets\launcher.ico'
if (Test-Path $icon) {
    $form.Icon = New-Object System.Drawing.Icon($icon)
}

# ---- Updates ----
$updates = New-Group 'Updates' 10 130
$updateStatus = New-Label 'Checking for updates...' 12 22 330
$updates.Controls.Add($updateStatus)
$updateLog = New-Object System.Windows.Forms.TextBox
$updateLog.Multiline = $true
$updateLog.ReadOnly = $true
$updateLog.ScrollBars = 'Vertical'
$updateLog.Location = New-Object System.Drawing.Point(12, 46)
$updateLog.Size = New-Object System.Drawing.Size(410, 72)
$updates.Controls.Add($updateLog)
$checkAgain = New-Object System.Windows.Forms.Button
$checkAgain.Text = 'Check again'
$checkAgain.Location = New-Object System.Drawing.Point(342, 16)
$checkAgain.Size = New-Object System.Drawing.Size(80, 25)
$updates.Controls.Add($checkAgain)

function Update-Plugins {
    $updateStatus.Text = 'Checking for updates...'
    $updateStatus.ForeColor = [System.Drawing.SystemColors]::ControlText
    $updateLog.Text = ''
    $form.Refresh()
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        $updateStatus.Text = "git isn't installed: launching the version you have."
        $updateStatus.ForeColor = $red
        return
    }
    $old = (git -C $root rev-parse HEAD 2>$null)
    # From the branch's upstream, else origin's branch of the same name (a checkout without one set still updates)
    git -C $root rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $out = (git -C $root pull --ff-only 2>&1 | Out-String)
    } else {
        $branch = (git -C $root rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
        $out = (git -C $root pull --ff-only origin $branch 2>&1 | Out-String)
    }
    if ($LASTEXITCODE -ne 0) {
        $updateStatus.Text = "Couldn't update (offline, or files changed here)."
        $updateStatus.ForeColor = $red
        $updateLog.Text = $out.Trim()
        return
    }
    $new = (git -C $root rev-parse HEAD 2>$null)
    if ($old -eq $new) {
        $updateStatus.Text = 'Up to date.'
        $updateStatus.ForeColor = $green
        $updateLog.Text = (git -C $root log -1 --format='Latest: %s (%cr)' 2>$null | Out-String).Trim()
    } else {
        $updateStatus.Text = 'Updated. New since last time:'
        $updateStatus.ForeColor = $green
        $updateLog.Text = (git -C $root log --format='- %s' "$old..$new" 2>$null | Out-String).Trim()
    }
}
$checkAgain.Add_Click({ Update-Plugins })

# ---- Setup checklist: RuneLite, its session setting, the saved session, Java, git ----
$runeLiteChoice = Join-Path $root '.launch-runelite'
$shell = New-Object -ComObject WScript.Shell

# Where RuneLite is: the one you picked with Find it..., its usual folders, its uninstall entry, its Start menu
# shortcuts, a running RuneLite, or under the Jagex Launcher's folder. Null if none of them has it.
function Find-RuneLite {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (Test-Path $runeLiteChoice) { $candidates.Add((Get-Content $runeLiteChoice -Raw).Trim()) }
    foreach ($base in @($env:LOCALAPPDATA, $env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ($base) { $candidates.Add((Join-Path $base 'RuneLite\RuneLite.exe')) }
    }
    foreach ($key in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                       'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                       'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*')) {
        Get-ItemProperty $key -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'RuneLite*' } | ForEach-Object {
            if ($_.InstallLocation) { $candidates.Add((Join-Path $_.InstallLocation 'RuneLite.exe')) }
            if ($_.DisplayIcon) { $candidates.Add(($_.DisplayIcon -split ',')[0].Trim('"')) }
        }
    }
    foreach ($menu in @((Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'),
                        (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs'))) {
        Get-ChildItem $menu -Recurse -Filter 'RuneLite*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates.Add($shell.CreateShortcut($_.FullName).TargetPath)
        }
    }
    Get-Process -Name 'RuneLite' -ErrorAction SilentlyContinue | ForEach-Object { if ($_.Path) { $candidates.Add($_.Path) } }
    foreach ($jagex in @((Join-Path ${env:ProgramFiles(x86)} 'Jagex Launcher'), (Join-Path $env:ProgramFiles 'Jagex Launcher'),
                         (Join-Path $env:ProgramData 'Jagex'))) {
        if ($jagex -and (Test-Path $jagex)) {
            Get-ChildItem $jagex -Recurse -Depth 4 -Filter 'RuneLite.exe' -ErrorAction SilentlyContinue |
                ForEach-Object { $candidates.Add($_.FullName) }
        }
    }
    return $candidates | Where-Object { $_ -and ($_ -like '*RuneLite.exe') -and (Test-Path $_) } | Select-Object -First 1
}

$script:rlExe = $null
$script:rlSettings = $null
$jagexLauncher = @(
    (Join-Path ${env:ProgramFiles(x86)} 'Jagex Launcher\JagexLauncher.exe'),
    (Join-Path $env:ProgramFiles 'Jagex Launcher\JagexLauncher.exe')
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
$flag = '--insecure-write-credentials'

$setup = New-Group 'Setup' 150 160
$rows = @{}
$rowY = 20
foreach ($key in 'runelite', 'flag', 'session', 'java', 'git') {
    $mark = New-Label '' 10 ($rowY + 4) 18
    $mark.Font = New-Object System.Drawing.Font('Segoe UI Symbol', 9, [System.Drawing.FontStyle]::Bold)
    $text = New-Label '' 28 ($rowY + 4) 290 18
    $button = New-Object System.Windows.Forms.Button
    $button.Location = New-Object System.Drawing.Point(322, $rowY)
    $button.Size = New-Object System.Drawing.Size(102, 24)
    $button.Visible = $false
    $setup.Controls.AddRange(@($mark, $text, $button))
    $rows[$key] = @{ Mark = $mark; Text = $text; Button = $button }
    $rowY += 27
}
$tip = New-Object System.Windows.Forms.ToolTip

function Set-Row($key, $ok, $text, $buttonText = $null, $hint = $null) {
    $row = $rows[$key]
    $row.Mark.Text = if ($ok) { [char]0x2714 } else { [char]0x2716 }
    $row.Mark.ForeColor = if ($ok) { $green } else { $red }
    $row.Text.Text = $text
    $tip.SetToolTip($row.Text, $hint)
    $row.Button.Visible = [bool]$buttonText
    if ($buttonText) { $row.Button.Text = $buttonText }
}

# The client arguments "RuneLite (configure)" saved: Ok is false when its settings can't be read. (Wrapped in a
# hashtable: PowerShell would turn an empty list returned on its own into $null.)
function Get-ClientArguments {
    if (-not $script:rlSettings -or -not (Test-Path $script:rlSettings)) { return @{ Ok = $true; Args = @() } }
    try {
        $json = Get-Content $script:rlSettings -Raw | ConvertFrom-Json
        return @{ Ok = $true; Args = @($json.clientArguments | Where-Object { $_ }) }
    } catch {
        return @{ Ok = $false; Args = @() }
    }
}

# Only reads the character's name and when the file was saved; the session itself is never shown
function Check-Setup {
    $script:rlExe = Find-RuneLite
    $hasRuneLite = [bool]$script:rlExe
    if ($hasRuneLite) {
        # RuneLite keeps its launcher settings (what configure saves) next to RuneLite.exe
        $script:rlSettings = Join-Path (Split-Path $script:rlExe -Parent) 'settings.json'
        Set-Row 'runelite' $true 'RuneLite is installed.' $null $script:rlExe
    } else {
        $script:rlSettings = $null
        Set-Row 'runelite' $false "Couldn't find RuneLite." 'Find it...' 'Point to RuneLite.exe, or get RuneLite from runelite.net if you need it'
    }

    $arguments = Get-ClientArguments
    if (-not $arguments.Ok) {
        Set-Row 'flag' $false "Couldn't read RuneLite's settings." 'Open configure' $script:rlSettings
    } elseif ($arguments.Args -contains $flag) {
        Set-Row 'flag' $true 'Session saving is on in RuneLite (configure).' $null $flag
    } elseif ($hasRuneLite) {
        Set-Row 'flag' $false "Session saving is off in RuneLite (configure)." 'Turn it on' "Adds $flag to RuneLite's client arguments"
    } else {
        Set-Row 'flag' $false 'Session saving: install RuneLite first.'
    }

    if (Test-Path $creds) {
        $who = (Select-String -Path $creds -Pattern '^JX_DISPLAY_NAME=(.*)$' | Select-Object -First 1)
        $name = if ($who) { $who.Matches[0].Groups[1].Value } else { 'your account' }
        $saved = (Get-Item $creds).LastWriteTime.ToString('g')
        Set-Row 'session' $true "Session saved for $name ($saved)." 'Refresh it' "If RuneLite asks for an email and password instead of showing Play, it has expired: refresh it"
    } else {
        Set-Row 'session' $false 'No Jagex session saved yet.' 'Save one' 'Log in once through the Jagex Launcher to save it'
    }

    if (Get-Command java -ErrorAction SilentlyContinue) {
        $version = (cmd /c 'java -version 2>&1' | Select-Object -First 1)
        Set-Row 'java' $true "Java: $version" $null
    } else {
        Set-Row 'java' $false "Java isn't installed (needed to build)." 'Get Java' 'Install a JDK 11 or newer (Temurin 17 recommended)'
    }

    if (Get-Command git -ErrorAction SilentlyContinue) {
        Set-Row 'git' $true 'git is installed (for updates).'
    } else {
        Set-Row 'git' $false "git isn't installed (no updates)." 'Get git'
    }
    $launch.Enabled = [bool](Get-Command java -ErrorAction SilentlyContinue)
}

# Point to RuneLite.exe (remembered), or get RuneLite
$rows['runelite'].Button.Add_Click({
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = 'Where is RuneLite.exe?'
    $dialog.Filter = 'RuneLite|RuneLite.exe|Programs|*.exe'
    if ($dialog.ShowDialog() -eq 'OK') {
        Set-Content -Path $runeLiteChoice -Value $dialog.FileName
    } elseif ([System.Windows.Forms.MessageBox]::Show("Don't have RuneLite? Open runelite.net to get it?", "Snuffy's", 'YesNo') -eq 'Yes') {
        Start-Process 'https://runelite.net/'
    }
    Check-Setup
})
$rows['java'].Button.Add_Click({ Start-Process 'https://adoptium.net/temurin/releases/?version=17' })
$rows['git'].Button.Add_Click({ Start-Process 'https://git-scm.com/download/win' })

# Turn on session saving: adds the argument to RuneLite's own settings (backed up first), or opens configure
$rows['flag'].Button.Add_Click({
    if (Get-Process -Name 'RuneLite' -ErrorAction SilentlyContinue) {
        [System.Windows.Forms.MessageBox]::Show('Close RuneLite first, then try again.', "Snuffy's") | Out-Null
        return
    }
    $answer = [System.Windows.Forms.MessageBox]::Show(
        "Add $flag to RuneLite's client arguments?`n`nThat's the setting under Start menu > RuneLite (configure) > Client arguments. It makes RuneLite save your Jagex session when you launch it from the Jagex Launcher, so the dev client can log in. A backup of RuneLite's settings is kept (settings.json.bak).`n`nNo: open RuneLite (configure) to add it yourself.",
        'Turn on session saving', 'YesNoCancel')
    if ($answer -eq 'Yes') {
        try {
            if (Test-Path $script:rlSettings) {
                Copy-Item $script:rlSettings "$script:rlSettings.bak" -Force
                $json = Get-Content $script:rlSettings -Raw | ConvertFrom-Json
            } else {
                $json = [pscustomobject]@{}
            }
            $current = @($json.clientArguments | Where-Object { $_ })
            $updated = @($current + $flag)
            if ($json.PSObject.Properties.Name -contains 'clientArguments') {
                $json.clientArguments = $updated
            } else {
                $json | Add-Member -NotePropertyName 'clientArguments' -NotePropertyValue $updated
            }
            # Without a byte order mark: RuneLite reads the file as plain UTF-8 and would reset its settings otherwise
            [System.IO.File]::WriteAllText($script:rlSettings, ($json | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding $false))
        } catch {
            [System.Windows.Forms.MessageBox]::Show("Couldn't change RuneLite's settings: $($_.Exception.Message)`n`nAdd it yourself in RuneLite (configure).", "Snuffy's") | Out-Null
            Start-Process $script:rlExe -ArgumentList '--configure'
        }
    } elseif ($answer -eq 'No') {
        Start-Process $script:rlExe -ArgumentList '--configure'
        [System.Windows.Forms.MessageBox]::Show("In the window that opened, add  $flag  to Client arguments and click Save.", "Snuffy's") | Out-Null
    }
    Check-Setup
})

# Save (or refresh) a session: open the Jagex Launcher, then watch for the file
$rows['session'].Button.Add_Click({
    if (-not ((Get-ClientArguments).Args -contains $flag)) {
        [System.Windows.Forms.MessageBox]::Show('Turn on session saving first (the row above).', "Snuffy's") | Out-Null
        return
    }
    if ($jagexLauncher) { Start-Process $jagexLauncher }
    [System.Windows.Forms.MessageBox]::Show(
        ($(if ($jagexLauncher) { 'The Jagex Launcher is opening.' } else { "Open the Jagex Launcher (it wasn't found in Program Files)." }) +
        "`n`nPick RuneLite as the game client, press Play and log in once. When RuneLite is open you can close it: this window ticks the row as soon as the session is saved."),
        'Save a Jagex session') | Out-Null
    $script:sessionBefore = if (Test-Path $creds) { (Get-Item $creds).LastWriteTime } else { $null }
    $watch.Start()
})
$watch = New-Object System.Windows.Forms.Timer
$watch.Interval = 2000
$watch.Add_Tick({
    if ((Test-Path $creds) -and ((Get-Item $creds).LastWriteTime -ne $script:sessionBefore)) {
        $watch.Stop()
        Check-Setup
    }
})

# ---- Plugins ----
$pick = New-Group 'Plugins to load' 320 214
$plugins = [ordered]@{
    'skilling' = 'Skilling Helper'
    'pvm'      = 'PvM/Slayer'
    'gemcrab'  = 'GemCrab Tracker'
    'tick'     = 'Tick Counter'
    'planner'  = 'Planner'
    'alerts'   = 'Alerts'
}
$last = 'toolbelt'
if (Test-Path $choiceFile) {
    $saved = (Get-Content $choiceFile -Raw).Trim()
    if ($saved) { $last = $saved }
}
$lastNames = $last.ToLower() -split '[,+ ]+'

$toolbelt = New-Object System.Windows.Forms.RadioButton
$toolbelt.Text = "Snuffy's Toolbelt (everything in one)"
$toolbelt.Location = New-Object System.Drawing.Point(14, 22)
$toolbelt.AutoSize = $true
$pick.Controls.Add($toolbelt)
$note = New-Label 'Runs on its own: it already includes every separate plugin.' 32 44 390
$note.ForeColor = $gray
$pick.Controls.Add($note)
$separate = New-Object System.Windows.Forms.RadioButton
$separate.Text = 'Separate plugins:'
$separate.Location = New-Object System.Drawing.Point(14, 70)
$separate.AutoSize = $true
$pick.Controls.Add($separate)

$boxes = @{}
# Two columns of three
$i = 0
foreach ($key in $plugins.Keys) {
    $box = New-Object System.Windows.Forms.CheckBox
    $box.Text = $plugins[$key]
    $box.Location = New-Object System.Drawing.Point((34 + [math]::Floor($i / 3) * 190), (94 + ($i % 3) * 22))
    $box.AutoSize = $true
    $box.Checked = ($lastNames -contains $key) -or ($lastNames -contains 'separate')
    $pick.Controls.Add($box)
    $boxes[$key] = $box
    $i++
}
$updateBoxes = { foreach ($box in $boxes.Values) { $box.Enabled = $separate.Checked } }
$toolbelt.Add_CheckedChanged($updateBoxes)
$separate.Add_CheckedChanged($updateBoxes)
if ($lastNames -contains 'toolbelt') { $toolbelt.Checked = $true } else { $separate.Checked = $true }
& $updateBoxes

# ---- Launch ----
$launch = New-Object System.Windows.Forms.Button
$launch.Text = 'Launch RuneLite'
$launch.Location = New-Object System.Drawing.Point(232, 600)
$launch.Size = New-Object System.Drawing.Size(126, 32)
$launch.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
$playTab.Controls.Add($launch)
$form.AcceptButton = $launch
$close = New-Object System.Windows.Forms.Button
$close.Text = 'Close'
$close.Location = New-Object System.Drawing.Point(366, 600)
$close.Size = New-Object System.Drawing.Size(82, 32)
$close.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$playTab.Controls.Add($close)
$form.CancelButton = $close

# Progress: building, starting RuneLite, running. The build runs hidden; its output goes to a log you can open.
$launchStatus = New-Label 'Ready.' 14 544 330
$playTab.Controls.Add($launchStatus)
$showLog = New-Object System.Windows.Forms.LinkLabel
$showLog.Text = 'Show log'
$showLog.Location = New-Object System.Drawing.Point(386, 544)
$showLog.AutoSize = $true
$showLog.Visible = $false
$playTab.Controls.Add($showLog)
$progress = New-Object System.Windows.Forms.ProgressBar
$progress.Location = New-Object System.Drawing.Point(14, 566)
$progress.Size = New-Object System.Drawing.Size(434, 18)
$progress.Style = 'Continuous'
$playTab.Controls.Add($progress)

$logFile = Join-Path $env:TEMP "snuffys-runelite.log"
$showLog.Add_LinkClicked({ if (Test-Path $logFile) { Start-Process notepad.exe -ArgumentList "`"$logFile`"" } })
$script:runner = $null
$script:logOffset = 0
$script:tasks = 0
$script:stage = 'idle'

function Set-Status($text, $color, $value) {
    $launchStatus.Text = $text
    $launchStatus.ForeColor = $color
    if ($value -ge 0) { $progress.Value = [Math]::Min(100, $value) }
}

# What's new in the log since last time (read without locking it: the build is still writing)
function Read-NewLog {
    if (-not (Test-Path $logFile)) { return '' }
    try {
        $stream = [System.IO.File]::Open($logFile, 'Open', 'Read', 'ReadWrite')
        try {
            if ($stream.Length -le $script:logOffset) { return '' }
            $stream.Seek($script:logOffset, 'Begin') | Out-Null
            $reader = New-Object System.IO.StreamReader($stream)
            $text = $reader.ReadToEnd()
            $script:logOffset = $stream.Length
            return $text
        } finally {
            $stream.Dispose()
        }
    } catch {
        return ''
    }
}

function RuneLite-Window {
    return Get-Process -Name 'java', 'javaw' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*RuneLite*' } | Select-Object -First 1
}

$watcher = New-Object System.Windows.Forms.Timer
$watcher.Interval = 500
$watcher.Add_Tick({
    $new = Read-NewLog
    if ($new) {
        $script:tasks += ([regex]::Matches($new, '(?m)^> Task :')).Count
        if ($new -match 'BUILD FAILED|FAILURE: Build failed') { $script:stage = 'failed' }
        elseif ($script:stage -eq 'building' -and $new -match "Loading Snuffy's") { $script:stage = 'starting' }
    }
    switch ($script:stage) {
        'building' { Set-Status "Building Snuffy's plugins..." ([System.Drawing.SystemColors]::ControlText) ([Math]::Min(65, 5 + 7 * $script:tasks)) }
        'starting' {
            if (RuneLite-Window) {
                $script:stage = 'running'
                Set-Status 'RuneLite is running.' $green 100
            } else {
                Set-Status 'Starting RuneLite...' ([System.Drawing.SystemColors]::ControlText) ([Math]::Min(95, [Math]::Max($progress.Value, 70) + 1))
            }
        }
    }
    if ($script:stage -eq 'failed') {
        $watcher.Stop()
        Set-Status "Couldn't build or start RuneLite: see the log." $red -1
        $showLog.Visible = $true
        $launch.Enabled = $true
        return
    }
    if ($script:runner -and $script:runner.HasExited) {
        $watcher.Stop()
        $launch.Enabled = $true
        $relaunch.Enabled = $true
        if ($script:stage -eq 'running') {
            Set-Status 'RuneLite closed.' ([System.Drawing.SystemColors]::ControlText) 0
        } else {
            Set-Status "RuneLite stopped before it opened: see the log." $red -1
            $showLog.Visible = $true
        }
        $script:stage = 'idle'
    }
})

function Start-RuneLite {
    if ($separate.Checked -and -not ($boxes.Values | Where-Object { $_.Checked })) {
        [System.Windows.Forms.MessageBox]::Show('Tick at least one plugin, or pick the Toolbelt.', "Snuffy's") | Out-Null
        return
    }
    $load = if ($toolbelt.Checked) { 'toolbelt' } else { ($plugins.Keys | Where-Object { $boxes[$_].Checked }) -join ',' }
    Set-Content -Path $choiceFile -Value $load
    # Hidden: no console window; everything it prints goes to the log
    Set-Content -Path $logFile -Value ''
    $script:logOffset = 0
    $script:tasks = 0
    $script:stage = 'building'
    $showLog.Visible = $true
    $launch.Enabled = $false
    Set-Status "Building Snuffy's plugins..." ([System.Drawing.SystemColors]::ControlText) 5
    Set-Content -Path $runningFile -Value (git -C $root rev-parse HEAD 2>$null)
    $script:warnedFor = $null
    $command = "gradlew.bat --console=plain runBrad `"-Pload=$load`" > `"$logFile`" 2>&1"
    $script:runner = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $command -WorkingDirectory $root -WindowStyle Hidden -PassThru
    $watcher.Start()
    $relaunch.Enabled = $true
    $relaunch.Font = New-Object System.Drawing.Font('Segoe UI', 9)
}
$launch.Add_Click({ Start-RuneLite })

function RuneLite-Running {
    return $script:runner -and -not $script:runner.HasExited
}

# ---- Relaunch: close RuneLite (as its X button does, forced if it hangs), update, launch again ----
$relaunch = New-Object System.Windows.Forms.Button
$relaunch.Text = 'Relaunch'
$relaunch.Location = New-Object System.Drawing.Point(132, 600)
$relaunch.Size = New-Object System.Drawing.Size(92, 32)
$relaunch.Enabled = $false
$playTab.Controls.Add($relaunch)
$script:closingSince = $null
$closer = New-Object System.Windows.Forms.Timer
$closer.Interval = 500
# RuneLite's exit warning (its Exit warning setting): Relaunch answers OK for you. Only a window titled "Exit"
# belonging to RuneLite's own process, brought to the front first, so Enter can't land anywhere else
function Confirm-Exit {
    $window = RuneLite-Window
    if (-not $window) { return }
    $hwnd = [IntPtr]::Zero
    while (($hwnd = [Snuffys.Win]::FindWindowEx([IntPtr]::Zero, $hwnd, [NullString]::Value, 'Exit')) -ne [IntPtr]::Zero) {
        $owner = 0
        [void][Snuffys.Win]::GetWindowThreadProcessId($hwnd, [ref]$owner)
        if ($owner -eq $window.Id -and [Snuffys.Win]::SetForegroundWindow($hwnd)) {
            Start-Sleep -Milliseconds 150
            [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
            return
        }
    }
}

# Whether any RuneLite is open: the one we started, or one opened before this window
function RuneLite-Open {
    return (RuneLite-Running) -or [bool](RuneLite-Window)
}

$closer.Add_Tick({
    Confirm-Exit
    if (-not (RuneLite-Open)) {
        $closer.Stop()
        Update-Plugins
        Start-RuneLite
        return
    }
    # Still open after 20 seconds: end it and the build behind it
    if (((Get-Date) - $script:closingSince).TotalSeconds -gt 20) {
        if (RuneLite-Running) {
            & taskkill.exe /PID $script:runner.Id /T /F | Out-Null
        } else {
            $window = RuneLite-Window
            if ($window) { & taskkill.exe /PID $window.Id /T /F | Out-Null }
        }
    }
})
$relaunch.Add_Click({
    if (-not (RuneLite-Open)) {
        Update-Plugins
        Start-RuneLite
        return
    }
    Set-Status 'Closing RuneLite...' ([System.Drawing.SystemColors]::ControlText) 0
    $relaunch.Enabled = $false
    $window = RuneLite-Window
    if ($window) { [void]$window.CloseMainWindow() }
    $script:closingSince = Get-Date
    $closer.Start()
})

# ---- Updates while the launcher's open: fetched every minute in the background (the window never freezes),
# pulled as soon as there's one; and the open RuneLite checked against the version it was built from ----

# The branch updates come from: its upstream, else origin's branch of the same name
function Get-Upstream {
    $up = (git -C $root rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null)
    if ($LASTEXITCODE -eq 0 -and $up) { return $up.Trim() }
    $branch = (git -C $root rev-parse --abbrev-ref HEAD 2>$null)
    if ($branch) { return "origin/$($branch.Trim())" }
    return $null
}

# The open RuneLite's version: this launcher's, or (opened before this window) the one saved at its launch
function Running-Commit {
    if (-not ((RuneLite-Running) -or (RuneLite-Window))) { return $null }
    if (Test-Path $runningFile) { return (Get-Content $runningFile -ErrorAction SilentlyContinue | Select-Object -First 1) }
    return $null
}

$tray = New-Object System.Windows.Forms.NotifyIcon
$tray.Icon = if ($form.Icon) { $form.Icon } else { [System.Drawing.SystemIcons]::Information }
$tray.Text = "Snuffy's Launcher"
$tray.Add_BalloonTipClicked({ $form.Activate() })
$form.Add_FormClosed({ $tray.Visible = $false; $tray.Dispose() })
$script:fetch = $null
$script:lastFetch = [DateTime]::MinValue
$script:warnedFor = $null

# Says when the open RuneLite is older than the plugins here (once a notification per new version)
function Check-RunningVersion {
    $running = Running-Commit
    $head = (git -C $root rev-parse HEAD 2>$null)
    if (-not $running -or -not $head -or $running.Trim() -eq $head.Trim()) {
        if ($script:warnedFor) {
            $script:warnedFor = $null
            $relaunch.Font = New-Object System.Drawing.Font('Segoe UI', 9)
        }
        return
    }
    $behind = (git -C $root rev-list --count "$($running.Trim())..HEAD" 2>$null)
    if (-not $behind) { $behind = 'some' }
    $updateStatus.Text = "RuneLite is out of date: press Relaunch."
    $updateStatus.ForeColor = $red
    $relaunch.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $relaunch.Enabled = $true
    if ($script:warnedFor -ne $head) {
        $script:warnedFor = $head
        $updateLog.Text = (git -C $root log --format='- %s' "$($running.Trim())..HEAD" 2>$null | Out-String).Trim()
        $tray.Visible = $true
        $tray.ShowBalloonTip(10000, "Snuffy's plugins updated", 'RuneLite is running an older version. Open the launcher and press Relaunch to use the new one.', 'Info')
    }
}

$autoUpdate = New-Object System.Windows.Forms.Timer
$autoUpdate.Interval = 5000
$autoUpdate.Add_Tick({
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { return }
    # Not while the Dev tab is pushing: it publishes the commit it started with
    if ($script:publishRun -and -not $script:publishRun.HasExited) { return }
    if ($script:fetch) {
        if (-not $script:fetch.HasExited) { return }
        $script:fetch = $null
        $upstream = Get-Upstream
        if ($upstream) {
            $behind = (git -C $root rev-list --count "HEAD..$upstream" 2>$null)
            if ($LASTEXITCODE -eq 0 -and [int]$behind -gt 0) { Update-Plugins }
        }
        Check-RunningVersion
        return
    }
    if (((Get-Date) - $script:lastFetch).TotalSeconds -ge 60) {
        $script:lastFetch = Get-Date
        $script:fetch = Start-Process -FilePath 'git' -ArgumentList '-C', "`"$root`"", 'fetch', '--quiet' -WindowStyle Hidden -PassThru
    }
})
$autoUpdate.Start()

# Desktop shortcut: opens the launcher straight through PowerShell, minimised and hidden (no cmd window, unlike the
# .bat); made with the Toolbelt's icon. Shortcuts to the .bat (or to this) are brought up to date when the launcher runs
$bat = Join-Path $root 'Launch-BradsMultiTool.bat'
$launcherScript = Join-Path $root 'scripts\launcher.ps1'
$powershellExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$launcherArgs = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$launcherScript`""
$desktop = [Environment]::GetFolderPath('Desktop')
function Set-LauncherShortcut($link) {
    $link.TargetPath = $powershellExe
    $link.Arguments = $launcherArgs
    $link.WorkingDirectory = $root
    $link.WindowStyle = 7
    $link.IconLocation = "$icon,0"
    $link.Save()
}
function Set-ShortcutIcons {
    Get-ChildItem $desktop -Filter '*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
        $link = $shell.CreateShortcut($_.FullName)
        $ours = ($link.TargetPath -ieq $bat) -or ($link.TargetPath -ieq $powershellExe -and $link.Arguments -like "*$launcherScript*")
        if ($ours -and ($link.TargetPath -ine $powershellExe -or $link.Arguments -ne $launcherArgs -or $link.WindowStyle -ne 7 -or $link.IconLocation -notlike "$icon*")) {
            Set-LauncherShortcut $link
        }
    }
}
$shortcut = New-Object System.Windows.Forms.LinkLabel
$shortcut.Text = 'Make a desktop shortcut'
$shortcut.Location = New-Object System.Drawing.Point(14, 644)
$shortcut.AutoSize = $true
$playTab.Controls.Add($shortcut)
$shortcut.Add_LinkClicked({
    Set-LauncherShortcut ($shell.CreateShortcut((Join-Path $desktop "Snuffy's Launcher.lnk")))
    [System.Windows.Forms.MessageBox]::Show("Snuffy's Launcher is on your desktop.", "Snuffy's") | Out-Null
})

# ---- Dev tab: push the plugins to their own repositories, then publish them to RuneLite (Plugin Hub pull
# request). Runs scripts\publish_plugins.py (repositories in publish.json; docs\ROLLOUT.md) hidden, output live. ----

# Python 3: "py -3" (the Windows launcher) or "python"
function Find-Python {
    # The comma keeps a one-item list a list (PowerShell would hand back the bare string)
    if (Get-Command py -ErrorAction SilentlyContinue) { return ,@('py', '-3') }
    if (Get-Command python -ErrorAction SilentlyContinue) { return ,@('python') }
    return $null
}

$devAbout = New-Label "1. Push to repos: copies each ticked plugin, as committed here, to its own GitHub repository; the ones that changed get a new version. Check first to see what would change.`n2. Publish to RuneLite: gets the Plugin Hub pull request ready, so players get what's in the repositories." 12 10 440 66
$devTab.Controls.Add($devAbout)

$publishConfig = Join-Path $root 'publish.json'
$devTicks = [ordered]@{}
if (Test-Path $publishConfig) {
    $config = Get-Content $publishConfig -Raw | ConvertFrom-Json
    $y = 80
    $x = 14
    foreach ($folder in $config.plugins.PSObject.Properties.Name) {
        $box = New-Object System.Windows.Forms.CheckBox
        $box.Text = $folder
        $box.Checked = [bool]$config.plugins.$folder.publish
        $box.Location = New-Object System.Drawing.Point($x, $y)
        $box.Size = New-Object System.Drawing.Size(140, 20)
        $devTab.Controls.Add($box)
        $devTicks[$folder] = $box
        if ($x -gt 250) { $x = 14; $y += 22 } else { $x += 145 }
    }
}

$bumpLabel = New-Label 'Version for changed plugins:' 12 154 170
$devTab.Controls.Add($bumpLabel)
$bump = New-Object System.Windows.Forms.ComboBox
$bump.DropDownStyle = 'DropDownList'
[void]$bump.Items.Add('patch (fixes: 1.0.0 -> 1.0.1)')
[void]$bump.Items.Add('minor (new features: 1.0.0 -> 1.1.0)')
[void]$bump.Items.Add('major (big changes: 1.0.0 -> 2.0.0)')
$bump.SelectedIndex = 0
$bump.Location = New-Object System.Drawing.Point(184, 151)
$bump.Size = New-Object System.Drawing.Size(266, 24)
$devTab.Controls.Add($bump)

$devCheck = New-Object System.Windows.Forms.Button
$devCheck.Text = 'Check (dry run)'
$devCheck.Location = New-Object System.Drawing.Point(12, 184)
$devCheck.Size = New-Object System.Drawing.Size(120, 30)
$devTab.Controls.Add($devCheck)
$devPush = New-Object System.Windows.Forms.Button
$devPush.Text = 'Push to repos'
$devPush.Location = New-Object System.Drawing.Point(140, 184)
$devPush.Size = New-Object System.Drawing.Size(120, 30)
$devTab.Controls.Add($devPush)
$devPublish = New-Object System.Windows.Forms.Button
$devPublish.Text = 'Publish to RuneLite'
$devPublish.Location = New-Object System.Drawing.Point(268, 184)
$devPublish.Size = New-Object System.Drawing.Size(140, 30)
$devPublish.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
$devTab.Controls.Add($devPublish)
$devStatus = New-Label '' 12 220 440
$devTab.Controls.Add($devStatus)

$devOut = New-Object System.Windows.Forms.TextBox
$devOut.Multiline = $true
$devOut.ReadOnly = $true
$devOut.ScrollBars = 'Vertical'
$devOut.Font = New-Object System.Drawing.Font('Consolas', 8.5)
$devOut.Location = New-Object System.Drawing.Point(12, 242)
$devOut.Size = New-Object System.Drawing.Size(438, 378)
$devTab.Controls.Add($devOut)

$prLink = New-Object System.Windows.Forms.LinkLabel
$prLink.Text = 'Open the Plugin Hub pull request'
$prLink.Location = New-Object System.Drawing.Point(12, 630)
$prLink.AutoSize = $true
$prLink.Visible = $false
$devTab.Controls.Add($prLink)
$prLink.Add_LinkClicked({ if ($script:prUrl) { Start-Process $script:prUrl } })
$devGuide = New-Object System.Windows.Forms.LinkLabel
$devGuide.Text = 'How it works (ROLLOUT.md)'
$devGuide.Location = New-Object System.Drawing.Point(290, 630)
$devGuide.AutoSize = $true
$devTab.Controls.Add($devGuide)
$devGuide.Add_LinkClicked({ Start-Process notepad.exe -ArgumentList "`"$(Join-Path $root 'docs\ROLLOUT.md')`"" })

$script:publishLog = Join-Path $env:TEMP 'snuffys-publish.log'
$script:publishRun = $null
$script:prUrl = $null
$devPoll = New-Object System.Windows.Forms.Timer
$devPoll.Interval = 500
$devPoll.Add_Tick({
    if (Test-Path $script:publishLog) {
        try {
            $stream = [System.IO.File]::Open($script:publishLog, 'Open', 'Read', 'ReadWrite')
            $reader = New-Object System.IO.StreamReader($stream)
            $text = $reader.ReadToEnd()
            $stream.Dispose()
            if ($text -ne $devOut.Text) {
                $devOut.Text = $text
                $devOut.SelectionStart = $devOut.Text.Length
                $devOut.ScrollToCaret()
            }
            $match = [regex]::Match($text, 'https://github\.com/runelite/plugin-hub/compare/\S+')
            if ($match.Success) {
                $script:prUrl = $match.Value
                $prLink.Visible = $true
            }
        } catch { }
    }
    if ($script:publishRun -and $script:publishRun.HasExited) {
        $devPoll.Stop()
        $devCheck.Enabled = $true
        $devPush.Enabled = $true
        $devPublish.Enabled = $true
        if ($script:publishRun.ExitCode -eq 0) {
            $devStatus.Text = 'Done.'
            $devStatus.ForeColor = $green
        } else {
            $devStatus.Text = 'Stopped: see the messages below.'
            $devStatus.ForeColor = $red
        }
        $script:publishRun = $null
    }
})

# Runs one step: 'push' (with $dryRun for Check) or 'hub'
function Start-DevStep($step, $dryRun) {
    $python = Find-Python
    if (-not $python) {
        $answer = [System.Windows.Forms.MessageBox]::Show("This needs Python 3. Open python.org to get it?", "Snuffy's", 'YesNo')
        if ($answer -eq 'Yes') { Start-Process 'https://www.python.org/downloads/' }
        return
    }
    $chosen = @($devTicks.Keys | Where-Object { $devTicks[$_].Checked })
    if (-not $chosen) {
        [System.Windows.Forms.MessageBox]::Show('Tick at least one plugin.', "Snuffy's") | Out-Null
        return
    }
    $level = @('patch', 'minor', 'major')[$bump.SelectedIndex]
    if (-not $dryRun) {
        $what = $(if ($step -eq 'push') { "Push " + ($chosen -join ', ') + " to their repositories (changed ones get a new $level version)?" }
                  else { "Get the Plugin Hub pull request ready for " + ($chosen -join ', ') + "?" })
        $answer = [System.Windows.Forms.MessageBox]::Show($what, "Snuffy's", 'YesNo')
        if ($answer -ne 'Yes') { return }
    }
    Set-Content -Path $script:publishLog -Value ''
    $devOut.Text = ''
    $prLink.Visible = $false
    $script:prUrl = $null
    $devCheck.Enabled = $false
    $devPush.Enabled = $false
    $devPublish.Enabled = $false
    $devStatus.Text = $(if ($dryRun) { 'Checking...' } elseif ($step -eq 'push') { 'Pushing...' } else { 'Publishing...' })
    $devStatus.ForeColor = [System.Drawing.SystemColors]::ControlText
    $pyArgs = @($python | Select-Object -Skip 1) + @('-u', 'scripts\publish_plugins.py', $step) + $chosen
    if ($step -eq 'push') { $pyArgs += @('--bump', $level) }
    if ($dryRun) { $pyArgs += '--dry-run' }
    $command = "`"$($python[0])`" " + ($pyArgs -join ' ') + " > `"$($script:publishLog)`" 2>&1"
    # cmd /c drops the first and last quote of what it runs: wrap the whole line in one more pair
    $script:publishRun = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$command`"" -WorkingDirectory $root -WindowStyle Hidden -PassThru
    # Reading the handle now keeps the exit code readable once it ends (Windows PowerShell)
    $null = $script:publishRun.Handle
    $devPoll.Start()
}
$devCheck.Add_Click({ Start-DevStep 'push' $true })
$devPush.Add_Click({ Start-DevStep 'push' $false })
$devPublish.Add_Click({ Start-DevStep 'hub' $false })
if (-not (Test-Path $publishConfig)) {
    $devStatus.Text = 'publish.json is missing: update the launcher.'
    $devStatus.ForeColor = $red
    $devCheck.Enabled = $false
    $devPush.Enabled = $false
    $devPublish.Enabled = $false
}


$form.Add_Shown({
    Set-ShortcutIcons
    Check-Setup
    Update-Plugins
    Check-RunningVersion
    $script:lastFetch = Get-Date
})
[void]$form.ShowDialog()
