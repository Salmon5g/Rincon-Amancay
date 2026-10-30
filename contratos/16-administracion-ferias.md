# Administración de ferias

## Rutas y documentos

La colección `ferias/{feriaId}` contiene `nombre`, `descripcion`, `organizador`, `direccion`, `fechaInicio`, `fechaFin`, `estado`, `creadoEn` y `actualizadoEn`. Puede incluir `imagenUrl` HTTPS y el par opcional `latitud`/`longitud`. Las fechas se guardan como Timestamp; la API recibe ISO 8601 con zona horaria. El UID del actor queda en la auditoría, no en el documento con lectura pública.

Estados de feria: `borrador`, `publicada`, `cancelada`. Crear produce un borrador. Publicar requiere que el inicio aún no ocurra. Las ferias canceladas quedan como historial terminal; no se borran. Una feria publicada se puede editar antes de su inicio.

`ferias/{feriaId}/participantes/{tipo}_{entidadId}` contiene `tipo` (`emprendedora` o `asociacion`), `entidadId`, `nombre`, `estado`, `asistencia`, `creadoPor`, `creadoEn` y `actualizadoEn`. Para emprendedoras puede incluir `nombreTienda`. Estados de participación: `invitada`, `confirmada`, `rechazada`, `agregada_admin` y `retirada`. Asistencia: `pendiente`, `asistio`, `no_asistio`.

La API construye los nombres desde los registros vigentes; el cliente no puede proporcionar el UID del actor ni sustituir el nombre de una persona. Solo se invitan cuentas emprendedoras activas con perfil y tienda coherentes o asociaciones activas. No se guardan correos personales en la participación.

## Operaciones

- `listarFeriasAdmin`, `crearFeria`, `editarFeria`, `cambiarEstadoFeria`.
- `leerParticipantesFeria`, `invitarParticipanteFeria`, `agregarParticipanteFeria`, `retirarParticipanteFeria`, `registrarAsistenciaFeria`.
- `responderInvitacionFeria` queda disponible para clientes emprendedora autenticados; solo la persona destinataria puede confirmar o rechazar su invitación.

Las modificaciones de administración requieren rol activo, versión vigente y `operacionId` estable para reintentos. Se guardan recibos y auditoría en las colecciones administrativas compartidas. Retirar es lógico. La asistencia se registra desde la hora de inicio, únicamente para participantes confirmados o agregados por administración en ferias publicadas.

## Lectura, límites y pendientes

Las reglas permiten lectura pública de ferias publicadas; los documentos de participantes son visibles solo a administración, porque incluyen identificadores de cuentas. Una futura página pública debe consultar una proyección sin UID ni datos privados. Las escrituras directas permanecen denegadas. Se requiere filtrar `estado == "publicada"` al listar ferias; el índice compuesto `ferias(estado ASC, fechaInicio ASC)` queda declarado en `firebase/firestore.indexes.json`.

El panel todavía no envía correo, WhatsApp ni notificaciones: registrar `invitada` solo deja constancia para que el equipo coordine la invitación. La imagen se acepta como URL HTTPS externa; no se carga a Firebase Storage. La ubicación usa dirección y coordenadas manuales, sin selector de mapa. La página pública de ferias aún no está implementada.
