# Administración de ferias

## Rutas y documentos

La colección `ferias/{feriaId}` contiene `nombre`, `descripcion`, `organizador`, `direccion`, `fechaInicio`, `fechaFin`, `imagenUrl`, `estado`, `creadoEn` y `actualizadoEn`. Puede incluir el par opcional `latitud`/`longitud`. `descripcion` e `imagenUrl` son obligatorias al crear y editar: la imagen debe ser una URL HTTPS pública, normalmente la devuelta por la carga de imagen de feria. Las fechas se guardan como Timestamp; la API recibe ISO 8601 con zona horaria. El UID del actor queda en la auditoría, no en el documento con lectura pública.

Estados de feria: `borrador`, `publicada`, `cancelada`. Crear produce un borrador. Publicar requiere que el inicio aún no ocurra. Las ferias canceladas quedan como historial terminal; no se borran. Una feria publicada se puede editar antes de su inicio.

`ferias/{feriaId}/participantes/{tipo}_{entidadId}` contiene `tipo` (`emprendedora` o `asociacion`), `entidadId`, `nombre`, `estado`, `creadoPor`, `creadoEn` y `actualizadoEn`. Para emprendedoras puede incluir `nombreTienda`. `asistencia` solo se usa para participantes confirmados o agregados directamente. Estados de participación: `invitada`, `confirmada`, `rechazada`, `agregada_admin` y `retirada`.

La API construye los nombres desde los registros vigentes; el cliente no puede proporcionar el UID del actor ni sustituir el nombre de una persona. Solo se invitan cuentas emprendedoras activas con perfil y tienda coherentes o asociaciones activas. No se guardan correos personales en la participación.

## Operaciones

- `listarFeriasAdmin`, `crearFeria`, `editarFeria`, `cambiarEstadoFeria`.
- `leerParticipantesFeria`, `invitarParticipanteFeria`, `cancelarInvitacionFeria`, `agregarParticipanteFeria`, `retirarParticipanteFeria`, `registrarAsistenciaFeria`.
- `responderInvitacionFeria` queda disponible para clientes emprendedora autenticados; solo la persona destinataria puede confirmar o rechazar su invitación.

La imagen de la feria se carga con `POST /api/v1/ferias/imagen`. Requiere una sesión administradora activa, un `X-Archivo-Id` UUID para reintentos idempotentes y un cuerpo binario JPEG, PNG o WebP de hasta 5 MiB. La API guarda el original privado, publica una versión WebP en el almacenamiento y responde `{ datos: { ruta } }` con la URL HTTPS pública que luego se envía como `imagenUrl` en `crearFeria` o `editarFeria`.

Las modificaciones de administración requieren rol activo, versión vigente y `operacionId` estable para reintentos. Se guardan recibos y auditoría en las colecciones administrativas compartidas. `cancelarInvitacionFeria` solo acepta una invitación pendiente, elimina el documento de participación para que la entidad vuelva a estar disponible y conserva el evento en auditoría. `retirarParticipanteFeria` solo acepta participantes confirmados o agregados directamente y conserva su registro con estado `retirada`. La asistencia se registra desde la hora de inicio, únicamente para participantes confirmados o agregados por administración en ferias publicadas. Las invitaciones pendientes no tienen estado de asistencia; una aceptación crea la asistencia como `pendiente`.

## Lectura, límites y pendientes

Las reglas permiten lectura pública de ferias publicadas; los documentos de participantes son visibles solo a administración, porque incluyen identificadores de cuentas. El comprador Android obtiene las ferias publicadas mediante GET /api/v1/feriasPublicas y, sin token, usa GET /api/v1/feriasPublicas/{feriaId} para participantes confirmadas, nombres públicos y una muestra de hasta tres productos publicados por tienda. La política pública de navegación se lee mediante GET /api/v1/configuracionPublica/navegacion; Android usa la API como respaldo cuando Firestore no responde. Para asociaciones usa hasta tres tiendas de su proyección pública. La respuesta no contiene UID, precios ni datos administrativos; la ruta tiene límite de 30 lecturas por minuto por IP. El cliente no consulta directamente la subcolección privada. Las escrituras directas permanecen denegadas. La consulta actual del comprador filtra `estado == "publicada"` y ordena por `fechaInicio` en el cliente; no usa `orderBy`. Firestore resuelve el filtro con su índice automático de campo único. El índice compuesto `ferias(estado ASC, fechaInicio ASC)` sigue declarado en `firebase/firestore.indexes.json` para una consulta futura que ordene en Firestore, pero no es necesario para la consulta actual.

El panel todavía no envía correo, WhatsApp ni notificaciones: registrar `invitada` solo deja constancia para que el equipo coordine la invitación. La imagen se sube al almacenamiento (Supabase Storage) y el documento guarda su URL pública; el formulario también permite marcar la ubicación en un mapa o buscarla por dirección. La página pública de ferias del comprador web está disponible en `/comprador/ferias`.
