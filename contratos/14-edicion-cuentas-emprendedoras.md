# Edición administrativa de cuentas de emprendedoras

## Alcance

Este contrato cubre la edición de una cuenta emprendedora desde **Administración → Emprendedoras**. El administrador puede actualizar el nombre visible guardado en `usuarios/{uid}.nombreMostrar`. El listado administrativo obtiene correo de Authentication y nombre de tienda para identificar a la persona.

El correo de acceso y la contraseña no se cambian desde este formulario. El correo identifica la cuenta de Authentication y modificarlo requiere un flujo de cambio y verificación; la contraseña se gestiona mediante recuperación de Firebase Auth. La ficha de la tienda se modifica por su flujo propio (RF-TIE-01). El alta sigue usando invitaciones y la activación de la cuenta ocurre cuando la emprendedora acepta.

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
  "nombreMostrar": "Nombre actualizado"
}
```

La API vuelve a verificar que el actor sea administrador activo, que la destinataria tenga rol emprendedora sin rol administrador, que perfil, acceso y tienda coincidan y que no haya una operación de cuenta pendiente. Acepta cuentas activas o desactivadas. Compara `usuarios/{uid}.actualizadoEn` con `versionEsperada` y actualiza únicamente `nombreMostrar` y `actualizadoEn`.

La operación es transaccional en Firestore, guarda comprobante en `operacionesAdministracion`, registra antes/después en `auditoriaAdministracion` y conserva el mismo `operacionId` para reintentos. Un cambio concurrente devuelve `conflicto`; el administrador debe actualizar el listado y revisar el valor vigente antes de volver a guardar.

## Seguridad

- La cuenta destino se recibe por el botón de una fila, pero el servidor vuelve a validar rol, estado, perfil, tienda y propiedad. El navegador nunca puede asignar UID, rol, estado o correo nuevos.
- Authentication se consulta en el backend solo después de validar que quien solicita sea administrador.
- La escritura usa Firebase Admin SDK. Firestore Rules no habilita escrituras administrativas desde el navegador; no se requiere abrir reglas para esta operación.
- El listado requiere que la identidad del backend tenga permisos IAM de Firestore y Firebase Authentication.
- El módulo no actualiza correo, contraseña, tienda, roles ni estado de acceso.

## Pendientes relacionados

La entrega solicita entregar credenciales al crear cuentas (RF-ADM-01). El flujo actual crea una invitación cuyo código debe compartirse manualmente y permite que la emprendedora cree su propia contraseña; el envío automático de invitaciones todavía no existe. Cambiar correo con verificación, gestionar credenciales iniciales y enviar invitaciones requiere un flujo seguro adicional.
