# Edición de ficha de tienda por la propietaria

La app Android puede leer `tiendasPrivadas/{tiendaId}` directamente con la sesión Firebase de su propietaria. Los campos de negocio se escriben mediante la API autenticada; las reglas Firestore no se amplían para permitir que el cliente modifique la ficha.

## Identidad y propiedad

La API valida el ID token y vuelve a comprobar en Firestore que:

- `accesos/{uid}` esté activo, tenga rol `emprendedora` y `tiendaId` igual a la solicitud;
- `emprendedoras/{uid}.tiendaId` coincida;
- `tiendasPrivadas/{tiendaId}.propietarioUid` sea el UID autenticado.

El cliente no envía UID, propietario ni estado de publicación como autoridad.

## Solicitud

POST `/api/v1/editarTiendaEmprendedora`, con `Authorization: Bearer <Firebase ID token>` y JSON:

```json
{
  "tiendaId": "id-de-la-tienda",
  "operacionId": "uuid-de-la-intencion",
  "versionEsperada": "segundos:nanosegundos",
  "nombre": "Tienda de ejemplo",
  "descripcion": "Descripción breve",
  "tipoEmprendimientoId": "tejidos",
  "sectorId": "alerce",
  "contacto": { "tipo": "whatsapp", "valor": "+56912345678" },
  "mostrarContacto": true
}
```

`contacto` puede ser `null` si `mostrarContacto` es `false`. Los tipos son `whatsapp`, `correo` o `web`; el sitio web debe ser HTTPS. El número de WhatsApp requiere formato válido con código de país. La versión es el `seconds:nanoseconds` de `actualizadoEn` de la ficha privada leída antes de abrir el formulario.

## Efectos

- Escribe nombre, descripción, tipo, sector, contacto, `mostrarContacto` y `actualizadoEn` en la ficha privada.
- Si la tienda está publicada, aplica los campos públicos aprobados a `tiendasPublicas/{tiendaId}` dentro de la misma transacción. Al ocultar contacto, lo elimina de la proyección pública.
- No modifica productos publicados, propietario, fecha de creación ni estado de publicación.
- Guarda un recibo idempotente en `tiendasPrivadas/{tiendaId}/operacionesEdicion/{operacionId}`. La propietaria no puede leer ni escribir esos recibos mediante reglas de cliente.

El logo se carga por separado mediante `POST /api/v1/tiendas/{tiendaId}/logo`, con el archivo binario, el ID token Firebase y `X-Archivo-Id` UUID. La API valida la propiedad, guarda el original privado en Supabase y actualiza `tiendasPrivadas/{tiendaId}.logo`. Si la tienda está publicada, también genera y sincroniza `logo` y `logoMiniatura` en la proyección pública. Las rutas y políticas están en el contrato 05. La pantalla Android aún no llama a esta operación ni muestra el logo cargado.

## Errores y reintentos

`400 datos-invalidos`: campos/contacto/catálogos inválidos. `401 no-autenticado`: sesión ausente, expirada o revocada. `403 sin-permiso`: rol, tienda o propiedad no coinciden. `404 no-encontrado`: tienda inexistente. `409 conflicto`: versión desactualizada o proyección pública inconsistente. `409 id-reutilizado`: el `operacionId` ya corresponde a otra solicitud.

Al reintentar la misma intención, conservar `operacionId` y contenido. Si la solicitud cambió, usar una nueva operación después de releer la ficha. El modelo es provisional y puede evolucionar coordinando los campos con web y Android.
