# Conexión local de la app emprendedora

La app Android unificada usa Firebase Authentication y Firestore. Las escrituras de tienda, catálogo, publicación y stock pasan por la API autenticada de `backend/`. En el modo real, el servidor local usa Firebase Admin con Application Default Credentials (ADC); la clave web de Firebase no sustituye esos permisos.

## Requisitos previos

- Abrir `android/app-combinada` en Android Studio y tener una aplicación Android Firebase para `com.example.amancay`.
- Guardar el archivo `google-services.json` en `android/app-combinada/app/`. Está ignorado por Git; no subirlo.
- La cuenta de prueba debe existir en Authentication y contar con documentos coherentes `accesos/{uid}`, `emprendedoras/{uid}` y `tiendasPrivadas/{tiendaId}`. `accesos` debe tener `estado: "activo"`, `roles` con `emprendedora` y `tiendaId`; el perfil debe apuntar al mismo `tiendaId`, y la ficha privada debe tener `propietarioUid` igual al UID.
- Firestore debe tener las reglas desplegadas desde `firebase/firestore.rules`. No conceder escrituras al cliente para reemplazar la API.

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
7. En **Productos**, la lista conserva el diseño acordado. Crear o editar abre el asistente guiado de cinco pasos de a251d70: foto; nombre, descripción, tipo y categoría; detalles que dependen del tipo activo; modalidad, precio, variantes y stock inicial; revisión final. El formulario conserva validaciones y operaciones Firebase/API. El selector de cámara o galería solo guarda la foto temporalmente en el teléfono: aún no se carga a Storage. Por eso el botón final guarda un borrador y avisa que publicar requiere una imagen autorizada. Desde la lista también se puede publicar/retirar y ajustar stock; los ajustes se hacen por API y quedan en el historial privado.

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

La app todavía no carga fotos ni logo. Las tiendas pueden publicarse sin logo; la API no permite publicar un producto sin una imagen guardada y validada en Firebase Storage. El formulario deja crear y editar borradores, pero el botón de publicación se mantiene bloqueado cuando no existe una imagen válida.

Se dejó el límite explícito para la futura integración Supabase: la app necesitará un adaptador de medios, un campo de referencia acordado para logos/fotos y una verificación del lado servidor compatible con esa referencia. No se acepta todavía una URL o ruta de Supabase en `imagenes`; el backend actual solo valida objetos de Firebase Storage. La política y credenciales de carga deben acordarse antes de implementar el proveedor. No poner una clave `service_role` en Android ni en Git.

La autenticación de emprendedoras utiliza correo y contraseña asignados por la administración, con desbloqueo biométrico opcional por dispositivo. El registro de ventas, las invitaciones a ferias y la carga de imágenes no forman parte de esta integración todavía.
