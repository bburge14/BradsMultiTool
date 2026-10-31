# Snuffy's launcher window (Launch-BradsMultiTool.bat opens it): updates the plugins, checks the setup (RuneLite
# installed, session saving on in RuneLite (configure), a Jagex session saved, Java, git) with a button to fix
# each, and walks through saving a session; lets you pick which plugins RuneLite loads, and launches it. The Toolbelt has every separate
# plugin in it, so it's either the Toolbelt on its own or any of the separate ones. The build and RuneLite's log
# open in their own console window.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$root = Split-Path $PSScriptRoot -Parent
$choiceFile = Join-Path $root '.launch-choice'
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
    $form.Controls.Add($group)
    return $group
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Snuffy's Launcher"
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.StartPosition = 'CenterScreen'
$form.ClientSize = New-Object System.Drawing.Size(460, 584)
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9)
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
    $out = (git -C $root pull --ff-only 2>&1 | Out-String)
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
$y = 94
foreach ($key in $plugins.Keys) {
    $box = New-Object System.Windows.Forms.CheckBox
    $box.Text = $plugins[$key]
    $box.Location = New-Object System.Drawing.Point(34, $y)
    $box.AutoSize = $true
    $box.Checked = ($lastNames -contains $key) -or ($lastNames -contains 'separate')
    $pick.Controls.Add($box)
    $boxes[$key] = $box
    $y += 22
}
$updateBoxes = { foreach ($box in $boxes.Values) { $box.Enabled = $separate.Checked } }
$toolbelt.Add_CheckedChanged($updateBoxes)
$separate.Add_CheckedChanged($updateBoxes)
if ($lastNames -contains 'toolbelt') { $toolbelt.Checked = $true } else { $separate.Checked = $true }
& $updateBoxes

# ---- Launch ----
$launch = New-Object System.Windows.Forms.Button
$launch.Text = 'Launch RuneLite'
$launch.Location = New-Object System.Drawing.Point(232, 544)
$launch.Size = New-Object System.Drawing.Size(126, 32)
$launch.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
$form.Controls.Add($launch)
$form.AcceptButton = $launch
$close = New-Object System.Windows.Forms.Button
$close.Text = 'Close'
$close.Location = New-Object System.Drawing.Point(366, 544)
$close.Size = New-Object System.Drawing.Size(82, 32)
$close.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($close)
$form.CancelButton = $close

$launch.Add_Click({
    if ($separate.Checked -and -not ($boxes.Values | Where-Object { $_.Checked })) {
        [System.Windows.Forms.MessageBox]::Show('Tick at least one plugin, or pick the Toolbelt.', "Snuffy's") | Out-Null
        return
    }
    $load = if ($toolbelt.Checked) { 'toolbelt' } else { ($plugins.Keys | Where-Object { $boxes[$_].Checked }) -join ',' }
    Set-Content -Path $choiceFile -Value $load
    # The build and RuneLite's log in their own window, kept open if something goes wrong
    $command = "title Snuffy's - RuneLite ($load) && gradlew.bat runBrad `"-Pload=$load`" || (echo. & echo RuneLite exited with an error. Scroll up for details. & pause)"
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $command -WorkingDirectory $root
    $form.Close()
})

# Desktop shortcut: make (or fix) one with the Toolbelt's icon; shortcuts already pointing here get the icon too
$bat = Join-Path $root 'Launch-BradsMultiTool.bat'
$desktop = [Environment]::GetFolderPath('Desktop')
function Set-ShortcutIcons {
    Get-ChildItem $desktop -Filter '*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
        $link = $shell.CreateShortcut($_.FullName)
        if ($link.TargetPath -ieq $bat -and $link.IconLocation -notlike "$icon*") {
            $link.IconLocation = "$icon,0"
            $link.Save()
        }
    }
}
$shortcut = New-Object System.Windows.Forms.LinkLabel
$shortcut.Text = 'Make a desktop shortcut'
$shortcut.Location = New-Object System.Drawing.Point(14, 552)
$shortcut.AutoSize = $true
$form.Controls.Add($shortcut)
$shortcut.Add_LinkClicked({
    $link = $shell.CreateShortcut((Join-Path $desktop "Snuffy's Launcher.lnk"))
    $link.TargetPath = $bat
    $link.WorkingDirectory = $root
    $link.IconLocation = "$icon,0"
    $link.Save()
    [System.Windows.Forms.MessageBox]::Show("Snuffy's Launcher is on your desktop.", "Snuffy's") | Out-Null
})

$form.Add_Shown({
    Set-ShortcutIcons
    Check-Setup
    Update-Plugins
})
[void]$form.ShowDialog()
