@echo off
rem Opens Snuffy's Launcher (scripts\launcher.ps1): it downloads and updates Snuffy's plugins, checks your setup, lets
rem you pick which plugins to load and starts RuneLite with them. The desktop shortcut it makes opens it with no window.
cd /d "%~dp0"
rem A script that can't be read (a broken update) opens its errors in Notepad instead of nothing happening
powershell -NoProfile -ExecutionPolicy Bypass -Command "$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile('%~dp0scripts\launcher.ps1', [ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content (Join-Path $env:TEMP 'snuffys-launcher-error.log'); exit 1 }"
if errorlevel 1 (
  start "" notepad "%TEMP%\snuffys-launcher-error.log"
  exit /b 1
)
start "" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0scripts\launcher.ps1"
