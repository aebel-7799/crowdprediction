@echo off
REM ── Crowd Prediction: start backend + PERMANENT ngrok tunnel ───────────────
REM Public URL (fixed, never changes across reboots):
REM   https://phonebook-shrine-affected.ngrok-free.dev
REM
REM This URL is already set in the Android app's NetworkModule.kt, so the app
REM keeps working after Wi-Fi/router/PC reboots and even on mobile data.
REM
REM Just double-click this file.

cd /d "%~dp0"

set "NGROK=C:\Users\jsk68\AppData\Local\Microsoft\WinGet\Packages\Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe\ngrok.exe"

REM Start Flask server in its own window (so you can see logs / close it)
start "CrowdPredict Server" cmd /k python app.py

REM Give Flask a few seconds to bind port 5000
timeout /t 6 /nobreak >nul

REM Start the ngrok tunnel on the fixed dev domain (auto-reconnects on drop)
start "CrowdPredict ngrok" cmd /k ""%NGROK%" http 5000"

echo.
echo ============================================================
echo   Crowd Prediction is going public via ngrok
echo   URL:  https://phonebook-shrine-affected.ngrok-free.dev
echo   (This URL is fixed - already set in the app.)
echo   Local inspector: http://127.0.0.1:4040
echo ============================================================
echo.
