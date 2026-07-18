@echo off
REM Wrapper started by dev-up.bat — do not run from elsewhere unless you know the cwd.
setlocal
cd /d "%~dp0.."
if not exist "scripts\dev\run.sh" (
  echo ERROR: run from suwayomi-exp-server
  pause
  exit /b 1
)
set "GIT_BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%GIT_BASH%" set "GIT_BASH=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not exist "%GIT_BASH%" (
  echo ERROR: Git Bash not found
  pause
  exit /b 1
)
"%GIT_BASH%" -lc "./scripts/dev/run.sh %*"
echo.
echo Server exited with code %ERRORLEVEL%
pause
