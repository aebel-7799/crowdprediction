@echo off
REM ── Crowd Prediction: start backend + public tunnel ──────────────────
REM Launches the Flask server and the localtunnel public link.
REM Public URL (always the same):  https://crowdpredictjsnk.loca.lt
REM Login: jsnk006@gmail.com / 12345678

cd /d "%~dp0"

REM Start Flask server (new window so you can see logs / close it)
start "CrowdPredict Server" cmd /k python app.py

REM Give the server a few seconds to bind port 5000
timeout /t 6 /nobreak >nul

REM Start the public tunnel with a fixed subdomain (auto-reconnects via loop)
start "CrowdPredict Tunnel" cmd /k "for /l %%i in (1,0,2) do (lt --port 5000 --subdomain crowdpredictjsnk & echo Tunnel dropped, reconnecting in 5s... & timeout /t 5 /nobreak >nul)"

echo.
echo ============================================================
echo   Crowd Prediction is going public...
echo   URL:   https://crowdpredictjsnk.loca.lt
echo   Login: jsnk006@gmail.com  /  12345678
echo   (first-time browser visitors enter IP: 103.70.199.127)
echo ============================================================
echo.
