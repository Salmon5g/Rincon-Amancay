# Registro de ventas de emprendedora

Contrato local inicial de la API autenticada. Las operaciones requieren una cuenta activa con rol `emprendedora`, asignación a `tiendaId` y propiedad de la tienda.

## Configurar historial

`POST /api/v1/configurarHistorialVentas` recibe exactamente `tiendaId` e `historialVentasActivo`. Modifica `tiendasPrivadas/{tiendaId}/configuracion/general`. No elimina ventas históricas al desactivar el interruptor.

## Registrar una venta

`POST /api/v1/registrarVenta` recibe `tiendaId`, `operacionId`, `productoId`, `cantidad`, `lugarTipo` y, según corresponda, `varianteId`, `feriaId` o `lugarTexto`, además de fecha ISO opcional.

El servidor exige historial activo, producto existente, variante activa cuando corresponda, precio vigente y stock suficiente si el producto controla stock. En una transacción guarda en `ventas/{operacionId}` el precio unitario, cantidad, total, fecha, producto y lugar; también descuenta únicamente el saldo del producto o variante vendido. Si hay publicación aprobada, actualiza en la misma transacción la disponibilidad pública según la política aprobada (`mostrar_agotado`, `ocultar` o `pasar_a_pedido`). Al vender la última unidad de una pieza única, la proyección se elimina y `piezaUnicaVendida` queda fijado en la ficha privada. `operacionesVentas/{operacionId}` conserva firma y resultado para reintentos idempotentes. Reutilizar un ID con otra solicitud se rechaza.

Desactivar el historial bloquea nuevas ventas y conserva las anteriores. La fecha se almacena como instante y la interfaz debe presentar la zona horaria local.

La app consulta ventas directamente en Firestore como propietaria, ordenadas por fecha descendente, con un límite inicial de 200 documentos; aplica localmente filtros por fecha, feria/lugar y producto. Las reglas locales permiten la lectura solo a la propietaria. Para historiales mayores se requiere paginación antes de dar A22 por cerrado.

## Pendiente

Siguen pendientes la paginación más allá de 200 ventas, la operación de disponibilidad masiva y la cola offline. Las reglas locales permiten lectura de ventas a la propietaria; deben publicarse en Firebase para usar el historial contra el proyecto remoto. Las invitaciones a ferias mantienen su implementación demo aparte.
