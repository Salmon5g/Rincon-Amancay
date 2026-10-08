# Cuentas y tiendas — contrato provisional 0.1

Actualización de implementación local: ver ../docs/permisos-locales.md. La ficha pública añade habilitada y mostrarPrecios, mantenidos por servidor. Las reglas locales ya permiten el subconjunto allí descrito; las menciones a reglas cerradas más abajo describen el estado inicial y siguen aplicando a la nube. No se ha desplegado este incremento.

8 de octubre de 2026. Acuerdo técnico provisional para web, Android y backend, pendiente de revisión del grupo y terreno. La implementación local del alta administrativa y sus límites se describen en 06-alta-emprendedoras.md; publicación e imágenes en los contratos 03–05. Este documento no cubre ventas o ferias.

## Convenciones

Acuerdo confirmado por el usuario: explorar tiendas y productos publicados sin iniciar sesión, tanto en web como en modo comprador Android. Para las funciones privadas se usará inicialmente correo y contraseña, junto a permisos por cuenta. Esta navegación pública no requiere Firebase Anonymous Authentication. Las lecturas públicas están probadas en emuladores; la nube continúa cerrada.

Habilitar correo/contraseña permite técnicamente registrar identidades mediante el SDK; una identidad creada no recibe automáticamente un rol ni una tienda. El alta y asignación de emprendedoras seguirá siendo administrativa según el modelo. Nunca confiar solo en ocultar el botón de registro: el backend y las reglas deben exigir los accesos autorizados. El flujo de alta de compradores se concretará antes de implementarlo.

- Campos en español sin tildes y camelCase. Respetar exactamente las rutas.
- El ID se obtiene de la ruta; no duplicarlo como campo editable.
- El UID es el de Firebase Authentication, igual para una misma cuenta en web y Android.
- `creadoEn` y `actualizadoEn` son Timestamp de Firestore asignados por servidor. El JSON ilustrativo usa texto ISO UTC; un futuro cargador deberá convertirlo. No usar la hora del teléfono como autoridad.
- Un campo opcional ausente no es cadena vacía ni null. En este bloque se omite tiendaId cuando no hay tienda asignada.
- No aceptar campos arbitrarios enviados por el cliente. Añadir campos requiere actualizar contrato, validación, permisos y clientes.

## Documentos del primer bloque

Todos los campos listados son obligatorios al completar la creación, salvo los marcados opcionales. Todos estos documentos incluyen creadoEn y actualizadoEn.

| Ruta | Campos adicionales y tipos |
|---|---|
| `usuarios/{uid}` | nombreMostrar: string |
| `accesos/{uid}` | roles: array sin duplicados de comprador, emprendedora, administrador; estado: activo o desactivado; tiendaId: string opcional |
| `emprendedoras/{uid}` | tiendaId: string; creadaPor: UID administrador |
| `tiendasPrivadas/{tiendaId}` | propietarioUid: UID; nombre, descripcion, tipoEmprendimientoId, sectorId: string; estadoPublicacion: borrador, publicado o archivado |
| `tiendasPrivadas/{tiendaId}/configuracion/general` | mostrarPrecios, historialVentasActivo: boolean; formaContacto: whatsapp, formulario o ambos |

Una cuenta puede tener varios roles. Propuesta inicial: una emprendedora tiene una tienda; su tiendaId es obligatorio y coincide con emprendedoras.tiendaId y con la propiedad de la ficha privada. No se permite reasignar esas relaciones desde el cliente. Administrador no implica ser emprendedora.

El borrador de ejemplo tiene los campos mínimos completos. El procedimiento de alta incompleta, los límites de longitud, preferencias, imágenes y canales de contacto siguen pendientes antes de permitir edición real. Los valores del ejemplo no fijan valores por defecto para todas las usuarias.

## Vista pública mínima

`tiendasPublicas/{tiendaId}` conserva el ID de la tienda. Lista inicial de campos permitidos: nombre, descripcion, tipoEmprendimientoId, sectorId (strings), estadoPublicacion (publicado) y actualizadoEn (Timestamp).

La ficha privada es la fuente de edición. La pública es una proyección controlada, nunca una copia automática de todo el documento privado. No incluye propietarioUid, roles, correo de acceso, configuración ni ventas. Una tienda borrador no tiene vista pública.

Publicación/retiro están implementados localmente. La desactivación administrativa coordinada de cuentas sigue pendiente. No habilitar contenido real hasta resolver esa coordinación y preparar el despliegue.

## Permisos a implementar

| Recurso | Lectura de clientes | Cambios |
|---|---|---|
| usuarios | Su cuenta | Campos personales permitidos |
| accesos | Su cuenta y administrador autorizado | Operación de backend autorizada |
| emprendedoras | Su cuenta y administrador autorizado | Operación de backend autorizada |
| Tienda privada y configuración | Propietaria activa | Propietaria: campos editables; backend: alta, asignación y publicación |
| Tienda pública | Visitantes y cuentas, solo publicada/habilitada | Operación controlada de publicación |

La propietaria no modifica roles, estado administrativo, tiendaId, propietarioUid ni creadoEn. El administrador de la aplicación no recibe lectura general de tiendas privadas o ventas. Los accesos técnicos de consola/SDK Admin requieren controles adicionales y no quedan limitados por las mismas reglas cliente.

Elegir un modo en pantalla no otorga permisos. Si falta accesos o está desactivado, no habilitar gestión. Las reglas locales implementan los permisos descritos en ../docs/permisos-locales.md; no se han desplegado.

## Criterios pendientes de prueba

1. Nadie se asigna administrador u otra tienda desde el cliente.
2. Una propietaria activa gestiona su tienda; otra cuenta no la lee ni modifica.
3. Visitantes no acceden a documentos privados ni tiendas borrador.
4. Publicar expone únicamente la lista de campos permitidos.
5. Web y Android resuelven la misma tiendaId para el mismo UID.
6. El administrador no obtiene acceso al historial de ventas por su rol.

Los contratos 03–06 y ../docs/permisos-locales.md detallan los criterios ya probados localmente. No hay aún pruebas de integración con una aplicación Android.
