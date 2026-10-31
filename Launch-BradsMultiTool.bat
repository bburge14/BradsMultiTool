@echo off
setlocal EnableDelayedExpansion
title Snuffy's - RuneLite
rem Double-click to pick which of Snuffy's plugins to load, then build and launch RuneLite with them.
rem Your pick is remembered (in .launch-choice next to this file): press Enter next time to reuse it.
cd /d "%~dp0"

set "CREDS=%USERPROFILE%\.runelite\credentials.properties"
if not exist "%CREDS%" (
    echo.
    echo  No Jagex session found at:
    echo    %CREDS%
    echo.
    echo  To create it ^(once per PC^):
    echo    1. Start menu -^> "RuneLite (configure)"
    echo    2. Add  --insecure-write-credentials  to Client arguments, click Save
    echo    3. Launch RuneLite once from the Jagex Launcher and log in
    echo.
    echo  Without it the client still opens, but you cannot log in to a Jagex Account.
    echo.
    choice /c YN /m "Launch anyway"
    if errorlevel 2 exit /b 1
)

set "LAST=toolbelt"
if exist ".launch-choice" set /p LAST=<".launch-choice"

echo.
echo  Which plugins should RuneLite load?
echo    1  Snuffy's Toolbelt (everything in one)
echo    2  Skilling Helper
echo    3  PvM/Slayer
echo    4  GemCrab Tracker
echo    5  Tick Counter
echo    6  Alerts
echo    7  All the separate plugins
echo    8  Pick several (e.g. skilling,tick)
echo.
set "PICK="
set /p PICK= Number, or Enter for last time's (!LAST!):

set "LOAD=!LAST!"
if "!PICK!"=="1" set "LOAD=toolbelt"
if "!PICK!"=="2" set "LOAD=skilling"
if "!PICK!"=="3" set "LOAD=pvm"
if "!PICK!"=="4" set "LOAD=gemcrab"
if "!PICK!"=="5" set "LOAD=tick"
if "!PICK!"=="6" set "LOAD=alerts"
if "!PICK!"=="7" set "LOAD=separate"
if "!PICK!"=="8" (
    set "LOAD="
    set /p LOAD= Names, comma separated ^(toolbelt, skilling, pvm, gemcrab, tick, alerts^):
    if "!LOAD!"=="" set "LOAD=!LAST!"
)
> ".launch-choice" echo !LOAD!

echo.
echo  Loading: !LOAD!
echo.
call gradlew.bat runBrad "-Pload=!LOAD!"
if errorlevel 1 (
    echo.
    echo  RuneLite exited with an error. Scroll up for details.
    pause
)
