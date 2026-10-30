# Administración de catálogos

Este contrato cubre el mantenimiento administrativo de sectores, categorías, tipos de emprendimiento y tipos de producto. Los sectores alimentan el formulario de invitación a emprendedoras; se incluyen para que el administrador pueda mantener esa opción sin editar Firestore manualmente.

## Rutas y datos

### Categorías y tipos de emprendimiento

- `categorias/{id}`
- `tiposEmprendimiento/{id}`

### Sectores

- `sectores/{id}`

Los sectores usan el mismo esquema común (`nombre`, `nombreNormalizado`, `activo`, `creadoEn` y `actualizadoEn`) y las mismas operaciones administrativas. Se consultan por todas las personas solo cuando están activos; el administrador puede leer también los inactivos para reactivarlos.

Documentos nuevos guardan `nombre`, `nombreNormalizado`, `activo`, `creadoEn` y `actualizadoEn`. El ID se genera desde el nombre al crear y no cambia al editarlo. Los nombres normalizados son únicos dentro de cada colección. El estado inactivo conserva el documento y todas sus referencias. Los autores de las operaciones se registran en la auditoría, no se publican en los catálogos.

### Tipos de producto versionados

- `tiposProducto/{id}` contiene nombre, estado y `versionActual`.
- `tiposProducto/{id}/versiones/{numero}` guarda la definición de esa versión: `version`, `campos`, `opcionesVariante`, `creadoEn` y `actualizadoEn`.
- Cada campo de producto tiene `clave`, `etiqueta`, `tipo: "texto"` y `obligatorio`.

Editar solamente el nombre mantiene la versión actual. Cambiar los campos u opciones de variante crea una versión nueva; las versiones anteriores no se modifican ni se eliminan. Los productos ya creados siguen apuntando a su versión original. El backend de productos permite mantener una ficha con referencias históricas inactivas, pero solo acepta catálogos activos al crear un producto.

Por ahora se admiten hasta 20 campos de texto y 10 opciones de variante por definición. No se admiten claves repetidas ni claves que no comiencen con minúscula o incluyan símbolos distintos de letras, números y guion bajo.

## Operaciones HTTP

Todas son `POST /api/v1/{operacion}`, requieren `Authorization: Bearer <ID_TOKEN>` de Firebase Authentication y vuelven a comprobar dentro de una transacción que el acceso del actor esté activo y contenga el rol `administrador`.

| Operación | Propósito | Campos principales |
| --- | --- | --- |
| `crearElementoCatalogo` | Crear un sector, categoría, tipo de emprendimiento o tipo de producto | `operacionId`, `coleccion`, `nombre`; los tipos de producto además requieren `definicion` |
| `editarElementoCatalogo` | Cambiar el nombre o la definición | `operacionId`, `coleccion`, `documentoId`, `versionEsperada`, `nombre`; tipos de producto además requieren `definicion` |
| `cambiarEstadoCatalogo` | Activar o desactivar sin borrar | `operacionId`, `coleccion`, `documentoId`, `versionEsperada`, `activo` |

`coleccion` solo admite `sectores`, `categorias`, `tiposEmprendimiento` o `tiposProducto`. Los valores `operacionId` se conservan en los reintentos. Cada escritura guarda un comprobante idempotente en `operacionesAdministracion/{uid}_{operacionId}` y una entrada en `auditoriaAdministracion/{uid}_{operacionId}` con actor, operación, colección, ID, estado anterior/nuevo y fecha. Reutilizar la misma operación con contenido distinto se rechaza. Editar o cambiar estado exige la versión leída desde `actualizadoEn`; una versión antigua se rechaza sin sobrescribir cambios ajenos.

No hay borrado físico desde el panel. Desactivar un valor impide ofrecerlo para nuevas invitaciones o productos, conserva las referencias existentes y permite consultar definiciones históricas. Una invitación creada mientras el catálogo estaba activo puede aceptarse hasta su vencimiento o cancelación, aunque entretanto se haya desactivado su tipo. Los administradores pueden leer los catálogos completos; compradores y visitantes solo pueden leer los activos. Las emprendedoras autenticadas pueden consultar definiciones inactivas para mantener productos existentes. Ningún perfil cliente puede escribir estos documentos directamente.

## Cliente web

La pantalla administrativa usa `web/app/repositories/admin.ts` para cargar datos y llamar a las operaciones autenticadas. El acceso de escritura solo está disponible en `real-api` (API local contra Firebase real o servicio HTTPS) y en `local` (emuladores); `real-lectura` permanece de solo lectura. Las pantallas no importan ni ejecutan escrituras de Firestore.

## Operación real

La API compartida inicializa Firebase Admin con Application Default Credentials y escribe en el proyecto configurado. Para probar desde un computador sin desplegar API, usar la guía `docs/admin-firebase-real-api-local.md`, habilitar el modo `real-api` en `web/.env` y ejecutar la API local contra el proyecto real. `firebase/scripts/seed-cloud.mjs` rechaza el fixture ficticio `datos-prueba/catalogo-local.json`; no se usa para poblar proyectos reales.

El archivo `firebase/firestore.rules` debe publicarse antes de que el cliente web pueda listar desde Firestore los valores inactivos como administrador. No publicar reglas automáticamente ni ejecutar operaciones reales durante desarrollo local.
