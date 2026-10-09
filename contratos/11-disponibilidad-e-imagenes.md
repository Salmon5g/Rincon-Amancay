# Disponibilidad e imágenes publicadas

Implementado y probado en emuladores. Mantiene las rutas de API y cuerpos existentes de publicación y ajuste de stock. No se creó infraestructura en Firebase real.

## Una ficha aprobada y un borrador

Al publicar un producto, el servidor conserva la ficha aprobada en:

`tiendasPrivadas/{tiendaId}/productos/{productoId}/publicacion/vigente`

Contiene `schema: 1`, `cicloCatalogo`, política aprobada (`modalidad`, `manejaStock`, `alAgotarse`), producto proyectado, variantes aprobadas y fecha de aprobación. Es un documento interno: ningún cliente puede leerlo o escribirlo directamente. No contiene el stock; los saldos vigentes siguen en el producto/variantes privados.

Un ajuste de stock utiliza esta copia para reconstruir únicamente la disponibilidad pública. No incorpora nombre, descripción, precio, fotos, atributos, política de agotamiento ni cambios de variantes que sigan en el borrador. La actualización del saldo, disponibilidad, historial y comprobante se confirma en una transacción Firestore.

| Política aprobada | Al llegar a cero | Al reponer |
| --- | --- | --- |
| `mostrar_agotado` | Ficha visible con `agotado`. | Vuelve a `disponible`. |
| `pasar_a_pedido` | Ficha visible con `a_pedido`. | Vuelve a `disponible`. |
| `ocultar` | Se quita la proyección pública; se conserva la aprobación interna. | Reaparece la ficha aprobada. |

Los productos de modalidad `a_pedido` mantienen esa disponibilidad. La cantidad exacta sigue siendo privada. Una pérdida de pieza única no se registra como venta ni cambia `piezaUnicaVendida`.

En productos con variantes se sincronizan solo las aprobadas, incluidas las que tenían cero unidades al publicar. Una variante nueva del borrador no se publica por ajustar su stock. Cambiar `activa` o el precio en el borrador requiere publicación explícita. Si todas las variantes aprobadas quedan ocultas, se oculta también el producto; una reposición puede restaurarlo con los valores aprobados.

## Retiradas explícitas y cuentas desactivadas

- `retirarProducto` elimina su aprobación interna incluso si ya estaba oculto por agotamiento. Una reposición posterior no lo publica.
- `retirarTienda` incrementa `cicloCatalogo` privado. Las aprobaciones anteriores quedan invalidadas, incluso las de productos agotados que no tenían proyección pública. Volver a publicar la tienda no vuelve a aprobar sus productos.
- Desactivar una cuenta mantiene bloqueados la gestión y el catálogo. Después de reactivarla, un ajuste no modifica `habilitada` de la tienda pública: sigue haciendo falta la publicación expresa de la tienda.

Cambios simultáneos de publicación, retirada y stock se serializan mediante las lecturas/transacciones y la versión del producto. No se vuelve a publicar una ficha nueva de manera implícita.

## Compatibilidad con los datos anteriores

Las publicaciones del fixture o creadas antes de este incremento no tienen aprobación interna. Un ajuste sobre ellas conserva el comportamiento anterior: retira la proyección y devuelve `requiereRepublicar:true`. La propietaria debe revisar y publicar una vez para crear la copia aprobada. No se puede inferir esa copia a partir del borrador porque podría contener cambios no publicados.

Las publicaciones nuevas devuelven `requiereRepublicar:false` al ajustar. La respuesta y el movimiento incluyen `actualizacionCatalogo`:

- `actualizado`: se guardó la disponibilidad de la ficha aprobada.
- `oculto`: quedó oculta por la política aprobada; se puede restaurar con stock.
- `sin_publicacion`: no había una publicación que mantener.
- `requiere_republicar`: publicación anterior/incompatible retirada, pendiente de revisión y publicación.

Los comprobantes históricos de operaciones anteriores conservan su respuesta original y pueden no incluir este campo. Ningún reintento vuelve a aplicar el saldo. `retiradoDelCatalogo` en el historial indica si existía una ficha pública y este movimiento la retiró.

