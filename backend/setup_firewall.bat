@echo off
REM ── Crowd Prediction: open port 5000 for phones on the same Wi-Fi ──────────
REM Windows blocks inbound port 5000 on "Public" networks by default, and a
REM Wi-Fi reboot often resets the network back to Public. This rule allows
REM inbound TCP 5000 on ALL profiles (Public/Private/Domain) permanently, so
REM phones can always reach the Flask backend regardless of reboots.
REM
REM Just double-click this file once. It auto-requests Administrator rights.

REM ── Self-elevate to Administrator if not already ──────────────────────────
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting Administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo ============================================================
echo   Opening firewall for Crowd Prediction backend (port 5000)
echo ============================================================
echo.

REM Remove any old copy of the rule so re-running stays clean (no duplicates)
netsh advfirewall firewall delete rule name="CrowdPrediction Flask 5000" >nul 2>&1

REM Add the inbound allow rule on every profile
netsh advfirewall firewall add rule name="CrowdPrediction Flask 5000" ^
    dir=in action=allow protocol=TCP localport=5000 profile=any

if %errorlevel% equ 0 (
    echo.
    echo [OK] Port 5000 is now open on Public/Private/Domain networks.
    echo      This survives Wi-Fi and PC reboots.
) else (
    echo.
    echo [FAILED] Could not add the rule. Make sure you allowed the
    echo          Administrator prompt.
)

echo.
echo ------------------------------------------------------------
echo Verifying the rule is active:
netsh advfirewall firewall show rule name="CrowdPrediction Flask 5000" | findstr /C:"Enabled" /C:"LocalPort" /C:"Action"
echo ------------------------------------------------------------
echo.
pause
