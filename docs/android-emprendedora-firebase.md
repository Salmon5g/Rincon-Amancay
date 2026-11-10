# Conexión local de la app emprendedora

La app Android unificada usa Firebase Authentication y Firestore. Las escrituras de tienda, catálogo, publicación y stock pasan por la API autenticada de `backend/`. En el modo real, el servidor local usa Firebase Admin con Application Default Credentials (ADC); la clave web de Firebase no sustituye esos permisos.

## Requisitos previos

- Abrir `android/app-combinada` en Android Studio y tener una aplicación Android Firebase para `com.example.amancay`.
- Guardar el archivo `google-services.json` en `android/app-combinada/app/`. Está ignorado por Git; no subirlo.
- La cuenta de prueba debe existir en Authentication y contar con documentos coherentes `accesos/{uid}`, `emprendedoras/{uid}` y `tiendasPrivadas/{tiendaId}`. `accesos` debe tener `estado: "activo"`, `roles` con `emprendedora` y `tiendaId`; el perfil debe apuntar al mismo `tiendaId`, y la ficha privada debe tener `propietarioUid` igual al UID.
- Firestore debe tener las reglas desplegadas desde `firebase/firestore.rules`. No conceder escrituras al cliente para reemplazar la API.

## Google y recuperación de contraseña

Firebase Authentication debe tener habilitados **Google** y **Correo electrónico/contraseña**. Cada integrante registra su propia huella SHA-1 `debug` en la app Android `com.example.amancay` de Firebase y descarga un `google-services.json` actualizado en `android/app-combinada/app/`; las huellas debug varían por computador y el archivo no se sube a Git. Se obtiene la huella con `.\gradlew.bat :app:signingReport` desde `android/app-combinada`. Después se sincroniza Gradle y se vuelve a ejecutar la app. Credential Manager usa el cliente OAuth web incluido en el archivo descargado.

La cuenta de emprendedora conserva su UID, rol y tienda: para vincular Google por primera vez, ingresa el correo y la contraseña entregados por administración y pulsa **Iniciar sesión con Google**. La dirección elegida en Google debe coincidir con ese correo. Si se pulsa Google sin escribir credenciales, la app intenta un ingreso normal y valida el mismo rol y tienda.

El comprador puede ingresar directamente con Google o correo/contraseña. Si ya tenía una cuenta compradora de correo/contraseña, escribe esas credenciales y pulsa Google para vincular ambos proveedores a la misma cuenta. El enlace **¿Olvidaste tu contraseña?** usa el envío de restablecimiento de Firebase; el texto del correo se administra en **Authentication → Plantillas → Restablecimiento de contraseña**. La app muestra una confirmación genérica para no revelar si un correo está registrado.

## Opción recomendada para comprobar con datos reales

Esta opción conecta el emulador Android con Authentication/Firestore reales y con una API ejecutada en el computador. Cambia datos del proyecto real. Usa una cuenta y tienda controladas.

1. En PowerShell, autentica la identidad de servidor que tiene permisos IAM para Firestore y Firebase Authentication:

   ```powershell
   gcloud auth application-default login
   gcloud config set project rincon-amancay
   ```

   No crees ni compartas claves de cuenta de servicio. El rol de administrador de la app no concede permisos IAM al proceso local.

2. Desde la raíz del repositorio, prepara y ejecuta la API local real siguiendo [API local con Firebase real](admin-firebase-real-api-local.md):

   ```powershell
   Copy-Item backend/.env.compartido.local.example backend/.env.compartido.local
   npm.cmd --prefix backend run start:compartido:local
   ```

   Deja abierta esta terminal. `http://localhost:8080/health` debe indicar el proyecto `rincon-amancay` y entorno `compartido`. No arranques emuladores al mismo tiempo.

3. Conservando la línea `sdk.dir` que Android Studio creó, agrega en `android/app-combinada/local.properties`:

   ```properties
   amancay.firebase.emulators=false
   amancay.api.baseUrl=http://10.0.2.2:8080/api/v1/
   ```

   `local.properties` está ignorado por Git. `10.0.2.2` permite que el emulador Android alcance el servidor local del computador; no sirve desde un teléfono físico.

