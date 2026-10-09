# Ciclo de cuentas provisional

Implementado para `demo-rincon-amancay`, con API local y emuladores. El catálogo sigue siendo público sin sesión. Una identidad de Firebase Authentication no recibe permisos de aplicación automáticamente. Los roles se consultan en `accesos/{uid}` y se asignan únicamente mediante el backend.

## Registro de compradores

1. Crear la identidad con `crearIdentidad(correo, clave)` del adaptador web o Android.
2. Llamar a `enviarVerificacion()`. Si falla el envío, reintentar este paso, sin intentar crear otra identidad.
3. Abrir el enlace simulado de Authentication Emulator o aplicar su código con `confirmarCorreo(codigo)`. El adaptador actualiza la identidad y su token. Si se verificó en otra ventana, volver a iniciar sesión.
4. Con la sesión iniciada, llamar a `POST /api/v1/registrarComprador` con `{"nombreMostrar":"Nombre elegido"}`.

La API consulta en Auth que el correo esté verificado y que la identidad esté habilitada. Crea perfil y acceso comprador activo en una sola transacción, usando el UID del token, nunca un UID/rol del cuerpo. Registro repetido de un comprador activo devuelve el mismo UID sin sobrescribir nombre ni permisos. Un acceso desactivado se rechaza; registro y recuperación de contraseña no reactivan cuentas. Un acceso con otro rol sin comprador se rechaza, sin sustituirlo.

Verificar el correo antes del alta es una decisión provisional de esta base. Las pantallas deberán explicar el paso con lenguaje sencillo y permitir reenviar el enlace. No se exige una cuenta para mirar las tiendas.

## Invitación de emprendedoras

La administradora activa llama a `POST /api/v1/invitarEmprendedora`:

```json
{
  "operacionId": "conservar-un-id-por-invitacion",
  "correo": "persona@example.test",
  "nombreMostrar": "Nombre de la persona",
  "nombreTienda": "Tejidos Amancay",
  "descripcion": "Tejidos y accesorios hechos a mano",
  "sectorId": "sector_demo",
  "tipoEmprendimientoId": "mixto_demo",
  "mostrarPrecios": true,
  "historialVentasActivo": false,
  "formaContacto": "formulario"
}
```

Usar IDs activos de los catálogos, no asumir que los ejemplos son valores universales. El servidor genera `invitacionId` y `tiendaId`, fija vencimiento de siete días y responde con ambos IDs, estado y `venceEn` ISO. Guarda los datos aprobados en `invitaciones/{invitacionId}`. Mismo administrador, operación y contenido reutilizan la invitación; cambiar contenido conservando la operación devuelve 409. Repetir una invitación vencida o cancelada no la renueva: emitir otra con un ID de operación nuevo.

La invitación se puede preparar antes de que exista la identidad. En este entorno local **no se envía un correo de invitación**: la administradora entrega manualmente el identificador a la persona de prueba. El envío real y una pantalla/enlace de aceptación se integrarán antes de usarlo en la nube. No existe todavía una ruta visual de aceptación.

La destinataria crea su identidad o inicia sesión, verifica el mismo correo y llama a:

| Operación POST | Cuerpo | Permiso / efecto |
| --- | --- | --- |
| `consultarInvitacion` | `{"invitacionId":"..."}` | Correo destinatario verificado; devuelve nombre de tienda, estado y vencimiento. |
| `aceptarInvitacion` | `{"invitacionId":"..."}` | Correo destinatario verificado; asigna la tienda y consume la invitación en la misma transacción. |
| `cancelarInvitacion` | `{"invitacionId":"..."}` | Administradora activa; cancela una pendiente. Repetir cancelación no cambia fechas. |

Conocer el ID no concede acceso: la autorización depende del correo verificado de Auth. Se compara sin distinguir mayúsculas, sin eliminar puntos ni sufijos `+`. El correo no se copia al catálogo público. La aceptación exige que quien invitó siga siendo administrador activo y que sector/tipo sigan activos. Invitaciones vencidas/canceladas se rechazan; no hay aceptación parcial. La comprobación de vencimiento ocurre al aceptar, sin necesitar un proceso programado.

Aceptar crea `emprendedoras`, la tienda privada en borrador, configuración y acceso; conserva perfil comprador existente y añade emprendedora a sus roles. Sin acceso previo, crea solo el rol emprendedora. No publica la tienda. Una cuenta no recibe una segunda tienda. El reintento de una aceptación ya completada devuelve su comprobante; no vuelve a asignar permisos, reactivar o crear documentos.

Se conserva `altaEmprendedora` del contrato 06 como alta administrativa directa para identidades existentes y pruebas anteriores. Esa ruta exige administrador y no requiere aceptación/verificación del destinatario; no utilizarla como registro abierto ni como sustituto del flujo de invitación en las pantallas del equipo. La decisión de conservarla en producción se revisará antes del despliegue.

## Recuperación y verificación con Firebase Auth

Estas operaciones usan el SDK cliente de Firebase, no la API de negocio. La API nunca recibe una contraseña ni un código de recuperación:

- `enviarVerificacion()` y `confirmarCorreo(codigo)`.
- `solicitarRecuperacion(correo)` devuelve un mensaje genérico tanto para correo existente como desconocido; los errores de red/cuota se mantienen como errores.
- `comprobarRecuperacion(codigo)` comprueba el código y devuelve el correo asociado.
- `confirmarRecuperacion(codigo, nuevaClave)` cambia la contraseña y cierra la sesión de este cliente. Iniciar sesión de nuevo.

