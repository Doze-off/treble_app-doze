@echo off
REM Wrapper to run the PowerShell signing script on Windows
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sign_and_copy.ps1"
