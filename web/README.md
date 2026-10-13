# Web

Una sola aplicación Nuxt 4 + Vue + TypeScript para todos los perfiles. La ruta /admin incluye acceso por correo, comprobación de rol y lecturas de Firestore. Las altas, invitaciones, publicaciones y cambios de stock aún no se ejecutan desde esta interfaz.

## Ejecutar

Desde la raíz: npm --prefix web ci y npm --prefix web run dev. Abrir http://localhost:3000/admin.

El único plugin activo es app/plugins/firebase.client.ts. No copiar plugins adicionales desde ejemplos/nuxt/: duplicarían la inicialización. NUXT_PUBLIC_FIREBASE_MODO selecciona el entorno y requiere reiniciar Nuxt y recargar la página al cambiarlo:

- local (predeterminado): demo-rincon-amancay y emuladores. Seguir [arranque local](../docs/arranque-equipos.md). El cliente local conserva las operaciones compartidas para futuros componentes.
- real-lectura: rincon-amancay, Authentication y lecturas Firestore desde servidor. Seguir [conexión real del administrador](../docs/admin-firestore-real.md). No requiere URL de API; no expone operaciones de negocio ni subida de imágenes.

.env está excluido de Git. Copiar .env.example solo si no existe y completar la configuración SDK. runtimeConfig en nuxt.config.ts declara las variables públicas. No incluir claves privadas Admin. La configuración de Storage no crea un bucket y este modo no lo utiliza.

El adaptador cliente-compartido.ts y su ejemplo siguen reservados para la API HTTPS completa, aún pendiente. No sustituir su URL por una dirección ficticia. El plugin de solo lectura no los utiliza.

## Comprobaciones

npm run typecheck comprueba TypeScript de los módulos. npm run build comprueba la compilación de Nuxt; no significa que la nube esté lista. npm run check:firebase prueba una lectura anónima que debe ser rechazada; no comprueba login ni todas las reglas.

npm run test:unit valida el adaptador compartido sin nube. npm run test:local requiere emuladores, API y dependencias backend; crea y elimina datos de prueba aislados y no debe coincidir con otras suites o una demostración.

El cliente base y los contratos siguen siendo comunes. Las pantallas deben invocar los métodos desde eventos u onMounted: el plugin se ejecuta únicamente en navegador. Nunca incluir Firebase Admin SDK en el frontend.
