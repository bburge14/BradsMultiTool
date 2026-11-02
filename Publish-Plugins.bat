@echo off
rem Publishes the separate plugins to their own GitHub repositories and prepares the Plugin Hub entries
rem (scripts\publish_plugins.py; repositories in publish.json). Add --dry-run to see what would change first.
cd /d "%~dp0"
where py >nul 2>nul && (py -3 scripts\publish_plugins.py %*) || (python scripts\publish_plugins.py %*)
pause
