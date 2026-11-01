# Aplicación Android

La aplicación unificada del equipo está en `android/app-combinada/`. Está desarrollada con Kotlin y Jetpack Compose; permite elegir modo comprador o modo emprendedora.

- **Comprador:** lectura pública de tiendas y productos publicados desde Firestore.
- **Emprendedora:** inicio con Firebase Authentication por correo/contraseña; comprueba que el acceso esté activo, incluya el rol `emprendedora` y coincida con su tienda asignada. Lee su tienda y catálogo privado desde Firestore. La ficha, creación/edición de productos, publicaciones y ajustes de stock pasan por la API autenticada.

La API local puede usar emuladores o conectarse al proyecto Firebase real mediante Application Default Credentials. En ambos casos, Android Studio debe ejecutar la app en un emulador para usar `10.0.2.2` como dirección del computador. Sigue la guía [Conexión local de la app emprendedora](../docs/android-emprendedora-firebase.md). No uses el modo real con seeds ni pruebas automatizadas.

La carga de logos/fotos desde Android todavía no está habilitada. Guardar productos como borradores sí funciona; publicar un producto exige una imagen que el backend pueda validar en Firebase Storage. Supabase se mantiene como futuro proveedor posible, pendiente de acordar permisos y validación con el profesor; una ruta de Supabase no es aceptada todavía por el validador de publicación.

El proyecto Android comprador anterior se conserva en `android/` como proyecto Gradle independiente. No abras ni edites ese proyecto para probar la aplicación unificada.

## Abrir y compilar

1. Abre `android/app-combinada` en Android Studio.
2. Espera que Gradle sincronice el proyecto.
3. Coloca el archivo local `google-services.json` en `android/app-combinada/app/`; debe registrar el ID `com.example.amancay`. El archivo está excluido de Git.
4. Selecciona la configuración `app` y ejecútala en un emulador Android.

Desde PowerShell:

```powershell
cd android/app-combinada
.\gradlew.bat :app:assembleDebug --console=plain
```

El APK de depuración queda en `android/app-combinada/app/build/outputs/apk/debug/app-debug.apk`.

## Organización del código

- `app/src/main/java/com/example/amancay/MainActivity.kt`: inicialización Firebase y selección de modo.
- `app/src/main/java/com/example/amancay/buyer/`: catálogo comprador.
- `app/src/main/java/com/example/amancay/emprendedora/ModoEmprendedoraConectada.kt`: ingreso y edición de la tienda.
- `app/src/main/java/com/example/amancay/emprendedora/ProductosEmprendedoraPanel.kt`: fichas, publicación/retirada y ajuste de stock.
- `app/src/main/java/com/example/amancay/data/CuentaEmprendedoraRepository.kt`: cuenta, permiso y tienda.
- `app/src/main/java/com/example/amancay/data/ProductosEmprendedoraRepository.kt`: lecturas de productos/catálogos y solicitudes a la API.
- `app/src/main/java/com/example/amancay/data/ApiEmprendedoraClient.kt`: cliente HTTP autenticado con ID token de Firebase.
- `app/src/main/java/com/example/amancay/data/FirebaseClientServices.kt`: selección de Firebase real o emuladores.
- `app/src/main/java/com/example/amancay/ui/`: tema visual y componentes compartidos.

Los contratos de datos y permisos están en [contratos](../contratos/README.md). La API local y su configuración real se describen en [esta guía](../docs/admin-firebase-real-api-local.md).
