# Catálogo: primer escenario implementado localmente

El fixture catalogo-local.json concreta un subconjunto del diseño provisional de productos/variantes, no sustituye el modelo completo.

Rutas: tiendasPrivadas/{tiendaId}/productos/{productoId}/variantes/{varianteId} y equivalentes tiendasPublicas. Se conservan los mismos IDs públicos/privados. No hay una colección distinta para cada rubro.

Los productos referencian categoriaId, tipoProductoId y versionTipoProducto. tiposProducto/{id}/versiones/{numero} describe campos específicos y opciones de variantes. Los tipos configurados son tejido, alimento y planta; sus campos obligatorios definitivos se ajustarán tras terreno. La propuesta inicial usa campos de texto para estos ejemplos.

Producto privado: nombre, descripcion, tipoProductoId, versionTipoProducto, categoriaId, imagenes, atributosEspecificos, tieneVariantes, moneda, unidadVenta, pasoCantidad, modalidad, manejaStock, alAgotarse, disponible, estadoPublicacion, piezaUnicaVendida, creadoEn y actualizadoEn. Sin variantes añade precioBase y stock solo si manejaStock. Con variantes, cada variante guarda opciones, precio, stock y activa en este ejemplo.

La vista pública usa una lista explícita de campos del catálogo, estadoDisponibilidad (disponible, agotado o a_pedido), `manejaStock` y el saldo exacto vigente. Los productos simples publican `stock` cuando se controla inventario; cada variante publica su propio `stock`. Cuando no se controla, el valor público es nulo y el comprador ve “Sin control de stock”. La proyección no contiene propietarioUid ni la política privada de agotamiento. El ejemplo solo muestra precios visibles; ocultarlos deberá retirar también los importes de todas las variantes y derivados antes de permitir lecturas.

Solo se admiten cantidades enteras y moneda CLP. La creación/edición privada está implementada según 07-edicion-productos.md y la publicación según 03-publicacion.md. Se prueban agotados y piezas únicas iniciales; ventas, cantidades fraccionarias, precios ocultos y migración de modalidad siguen pendientes. Las proyecciones del fixture se precargan como un escenario de prueba.