Firebase valida vencimiento y uso de códigos. No guardarlos en Firestore, repositorio, capturas ni logs propios. En los emuladores, los enlaces aparecen en la consola local y las pruebas leen códigos mediante la API exclusiva del emulador. Eso no existe como mecanismo de acceso en producción. No se mandaron correos reales. Antes de producción revisar plantillas, dominios/enlaces autorizados, protección contra enumeración y cuotas de Auth.

## Reactivación segura de emprendedoras

`POST /api/v1/reactivarEmprendedora`:

```json
{
  "uidDestino": "uid-de-la-emprendedora",
  "operacionId": "conservar-id-y-cuerpo-en-reintentos",
  "versionEsperada": "segundos:nanosegundos",
  "motivo": "Revisión administrativa completada"
}
```

Leer la versión de `accesos/{uid}.actualizadoEn`. Solo una administradora activa puede reactivar a otra cuenta desactivada con rol emprendedora, sin rol administrador, con propiedad de tienda consistente y sin otra operación pendiente. Esta primera versión no administra la suspensión/reactivación de compradores ni administradores.

1. Transacción: validar permisos/versión/relaciones, conservar el acceso desactivado, dejar la tienda pública deshabilitada y crear `reactivaciones/{adminUid}_{operacionId}` en `pendienteAuth`. Reservar la operación en `accesos.operacionCuentaPendiente`.
2. Revocar sesiones anteriores y habilitar la identidad en Auth. Una identidad eliminada no se recrea.
3. Transacción: volver a comprobar el estado y al administrador, activar el acceso, quitar la reserva y completar el comprobante. Respuesta: `uid`, `tiendaId`, `operacionId`, `estado: completada`, `autenticacion: habilitada`.

Un fallo de Auth devuelve 503 `pendiente` y conserva bloqueada la gestión. Repetir la misma solicitud, incluyendo su versión original, permite terminar. No inventar una operación nueva ni editar el acceso a mano. El comprobante completo es histórico: repetirlo no vuelve a habilitar Auth ni modifica fechas.

El acceso guarda `sesionesRevocadasHasta` en segundos. API, reglas Firestore y reglas Storage exigen `auth_time` estrictamente posterior a ese corte para la gestión privada. Renovar un ID token de la sesión anterior no basta: cerrar sesión e iniciar de nuevo. Si el nuevo inicio ocurre en el mismo segundo que la reactivación, esperar al siguiente segundo y volver a iniciar. El perfil y estado de acceso propios siguen legibles según sus reglas.

La tienda continúa con `habilitada:false` después de reactivar. Su propietaria debe iniciar sesión de nuevo, revisar y publicar expresamente. No se pierden fichas, imágenes ni historial. Auth y Firestore no comparten transacción: si la habilitación de Auth termina y falla la última transacción, la identidad podría iniciar sesión, pero la gestión sigue bloqueada hasta completar la operación.

## Concurrencia, permisos y límites operativos

Desactivación y reactivación comparten una reserva persistente en `ejecucionesCuentas/{uid}` y una cola en memoria por UID. La reserva se crea en una transacción Firestore que exige un administrador activo y se libera al terminar o fallar la operación. Un segundo intento simultáneo —incluso desde otra instancia de la API— obtiene `pendiente`; la cola en memoria solo evita contención dentro de un mismo proceso. Una operación pendiente impide iniciar la contraria; un reintento completado no ejecuta Auth otra vez.

La reserva **no tiene vencimiento automático**. Una interrupción se atiende mediante [recuperación técnica de cuentas](../docs/recuperacion-cuentas.md): inspeccionar, detener instancias y resolver peticiones en vuelo, liberar solo el ejecutor inspeccionado con auditoría y retomar la solicitud original. Firebase Auth no ofrece un *fencing token* que impida efectos tardíos de otro proceso. La prueba local termina un proceso real en fase Auth (simulada) y recupera ambas operaciones; la operación en nube aún requiere validación. No borrar reservas manualmente ni abrir esta herramienta a los clientes.

Administradores activos pueden leer `invitaciones`, `desactivaciones` y `reactivaciones` para seguimiento. Los comprobantes nuevos de desactivación/reactivación guardan `solicitud` con el cuerpo validado original para recuperación. Ningún cliente puede escribir esos documentos. Las destinatarias consultan su invitación por la API, sin listar correos ajenos. Los comprobantes internos `operacionesCuentas`, la reserva `ejecucionesCuentas` y la auditoría `recuperacionesCuentas` permanecen inaccesibles para clientes.

No hay reconciliación automática. Si el administrador original pierde permisos o hay una identidad eliminada/inconsistencia manual, hace falta revisión técnica; no se puede iniciar otra operación para saltarse la reserva. Comprobantes pendientes creados por versiones anteriores sin reserva tampoco se reanudan automáticamente. Eliminar cuentas, reasignar tiendas, cambiar correo coordinadamente, crear el primer administrador real y definir retención de registros quedan fuera de este incremento.

## Pruebas

Con emuladores activos: `npm --prefix backend run test:ciclo`. Con API local activa además: `npm --prefix web run test:local`. Cubren registro sin privilegios, verificación, invitación por correo, expiración/cancelación, aceptación concurrente, reglas, fallos/reintentos de Auth, operaciones opuestas y recuperación sin reactivar permisos. Los adaptadores web se ejecutan con SDK real contra emuladores. El ejemplo Kotlin está actualizado, pendiente de compilar e integrar en el proyecto Android del equipo.

Referencias: [revocación de sesiones](https://firebase.google.com/docs/auth/admin/manage-sessions), [Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth), [gestión de usuarios web](https://firebase.google.com/docs/auth/web/manage-users), [acciones por correo](https://firebase.google.com/docs/auth/custom-email-handler), [FirebaseAuth Android](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseAuth).
