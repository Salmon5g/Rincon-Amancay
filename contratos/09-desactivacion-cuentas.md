# Desactivación coordinada de emprendedoras — local

Implementada y probada solo en emuladores. Bloquea la gestión y oculta el catálogo sin borrar fichas privadas, productos, variantes, movimientos de stock ni archivos. No es eliminación de datos ni reactivación de una cuenta.

## Solicitud administrativa

POST `/api/v1/desactivarEmprendedora`, Content-Type: application/json y Authorization: Bearer con ID token de una administradora activa.

```json
{
  "uidDestino": "uid-de-emprendedora",
  "operacionId": "uuid-de-la-intencion",
  "versionEsperada": "segundos:nanosegundos",
  "motivo": "Motivo administrativo de la suspensión"
}
```

versionEsperada corresponde a actualizadoEn de accesos/{uidDestino}, no de la tienda. Se obtiene al leer el acceso desde la sesión administrativa. Motivo obligatorio, no vacío, hasta 500 caracteres; evitar detalles personales innecesarios. Los IDs admiten letras, números, guion o guion bajo, hasta 128 caracteres. No enviar tiendaId, roles, estado ni fechas: se resuelven desde los documentos autorizados.

Solo admite cuentas con rol emprendedora y sin rol administrador. No permite desactivar la propia cuenta ni cuentas administradoras. Compradores sin tienda y gestión del primer/último administrador quedan fuera de este incremento. La asignación en accesos, emprendedoras y la propiedad de tiendasPrivadas deben coincidir; inconsistencias se rechazan antes de escribir.

## Dos fases y reintentos

1. En una transacción Firestore, comprobar permisos y versión, poner accesos.estado=desactivado, actualizar su fecha y poner habilitada=false en la tienda pública si existe. Crear un comprobante con estado pendienteAuth y reservar su ruta en accesos.operacionCuentaPendiente. No se crea ficha pública si no existía.
2. Fuera de la transacción, deshabilitar la identidad Authentication y revocar sus refresh tokens. Después marcar el comprobante completada y quitar la reserva. Si la identidad ya no existe, completar con autenticacion=ausente: los datos quedan igualmente bloqueados.

Authentication y Firestore no comparten transacción. Un fallo temporal de la segunda fase devuelve 503, code pendiente, con un mensaje que indica que la gestión y el catálogo ya se bloquearon. Repetir la misma solicitud, con el mismo operacionId y versionEsperada original, completa el trabajo. No se revierte el bloqueo de Firestore ante un fallo de Auth. Una interrupción del proceso entre fases se recupera por ese mismo reintento.

Una operación completada devuelve su resultado guardado sin repetir Auth ni modificar fechas. Es un comprobante histórico, no una consulta del estado actual. Cambiar datos conservando el ID produce 409. Se vuelve a comprobar que quien reintenta sea una administradora activa. Si la cuenta cambió de estado/asignación durante una operación pendiente, se pide revisión en lugar de continuar ciegamente.

No hay un trabajador automático que termine operaciones pendientes. La web administrativa deberá mostrar las pendientes y permitir reintento. Si la administradora original pierde permisos, hace falta revisión técnica: una operación nueva no puede saltarse la reserva pendiente. No modificarla directamente desde el cliente. Desactivación y reactivación comparten una cola por UID en el único proceso local; consultar los límites de concurrencia del contrato 10.

Respuesta 200:

```json
{"datos":{"uid":"...","tiendaId":"...","operacionId":"...","estado":"completada","autenticacion":"deshabilitada"}}
```

400: solicitud inválida; 401: sesión inválida; 403: falta de permisos o destino no permitido; 404: acceso destino inexistente; 409: versión, ID o relaciones en conflicto; 503: falta completar Auth. No se devuelven errores internos del SDK, contraseñas ni tokens.

## Efecto de los permisos

El estado desactivado bloquea operaciones del backend y lecturas/escrituras de gestión privada. habilitada=false bloquea nuevas lecturas de tienda, productos, variantes e imágenes por las reglas, incluso con una proyección antigua todavía almacenada. Las operaciones de publicación leen el acceso dentro de su transacción, evitando que una publicación concurrente vuelva a abrir el catálogo.

Durante una demora de Auth, el token anterior puede seguir existiendo, pero ya no autoriza gestión privada. Las reglas actuales permiten a la persona leer su propio perfil y su estado de acceso; no se promete bloquear toda lectura personal. La navegación por catálogos ajenos sigue siendo pública sin sesión.

Deshabilitar Authentication y revocar tokens complementa el bloqueo de datos; no borra cachés, copias descargadas ni URLs con token previamente distribuidas. Las imágenes conservan las limitaciones del contrato 05.

## Registro administrativo

`desactivaciones/{administradoraUid}_{operacionId}` contiene uidDestino, tiendaId, operacionId, motivo, realizadaPor, estado, creadoEn, actualizadoEn, firma interna y resultado al completar. Solo administradores activos pueden leer/listar estos documentos; ningún cliente puede crearlos, modificarlos o borrarlos. Las herramientas técnicas con SDK Admin siguen requiriendo controles operativos.

Para una primera vista de pendientes, consultar desactivaciones con estado==pendienteAuth y un límite de 50. Si se agrega orden por fecha u otros filtros, definir y probar el índice compuesto. La retención de estos registros y la reconciliación automática están pendientes.

## Pruebas y alcance

Con emuladores activos, desde backend/: npm run typecheck y npm run test:desactivacion. 15 casos (16 incluyendo grupo padre) cubren autorización, estados, propiedad, versiones, fallos simulados de Auth/revocación, reintentos, privacidad administrativa, lecturas de Firestore/Storage y publicación concurrente. Auth y los datos son reales del emulador; solo la interrupción de las llamadas a Auth se simula.

La reactivación local está implementada en 10-ciclo-cuentas.md. No habilitar manualmente la tienda ni el acceso con una operación pendiente. Antes de producción se requieren coordinación entre instancias del servidor, seguimiento de pendientes, políticas administrativas y despliegue seguro. No se modificó ninguna cuenta de Firebase real.
