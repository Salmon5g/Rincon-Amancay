# Registro de ventas de emprendedora

Contrato local inicial de la API autenticada. Las operaciones requieren una cuenta activa con rol `emprendedora`, asignación a `tiendaId` y propiedad de la tienda.

## Configurar historial

`POST /api/v1/configurarHistorialVentas` recibe exactamente `tiendaId` e `historialVentasActivo`. Modifica `tiendasPrivadas/{tiendaId}/configuracion/general`. No elimina ventas históricas al desactivar el interruptor.

## Registrar una venta

`POST /api/v1/registrarVenta` recibe `tiendaId`, `operacionId`, `productoId`, `cantidad` y, cuando corresponda, `varianteId`. Con el historial activo también requiere feria o lugar; la fecha ISO es opcional y, si se omite, se usa la hora del servidor. Con el historial apagado no exige ni guarda los datos del lugar.

El servidor exige producto existente, variante activa cuando corresponda, precio vigente y stock suficiente si el producto controla stock. En una transacción descuenta únicamente el saldo del producto o variante vendido. Si `historialVentasActivo` está encendido, también guarda en `ventas/{operacionId}` el precio unitario, cantidad, total, fecha, producto y lugar; si está apagado, la venta no aparece en el historial, pero el descuento de stock sí se realiza. La respuesta indica `historialRegistrado`. Si hay publicación aprobada, actualiza en la misma transacción la disponibilidad pública según la política aprobada (`mostrar_agotado`, `ocultar` o `pasar_a_pedido`). Al vender la última unidad de una pieza única, la proyección se elimina y `piezaUnicaVendida` queda fijado en la ficha privada. `operacionesVentas/{operacionId}` conserva firma y resultado para reintentos idempotentes. Reutilizar un ID con otra solicitud se rechaza.

Desactivar el historial deja de guardar nuevas entradas y conserva las anteriores; no bloquea el registro manual ni el descuento de stock. La fecha se almacena como instante y la interfaz debe presentar la zona horaria local.

La app consulta ventas directamente en Firestore como propietaria, ordenadas por fecha descendente, con un límite inicial de 200 documentos; aplica localmente filtros por fecha, feria/lugar y producto. Las reglas locales permiten la lectura solo a la propietaria. Para historiales mayores se requiere paginación antes de dar A22 por cerrado.

## Pendiente

Siguen pendientes la paginación más allá de 200 ventas, la operación de disponibilidad masiva y la cola offline. Las reglas locales permiten lectura de ventas a la propietaria; deben publicarse en Firebase para usar el historial contra el proyecto remoto. Las invitaciones a ferias mantienen su implementación demo aparte.
