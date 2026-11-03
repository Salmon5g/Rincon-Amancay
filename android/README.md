# Aplicación Android

La aplicación unificada del equipo está en `android/app-combinada/`. Está desarrollada con Kotlin y Jetpack Compose; permite elegir modo comprador o modo emprendedora.

- **Comprador:** navegación pública de tiendas y productos publicados; puede crear una cuenta o ingresar con correo/contraseña o Google.
- **Emprendedora:** ingresa con las credenciales entregadas por administración; también puede vincular Google a esa misma cuenta. La app comprueba que el acceso esté activo, incluya el rol `emprendedora` y coincida con su tienda asignada. Desde el ingreso se puede solicitar por correo el restablecimiento de contraseña. La huella sigue siendo opcional y se configura desde el flujo existente de ajustes.

La API local puede usar emuladores o conectarse al proyecto Firebase real mediante Application Default Credentials. En ambos casos, Android Studio debe ejecutar la app en un emulador para usar `10.0.2.2` como dirección del computador. Sigue la guía [Conexión local de la app emprendedora](../docs/android-emprendedora-firebase.md). No uses el modo real con seeds ni pruebas automatizadas.

La app envía las fotos de productos a la API local autenticada; esta conserva originales privados y publica derivados del catálogo en Supabase Storage. La API debe estar encendida para cargar o publicar con imágenes. La carga del logo de tienda desde Android aún no está integrada.

El proyecto Android comprador anterior se conserva en `android/` como proyecto Gradle independiente. No abras ni edites ese proyecto para probar la aplicación unificada.

## Abrir y compilar

1. Abre `android/app-combinada` en Android Studio.
2. Espera que Gradle sincronice el proyecto.
3. Coloca el archivo local `google-services.json` en `android/app-combinada/app/`; debe registrar el ID `com.example.amancay`. El archivo está excluido de Git.
4. Selecciona la configuración `app` y ejecútala en un emulador Android.

## Configurar Google y recuperación de contraseña

1. En Firebase Console, confirma que **Authentication → Método de acceso → Google** esté habilitado y que **Correo electrónico/contraseña** también esté habilitado.
2. Cada integrante obtiene la huella SHA-1 de su certificado de desarrollo desde `android/app-combinada`:

   ```powershell
   .\gradlew.bat :app:signingReport
   ```

   Registra la SHA-1 de la variante `debug` en **Configuración del proyecto → Tus apps → Android (`com.example.amancay`)**. Cada computador puede tener una SHA-1 distinta.
3. Descarga el `google-services.json` actualizado para esa app y colócalo en `android/app-combinada/app/`. No lo subas a Git. Gradle obtiene de ese archivo el cliente OAuth web que necesita Credential Manager.
4. Sincroniza Gradle y vuelve a ejecutar la app. Si Google se acaba de habilitar o se agregó una SHA-1, los cambios pueden tardar unos minutos en propagarse.
5. Para una cuenta de emprendedora creada por administración, la primera vinculación se hace escribiendo su correo y contraseña en el ingreso y pulsando **Iniciar sesión con Google**. Debe elegirse en Google la misma dirección. Después podrá entrar con Google; la validación del rol y de la tienda continúa igual.
6. Para una cuenta compradora ya creada con correo y contraseña, se vincula Google de la misma forma desde **Iniciar sesión para comprar**. Una cuenta Google nueva puede iniciar sesión directamente. El botón de recuperación envía el enlace de Firebase al correo ingresado; el contenido del correo se administra en **Authentication → Plantillas → Restablecimiento de contraseña**.

No compartas contraseñas, archivos de configuración ni tokens por chat o en el repositorio. La configuración Android `google-services.json` y `local.properties` están excluidos de Git.

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
