# Launcher InicioTV

Pantalla de inicio minimalista para tu Mi TV: fondo rotativo, dock con tus apps
(Netflix, YouTube, Prime Video, Disney+, Movistar+, Max, RTVE Play, Mitele),
TV en directo, Ajustes y una vista "Todas las apps". Sin anuncios ni dependencias.

IMPORTANTE: este codigo NO se ha compilado ni probado todavia. Si la primera
compilacion da algun error, copialo tal cual y lo corregimos.

## Compilar con GitHub (sin instalar nada)

1. Crea una cuenta en github.com y un repositorio nuevo (privado vale), por ejemplo "inicio-tv".
2. En el repositorio: "Add file" > "Upload files" y arrastra TODO el contenido de esta
   carpeta (incluida la carpeta .github). Pulsa "Commit changes".
3. Pestaña "Actions" > "Compilar APK" > "Run workflow". Tarda unos minutos.
4. Al terminar, entra en la ejecucion y descarga "InicioTV-apk" (es un zip con app-debug.apk).

## Instalar (la tele conectada por ADB)

    instalar.bat ruta\al\app-debug.apk
    adb shell am start -n es.marta.inicio/.MainActivity

Asi se prueba sin cambiar nada: tu pantalla de inicio actual sigue como esta.

## Fondos de naturaleza

Sin fotos usa degradados de bosque, lago y atardecer. Para fotos reales, copia
.jpg o .png a la carpeta "fondos" (junto a subir_fondos.bat) y ejecuta subir_fondos.bat.
Fuentes de fotos gratuitas: Unsplash, Pexels o Wikimedia Commons (revisa la licencia).
El fondo cambia cada 5 minutos con fundido.

## Volver atras

    quitar_launcher.bat
