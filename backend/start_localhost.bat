@echo off
REM ── Crowd Prediction: start backend locally on localhost ───────────────
REM Just double-click this file to start the backend on http://localhost:5000.
REM
REM Since you are running the Android app on an emulator, the app has been
REM configured to connect to http://10.0.2.2:5000/ to reach this server.

cd /d "%~dp0"

REM Start Flask server in its own window (so you can see logs / close it)
start "CrowdPredict Server" cmd /k python app.py

echo.
echo ============================================================
echo   Crowd Prediction local server is starting...
echo   Web admin (on PC): http://localhost:5000/
echo   Android emulator:   http://10.0.2.2:5000/
echo ============================================================
echo.
