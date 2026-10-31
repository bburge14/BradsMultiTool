# Snuffy's launcher window (Launch-BradsMultiTool.bat opens it): updates the plugins, shows whether a Jagex
# session is saved, lets you pick which plugins RuneLite loads, and launches it. The Toolbelt has every separate
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
$form.ClientSize = New-Object System.Drawing.Size(460, 520)
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9)
$icon = Join-Path $root 'icon.png'
if (Test-Path $icon) {
    $form.Icon = [System.Drawing.Icon]::FromHandle(([System.Drawing.Bitmap]::FromFile($icon)).GetHicon())
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

# ---- Jagex session ----
$session = New-Group 'Jagex Account session' 150 92
$sessionStatus = New-Label '' 12 22 410 36
$session.Controls.Add($sessionStatus)
$sessionHelp = New-Object System.Windows.Forms.LinkLabel
$sessionHelp.Location = New-Object System.Drawing.Point(12, 62)
$sessionHelp.Size = New-Object System.Drawing.Size(410, 18)
$session.Controls.Add($sessionHelp)
$sessionHelp.Add_LinkClicked({
    [System.Windows.Forms.MessageBox]::Show(
        "To save a session (once per PC):`n`n1. Start menu > `"RuneLite (configure)`"`n2. Add  --insecure-write-credentials  to Client arguments, click Save`n3. Launch RuneLite once from the Jagex Launcher and log in`n`nLeave the argument on and it refreshes each time you play through the Jagex Launcher.`n`nThe file is a live login: never share it. Delete it to sign the dev client out.",
        "Jagex Account session") | Out-Null
})

# Only reads the character's name and when the file was saved; the session itself is never shown
function Show-Session {
    if (Test-Path $creds) {
        $who = (Select-String -Path $creds -Pattern '^JX_DISPLAY_NAME=(.*)$' | Select-Object -First 1)
        $name = if ($who) { $who.Matches[0].Groups[1].Value } else { 'your account' }
        $saved = (Get-Item $creds).LastWriteTime.ToString('g')
        $sessionStatus.Text = "Saved for $name on $saved.`nIf RuneLite asks for an email and password instead of showing Play, it has expired."
        $sessionStatus.ForeColor = $green
        $sessionHelp.Text = 'How to save a fresh one'
    } else {
        $sessionStatus.Text = "No session saved: RuneLite will open, but you can't log in to a Jagex Account."
        $sessionStatus.ForeColor = $red
        $sessionHelp.Text = 'How to set it up (once per PC)'
    }
}

# ---- Plugins ----
$pick = New-Group 'Plugins to load' 252 210
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
$launch.Location = New-Object System.Drawing.Point(232, 476)
$launch.Size = New-Object System.Drawing.Size(126, 32)
$launch.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
$form.Controls.Add($launch)
$form.AcceptButton = $launch
$close = New-Object System.Windows.Forms.Button
$close.Text = 'Close'
$close.Location = New-Object System.Drawing.Point(366, 476)
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

$form.Add_Shown({
    Show-Session
    Update-Plugins
})
[void]$form.ShowDialog()
