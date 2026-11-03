# Demo del administrador contra Firebase real

Este modo usa Authentication, Firestore y Firebase Admin del proyecto `rincon-amancay` y ejecuta la API en el computador del responsable. No usa emuladores ni requiere Blaze para esa ejecución local. La API escucha solo en `127.0.0.1`; otros computadores no pueden conectarse directamente. El proveedor para una futura URL HTTPS compartida aún debe decidirse por el grupo; se ha mencionado Railway u otro servicio compatible. No desplegar en Cloud Run sin un nuevo acuerdo sobre costos.

## Preparar la identidad local

Instala Google Cloud CLI y autentica la cuenta que tiene acceso IAM al proyecto:

```powershell
gcloud auth application-default login
gcloud config set project rincon-amancay
```

La identidad de Google Cloud que ejecuta el backend necesita permisos IAM para Firestore (por ejemplo, `roles/datastore.user`) y para administrar usuarios de Authentication. La desactivación/reactivación usa `getUser`, `updateUser` y revocación de tokens; concede el rol de Identity Toolkit adecuado o un rol personalizado de menor privilegio con los permisos requeridos (`firebaseauth.users.get` y `firebaseauth.users.update`). El rol `administrador` en `accesos/{uid}` autoriza a la persona usuaria dentro de la aplicación; no reemplaza los permisos IAM de la identidad del proceso backend. No crear ni compartir claves de cuenta de servicio.

## Arrancar el backend

Desde la raíz del repositorio, copiar el ejemplo y luego ejecutar:

```powershell
Copy-Item backend/.env.compartido.local.example backend/.env.compartido.local
npm.cmd --prefix backend run start:compartido:local
```

La configuración fija el proyecto y el bucket esperados, limita CORS a localhost y prohíbe variables de emulador. El endpoint `http://localhost:8080/health` debe mostrar `entorno: compartido` y `proyecto: rincon-amancay`.

## Conectar el panel

En `web/.env`, mantener los valores `NUXT_PUBLIC_FIREBASE_*` del proyecto y establecer:

```ini
NUXT_PUBLIC_FIREBASE_MODO=real-api
NUXT_PUBLIC_API_URL=http://localhost:8080
```

En otra terminal, ejecutar `npm.cmd --prefix web run dev`, abrir `http://localhost:3000/admin` e iniciar sesión con la cuenta administradora de Firebase. El panel verifica el acceso en Firestore y la API vuelve a verificar el ID token, el estado de sesión y el rol antes de aceptar operaciones.

## Operaciones reales disponibles en esta integración

En **Emprendedoras**, el modo `real-api` permite crear y cancelar invitaciones, editar el nombre visible y el correo de acceso de una cuenta, y desactivar o reactivar cuentas existentes. El correo se cambia en Firebase Authentication mediante la API. Al cambiarlo, se cierra la sesión anterior, se marca la dirección nueva sin verificar y el panel entrega un enlace de verificación que debe compartirse en privado; no se manda automáticamente. La emprendedora conserva su contraseña, verifica el nuevo correo e inicia sesión nuevamente. La ficha de tienda mantiene su propio flujo. Las invitaciones se guardan en `invitaciones/{id}`; el panel entrega un enlace para compartir manualmente y la destinataria crea su contraseña y verifica el correo desde `/invitacion-emprendedora?invitacionId={id}`. La web no envía la invitación por correo; Firebase sí envía el mensaje de verificación que solicita la destinataria. Agrega el dominio web a Dominios autorizados de Authentication para que el enlace vuelva al sitio. Los sectores y tipos seleccionados deben existir y estar activos. Las operaciones de cuenta requieren motivo, versión vigente del acceso y una identidad emprendedora vinculada a su tienda. Al desactivar, se oculta la tienda, se bloquea el acceso y se deshabilita Authentication. Al reactivar, la tienda sigue oculta y la emprendedora debe iniciar sesión y publicarla de nuevo. La edición comprueba la versión de `usuarios/{uid}` y el correo vigente de Authentication. Si una respuesta se interrumpe, reintentar la misma solicitud conserva el `operacionId`. Ver [edición administrativa de cuentas](../contratos/14-edicion-cuentas-emprendedoras.md) y [ciclo de cuentas](../contratos/10-ciclo-cuentas.md).

El proyecto real no recibe los datos de `datos-prueba/`: ese archivo es exclusivo de emuladores. Para probar una invitación, comprueba en **Catálogos** que haya al menos un documento activo en `sectores` y otro en `tiposEmprendimiento`. Los valores existentes se cargan desde Firestore; no hace falta crearlos en la consola si ya están disponibles.

