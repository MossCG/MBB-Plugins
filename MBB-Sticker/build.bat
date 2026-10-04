@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
endlocal
