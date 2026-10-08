# Catálogo: primer escenario implementado localmente

El fixture catalogo-local.json concreta un subconjunto del diseño provisional de productos/variantes, no sustituye el modelo completo.

Rutas: tiendasPrivadas/{tiendaId}/productos/{productoId}/variantes/{varianteId} y equivalentes tiendasPublicas. Se conservan los mismos IDs públicos/privados. No hay una colección distinta para cada rubro.

Los productos referencian categoriaId, tipoProductoId y versionTipoProducto. tiposProducto/{id}/versiones/{numero} describe campos específicos y opciones de variantes. Los tipos configurados son tejido, alimento y planta; sus campos obligatorios definitivos se ajustarán tras terreno. La propuesta inicial usa campos de texto para estos ejemplos.

Producto privado: nombre, descripcion, tipoProductoId, versionTipoProducto, categoriaId, imagenes, atributosEspecificos, tieneVariantes, moneda, unidadVenta, pasoCantidad, modalidad, manejaStock, alAgotarse, disponible, estadoPublicacion, piezaUnicaVendida, creadoEn y actualizadoEn. Sin variantes añade precioBase y stock solo si manejaStock. Con variantes, cada variante guarda opciones, precio, stock y activa en este ejemplo.

La vista pública usa una lista explícita de campos del catálogo y estadoDisponibilidad (disponible, agotado o a_pedido). No contiene cantidades de stock, propietarioUid ni configuración privada. El ejemplo solo muestra precios visibles; ocultarlos deberá retirar también los importes de todas las variantes y derivados antes de permitir lecturas.

Solo se prueban cantidades enteras y moneda CLP. Cantidades fraccionarias, agotados, piezas únicas vendidas, precios ocultos y cambios de modalidad siguen pendientes de implementación/pruebas. Tampoco hay servicio de publicación: las proyecciones del fixture son datos precargados para preparar ese trabajo.
