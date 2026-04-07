@echo off
setlocal
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%deploy_bundle.ps1"

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
        "Start-Process powershell.exe -Verb RunAs -ArgumentList @('-NoExit','-ExecutionPolicy','Bypass','-File','%PS_SCRIPT%')"
    exit /b
)

powershell.exe -NoExit -ExecutionPolicy Bypass -File "%PS_SCRIPT%"
exit /b %errorlevel%
