@echo off
setlocal enabledelayedexpansion
set GRADLE_VERSION=8.13
set ROOT_DIR=%~dp0
set CACHE_DIR=%USERPROFILE%\.gradle\vicovpn-dist
set GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%
set ZIP=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$h=(Get-FileHash -Algorithm SHA256 '%ZIP%').Hash.ToLower(); if($h -ne '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'){throw 'Gradle checksum mismatch'}; Expand-Archive -Force '%ZIP%' '%CACHE_DIR%'"
)
pushd "%ROOT_DIR%"
call "%GRADLE_HOME%\bin\gradle.bat" %*
set EXIT_CODE=%ERRORLEVEL%
popd
exit /b %EXIT_CODE%