En modo `real-api`, **Catálogos** permite crear, editar, activar y desactivar sectores, categorías, tipos de emprendimiento y tipos de producto. Los sectores alimentan las invitaciones. Las definiciones de tipos de producto mantienen versiones históricas para no cambiar fichas ya creadas. Estas operaciones pasan por la API autenticada; el navegador no escribe directamente en Firestore. El entorno `real-lectura` solo permite consultar.

En modo `real-api`, **Asociaciones** permite crear, editar, desactivar/reactivar fichas y administrar sus integrantes. La asociación debe referenciar un sector activo; las asignaciones solo admiten cuentas emprendedoras activas con perfil y tienda coherentes. La relación se conserva al retirarla y no desactiva la cuenta ni la tienda. Los cambios usan la API autenticada, quedan auditados y conservan `operacionId` para reintentos. El contrato y los límites están en [administración de asociaciones](../contratos/13-administracion-asociaciones.md).

En modo `real-api`, **Ferias** permite crear borradores, editarlos antes de su inicio, publicar o cancelar actividades, registrar invitaciones y altas directas de emprendedoras o asociaciones, retirar participantes y marcar asistencia durante la actividad. Las operaciones requieren versiones vigentes, usan la API autenticada y quedan auditadas. Una invitación no envía correo ni WhatsApp; se registra para coordinación manual. El contrato, estados, reglas y límites están en [administración de ferias](../contratos/16-administracion-ferias.md).

En **Configuración general**, el panel guarda `configuracionAplicacion/navegacion` por la API autenticada y con control de versión. El valor inicial es `permitir`. Aunque se pueden guardar `invitar` y `exigir`, la web compradora y Android aún no consumen esa preferencia; no activar esos modos hasta completar y verificar su integración en los dos clientes.

En **Administradores**, se puede otorgar el rol a una cuenta de Authentication existente, habilitada y con correo verificado, y retirar el rol de otra cuenta sin borrarla ni deshabilitar su inicio de sesión. No se puede quitar la propia cuenta ni dejar el sistema sin administradores. La gestión no crea identidades ni contraseñas; requiere que el servidor tenga permisos IAM para consultar Authentication.

Las reglas locales permiten que un administrador lea también los valores inactivos y mantienen bloqueadas las escrituras directas de cualquier perfil. Publica las reglas revisadas de `firebase/firestore.rules` antes de administrar catálogos desde la web real. La API usa Firebase Admin y requiere permisos IAM de la identidad ADC. El CRUD de catálogos no usa Storage; no hay que crear un bucket para estas operaciones.

La vista pública de asociaciones (RF-ASO-03) y la página pública de ferias siguen pendientes. Los logos de asociación y las imágenes de feria se guardan como URL HTTPS; estas pantallas todavía no cargan archivos en Storage. La política de navegación se almacena, pero aún no es aplicada por los clientes comprador. Android ahora integra Authentication, edición de tienda de texto, productos y ajustes de stock con la API local; publicación de producto requiere imagen y no hay carga Android todavía. La web aún no integra el portal de productos y stock de la emprendedora.

## Uso desde Android Emulator en el mismo computador

La aplicación unificada puede llamar esta misma API desde un emulador Android usando `http://10.0.2.2:8080/api/v1/`; el alias `10.0.2.2` apunta al computador anfitrión. Configura `amancay.firebase.emulators=false` y `amancay.api.baseUrl=http://10.0.2.2:8080/api/v1/` en el `local.properties` ignorado de `android/app-combinada`. La app además se conecta directamente a Authentication/Firestore reales mediante `google-services.json`. Ver [la guía completa de Android](android-emprendedora-firebase.md).

## Uso desde otro computador

La API local escucha solo en `127.0.0.1`. Por eso, `http://localhost:8080` solo funciona desde el mismo computador donde corre la API; nunca reemplazarlo por una IP de red ni exponer ese puerto. Para una prueba local, cada computador debe iniciar su propia API con identidad ADC autorizada en el proyecto. Para compartir un único backend entre el equipo, desplegar más adelante en el proveedor HTTPS que el grupo elija (posiblemente Railway) y revisar antes credenciales, IAM, costos y límites.

## Datos reales y cuidado operativo

Las operaciones modifican el proyecto real y el backend usa Admin SDK, que no está limitado por reglas Firestore. Empieza con una invitación controlada, revisa sus datos en consola y borra datos solo mediante un procedimiento administrativo. No ejecutar seeds, scripts demo ni pruebas automatizadas contra este modo. No expongas el puerto local a la red ni publiques `.env`.

El despliegue público de la API permanece pendiente de una decisión del grupo y de revisar proveedor, IAM, secretos, costos y URL HTTPS. La web publicada no podrá ejecutar operaciones administrativas hasta que use esa URL. El modo local permite trabajar y verificar cambios en un solo computador.
