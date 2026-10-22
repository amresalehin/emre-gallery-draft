@echo off
REM Lightweight Gradle launcher fallback. Open the project in Android Studio to sync dependencies.
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Open this project in Android Studio and allow Gradle sync.
exit /b 1
