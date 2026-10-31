@echo off
rem Opens Snuffy's launcher window (scripts\launcher.ps1): it updates the plugins, shows whether a Jagex
rem session is saved, lets you pick which plugins to load (the Toolbelt on its own, or any separate ones),
rem and launches RuneLite. Your pick is remembered in .launch-choice next to this file.
cd /d "%~dp0"
start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0scripts\launcher.ps1"
