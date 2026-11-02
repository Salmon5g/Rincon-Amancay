Las páginas consumen repositorios y reciben modelos de la aplicación, sin depender de tipos de documentos o cursores de Firestore. Cada proveedor implementa el contrato de `catalogo-publico.ts`.

- `catalogo-publico.ts`: contrato neutral para tiendas, productos, variantes e imágenes públicas. La paginación usa un cursor opaco de texto y las respuestas contienen modelos de `types/comprador-publico.ts`.
- `firebase/catalogo-publico.ts`: adaptador actual. Traduce consultas, cursores, documentos e imágenes de Firebase al contrato público; también normaliza errores de permisos y conectividad.
- `admin.ts`: repositorio del panel. Sus lecturas de catálogos y metadatos usan Firestore desde esta capa; las operaciones de modificación llaman a la API autenticada.
- `firebase/cliente-firestore.ts` y `firebase/lecturas-admin.ts`: adaptadores Firestore para las lecturas comunes del cliente, la selección de imágenes y el panel administrativo. Las interfaces antiguas de `lib/cliente-base.ts` y `lib/admin-lecturas.ts` se conservan para mantener compatibilidad con las pantallas.

El plugin de Nuxt crea el adaptador actual y lo entrega como `$catalogoPublico`. Las páginas de comprador y `ImagenPublica` solo usan ese contrato. Para Supabase se podrá implementar otro adaptador del mismo contrato y seleccionar el proveedor en el punto de composición, sin cambiar las pantallas.

La capa del comprador no implica que el backend completo ya sea intercambiable ni que Supabase esté conectado. Ver `docs/migracion-proveedores-datos.md` para el alcance y los pendientes.
