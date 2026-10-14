# Aplicación Android

La aplicación de Rincón Amancay está en `APP/` y está desarrollada con Kotlin y Jetpack Compose. Su experiencia móvil permite recorrer flujos de emprendedora: inicio, productos, creación y edición guiada con fotografía y variantes, vista previa de catálogo, registro de ventas, historial y ajustes. Incluye productos ficticios para facilitar las pruebas de navegación.

## Abrir y ejecutar

1. Abre la carpeta `android/APP` en Android Studio.
2. Espera a que Gradle sincronice el proyecto.
3. Selecciona la configuración `app` y ejecuta en un emulador o dispositivo Android.

Desde PowerShell también puedes compilar la app y ejecutar las pruebas unitarias:

```powershell
cd android/APP
.\gradlew.bat testDebugUnitTest assembleDebug
```

El APK de depuración se genera en `APP/app/build/outputs/apk/debug/app-debug.apk`.

## Organización

- `APP/app/src/main/java/com/example/amancay/MainActivity.kt`: navegación y coordinación de pantallas.
- `APP/app/src/main/java/com/example/amancay/ui/`: pantallas, componentes, transiciones y tema visual.
- `APP/app/src/main/java/com/example/amancay/data/AmancayData.kt`: modelos y estado de demostración que usan los flujos.
- `APP/app/src/main/res/`: recursos visuales e íconos.

El identificador de aplicación configurado actualmente es `com.example.amancay`. Las credenciales y archivos privados no se guardan en Git. `google-services.json` está excluido por `.gitignore`; nunca incorpores claves de cuentas de servicio a una aplicación móvil.

Para acuerdos de campos, permisos y operaciones compartidas con la web, consulta [contratos](../contratos/README.md). Para las instrucciones generales de los equipos, consulta [arranque por equipo](../docs/arranque-equipos.md).
