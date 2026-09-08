# DoVimu

App Android (Kotlin / Compose Multiplatform) para inspeccionar un enlace, elegir calidad y descargar **video o música**. Habla con la **DoVimu API** (Python) del proyecto `apidowloadmusic`.

## Qué incluye

- Inicio: pegar enlace, analizar, ficha con miniatura y calidades (video / MP3).
- Buscar: resultados de YouTube; al tocar uno se analizan las calidades.
- Descargas: progreso, guardado en Descargas del teléfono y botón para abrir.
- Ajustes: URL de la API (emulador o IP de tu PC).

## Arranque

1. Deja la API corriendo en el PC (`http://0.0.0.0:8000`).
2. Abre este proyecto en Android Studio y ejecuta `androidApp`.
3. En el emulador la URL por defecto es `http://10.0.2.2:8000`.
4. En un teléfono de la misma Wi‑Fi, en Ajustes pon `http://IP-DE-TU-PC:8000`.

También: `./gradlew :androidApp:assembleDebug`.

El punto verde del encabezado indica que la API responde. Si está rojo, revisa que el servidor esté encendido y FFmpeg instalado si quieres 720p/1080p y MP3.
