# Aplicación Android

La aplicación unificada para el equipo está en `android/app-combinada/`, desarrollada con Kotlin y Jetpack Compose. Al iniciar permite elegir modo comprador o modo emprendedora. El modo comprador lee en tiempo real desde Firestore las tiendas habilitadas y publicadas y sus productos publicados. El modo emprendedora todavía usa datos de demostración en memoria; sus cambios no se escriben en Firebase.

Para conectar el comprador, registra una aplicación Android con el identificador `com.example.amancay` en el proyecto Firebase del grupo y coloca el archivo descargado como `android/app-combinada/app/google-services.json`. Ese archivo local está excluido de Git. La lectura pública depende de que las reglas de Firestore estén publicadas y de que existan tiendas con `estadoPublicacion = publicado`, `habilitada = true`, `mostrarPrecios = true` y productos con `estadoPublicacion = publicado`. Este cliente no escribe en Firestore ni lee imágenes de Storage.

El proyecto Android anterior del comprador se conserva en la carpeta `android/` (raíz del proyecto Gradle). Mantén ambos proyectos en rutas distintas; en Windows no uses nombres que solo cambian entre mayúsculas y minúsculas.

## Abrir y ejecutar

1. Abre `android/app-combinada` en Android Studio.
2. Espera a que Gradle sincronice el proyecto.
3. Selecciona la configuración `app` y ejecútala en un emulador o dispositivo Android.

Desde PowerShell puedes compilar la app:

```powershell
cd android/app-combinada
.\gradlew.bat :app:assembleDebug --console=plain
```

El APK de depuración se genera en `android/app-combinada/app/build/outputs/apk/debug/app-debug.apk`.

## Organización

- `app/src/main/java/com/example/amancay/MainActivity.kt`: navegación y coordinación de modos.
- `app/src/main/java/com/example/amancay/buyer/`: catálogo comprador, filtros y detalles.
- `app/src/main/java/com/example/amancay/data/AmancayData.kt`: modelos y repositorio de demostración del modo emprendedora.
- `app/src/main/java/com/example/amancay/data/CatalogoCompradorRepository.kt`: repositorio de solo lectura de Firestore para el catálogo comprador.
- `app/src/main/java/com/example/amancay/ui/`: pantallas, componentes, transiciones y tema visual.
- `app/src/main/res/`: recursos e íconos.

Para acuerdos de campos, permisos y operaciones compartidas con la web, consulta [contratos](../contratos/README.md). Para instrucciones generales, consulta [arranque por equipo](../docs/arranque-equipos.md).
