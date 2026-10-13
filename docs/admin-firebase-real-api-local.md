# Demo del administrador contra Firebase real

Este modo usa Authentication y Firestore del proyecto `rincon-amancay` y ejecuta la API en el computador del responsable. No usa emuladores ni requiere Cloud Run/Blaze para esa demo local. La API escucha solo en `127.0.0.1`; otros computadores no pueden conectarse. Para una URL pública y que el equipo la use desde distintos dispositivos, se debe desplegar en Cloud Run, lo que requiere vincular facturación.

## Preparar la identidad local

Instala Google Cloud CLI y autentica la cuenta que tiene acceso IAM al proyecto:

```powershell
gcloud auth application-default login
gcloud config set project rincon-amancay
```

La identidad de Google Cloud que ejecuta el backend necesita permisos IAM para Firestore, como `roles/datastore.user`. El rol `administrador` en `accesos/{uid}` autoriza a la persona usuaria dentro de la aplicación; no reemplaza los permisos IAM de la identidad del proceso backend. No crear ni compartir claves de cuenta de servicio.

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

El formulario **Emprendedoras → Invitar emprendedora** registra una invitación real en `invitaciones/{id}`. Los sectores y tipos elegidos deben existir y estar activos. La API devuelve un código para compartir manualmente; el backend todavía no manda correos. Reintentar sin cambiar el formulario conserva el `operacionId` y no duplica la invitación.

Las otras secciones del panel aún muestran vistas previas o lecturas. No están habilitadas para escribir asociaciones, ferias, catálogos ni administradores. La edición del panel todavía no cubre las otras operaciones de catálogo, productos, stock e imágenes que existen en la API. Antes de una demo con clientes, confirmar en la interfaz qué se puede hacer realmente y no presentar esos formularios como persistentes.

## Datos reales y cuidado operativo

Las operaciones modifican el proyecto real y el backend usa Admin SDK, que no está limitado por reglas Firestore. Empieza con una invitación controlada, revisa sus datos en consola y borra datos solo mediante un procedimiento administrativo. No ejecutar seeds, scripts demo ni pruebas automatizadas contra este modo. No expongas el puerto local a la red ni publiques `.env`.

Para una demo compartida desde otros equipos, configura Cloud Run y los permisos de servicio siguiendo [entorno compartido](entorno-compartido.md). Eso requiere revisar facturación y despliegue por separado.
