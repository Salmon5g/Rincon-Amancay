# Aplicación Android

La aplicación unificada para el equipo está en `android/app-combinada/`, desarrollada con Kotlin y Jetpack Compose. Al iniciar permite elegir modo comprador o modo emprendedora. El catálogo y los filtros por categoría comparten el estado de productos de la misma ejecución.

Los datos de esta demo están en memoria: no se publican en un backend ni se comparten entre teléfonos, y se reinician al cerrar la app. Para que los productos se compartan entre integrantes se debe integrar un backend autenticado con permisos de lectura y publicación.

El proyecto Android anterior del comprador se conserva en la carpeta `android/` (raíz del proyecto Gradle). Mantén ambos proyectos en rutas distintas; en Windows no uses nombres que solo cambian entre mayúsculas y minúsculas.

## Abrir y ejecutar

1. Abre `android/app-combinada` en Android Studio.
2. Espera a que Gradle sincronice el proyecto.
3. Selecciona la configuración `app` y ejecútala en un emulador o dispositivo Android.

Desde PowerShell puedes compilar la app y ejecutar pruebas unitarias:

```powershell
cd android/app-combinada
.\gradlew.bat testDebugUnitTest assembleDebug
```

El APK de depuración se genera en `android/app-combinada/app/build/outputs/apk/debug/app-debug.apk`.

## Organización

- `app/src/main/java/com/example/amancay/MainActivity.kt`: navegación y coordinación de modos.
- `app/src/main/java/com/example/amancay/buyer/`: catálogo comprador, filtros y detalles.
- `app/src/main/java/com/example/amancay/data/AmancayData.kt`: modelos, catálogo de demostración y estado compartido en memoria.
- `app/src/main/java/com/example/amancay/ui/`: pantallas, componentes, transiciones y tema visual.
- `app/src/main/res/`: recursos e íconos.

Para acuerdos de campos, permisos y operaciones compartidas con la web, consulta [contratos](../contratos/README.md). Para instrucciones generales, consulta [arranque por equipo](../docs/arranque-equipos.md).