# API autenticada local — v1

La API funciona en http://127.0.0.1:8787 exclusivamente con demo-rincon-amancay y sus emuladores. No usa el proyecto real ni acepta sus tokens. No hay despliegue.

## Arranque

Terminal 1, en firebase/: `npm run emulators`.

Terminal 2, en firebase/: `npm run seed` para disponer del catálogo ficticio. Sus UID de ejemplo no son por sí solos cuentas Auth.

Terminal 3, en backend/: `npm ci` y `npm run dev`.

GET /health devuelve el estado del proceso y el proyecto demo. No verifica la disponibilidad de todos los emuladores ni concede acceso a datos. Ctrl+C detiene la API. Si el puerto está ocupado, no detener procesos desconocidos.

## Endpoints

Todos son POST y requieren Content-Type: application/json y Authorization: Bearer <ID_TOKEN>:

| Ruta | Cuerpo |
|---|---|
| /api/v1/publicarTienda | tiendaId, operacionId, versionEsperada |
| /api/v1/retirarTienda | tiendaId, operacionId, versionEsperada |
| /api/v1/publicarProducto | Los anteriores y productoId |
| /api/v1/retirarProducto | Los anteriores y productoId |

Los valores son strings. versionEsperada es seconds:nanoseconds de actualizadoEn privado. operacionId se conserva al reintentar la misma intención. No enviar uid, roles, imágenes ni una copia de la ficha: el servidor obtiene identidad del token y contenido desde Firestore. Cualquier campo extra se rechaza. Límite del cuerpo: 16 KiB, sin compresión.

Respuesta correcta: {"datos":{"accion":"publicarTienda","operacionId":"..."}}. Error: {"error":{"code":"sin-permiso","mensaje":"..."}}.

## Identidad y autorización

El cliente inicia sesión con Firebase Authentication y obtiene un ID token con getIdToken (web) o getIdToken (Android). Envía ese token en Authorization. El backend llama a verifyIdToken(token, true), verifica expiración, proyecto, cuenta deshabilitada y revocación. Luego las operaciones consultan accesos y la propiedad dentro de la transacción. No basta con estar autenticado.

El emulador de Auth emite tokens sin firma criptográfica de producción; el Admin SDK los acepta únicamente con FIREBASE_AUTH_EMULATOR_HOST. El adaptador fuerza localhost, proyecto demo y rechaza NODE_ENV=production. Esta comprobación local no demuestra la verificación de firmas reales ni configura HTTPS. Para producción se necesita otro adaptador seguro, credenciales administradas y despliegue HTTPS, sin variables de emulador.

No hay endpoint de registro ni altas automáticas de roles. Las pruebas crean cuentas temporales solo en Auth local, inician sesión con correo/contraseña, comprueban peticiones y eliminan sus propias cuentas al terminar. Los UID de la carga ficticia no quedan convertidos en cuentas de acceso reutilizables.

## Estados HTTP

- 200: operación completada o reintento ya resuelto.
- 400: JSON, campos o formato inválidos.
- 401: sesión ausente, inválida, expirada, revocada o cuenta Auth deshabilitada.
- 403: permisos insuficientes o un Origin no permitido.
- 404: ruta/recurso no encontrado; 405: método incorrecto.
- 409: versión en conflicto, operacionId reutilizado o tienda no publicada.
- 413: cuerpo demasiado grande; 415: formato/compresión no admitidos.
- 422: datos no publicables, imagen inválida o límite del módulo.
- 503: Auth no disponible o integración pendiente; 500: fallo interno sin detalles sensibles.

No reintentar automáticamente 400/401/403/409 sin resolver la causa. Ante un fallo transitorio, conservar operacionId y la solicitud original para evitar repetir efectos.

## CORS y límites actuales

Orígenes web locales permitidos: http://localhost:3000 y http://127.0.0.1:3000. Clientes sin Origin (p. ej. Android) pueden enviar solicitudes, pero necesitan igualmente token y permisos. CORS no sustituye autenticación. La API escucha únicamente en loopback; acceso desde un dispositivo físico requiere una configuración futura revisada.

La publicación de productos no admite imágenes ficticias en esta API: si no hay imágenes responde 422 y si llega a validar una referencia responde 503 pendiente hasta integrar Storage. Publicar/retirar tiendas y retirar productos sí ejecutan las operaciones reales del emulador. La biblioteca conserva las pruebas de publicación de productos con un verificador de imágenes sustituido únicamente dentro de pruebas.

Sin alta de cuentas, validación real de Storage, ventas, límites de tasa de producción ni despliegue. No se abre Firestore en la nube.

## Verificación

Con emuladores activos, desde backend/: npm run typecheck y npm test. Las suites se ejecutan en serie porque comparten el fixture. npm run test:api ejecuta solo HTTP/Auth. Reponen el catálogo ficticio, por lo que no deben ejecutarse durante ediciones manuales.

23 casos HTTP/Auth aprobados y 16 casos del módulo de publicación aprobados. La suite HTTP cubre sesión real de emulador, permisos, revocación, cuenta deshabilitada, expiración, otro proyecto, suplantación por cuerpo, validación JSON, tamaño, CORS, errores y operaciones con reintentos.

Fuentes: https://firebase.google.com/docs/auth/admin/verify-id-tokens y https://firebase.google.com/docs/emulator-suite/connect_auth
