# Registro de acciones de administración

## Alcance

La sección **Administración → Registro de acciones** permite consultar cambios administrativos sobre cuentas, asociaciones y ferias. Cada entrada identifica a la cuenta administradora (`actorUid`), la acción, el área, el registro afectado y la fecha. La consulta se abre al entrar a la sección; no mantiene listeners ni se carga al iniciar el panel.

## Consulta

### `POST /api/v1/listarAuditoriaAdministracion`

Requiere un ID token válido y un acceso activo con rol `administrador`. La entrada usa `cursor: null` para la primera página o el cursor devuelto por la página anterior:

```json
{ "cursor": null }
```

Devuelve hasta 30 entradas y un cursor para continuar:

```json
{
  "acciones": [{
    "id": "id-del-registro",
    "actorUid": "uid-administrador",
    "area": "Asociaciones",
    "accion": "Edición de asociación",
    "objetivo": "Asociación ejemplo",
    "creadoEn": "2026-11-25T20:00:00.000Z"
  }],
  "siguiente": { "seconds": 1795636800, "nanoseconds": 0, "id": "id-del-registro" }
}
```

La página se ordena de más reciente a más antigua. El cursor combina fecha e ID del documento para mantener el orden estable cuando varias entradas comparten la misma fecha. El navegador busca y filtra por área sobre las páginas ya cargadas; «Cargar acciones anteriores» consulta la siguiente página.

## Acciones incluidas

- **Cuentas:** altas, invitaciones y su cancelación, edición, desactivación y reactivación de cuentas emprendedoras, y asignación o retiro del rol administrador.
- **Asociaciones:** creación, edición, cambios de estado y asignación o retiro de integrantes.
- **Ferias y eventos:** creación, edición, publicación/cancelación, gestión de participantes, asistencia y notificaciones administrativas.

Las entradas se escriben junto con la operación administrativa correspondiente cuando esta se confirma. Las acciones anteriores que ya estaban guardadas en `auditoriaAdministracion` se pueden consultar si contienen actor, fecha y acción reconocidos. Una acción pasada que nunca se registró no se puede reconstruir.

## Privacidad y permisos

- Firestore Rules permiten leer `auditoriaAdministracion` solo a administradores. El navegador usa la API autenticada; no lee la colección directamente ni puede escribir auditorías.
- La API devuelve una vista resumida y validada. No expone los campos históricos `antes` y `despues`, correos, motivos, mensajes push ni otros metadatos privados que puedan existir en documentos antiguos.
- Los ajustes a las reglas no son necesarios: el acceso de lectura administrativa ya estaba definido y las escrituras siguen reservadas al backend con Firebase Admin SDK.
- La interfaz presenta el UID del actor abreviado y deja el valor completo en la ayuda emergente de la fila, sin consultar perfiles adicionales.
