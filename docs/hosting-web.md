# Publicación web en Firebase Hosting

La web Nuxt se genera como archivos estáticos y se publica con Firebase Hosting. El catálogo consulta Firestore desde el navegador al abrir la página, así que las tiendas publicadas no quedan congeladas dentro de la generación.

**Estado al 14 de octubre de 2026:** [https://rincon-amancay.web.app](https://rincon-amancay.web.app) está publicado en modo `real-lectura`. La raíz redirige a `/comprador`, `/admin` permite iniciar sesión y consultar Firestore, y todavía no hay tiendas públicas cargadas.

## Antes de generar

1. Instalar dependencias desde la raíz del repositorio: `npm.cmd --prefix web ci` y `npm.cmd --prefix firebase ci`.
2. Crear `web/.env` desde `web/.env.example` si aún no existe y completar la configuración pública de la aplicación web Firebase.
3. Para esta entrega, configurar `NUXT_PUBLIC_FIREBASE_MODO=real-lectura` y dejar vacía `NUXT_PUBLIC_API_URL`. No incluir credenciales de Admin SDK.

La configuración Firebase Web queda incluida en el JavaScript público que recibe el navegador; las reglas de Authentication y Firestore controlan el acceso. En `real-lectura`, la administración puede iniciar sesión y consultar datos, pero no crear invitaciones ni efectuar cambios. Las operaciones de negocio necesitan la API real configurada en `real-api`.

## Generar y publicar

Desde la raíz del repositorio, ejecutar:

```powershell
npm.cmd --prefix web run generate
.\firebase\node_modules\.bin\firebase.cmd deploy --only hosting --project rincon-amancay
```

`generate` produce `web/.output/public`; esa carpeta es la única que Firebase Hosting publica. El `firebase.json` conserva las reglas de Firestore y Storage y añade Hosting sin alterar sus configuraciones. La raíz redirige al catálogo comprador y las páginas `/comprador` y `/admin` se generan como rutas estáticas.

La segunda orden usa la [Firebase CLI](https://firebase.google.com/docs/cli) incluida en `firebase/` y una cuenta con permiso para publicar Hosting en `rincon-amancay`. Puede usar las credenciales ADC creadas por `gcloud auth application-default login`; comprueba el acceso con `.\firebase\node_modules\.bin\firebase.cmd projects:list`. Si prefieres una instalación global con sesión propia:

```powershell
npm install -g firebase-tools
firebase login
firebase projects:list
```

El proyecto local se especifica explícitamente para evitar desplegar al proyecto equivocado. Después de publicar, abre la URL `https://rincon-amancay.web.app` y comprueba `/`, `/comprador` y `/admin`.

## Alcance publicado

Hosting sirve la interfaz web; no despliega ni crea el bucket de Storage ni la API de negocio. Si se publica con `real-lectura`, la vista comprador puede leer el catálogo público y administración conserva inicio de sesión y lecturas. Las invitaciones, cambios de cuentas, productos, stock e imágenes requieren que la API y Storage estén preparados por separado.
