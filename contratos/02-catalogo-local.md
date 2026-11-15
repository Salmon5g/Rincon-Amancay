# Catálogo: primer escenario implementado localmente

El fixture catalogo-local.json concreta un subconjunto del diseño provisional de productos/variantes, no sustituye el modelo completo.

Rutas privadas: `tiendasPrivadas/{tiendaId}/productos/{productoId}/variantes/{varianteId}`. La proyección pública con precios usa los mismos IDs bajo `tiendasPublicas/{tiendaId}/productos`. Si la tienda oculta los importes, el comprador consulta `tiendasPublicas/{tiendaId}/productosConsulta/{productoId}` y sus variantes; esta vista omite `precioBase` y `precio`.

Los productos referencian categoriaId, tipoProductoId y versionTipoProducto. tiposProducto/{id}/versiones/{numero} describe campos específicos y opciones de variantes. Los tipos configurados son tejido, alimento y planta; sus campos obligatorios definitivos se ajustarán tras terreno. La propuesta inicial usa campos de texto para estos ejemplos.

Producto privado: nombre, descripcion, tipoProductoId, versionTipoProducto, categoriaId, imagenes, atributosEspecificos, tieneVariantes, moneda, unidadVenta, pasoCantidad, modalidad, manejaStock, alAgotarse, disponible, estadoPublicacion, piezaUnicaVendida, creadoEn y actualizadoEn. Sin variantes añade precioBase y stock solo si manejaStock. Con variantes, cada variante guarda opciones, precio, stock y activa en este ejemplo.

Las dos vistas públicas usan campos explícitos del catálogo, `estadoDisponibilidad`, `manejaStock` y el saldo vigente. Los productos simples publican `stock` cuando se controla inventario; cada variante publica su propio saldo. Cuando no se controla, el valor público es nulo y el comprador ve “Sin control de stock”. Ninguna vista contiene `propietarioUid` ni la política privada de agotamiento. Las reglas permiten leer `productos` y sus variantes solo con precios visibles; cuando se ocultan, permiten leer únicamente `productosConsulta`, cuya proyección omite los importes.

Solo se admiten cantidades enteras y moneda CLP. La creación/edición privada está implementada según 07-edicion-productos.md y la publicación según 03-publicacion.md. La vista sin precios se mantiene al publicar, editar, retirar y actualizar existencias. Los documentos públicos anteriores requieren ejecutar la sincronización de vistas en modo simulación y luego escritura. Las proyecciones del fixture se precargan como un escenario de prueba.
