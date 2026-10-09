# Ajustes de stock con historial — local

Implementado únicamente en emuladores. Es un ajuste de inventario, no una venta ni una reserva. La propietaria debe estar autenticada, activa y asignada a la tienda. El rol administrador no concede acceso al stock ni a su historial.

## Solicitud

POST `/api/v1/ajustarStock`, Content-Type: application/json y Authorization: Bearer con ID token Firebase.

```json
{
  "tiendaId": "id-de-tienda",
  "productoId": "id-de-producto",
  "operacionId": "uuid-de-la-intencion",
  "versionEsperada": "segundos:nanosegundos",
  "cantidad": -2,
  "motivo": "perdida",
  "nota": "Dos unidades dañadas"
}
```

En un producto con variantes, agregar varianteId. En un producto simple, omitirlo. versionEsperada siempre corresponde al actualizadoEn del producto padre, incluso al ajustar una variante. Releer la ficha para obtenerla.

cantidad es un entero distinto de cero, positivo para sumar y negativo para restar. Motivos: reposicion solo positiva; perdida solo negativa; correccion admite ambos signos. nota es opcional, no vacía si se envía y de hasta 500 caracteres. No enviar saldo final, identidad del actor, fechas ni campos adicionales.

El producto debe manejar stock. Se rechazan un stock existente inválido, saldo final negativo o valores fuera del rango entero seguro de JavaScript. Las variantes inactivas también pueden recibir ajustes de inventario; esto no las activa. Una pieza única admite saldo 0 o 1; una pieza marcada como vendida requiere un futuro flujo de devolución, no un ajuste directo. Restar por pérdida no cambia piezaUnicaVendida.

## Catálogo público

En esta primera versión cualquier ajuste retira el producto público completo y sus variantes, si estaban publicados, dentro de la misma transacción. La ficha privada pasa a archivado cuando tenía una publicación; un borrador sin publicación conserva su estado. Se conserva la tienda pública y los demás productos.

Después del ajuste, releer la ficha, revisar los cambios y llamar a publicarProducto con otra operación. Allí se calculan agotado, a_pedido u ocultación según la política vigente. Esta retirada temporal evita mostrar cantidades/disponibilidad antiguas y evita publicar precios, textos o fotos pendientes en el borrador. La sincronización automática de disponibilidad sin republicación queda pendiente; no presentar este incremento como stock público en tiempo real.

Los archivos de Storage se conservan. Al desaparecer la proyección pública, las reglas de Storage bloquean nuevas lecturas públicas de sus referencias; esto no borra copias descargadas ni enlaces con token distribuidos previamente.

## Historial y consistencia

Una transacción guarda el stock, la nueva versión del producto (y de la variante, cuando corresponde), la retirada pública y dos documentos:

- `tiendasPrivadas/{tiendaId}/movimientosStock/{operacionId}`: productoId, varianteId opcional, cantidad, motivo, nota opcional, stockAnterior, stockNuevo, realizadoPor, creadoEn y retiradoDelCatalogo.
- `tiendasPrivadas/{tiendaId}/operacionesStock/{operacionId}`: comprobante interno del servidor para reintentos; los clientes no lo leen.

El movimiento es legible únicamente por la propietaria activa. Ningún cliente, incluida ella, puede crearlo, modificarlo o borrarlo directamente. Una corrección posterior genera otro movimiento. Las herramientas técnicas con SDK Admin pueden modificar datos y requieren controles operativos antes del despliegue; estas reglas no equivalen a un registro inviolable de producción.

El historial comienza con los ajustes realizados mediante esta API; el stock inicial de creación no genera todavía un movimiento. historialVentasActivo no controla esta bitácora: no es el historial comercial de ventas.

Reintentar una misma solicitud con el mismo operacionId devuelve el resultado original sin aplicar dos veces la cantidad. Cambiar sus datos conservando el ID produce conflicto. Si cambió la versión del producto, se rechaza el ajuste sin escribir movimientos. No reintentar un conflicto a ciegas: releer, revisar y generar una nueva intención. Los permisos se verifican también al recuperar un reintento.

Respuesta 200:

```json
{"datos":{"operacionId":"...","stockAnterior":5,"stockNuevo":3,"requiereRepublicar":true}}
```

requiereRepublicar indica que este ajuste retiró una publicación o encontró la ficha marcada publicada. Es el resultado histórico de esa operación, no una consulta del estado actual en futuros reintentos. Un borrador no publicado también requiere publicación explícita para aparecer en el catálogo.

400: datos incompatibles; 401/403: sesión/permisos; 404: producto/variante inexistente; 409: versión, ID o saldo en conflicto; 422: más de 100 variantes públicas en la retirada. El límite protege la transacción y falla sin ajustes parciales. El editor actual admite hasta 20 variantes.

## Lectura compartida web/Android

Leer movimientosStock dentro de la tienda propia. Para una primera pantalla: ordenar por creadoEn descendente, limitar a 50 y paginar con el último documento (startAfter). Es una consulta de una sola colección; no consultar globalmente historiales de todas las tiendas. Si se agrega filtro por producto junto al orden temporal, definir y probar su índice compuesto.

## Verificación

Desde backend/, con emuladores activos: npm run typecheck y npm run test:stock. 17 casos (18 incluyendo el grupo) cubren sesión, permisos, motivos, saldos, historial inmutable para clientes, privacidad, variantes, piezas únicas, retirada pública, idempotencia y concurrencia. Las pruebas crean datos aislados y los eliminan al terminar.

Ventas, cobros, reservas, devoluciones, stock inicial auditado y sincronización automática de disponibilidad permanecen pendientes. No se desplegaron reglas ni código en Firebase real.
