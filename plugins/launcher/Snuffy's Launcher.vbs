' Opens Snuffy's Launcher with no window at all (scripts\launcher.ps1, hidden): double-click this.
' If the launcher's script can't be read (a broken update) its errors open in Notepad instead.
Set shell = CreateObject("WScript.Shell")
folder = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
script = folder & "\scripts\launcher.ps1"
ps = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden "
check = ps & "-Command ""$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile('" & script & _
    "', [ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content (Join-Path $env:TEMP 'snuffys-launcher-error.log'); exit 1 }"""
If shell.Run(check, 0, True) <> 0 Then
    shell.Run "notepad.exe """ & shell.ExpandEnvironmentStrings("%TEMP%") & "\snuffys-launcher-error.log""", 1, False
    WScript.Quit 1
End If
shell.Run ps & "-File """ & script & """", 0, False
