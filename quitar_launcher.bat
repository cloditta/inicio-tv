@echo off
rem Vuelve al launcher de Google: desinstala SOLO este launcher (es.marta.inicio)
rem y reactiva el launcher de Google por si lo habias deshabilitado.
adb shell pm enable com.google.android.tvlauncher
adb uninstall es.marta.inicio
adb shell input keyevent KEYCODE_HOME
echo.
echo Listo. Reinicia la tele si la pantalla de inicio no vuelve a la de Google.