## Imágenes preparadas por el servidor

La propietaria sigue subiendo originales a `tiendas/{tiendaId}/productos/{productoId}/{archivoId}.{jpg|png|webp}`. **Los originales ahora son privados**, aunque una proyección antigua todavía tenga su ruta. Para restaurar las fotos de una publicación anterior, publicar el producto de nuevo.

Al publicar, el servidor comprueba pertenencia, MIME y tamaño (hasta 5 MiB), decodifica la imagen completa con Sharp, rechaza formatos reales distintos, archivos dañados, animaciones, más de 20 millones de píxeles o dimensiones superiores a 10.000 px. Aplica orientación EXIF antes de eliminar los metadatos y genera:

- Imagen WebP, calidad 82, dentro de 1600 × 1600 px.
- Miniatura WebP, calidad 78, dentro de 320 × 320 px.

Se mantiene la proporción y no se amplían imágenes pequeñas. Las copias no conservan EXIF/GPS, XMP, IPTC ni perfiles ICC. Hay un límite de cinco segundos por transformación; no es un análisis antivirus ni una moderación del contenido visual.

Las copias usan rutas por contenido: `catalogo/{tiendaId}/productos/{productoId}/{hash}_1600.webp` y `{hash}_320.webp`. Solo el servidor puede crearlas. Un reintento reutiliza las mismas rutas; se evita sobrescribirlas. No se crean URLs con tokens de descarga. Las reglas permiten leerlas públicamente solo mientras estén referenciadas por un producto publicado de una tienda habilitada y con precios visibles.

Storage y Firestore no comparten transacción. Las copias se preparan antes de confirmar la proyección: si la publicación falla o cambia la versión, pueden quedar copias sin referencia. Esas copias no son públicas y no se eliminan automáticamente. La futura limpieza deberá consultar borradores, proyecciones y aprobaciones internas: una imagen de un producto agotado puede necesitarse al reponerlo.

Sharp requiere su binario nativo de la plataforma; `npm ci` instala la dependencia fijada por el lockfile. Antes de producción aún deben dimensionarse CPU/memoria, concurrencia y cuotas, definir procesamiento en cola y limpieza, y revisar costos/caché. Las pruebas locales no equivalen a desplegar ese servicio.

## Acuerdo para web y Android

La ficha privada conserva `imagenes` con originales. La ficha pública devuelve `imagenes` con copias WebP y `miniaturas` en el mismo orden. La primera sigue siendo la portada. Para listas, preferir `miniaturas[index]` y usar `imagenes[index]` si la miniatura no existe en un dato anterior. Para detalle, leer la imagen principal.

Usar las rutas entregadas por Firestore mediante SDK Storage; no deducirlas del nombre original ni usar URLs públicas permanentes. El adaptador web `leerImagen` sirve para ambas. Los originales se usan únicamente al editar desde la sesión propietaria.

Después de ajustar stock, la pantalla puede releer el catálogo o mantener una suscripción Firestore. La transacción actualiza los datos públicos, pero no añade automáticamente suscripciones a las pantallas que construya el grupo. `requiereRepublicar:false` no significa que se hayan publicado los cambios pendientes del borrador.

## Pruebas

`npm --prefix backend run test:disponibilidad` cubre políticas, variantes, borradores, piezas únicas, retiradas, reintentos y concurrencia. `node --test backend/tests/imagenes.test.ts` cubre validación, rotación, dimensiones, eliminación de metadatos y reutilización. `npm --prefix web run test:local` prueba el SDK contra la API real local. Las suites de reglas verifican originales privados y copias referenciadas. Ejecutar las suites secuencialmente con los emuladores activos; la suite web necesita además la API.

Referencias: [opciones y metadatos de Sharp](https://sharp.pixelplumbing.com/api-output/), [límites de entrada](https://sharp.pixelplumbing.com/api-constructor/), [descargas mediante SDK](https://firebase.google.com/docs/storage/web/download-files).
