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

En **Emprendedoras**, el modo `real-api` permite crear y cancelar invitaciones, además de desactivar y reactivar cuentas existentes. Las invitaciones se guardan en `invitaciones/{id}`; el backend devuelve un código para compartir manualmente y todavía no manda correos. Los sectores y tipos seleccionados deben existir y estar activos. Las operaciones de cuenta requieren motivo, versión vigente del acceso y una identidad emprendedora vinculada a su tienda. Al desactivar, se oculta la tienda, se bloquea el acceso y se deshabilita Authentication. Al reactivar, la tienda sigue oculta y la emprendedora debe iniciar sesión y publicarla de nuevo. Si la conexión se interrumpe, reintentar con la misma solicitud conserva el `operacionId`.

El proyecto real no recibe los datos de `datos-prueba/`: ese archivo es exclusivo de emuladores. Para probar una invitación, primero comprueba en **Catálogos** que haya al menos un documento activo en `sectores` y otro en `tiposEmprendimiento`. Si están vacíos, crea esos documentos en **Firestore → Datos** con un ID estable y los campos `nombre` (texto) y `activo` (booleano `true`). El formulario **Agregar valor** del panel sigue siendo una vista previa; no guarda catálogos. En el proyecto `rincon-amancay` se crearon `sectores/alerce_demo` y `tiposEmprendimiento/artesania_demo` para la prueba controlada del 14 de octubre; sus nombres indican que son datos de prueba. Al abrir **Invitar emprendedora**, el panel vuelve a consultar los catálogos activos para mostrar los valores recientes.

Administradores y catálogos siguen disponibles como lectura. Asociaciones, ferias y configuración no tienen un contrato de datos acordado ni operaciones de servidor; sus formularios son demostrativos. La web aún no integra operaciones de catálogo, productos, stock e imágenes destinadas a la emprendedora. La asignación del rol administrador se hace mediante un proceso privilegiado, no desde el panel.

## Uso desde otro computador

La API local escucha solo en `127.0.0.1`. Por eso, `http://localhost:8080` solo funciona desde el mismo computador donde corre la API; nunca reemplazarlo por una IP de red ni exponer ese puerto. Para una prueba local, cada computador debe iniciar su propia API con identidad ADC autorizada en el proyecto. Para compartir un único backend entre el equipo, desplegar el servicio en Cloud Run y usar su URL HTTPS, con facturación y permisos de servicio revisados.

## Datos reales y cuidado operativo

Las operaciones modifican el proyecto real y el backend usa Admin SDK, que no está limitado por reglas Firestore. Empieza con una invitación controlada, revisa sus datos en consola y borra datos solo mediante un procedimiento administrativo. No ejecutar seeds, scripts demo ni pruebas automatizadas contra este modo. No expongas el puerto local a la red ni publiques `.env`.

Para una demo compartida desde otros equipos, configura Cloud Run y los permisos de servicio siguiendo [entorno compartido](entorno-compartido.md). Eso requiere revisar facturación y despliegue por separado.
