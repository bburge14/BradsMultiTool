@echo off
rem Opens Snuffy's Launcher (scripts\launcher.ps1): it downloads and updates Snuffy's plugins, checks your setup, lets
rem you pick which plugins to load and starts RuneLite with them. The desktop shortcut it makes opens it with no window.
cd /d "%~dp0"
rem A script that can't be read (a broken update) opens its errors in Notepad instead of nothing happening. (Its path
rem goes in through a variable: a folder named with an apostrophe, "Snuffy's Launcher", can't break the check.)
set "SNUFFYS_SCRIPT=%~dp0scripts\launcher.ps1"
set "SNUFFYS_ERRORS=%TEMP%\snuffys-launcher-error.log"
if exist "%SNUFFYS_ERRORS%" del "%SNUFFYS_ERRORS%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile($env:SNUFFYS_SCRIPT, [ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content $env:SNUFFYS_ERRORS }"
if exist "%SNUFFYS_ERRORS%" (
  start "" notepad "%SNUFFYS_ERRORS%"
  exit /b 1
)
start "" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%SNUFFYS_SCRIPT%"