4. Sincroniza Gradle y ejecuta la configuración `app` en un emulador.
5. Entra a **Modo emprendedora** con el correo y contraseña asignados. La app valida sesión, rol, estado y vínculo con la tienda antes de abrir las pantallas acordadas en el commit `23ceb82`.
6. En **Más → Mi tienda**, comprueba la lectura y edición de nombre, descripción, tipo, sector y contacto. Si está publicada, el backend actualiza la proyección pública de la tienda en la misma operación.
7. En **Productos**, la lista conserva el diseño acordado. Crear o editar abre el asistente guiado de cinco pasos de a251d70: foto; nombre, descripción, tipo y categoría; detalles que dependen del tipo activo; modalidad, precio, variantes y stock inicial; revisión final. La cámara guarda una captura completa en un archivo temporal, y la galería/cámara conservan JPEG, PNG o WebP originales si caben en 5 MiB y el límite de píxeles del servidor; solo se transcodifica cuando hace falta. Al guardar, la app envía la imagen a la API local autenticada; el original queda privado en Supabase y la emprendedora puede verlo en su vista previa. Publicar crea las derivadas WebP públicas del catálogo. Desde la lista también se puede publicar/retirar, ajustar stock y eliminar; los ajustes se hacen por API y quedan en el historial privado.

## Acceso biométrico en el dispositivo

Después de ingresar con las credenciales entregadas por la administración, la emprendedora puede activar **Entrar con huella digital** desde **Más → Configuración → Acceso**. Android solicita una verificación biométrica del dispositivo. En los siguientes ingresos al modo emprendedora, la pantalla original de bloqueo pide esa verificación antes de volver a validar en Firebase la sesión, rol, estado y tienda asignada.

La app no guarda la contraseña. Firebase Authentication conserva su sesión local; la preferencia local solo indica que ese UID activó el bloqueo biométrico en ese dispositivo. **Cerrar sesión**, desde Configuración, cierra Firebase y elimina esa preferencia. Si no hay biometría registrada o disponible, se debe ingresar con correo y contraseña. La prueba requiere un emulador con biometría inscrita o un dispositivo compatible con biometría fuerte.

Para comprobar el siguiente ingreso, activa la huella, cierra completamente la app sin usar **Cerrar sesión**, vuelve a abrirla y entra a **Modo emprendedora**. La pantalla debe pedir biometría y después validar la cuenta. Desactiva la huella desde **Más → Configuración** y repite para confirmar que vuelve al ingreso habitual. Finalmente, prueba **Cerrar sesión**: debe cerrar Firebase y borrar la preferencia biométrica de ese dispositivo.

La URL local de la API no es pública: solo la app del mismo emulador en ese computador puede alcanzarla. Otros integrantes deben iniciar su propia API con una identidad ADC autorizada o esperar una URL HTTPS compartida cuando el grupo decida dónde desplegarla.

## Modo de emuladores

Para usar los datos ficticios locales, inicia Firebase Emulators y la API de emuladores según [arranque por equipo](arranque-equipos.md). En `android/app-combinada/local.properties` configura:

```properties
amancay.firebase.emulators=true
amancay.api.baseUrl=http://10.0.2.2:8787/api/v1/
```

La app usa Authentication Emulator en `9099`, Firestore Emulator en `8080` y la API de emuladores en `8787`. Es un proyecto aislado `demo-rincon-amancay`; no contiene datos de Firebase real.

## Alcance de imágenes y funciones pendientes

En el modo compartido, la app carga imágenes de productos mediante la API local: los originales se guardan en el bucket privado de Supabase y la vista previa de la emprendedora los solicita con su sesión. Al publicar, la API genera WebP públicas para el catálogo; el comprador Android y la web convierten las rutas `catalogo/...` en imágenes públicas de Supabase. La carga del logo de tienda desde Android aún no está integrada.

El modo emulador mantiene Firebase Storage. El modo compartido local usa las políticas y rutas de Supabase del contrato 05. No poner la contraseña de la cuenta técnica ni una clave `service_role` en Android, Nuxt o Git.

La autenticación de emprendedoras utiliza correo y contraseña asignados por la administración, con desbloqueo biométrico opcional por dispositivo. El registro de ventas usa la API autenticada local y persiste precio, fecha, lugar y descuento de stock en Firestore; el historial lee hasta 200 registros como propietaria. Las invitaciones a ferias siguen siendo de demostración. Para leer ventas en Firebase remoto, publicar primero la regla de lectura propietaria añadida a `firebase/firestore.rules`.
