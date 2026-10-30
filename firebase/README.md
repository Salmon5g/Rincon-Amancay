# Configuración Firebase

Entorno local: reglas de Firestore por rol y reglas de Storage para imágenes de productos. Consultar ../docs/permisos-locales.md y ../contratos/05-imagenes-storage.md.

Desde esta carpeta, npm ci instala las herramientas; npm run emulators inicia Authentication (9099), Firestore (8080) y Storage (9199), con interfaz en http://127.0.0.1:4000. Todos escuchan en 127.0.0.1 y usan demo-rincon-amancay.

npm run seed carga 29 documentos ficticios exclusivamente en los emuladores. npm run test:rules y npm run test:storage comprueban las reglas contra emuladores activos, en ese orden y sin ejecutar otras suites simultáneas. npm run check inicia los emuladores, ejecuta la carga/comprobaciones y ambas suites de reglas, y luego solicita su cierre; usarlo con los puertos libres.

No cargar `datos-prueba/catalogo-local.json` en Firebase real. `npm run seed:cloud` se conserva por compatibilidad, pero ahora rechaza el fixture porque está marcado como exclusivo de emuladores. Para preparar datos reales, usar los módulos administrativos conectados a la API con el proyecto y permisos revisados.

El estado de Firebase real se debe verificar en la consola del proyecto antes de operar: el repositorio no confirma qué reglas están publicadas ni si Storage, Hosting o la API están desplegados. Publicar reglas desde este repositorio reemplaza las reglas vigentes. Los SDK Admin requieren autorización IAM en el backend porque no aplican las reglas de cliente.

firebase.json referencia reglas, índices y puertos. .firebaserc define development = rincon-amancay, pero los comandos locales fijan explícitamente demo-rincon-amancay. firestore.indexes.json sigue vacío; añadir índices según las consultas acordadas.

Las fotos se almacenan en Storage; los documentos guardan sus rutas. No guardar archivos de usuarios ni credenciales en este repositorio.
