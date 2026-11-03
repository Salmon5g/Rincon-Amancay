# Edición administrativa de cuentas de emprendedoras

## Alcance

Este contrato cubre la edición de una cuenta emprendedora desde **Administración → Emprendedoras**. El administrador puede actualizar el nombre visible guardado en `usuarios/{uid}.nombreMostrar` y cambiar el correo de acceso de Firebase Authentication. El listado administrativo obtiene el correo de Authentication y el nombre de tienda para identificar a la persona.

La contraseña no se modifica desde este formulario; se gestiona mediante recuperación de Firebase Auth. La ficha de la tienda se modifica por su flujo propio (RF-TIE-01).

Para el alta, el panel ofrece dos caminos: **Crear cuenta con contraseña inicial** crea la identidad de Firebase Auth desde una instancia secundaria en memoria y luego asigna la tienda mediante `altaEmprendedora`; el administrador debe entregar la contraseña directamente a la emprendedora. **Enviar invitación** crea un enlace para que ella registre o use su cuenta y defina su propia contraseña. En ambos caminos, la asignación del rol y la tienda se valida en la API autenticada.

## Operaciones

### `POST /api/v1/listarEmprendedorasAdmin`

Entrada: `{}`. Requiere una cuenta administradora activa. Devuelve hasta 50 cuentas con:

```json
{
  "emprendedoras": [{
    "uid": "uid-ficticio",
    "correo": "persona@example.test",
    "nombreMostrar": "Nombre visible",
    "nombreTienda": "Tienda de ejemplo",
    "tiendaId": "tienda_ficticia",
    "estado": "activo",
    "consistente": true,
    "versionAcceso": "segundos:nanosegundos",
    "versionPerfil": "segundos:nanosegundos"
  }],
  "invitaciones": []
}
```

La API une `accesos`, `emprendedoras`, `usuarios`, `tiendasPrivadas` y Authentication. `consistente` indica que la identidad, el rol, el perfil y la propiedad de la tienda corresponden. El panel deshabilita las acciones de una cuenta inconsistente. Se muestran hasta 50 invitaciones pendientes.

### `POST /api/v1/editarCuentaEmprendedora`

Entrada:

```json
{
  "uidDestino": "uid-ficticio",
  "operacionId": "uuid-de-la-solicitud",
  "versionEsperada": "segundos:nanosegundos",
  "nombreMostrar": "Nombre actualizado",
  "correoEsperado": "persona-anterior@example.test",
  "correo": "persona-nueva@example.test"
}
```

La API vuelve a verificar que el actor sea administrador activo, que la destinataria tenga rol emprendedora sin rol administrador, que perfil, acceso y tienda coincidan y que no haya una operación de cuenta pendiente. Acepta cuentas activas o desactivadas. Compara `usuarios/{uid}.actualizadoEn` con `versionEsperada` y `correoEsperado` con el correo vigente de Authentication para evitar sobrescribir un cambio reciente.

El nombre, el comprobante y la auditoría se guardan en Firestore. Cuando cambia el correo, el backend lo actualiza en Firebase Authentication, lo deja sin verificar, revoca las sesiones anteriores y guarda el corte de sesiones en `accesos/{uid}` para que las reglas rechacen tokens anteriores. La API genera un enlace de verificación para el nuevo correo y lo devuelve al panel; el sistema no lo envía automáticamente. El administrador debe compartirlo en privado con la emprendedora. Ella debe verificar la nueva dirección e iniciar sesión de nuevo con el correo nuevo y su contraseña actual.

La operación conserva un comprobante `pendienteAuth` en `operacionesAdministracion` para poder continuar con el mismo `operacionId` si Authentication ya cambió el correo pero la respuesta se interrumpió. En ese caso, el formulario bloquea los datos y exige reintentar sin modificarlos. Un correo ya asociado a otra cuenta devuelve `correo-ocupado`; no cambia la identidad. La actualización final registra antes/después en `auditoriaAdministracion`. Un conflicto de versión exige actualizar el listado y revisar los datos antes de iniciar otra solicitud.

## Seguridad

- La cuenta destino se recibe por el botón de una fila, pero el servidor vuelve a validar rol, estado, perfil, tienda y propiedad. El navegador nunca puede asignar UID, rol, estado ni un correo sin comprobar el correo vigente de Authentication.
- Authentication se consulta en el backend solo después de validar que quien solicita sea administrador.
- La escritura usa Firebase Admin SDK. Firestore Rules no habilita escrituras administrativas desde el navegador; no se requiere abrir reglas para esta operación.
- El listado requiere que la identidad del backend tenga permisos IAM de Firestore y Firebase Authentication.
- El módulo no actualiza contraseña, tienda, roles ni estado de acceso. El correo solo cambia mediante esta operación administrativa autenticada; nunca se escribe como dato de autorización en Firestore.

## Pendientes relacionados

La opción de alta directa satisface la entrega de credenciales iniciales: la contraseña se define al crear Authentication y se muestra temporalmente al administrador para entregarla por un canal privado. No se fuerza un cambio de contraseña al primer ingreso ni se manda correo automáticamente. La invitación sigue disponible como alternativa. El enlace de verificación emitido al cambiar el correo se comparte manualmente; el proyecto no tiene un servicio de correo propio para entregarlo automáticamente.
