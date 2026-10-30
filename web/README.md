# Web

Una sola aplicación Nuxt 4 + Vue + TypeScript para todos los perfiles. La ruta `/admin` incluye inicio de sesión y comprobación de rol. En `real-api`, la API autenticada permite gestionar catálogos, asociaciones e integrantes, invitaciones y desactivación/reactivación de emprendedoras, además de editar el nombre visible de sus cuentas. La invitada crea su contraseña y verifica su correo en `/invitacion-emprendedora?invitacionId={id}`. El panel comparte el enlace, pero no envía la invitación por correo. La ruta se incluye en la generación estática de Hosting; la ficha de tienda sigue su flujo separado y la vista pública de asociaciones todavía no está implementada.

## Ejecutar

Desde la raíz: npm --prefix web ci y npm --prefix web run dev. Abrir http://localhost:3000/admin.

El único plugin activo es app/plugins/firebase.client.ts. No copiar plugins adicionales desde ejemplos/nuxt/: duplicarían la inicialización. NUXT_PUBLIC_FIREBASE_MODO selecciona el entorno y requiere reiniciar Nuxt y recargar la página al cambiarlo:

- local (predeterminado): demo-rincon-amancay y emuladores. Seguir [arranque local](../docs/arranque-equipos.md). El cliente local conserva las operaciones compartidas para futuros componentes.
- real-lectura: rincon-amancay, Authentication y lecturas Firestore desde el navegador. Seguir [conexión real del administrador](../docs/admin-firestore-real.md). No requiere URL de API; no expone operaciones de negocio ni subida de imágenes.
- real-api: Firebase real más API autenticada. Para una demo local contra datos reales, seguir [API local con Firebase real](../docs/admin-firebase-real-api-local.md). Para servicio compartido, configurar la URL HTTPS de Cloud Run.

.env está excluido de Git. Copiar .env.example solo si no existe y completar la configuración SDK. runtimeConfig en nuxt.config.ts declara las variables públicas. No incluir claves privadas Admin. La configuración de Storage no crea un bucket y este modo no lo utiliza.

El modo real-api usa `cliente-compartido.ts`; en Nuxt dev admite HTTP solo para localhost. En producción la URL debe ser HTTPS. No configurar una URL ficticia.

## Comprobaciones

npm run typecheck comprueba TypeScript de los módulos. npm run build comprueba la compilación de Nuxt; no significa que la nube esté lista. npm run check:firebase prueba una lectura anónima que debe ser rechazada; no comprueba login ni todas las reglas.

Para compilar archivos estáticos destinados a Firebase Hosting, configura `.env` en `real-lectura` con `NUXT_PUBLIC_API_URL` vacía o en `real-api` con la API en HTTPS, y ejecuta `npm run generate`. El comando valida el proyecto `rincon-amancay`; luego publica desde la raíz siguiendo [la guía de Hosting](../docs/hosting-web.md).

npm run test:unit valida el adaptador compartido sin nube. npm run test:local requiere emuladores, API y dependencias backend; crea y elimina datos de prueba aislados y no debe coincidir con otras suites o una demostración.

El cliente base y los contratos siguen siendo comunes. Las pantallas deben invocar los métodos desde eventos u onMounted: el plugin se ejecuta únicamente en navegador. Nunca incluir Firebase Admin SDK en el frontend.
