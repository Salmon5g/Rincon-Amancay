# Configuración Firebase

Firebase Authentication y Firestore son la identidad y base compartida. El backend autenticado usa Firebase Admin para operaciones privilegiadas; Firebase Admin no aplica las reglas de los SDK cliente, así que la API debe validar rol, estado y propiedad. Las imágenes del flujo real se guardan en Supabase Storage; Firebase Storage se conserva para emuladores locales. Ver [reglas y permisos de Entrega 4](../docs/Entrega%204%20%E2%80%94%20Acceso,%20administraci%C3%B3n%20y%20tienda/5-seguridad-firestore-storage.md) y [capa de datos y fixtures](../docs/Entrega%204%20%E2%80%94%20Acceso,%20administraci%C3%B3n%20y%20tienda/6-capa-datos-y-datos-prueba.md).

Desde esta carpeta, `npm ci` instala las herramientas. `npm run emulators` inicia Authentication (9099), Firestore (8080) y Storage (9199), con interfaz en http://127.0.0.1:4000. Todos escuchan en `127.0.0.1` y usan `demo-rincon-amancay`.

`npm run seed` carga 29 documentos ficticios exclusivamente en los emuladores. `npm run test:rules` y `npm run test:storage` comprueban reglas contra emuladores activos. `npm run check` inicia emuladores, carga la fixture y ejecuta las verificaciones; úsalo con los puertos libres y nunca durante trabajo con datos reales.

`npm run seed:cloud` rechaza la fixture. Para probar Firestore Cloud con datos ficticios, usar exclusivamente un proyecto separado cuyo ID empiece con `rincon-amancay-dev-` y seguir `6-capa-datos-y-datos-prueba.md`. El cargador cloud usa Firebase Admin/ADC y reemplaza solo documentos de esa fixture, por lo que sus credenciales y permisos no deben compartirse.

El release Firestore del proyecto `rincon-amancay` se verificó el 1 de noviembre de 2026: apunta al ruleset `4005f7fb-84a4-43ce-bedc-166b2ae2431c`, cuyo contenido coincide con `firebase/firestore.rules`. No se encontró release de Firebase Storage; el flujo real de imágenes usa las políticas RLS del archivo `../infra/supabase/storage-imagenes.sql`. La API y el Hosting tienen procesos de publicación separados.

`firebase.json` referencia reglas, índices y puertos. `firestore.indexes.json` contiene el índice compuesto de ferias. `.firebaserc` conserva el alias `development = rincon-amancay`, que actualmente apunta al proyecto compartido: para cualquier operación cloud especifica siempre el ID completo del proyecto y no uses ese alias para cargar fixtures.

No guardar `.env`, claves, contraseñas ni credenciales de cuentas de servicio en el repositorio. Las credenciales locales del cargador de desarrollo deben quedar en `firebase/secrets/development.env`, que está excluido de Git.
