@echo off
setlocal EnableDelayedExpansion
title Snuffy's - RuneLite
rem Double-click to update Snuffy's plugins, check your Jagex session, pick which plugins to load, then build
rem and launch RuneLite with them.
rem Your pick is remembered (in .launch-choice next to this file): press Enter next time to reuse it.
cd /d "%~dp0"

rem ---- 1. Update: fetch the latest version of the plugins (skipped with no git, local changes or no internet) ----
echo.
where git >nul 2>nul
if errorlevel 1 (
    echo  Update: git isn't installed, so launching the version you have.
) else (
    set "OLD="
    for /f %%h in ('git rev-parse HEAD 2^>nul') do set "OLD=%%h"
    git pull --ff-only --quiet
    if errorlevel 1 (
        echo  Update: couldn't update ^(offline, or files changed here^). Launching the version you have.
    ) else (
        set "NEW="
        for /f %%h in ('git rev-parse HEAD') do set "NEW=%%h"
        if "!OLD!"=="!NEW!" (
            echo  Update: already up to date.
        ) else (
            echo  Update: new changes since last time:
            git --no-pager log --format="    - %%s" !OLD!..!NEW!
        )
    )
)

rem ---- 2. Jagex session: is one saved, and for which character ----
rem Only reads the character's name and when the file was saved; the session itself is never shown.
set "CREDS=%USERPROFILE%\.runelite\credentials.properties"
echo.
if exist "%CREDS%" (
    set "WHO="
    for /f "tokens=1,* delims==" %%a in ('findstr /b "JX_DISPLAY_NAME=" "%CREDS%"') do set "WHO=%%b"
    for %%F in ("%CREDS%") do set "SAVED=%%~tF"
    if defined WHO (
        echo  Session: saved for !WHO! on !SAVED!.
    ) else (
        echo  Session: saved on !SAVED! ^(no character name in it^).
    )
    echo  If the login screen asks for an email and password instead of showing Play, it has expired:
    echo  launch RuneLite once from the Jagex Launcher to save a fresh one.
) else (
    echo  Session: none found at
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

rem ---- 3. Which plugins ----
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
