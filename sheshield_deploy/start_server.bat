@echo off
echo ========================================
echo   SheShield AI Server - Starting...
echo ========================================

cd /d "%~dp0"

if not exist venv (
    echo Creating virtual environment...
    python -m venv venv
    if errorlevel 1 (
        echo ERROR: Python not found. Install Python 3.11 first.
        pause
        exit /b 1
    )
)

call venv\Scripts\activate.bat

echo Installing dependencies (first run takes 3-5 minutes)...
pip install -q -r requirements.txt

echo.
echo ========================================
echo   Server live at: http://localhost:8000
echo   API docs:       http://localhost:8000/docs
echo   Press Ctrl+C to stop.
echo ========================================
echo.

uvicorn api:app --host 0.0.0.0 --port 8000

pause
