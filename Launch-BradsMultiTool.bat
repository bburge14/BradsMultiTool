@echo off
rem Opens Snuffy's launcher window (scripts\launcher.ps1): it updates the plugins, shows whether a Jagex
rem session is saved, lets you pick which plugins to load (the Toolbelt on its own, or any separate ones),
rem and launches RuneLite. Your pick is remembered in .launch-choice next to this file. The launcher hides its own
rem console window; the desktop shortcut it makes opens it with no window at all.
cd /d "%~dp0"
rem A script that can't even be read (a broken update) opens its errors in Notepad instead of nothing happening (its
rem path goes in through a variable, so a folder named with an apostrophe can't break the check)
set "SNUFFYS_SCRIPT=%~dp0scripts\launcher.ps1"
set "SNUFFYS_ERRORS=%TEMP%\snuffys-launcher-error.log"
if exist "%SNUFFYS_ERRORS%" del "%SNUFFYS_ERRORS%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile($env:SNUFFYS_SCRIPT, [ref]$null, [ref]$e); if ($e) { ($e | Out-String) | Set-Content $env:SNUFFYS_ERRORS }"
if exist "%SNUFFYS_ERRORS%" (
  start "" notepad "%SNUFFYS_ERRORS%"
  exit /b 1
)
start "" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%SNUFFYS_SCRIPT%"
