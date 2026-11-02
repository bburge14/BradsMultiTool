@echo off
rem Publishes the separate plugins to their own GitHub repositories and prepares the Plugin Hub entries
rem (scripts\publish_plugins.py; repositories in publish.json). The launcher's Dev tab does the same with buttons.
rem   Publish-Plugins.bat push [--dry-run] [--bump minor]   then   Publish-Plugins.bat hub
cd /d "%~dp0"
where py >nul 2>nul && (py -3 scripts\publish_plugins.py %*) || (python scripts\publish_plugins.py %*)
pause
