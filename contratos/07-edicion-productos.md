# Creación y edición de productos — API local

Implementado en emuladores, sin despliegue. Ambos endpoints son POST con ID token Firebase en Authorization: Bearer y Content-Type: application/json. Solo una propietaria activa con rol emprendedora y tienda asignada puede operar; el rol administrador por sí solo no permite editar el catálogo privado.

## Crear

`/api/v1/crearProducto` recibe:

```json
{
  "tiendaId": "id-de-la-tienda",
  "productoId": "id-nuevo-del-producto",
  "operacionId": "uuid-de-la-intencion",
  "producto": {
    "nombre": "Gorro de lana",
    "descripcion": "Gorro tejido a mano",
    "tipoProductoId": "tejido",
    "versionTipoProducto": 1,
    "categoriaId": "vestuario",
    "atributosEspecificos": {"material": "Lana", "cuidados": "Lavar a mano"},
    "tieneVariantes": false,
    "moneda": "CLP",
    "unidadVenta": "unidad",
    "pasoCantidad": 1,
    "modalidad": "regular",
    "manejaStock": true,
    "alAgotarse": "mostrar_agotado",
    "precioBase": 5000,
    "stockInicial": 3
  },
  "variantes": []
}
```

El servidor crea la ficha privada como borrador, con imagenes vacío, disponible=true, piezaUnicaVendida=false y fechas de servidor. No crea documentos públicos. Rechaza campos adicionales, incluidos estadoPublicacion, roles, stock, fechas o imágenes. Las imágenes se seleccionan por el flujo del contrato 05 después de crear el producto.

Producto simple: precioBase obligatorio; stockInicial obligatorio únicamente si manejaStock=true. Sin manejo de stock, omitir stockInicial. Modalidades: regular, a_pedido y pieza_unica. Pieza única exige manejo de stock, exactamente una unidad inicial y ninguna variante. A pedido puede usar o no stock según el acuerdo provisional; su estado público es a_pedido.

Producto con variantes: tieneVariantes=true, sin precioBase ni stockInicial en el producto. En variantes enviar de 1 a 20 elementos:

```json
[
  {
    "varianteId": "azul_m",
    "opciones": {"color": "Azul", "talla": "M"},
    "precio": 5000,
    "stockInicial": 2,
    "activa": true
  }
]
```

Cada variante maneja su propio precio y, si corresponde, stock. Sin manejo de stock, omitir stockInicial también en variantes. No duplicar IDs ni combinaciones de opciones; la comparación de combinaciones ignora mayúsculas y espacios externos. El tipo debe definir opciones de variante; no se inventan claves libres.

## Editar

`/api/v1/editarProducto` recibe la misma estructura completa de campos editables, más versionEsperada en el nivel principal. Su valor es seconds:nanoseconds de actualizadoEn del producto privado releído. Usar un operacionId nuevo para una nueva intención.

- Enviar nombre, descripción, categoría, atributos, precio y política de agotamiento completos, aunque algún valor no cambie. No es un parche parcial.
- Omitir stockInicial en el producto y en variantes existentes. La edición conserva su stock, imágenes, fechas de creación y estado de publicación.
- Enviar todas las variantes existentes. Para desactivar una, usar activa=false. No se permite borrarla por omisión.
- Se pueden agregar variantes nuevas, con stockInicial si el producto maneja stock.
- Las opciones de una variante existente identifican esa variante y no se cambian. Para otra combinación se agrega otro ID.
- No se permite cambiar tipoProductoId, versionTipoProducto, modalidad, manejo de stock, presencia de variantes, moneda ni unidad. Esos cambios requieren un flujo posterior de migración; crear otro producto o consultar al equipo, sin modificar Firestore manualmente.

Editar actualiza actualizadoEn del producto incluso si solo cambió una variante. La vista pública conserva su última publicación; volver a publicar aplica el nuevo texto, precios y variantes activas. Desactivar una variante en el borrador no la retira inmediatamente de la vista pública. Para retirada inmediata del producto completo, usar retirarProducto.

## Validación y reintentos

Tipo, versión y categoría deben existir, con tipo y categoría activos. Los atributos se validan contra tiposProducto/{id}/versiones/{version}: se rechazan claves desconocidas, campos obligatorios ausentes y opciones distintas de las definidas. Este incremento admite campos específicos de texto de hasta 500 caracteres y opciones de hasta 100. Nombre: hasta 120; descripción: hasta 2000; ambos no vacíos. Solo CLP, precios enteros no negativos y cantidades enteras por unidad.

La ficha, las variantes y el comprobante privado en tiendasPrivadas/{tiendaId}/operacionesProductos/{operacionId} se escriben en una transacción. Repetir la misma solicitud devuelve el resultado anterior sin reescribir. Cambiar el contenido conservando operacionId da 409. Cambiar solo el orden de claves JSON no cambia la intención; el orden de las variantes sí forma parte de la solicitud. Se comprueban permisos vigentes también en los reintentos.

Respuesta 200: `{"datos":{"accion":"crearProducto","productoId":"...","operacionId":"..."}}`. Releer la ficha para conocer su versión confirmada. 400: datos inválidos; 401/403: sesión/permisos; 404: producto a editar inexistente; 409: conflicto, ID reutilizado o cambio estructural; 422: límite de variantes existentes. Se conserva el límite HTTP de 16 KiB: 20 variantes es un máximo, y una ficha con textos largos puede necesitar una solicitud menor.

## Pruebas y pendientes

Con emuladores activos: npm run typecheck y npm run test:productos desde backend/. Se probaron 21 casos de sesión, propiedad, validación, modalidades, stock inicial, variantes, publicación explícita, reintentos y concurrencia. La prueba de edición/publicación usa un sustituto de imágenes; la suite api.test.ts prueba por separado el verificador de Storage.

El editor no registra ventas, reservas, devoluciones ni elimina productos. Los ajustes de stock tienen su propia operación, descrita en 08-ajustes-stock.md. No admite cantidades fraccionarias, tipos específicos no textuales ni migraciones de estructura. El modelo puede ampliarse después del levantamiento de requerimientos conservando contratos versionados.
