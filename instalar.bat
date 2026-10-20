@echo off
rem Uso: instalar.bat [ruta\al\app-debug.apk]
rem Instala el launcher y crea la carpeta de fondos en la tele.
rem NO cambia el launcher actual ni deshabilita nada.
setlocal
set "APK=%~1"
if "%APK%"=="" set "APK=%~dp0app-debug.apk"
if not exist "%APK%" (
  echo No encuentro el APK: %APK%
  exit /b 1
)
adb install -r "%APK%"
adb shell mkdir -p /sdcard/Android/data/es.marta.inicio/files/fondos
echo.
echo Instalado. Para probarlo sin cambiar nada:
echo   adb shell am start -n es.marta.inicio/.MainActivity
