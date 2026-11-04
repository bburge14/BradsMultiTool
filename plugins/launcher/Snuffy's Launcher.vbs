' Opens Snuffy's Launcher with no window at all (scripts\launcher.ps1, hidden): double-click this.
' If the launcher's script can't be read (a broken update) its errors open in Notepad instead. Its path goes to the
' check through a variable, so a folder named with an apostrophe ("Snuffy's Launcher") can't break it; and Notepad only
' opens when there are errors to show (if the check itself fails, it opens the launcher anyway).
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
folder = files.GetParentFolderName(WScript.ScriptFullName)
script = folder & "\scripts\launcher.ps1"
errors = shell.ExpandEnvironmentStrings("%TEMP%") & "\snuffys-launcher-error.log"
shell.Environment("Process")("SNUFFYS_SCRIPT") = script
shell.Environment("Process")("SNUFFYS_ERRORS") = errors
If files.FileExists(errors) Then files.DeleteFile errors, True
ps = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden "
check = ps & "-Command ""$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile($env:SNUFFYS_SCRIPT, " & _
    "[ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content $env:SNUFFYS_ERRORS }"""
shell.Run check, 0, True
If files.FileExists(errors) Then
    If files.GetFile(errors).Size > 0 Then
        shell.Run "notepad.exe """ & errors & """", 1, False
        WScript.Quit 1
    End If
End If
shell.Run ps & "-File """ & script & """", 0, False
