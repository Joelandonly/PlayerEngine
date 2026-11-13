@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Ellie_Storage_Warehouse_Apply_Build_Verify_20261112.ps1"
set "ELLIE_RESULT=%ERRORLEVEL%"
echo.
if not "%ELLIE_RESULT%"=="0" echo Build or verification FAILED. Read the saved logs. No JAR was delivered as verified.
pause
exit /b %ELLIE_RESULT%
