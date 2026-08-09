@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "GRADLEW=%~dp0gradlew.bat"
set "SDK_BUILD_TOOLS=%~dp0sdk\build-tools\30.0.3"
set "KEY_CERT=%~dp0keys\platform.x509.pem"
set "KEY_PK8=%~dp0keys\platform.pk8"
set "UNSIGNED_APK=app\build\outputs\apk\release\app-release-unsigned.apk"
set "ALIGNED_APK=app\build\outputs\apk\release\app-release-aligned.apk"
set "OUTPUT_APK=%~dp0TrebleApp.apk"

if not exist "%GRADLEW%" (
  echo ERROR: gradlew.bat not found in "%~dp0".
  exit /b 1
)
if not exist "%SDK_BUILD_TOOLS%\zipalign.exe" (
  echo ERROR: zipalign.exe not found in "%SDK_BUILD_TOOLS%".
  exit /b 1
)
if not exist "%SDK_BUILD_TOOLS%\apksigner.bat" (
  echo ERROR: apksigner.bat not found in "%SDK_BUILD_TOOLS%".
  exit /b 1
)
if not exist "%KEY_CERT%" (
  echo ERROR: Signing certificate not found: "%KEY_CERT%".
  exit /b 1
)
if not exist "%KEY_PK8%" (
  echo ERROR: Signing key not found: "%KEY_PK8%".
  exit /b 1
)

echo Building release APK...
call "%GRADLEW%" assembleRelease
if errorlevel 1 (
  echo ERROR: Gradle release build failed.
  exit /b 1
)

if not exist "%UNSIGNED_APK%" (
  echo ERROR: Unsigned APK not found: "%UNSIGNED_APK%".
  exit /b 1
)

echo Aligning APK...
if exist "%ALIGNED_APK%" del /f /q "%ALIGNED_APK%"
"%SDK_BUILD_TOOLS%\zipalign.exe" -v 4 "%UNSIGNED_APK%" "%ALIGNED_APK%"
if errorlevel 1 (
  echo ERROR: zipalign failed.
  exit /b 1
)

echo Signing APK...
"%SDK_BUILD_TOOLS%\apksigner.bat" sign --key "%KEY_PK8%" --cert "%KEY_CERT%" --out "%OUTPUT_APK%" "%ALIGNED_APK%"
if errorlevel 1 (
  echo ERROR: apksigner failed.
  exit /b 1
)

echo Verifying signed APK...
"%SDK_BUILD_TOOLS%\apksigner.bat" verify --print-certs "%OUTPUT_APK%"
if errorlevel 1 (
  echo ERROR: signed APK verification failed.
  exit /b 1
)

echo
echo SUCCESS: Created "%OUTPUT_APK%"
exit /b 0
