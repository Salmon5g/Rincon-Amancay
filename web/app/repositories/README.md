Las páginas consumen repositorios y reciben modelos de la aplicación, sin depender de tipos de documentos o cursores de Firestore. Cada proveedor implementa el contrato de `catalogo-publico.ts`.

- `catalogo-publico.ts`: contrato neutral para tiendas, productos, variantes e imágenes públicas. La paginación usa un cursor opaco de texto y las respuestas contienen modelos de `types/comprador-publico.ts`.
- `firebase/catalogo-publico.ts`: adaptador actual. Traduce consultas, cursores, documentos e imágenes de Firebase al contrato público; también normaliza errores de permisos y conectividad.
- `admin.ts`: repositorio del panel. Las operaciones de modificación llaman a la API autenticada. Sus lecturas y comprobaciones de sesión aún usan Firebase directamente; quedan pendientes de mover detrás de puertos propios antes de una migración de proveedor.

El plugin de Nuxt crea el adaptador actual y lo entrega como `$catalogoPublico`. Las páginas de comprador y `ImagenPublica` solo usan ese contrato. Para Supabase se podrá implementar otro adaptador del mismo contrato y seleccionar el proveedor en el punto de composición, sin cambiar las pantallas.

La capa del comprador no implica que el backend completo ya sea intercambiable ni que Supabase esté conectado. Ver `docs/migracion-proveedores-datos.md` para el alcance y los pendientes.
