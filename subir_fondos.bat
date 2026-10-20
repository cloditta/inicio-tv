@echo off
rem Sube a la tele las fotos .jpg/.jpeg/.png de la carpeta "fondos" de aqui.
rem Mejor 1920x1080, y cada una por debajo de 3 MB.
setlocal
set "DEST=/sdcard/Android/data/es.marta.inicio/files/fondos"
adb shell mkdir -p %DEST%
for %%F in ("%~dp0fondos\*.jpg" "%~dp0fondos\*.jpeg" "%~dp0fondos\*.png") do (
  adb push "%%~fF" %DEST%/
)
echo.
echo Hecho. Vuelve a abrir el launcher para ver los fondos nuevos.
