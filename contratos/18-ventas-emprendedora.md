# Registro de ventas de emprendedora

Contrato local inicial de la API autenticada. Las operaciones requieren una cuenta activa con rol `emprendedora`, asignación a `tiendaId` y propiedad de la tienda.

## Configurar historial

`POST /api/v1/configurarHistorialVentas` recibe exactamente `tiendaId` e `historialVentasActivo`. Modifica `tiendasPrivadas/{tiendaId}/configuracion/general`. No elimina ventas históricas al desactivar el interruptor.

## Configurar visibilidad de precios

`POST /api/v1/configurarMostrarPrecios` recibe exactamente `tiendaId` y `mostrarPrecios: boolean`. Solo la propietaria activa asignada puede cambiarlo. La preferencia se guarda en `tiendasPrivadas/{tiendaId}/configuracion/general` y, cuando la tienda está publicada, se actualiza junto con la ficha de `tiendasPublicas`.

El ajuste es global: afecta a los productos publicados y a los que se publiquen después. Antes de ocultar los precios de una tienda publicada, la API prepara o actualiza `productosConsulta` y sus variantes desde las proyecciones públicas vigentes, eliminando `precioBase` y `precio`. Solo después cambia `mostrarPrecios` a `false`; así la colección con importes queda bloqueada por reglas y el comprador lee la vista sin precios. Al volver a activar los montos, el comprador usa la proyección normal `productos`. La ficha privada y la gestión de la emprendedora conservan los precios.

## Registrar una venta

`POST /api/v1/listarFeriasVenta` recibe `tiendaId` y devuelve únicamente ferias publicadas donde la emprendedora autenticada figura como `confirmada` o `agregada_admin`. La consulta parte de sus documentos de participación, con un máximo de 100; no enumera todas las ferias públicas. Incluye fechas, coordenadas opcionales y estado de participación para las sugerencias locales de venta.

`POST /api/v1/registrarVenta` recibe `tiendaId`, `operacionId`, `productoId`, `cantidad` y, cuando corresponda, `varianteId`. Con el historial activo también requiere feria o lugar; la fecha ISO es opcional y, si se omite, se usa la hora del servidor. Si se asocia a una feria, la API comprueba en la transacción que esté publicada, que la emprendedora esté confirmada o agregada directamente y que la fecha de venta corresponda a uno de sus días según `America/Santiago`. La app sugiere y preselecciona la única feria elegible de hoy; si hay varias, pide escoger entre ellas. Con el historial apagado no exige ni guarda los datos del lugar.

El servidor exige producto existente, variante activa cuando corresponda, precio vigente y stock suficiente si el producto controla stock. En una transacción descuenta únicamente el saldo del producto o variante vendido. Si `historialVentasActivo` está encendido, también guarda en `ventas/{operacionId}` el precio unitario, cantidad, total, fecha, producto y lugar; si está apagado, la venta no aparece en el historial, pero el descuento de stock sí se realiza. La respuesta indica `historialRegistrado`. Si hay publicación aprobada, actualiza en la misma transacción la disponibilidad pública según la política aprobada (`mostrar_agotado`, `ocultar` o `pasar_a_pedido`). Al vender la última unidad de una pieza única, la proyección se elimina y `piezaUnicaVendida` queda fijado en la ficha privada. `operacionesVentas/{operacionId}` conserva firma y resultado para reintentos idempotentes. Reutilizar un ID con otra solicitud se rechaza.

Desactivar el historial deja de guardar nuevas entradas y conserva las anteriores; no bloquea el registro manual ni el descuento de stock. La fecha se almacena como instante y la interfaz debe presentar la zona horaria local. La sugerencia por día solo aparece cuando el historial está activo y se registra el lugar de la venta; no usa GPS, que corresponde al criterio A06.

Para A06, cuando el historial está activo y hay una feria de hoy con estado de participación `confirmada` y coordenadas, la pantalla de registro usa ubicación solo en primer plano. Si el permiso ya está concedido, obtiene una posición nueva al abrir la pantalla; si no, ofrece solicitarlo. Sugiere y preselecciona la feria únicamente cuando está dentro de su intervalo horario, la posición tiene menos de 60 segundos y su precisión permite ubicarla dentro de un radio de 500 m del punto de la feria. Si hay varias coincidencias, sugiere la más cercana. No transmite ni guarda la ubicación. Sin permiso, coordenadas, GPS o precisión suficiente, conserva el flujo por fecha de A05 y la selección manual; se puede volver a consultar la ubicación desde «Actualizar ubicación».

La app consulta todas las ventas directamente en Firestore como propietaria, ordenadas por fecha descendente, y aplica localmente filtros por fecha, feria/lugar y producto. No hay un límite explícito de documentos consultados. Las reglas locales permiten la lectura solo a la propietaria.

## Pendiente

Siguen pendientes la operación de disponibilidad masiva y la cola offline. Las reglas locales permiten lectura de ventas a la propietaria; deben publicarse en Firebase para usar el historial contra el proyecto remoto. Las invitaciones a ferias mantienen su implementación demo aparte.
