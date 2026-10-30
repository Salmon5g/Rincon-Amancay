# Administración de asociaciones

## Alcance

Este contrato cubre el módulo administrativo de asociaciones de la Entrega 4: crear, editar y desactivar asociaciones, además de asignar y retirar emprendedoras. La relación no se guarda en el perfil de la persona: una emprendedora puede pertenecer a cero, una o varias asociaciones.

La ficha pública de una asociación con sus integrantes y enlaces a tiendas (RF-ASO-03) queda fuera de este contrato y debe implementarse como lectura pública separada. Por ahora, la colección y sus relaciones se consultan desde el panel mediante la API autenticada.

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
- No se crea una proyección pública ni se permiten lecturas anónimas de asociaciones en Firestore. Para RF-ASO-03 se deberá definir qué datos se publican, sus reglas y el enlace a las tiendas públicas.

## Evolución

La ficha es provisional y puede cambiar tras el levantamiento en terreno. Si se modifica el contrato, coordinar el cambio con la web, Android, backend y documentación antes de publicar una versión compartida.
