@echo off
title Snuffy's Toolbelt - RuneLite
rem Double-click to build and launch RuneLite with Snuffy's Toolbelt loaded.
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

call gradlew.bat runBrad
if errorlevel 1 (
    echo.
    echo  RuneLite exited with an error. Scroll up for details.
    pause
)
