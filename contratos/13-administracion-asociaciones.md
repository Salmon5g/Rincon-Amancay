# Administración de asociaciones

## Alcance

Este contrato cubre el módulo administrativo de asociaciones de la Entrega 4: crear, editar y desactivar asociaciones, además de asignar y retirar emprendedoras. La relación no se guarda en el perfil de la persona: una emprendedora puede pertenecer a cero, una o varias asociaciones.

La ficha administrativa y las relaciones privadas siguen bajo la API autenticada. Para RF-ASO-03, `asociacionesPublicas/{asociacionId}` contiene solo nombre, descripción, sector, logo opcional y tiendas públicas activas. Los integrantes se proyectan como `tiendaId` y nombre público de tienda; nunca se copia el UID de la emprendedora. Firestore solo permite lectura anónima cuando `activo == true`.

La app compradora usa la proyección para listar y filtrar asociaciones y abrir las tiendas públicas de sus integrantes. Los cambios administrativos y los cambios de publicación/edición de tienda vuelven a sincronizar las asociaciones relacionadas. Para asociaciones y tiendas existentes, el script `catalogo:sincronizar-vistas-publicas` ofrece simulación y aplicación explícita.

## Documentos

### `asociaciones/{asociacionId}`

| Campo | Tipo | Reglas |
| --- | --- | --- |
| `nombre` | string | Obligatorio, hasta 120 caracteres. |
| `nombreNormalizado` | string | Generado por el servidor para detectar duplicados sin distinguir mayúsculas ni tildes. |
| `descripcion` | string | Hasta 2000 caracteres; puede estar vacío. |
| `sectorId` | string | Debe referenciar un documento activo de `sectores`. |
| `contacto` | objeto | `{ tipo, valor }`; `tipo` es `whatsapp`, `correo` o `web`. |
| `logoUrl` | string | Opcional; URL HTTPS pública. No representa una carga a Storage. |
| `activo` | boolean | Baja lógica; no se borra la ficha ni sus relaciones. |
| `creadoEn`, `actualizadoEn` | timestamp | Escritos por el servidor. |

El ID se deriva del nombre normalizado como slug. El servidor rechaza nombres duplicados y compara `actualizadoEn` para evitar sobrescribir una edición concurrente.

### `asociaciones/{asociacionId}/integrantes/{uidEmprendedora}`

| Campo | Tipo | Reglas |
| --- | --- | --- |
| `tiendaId` | string | Tienda privada que pertenece a esa cuenta. |
| `estado` | string | `activa` o `retirada`. |
| `asignadaPor`, `asignadaEn` | string/timestamp | Se fijan al crear la relación. |
| `retiradaPor`, `retiradaEn` | string/timestamp | Se registran al retirar; la relación se conserva. |
| `creadoEn`, `actualizadoEn` | timestamp | Escritos por el servidor. |

Solo se asignan cuentas activas con rol `emprendedora`, perfil y tienda privada coherentes. Reasignar una relación retirada la reactiva conservando la fecha y autoría de la primera asignación. La cuenta y tienda no se desactivan al retirar a una emprendedora de una asociación.

## Operaciones de API

Todas requieren ID token válido y una cuenta con `accesos/{uid}.estado == "activo"` y rol `administrador`. Las mutaciones escriben con Firebase Admin SDK, registran resultado idempotente en `operacionesAdministracion` y auditoría en `auditoriaAdministracion`. El navegador y Android no escriben directamente en estas rutas.

| Ruta/acción | Entrada principal | Resultado |
| --- | --- | --- |
| `listarAsociaciones` | `{}` | `{ asociaciones: [...] }`, hasta 200. |
| `leerIntegrantesAsociacion` | `{ asociacionId }` | Ficha/versiones, integrantes históricos y emprendedoras activas disponibles. |
| `crearAsociacion` | `operacionId`, ficha | Crea una ficha activa. |
| `editarAsociacion` | `operacionId`, ID, `versionEsperada`, ficha | Actualiza la ficha sin cambiar su estado. |
| `cambiarEstadoAsociacion` | `operacionId`, ID, `versionEsperada`, `activo` | Desactiva o reactiva sin borrar relaciones. |
| `asignarEmprendedora` | `operacionId`, asociación y versión, UID | Crea o reactiva una relación. |
| `quitarEmprendedora` | `operacionId`, asociación, UID y versión de relación | Marca la relación como retirada. |

Los errores de versión (`conflicto`) requieren actualizar los datos antes de volver a operar. Si la respuesta se pierde, se debe reintentar exactamente la misma solicitud con el mismo `operacionId`.

## Seguridad y límites

- La autorización de la aplicación se comprueba en el servidor; ocultar botones no es una medida de seguridad.
- Firestore Rules mantienen denegado el acceso directo a estas colecciones. La API usa Admin SDK y su identidad necesita permisos IAM para Firestore.
- Los listados de esta primera versión están limitados a 200 asociaciones, integrantes y cuentas candidatas por solicitud; superar el límite devuelve error y requiere implementar paginación.
- `logoUrl` es una referencia HTTPS. La carga y eliminación de archivos en Firebase Storage todavía no está implementada en este módulo.
- Las mutaciones administrativas regeneran la proyección pública; publicar/retirar o editar una tienda actualiza los enlaces de sus asociaciones.
- Para materializar el estado ya existente, `npm.cmd --prefix backend run catalogo:sincronizar-vistas-publicas -- --dry-run TU_PROJECT_ID` simula la creación de proyecciones de asociaciones y tiendas. Revisar el resumen antes de ejecutar con `--aplicar`.

## Evolución

La ficha es provisional y puede cambiar tras el levantamiento en terreno. Si se modifica el contrato, coordinar el cambio con la web, Android, backend y documentación antes de publicar una versión compartida.
