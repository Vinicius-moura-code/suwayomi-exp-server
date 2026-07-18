@echo off
REM Build suwayomi-exp-ui and install into %%LOCALAPPDATA%%\Tachidesk\webUI
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
set "GIT_BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%GIT_BASH%" set "GIT_BASH=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not exist "%GIT_BASH%" (
  echo ERROR: Git Bash required
  exit /b 1
)
"%GIT_BASH%" -lc "cd \"$(cygpath -u '%SCRIPT_DIR%')\" && ./install-ui.sh %*"
endlocal
