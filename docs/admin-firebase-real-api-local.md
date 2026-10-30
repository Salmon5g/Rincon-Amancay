# Demo del administrador contra Firebase real

Este modo usa Authentication y Firestore del proyecto `rincon-amancay` y ejecuta la API en el computador del responsable. No usa emuladores ni requiere Cloud Run/Blaze para esa demo local. La API escucha solo en `127.0.0.1`; otros computadores no pueden conectarse. Para una URL pública y que el equipo la use desde distintos dispositivos, se debe desplegar en Cloud Run, lo que requiere vincular facturación.

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

En **Emprendedoras**, el modo `real-api` permite crear y cancelar invitaciones, editar el nombre visible de una cuenta, y desactivar o reactivar cuentas existentes. El correo de acceso se muestra desde Authentication; no se modifica en este formulario. La ficha de tienda mantiene su propio flujo. Las invitaciones se guardan en `invitaciones/{id}`; el panel entrega un enlace para compartir manualmente y la destinataria crea su contraseña y verifica el correo desde `/invitacion-emprendedora?invitacionId={id}`. La web no envía la invitación por correo; Firebase sí envía el mensaje de verificación que solicita la destinataria. Agrega el dominio web a Dominios autorizados de Authentication para que el enlace vuelva al sitio. Los sectores y tipos seleccionados deben existir y estar activos. Las operaciones de cuenta requieren motivo, versión vigente del acceso y una identidad emprendedora vinculada a su tienda. Al desactivar, se oculta la tienda, se bloquea el acceso y se deshabilita Authentication. Al reactivar, la tienda sigue oculta y la emprendedora debe iniciar sesión y publicarla de nuevo. La edición del nombre visible comprueba la versión de `usuarios/{uid}`. Si una respuesta se interrumpe, reintentar la misma solicitud conserva el `operacionId`. Ver [edición administrativa de cuentas](../contratos/14-edicion-cuentas-emprendedoras.md) y [ciclo de cuentas](../contratos/10-ciclo-cuentas.md).

El proyecto real no recibe los datos de `datos-prueba/`: ese archivo es exclusivo de emuladores. Para probar una invitación, comprueba en **Catálogos** que haya al menos un documento activo en `sectores` y otro en `tiposEmprendimiento`. Los valores existentes se cargan desde Firestore; no hace falta crearlos en la consola si ya están disponibles.

En modo `real-api`, **Catálogos** permite crear, editar, activar y desactivar sectores, categorías, tipos de emprendimiento y tipos de producto. Los sectores alimentan las invitaciones. Las definiciones de tipos de producto mantienen versiones históricas para no cambiar fichas ya creadas. Estas operaciones pasan por la API autenticada; el navegador no escribe directamente en Firestore. El entorno `real-lectura` solo permite consultar.

En modo `real-api`, **Asociaciones** permite crear, editar, desactivar/reactivar fichas y administrar sus integrantes. La asociación debe referenciar un sector activo; las asignaciones solo admiten cuentas emprendedoras activas con perfil y tienda coherentes. La relación se conserva al retirarla y no desactiva la cuenta ni la tienda. Los cambios usan la API autenticada, quedan auditados y conservan `operacionId` para reintentos. La colección se administra desde el backend: las reglas de Firestore no habilitan escritura directa desde el navegador. El contrato y los límites están en [administración de asociaciones](../contratos/13-administracion-asociaciones.md).

Las reglas locales permiten que un administrador lea también los valores inactivos y mantienen bloqueadas las escrituras directas de cualquier perfil. Publica las reglas revisadas de `firebase/firestore.rules` antes de administrar catálogos desde la web real. La API usa Firebase Admin y requiere permisos IAM de la identidad ADC. El CRUD de catálogos no usa Storage; no hay que crear un bucket para estas operaciones.

La vista pública de asociaciones (RF-ASO-03), ferias y configuración siguen pendientes de modelo y operaciones. Los logos de asociación se guardan como URL HTTPS; esta pantalla todavía no carga archivos en Storage. La web aún no integra las operaciones de catálogo, productos, stock e imágenes destinadas a la emprendedora. La asignación del rol administrador se hace mediante un proceso privilegiado, no desde el panel.

## Uso desde otro computador

La API local escucha solo en `127.0.0.1`. Por eso, `http://localhost:8080` solo funciona desde el mismo computador donde corre la API; nunca reemplazarlo por una IP de red ni exponer ese puerto. Para una prueba local, cada computador debe iniciar su propia API con identidad ADC autorizada en el proyecto. Para compartir un único backend entre el equipo, desplegar el servicio en Cloud Run y usar su URL HTTPS, con facturación y permisos de servicio revisados.

## Datos reales y cuidado operativo

Las operaciones modifican el proyecto real y el backend usa Admin SDK, que no está limitado por reglas Firestore. Empieza con una invitación controlada, revisa sus datos en consola y borra datos solo mediante un procedimiento administrativo. No ejecutar seeds, scripts demo ni pruebas automatizadas contra este modo. No expongas el puerto local a la red ni publiques `.env`.

Para una demo compartida desde otros equipos, configura Cloud Run y los permisos de servicio siguiendo [entorno compartido](entorno-compartido.md). Eso requiere revisar facturación y despliegue por separado.
