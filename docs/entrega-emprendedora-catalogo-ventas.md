# Entrega de emprendedora: catálogo y ventas

## Alcance revisado

La app unificada es el destino de esta entrega. La sesión de emprendedora valida Firebase Auth, rol y tienda. Los productos se leen de Firestore y se modifican mediante la API autenticada local. Las pantallas legadas de ventas y ferias siguen usando `DemoAmancayRepository`; sus datos no son persistentes y no sirven como evidencia de cumplimiento.

## Estado por criterio

| Criterios | Estado actual | Brecha principal |
| --- | --- | --- |
| A01–A05 | Implementados en el editor conectado | El alta tiene pasos para foto, datos, tipo, variantes, disponibilidad y revisión/publicación. Admite cámara y galería; muestra campos por tipo y persiste los datos. Falta la comprobación manual con sesión real en dispositivo. |
| A06 | Implementado en el editor conectado | Cada variante conserva su precio, stock y foto propios; la elección de usar o no variantes se envía explícitamente a la API. El publicador genera `imagen`/`miniatura` públicas independientes. Falta comprobación manual conectada y en web. |
| A07–A09 | Implementados en el editor | Modalidad, control de stock y las tres políticas de agotamiento se guardan en la ficha privada. |
| A10 | Implementado localmente | Se puede marcar disponible/no disponible desde Organizar catálogo, incluso seleccionando un solo producto. La API autenticada retira la proyección pública al desactivar y conserva la ficha privada para la tienda de la emprendedora. |
| A11 | Implementado localmente | El asistente guarda en almacenamiento privado local los campos, paso actual, atributos, variantes, fotos, ID de producto e ID de operación; al volver a Agregar producto restaura el borrador y puede reintentar una operación interrumpida sin crear otro producto. Guardar con éxito o cancelar explícitamente lo elimina. Falta comprobar en dispositivo tras cierre forzado. |
| A12–A13 | Implementados en línea; edición y eliminación también se encolan sin conexión | Editar usa `editarProducto`; eliminar conserva tombstone privado y retira su proyección pública. El listener filtra tombstones. Sin conexión se guardan localmente las ediciones de campos y eliminaciones; los cambios de fotos necesitan conexión. |
| A14 | Implementado localmente | Organizar catálogo permite seleccionar productos por categoría, todos los filtrados o el catálogo completo. La API aplica lotes de hasta 20 con control de versión. Los grupos se confirman por separado: si falla un grupo intermedio, los anteriores ya quedaron guardados y se pueden volver a seleccionar/reintentar. |
| A15 | Implementado localmente | Antes de abrir un alta, la app consulta los valores por defecto del servidor y guarda una copia local sincronizada. Sin conexión usa esa copia si ya se había descargado; si el dispositivo nunca obtuvo la configuración, bloquea el alta hasta conectarse. |
| A16 | Implementado localmente | Configuración y vista previa recorre todos los productos. La etiqueta por producto se calcula con modalidad, stock, regla al agotarse, disponibilidad y estado de publicación; el destacado además muestra interacción de cantidad/encargo. |
| A17 | Parcial, requiere comprobación en dispositivo | Firestore conserva lecturas en caché. La cola local por tienda cubre disponibilidad, ajustes de stock, eliminación y edición de campos de productos existentes; usa IDs idempotentes, reintenta al reconectar y conserva operaciones fallidas con un motivo visible. En conflictos de versión vuelve a leer la versión del servidor antes de reintentar. La edición de fotos y la carga remota de un producto nuevo todavía requieren conexión; no se sincronizan desde la cola. |
| A18–A19 | Implementado localmente | **Ventas → Registrar venta** captura producto, variante cuando corresponde y cantidad. La API descuenta en transacción solo la variante elegida (o el producto simple), y actualiza su proyección pública según stock. La venta funciona con historial apagado. Se corrigió para que vender o ajustar stock de un producto no disponible no vuelva a publicarlo. |
| A20 | Implementado localmente | El interruptor de historial está en **Más → Configuración y vista previa**; también se conserva un acceso desde Ventas. Apagarlo conserva el historial anterior y no bloquea las ventas ni el descuento de stock. |
| A21 | Implementado localmente | Con el historial activo, la venta conserva precio vigente, fecha y feria/lugar; el nombre de la feria también queda como copia para mantener la referencia histórica. |
| A22 | Implementado localmente | El historial consulta todas las ventas y permite filtrar por fechas, feria/lugar y producto. |

## Secciones de interfaz previstas

Tomando los mockups como referencia, **Productos → Organizar catálogo** usa tarjetas marfil, acentos ciruela, títulos serif, filtros por categoría y controles visibles para disponibilidad; el asistente de alta conserva sus pasos visuales para foto, datos, tipo, variantes, stock y revisión. **Más → Configuración y vista previa** ofrece valores por defecto, el interruptor del historial y una tarjeta destacada más un resumen de todos los productos con su estado de catálogo. Las lecturas offline usan la caché de Firestore y las mutaciones compatibles usan una cola local por tienda.

Estas pantallas deben leer y escribir datos persistidos de la cuenta autenticada. No se deben reutilizar las listas locales de ejemplo como fuente de ventas reales. Para la comprobación web, el flujo de stock de la app debe dejar en la proyección pública los estados que prueban W09–W12; en especial, la última venta de una pieza única debe retirar su ficha pública de manera permanente.

## Dependencias de implementación

Para completar la verificación aún se necesitan:

1. Probar en dispositivo la lectura del catálogo sin red y la sincronización al recuperar conexión, incluidos conflictos y cierres de la app (A17).
2. Comprobar con sesión real en la app los flujos A12–A19 y los estados de stock que se proyectan en la web. La cola offline no carga fotos ni publica productos nuevos.

La venta guarda una feria publicada de la plataforma o texto libre; la fecha se muestra en la zona horaria local y se conserva como instante. Al desactivar el historial no se guardan nuevas ventas históricas, pero el registro manual y el descuento de stock siguen funcionando; las ventas anteriores se conservan. El contrato 07 describe las fotos independientes por variante.

## Revisión de flujos y textos (2026-11-11)

La pantalla de ventas conserva la misma fecha y el mismo identificador durante un reintento, para que una respuesta de red ambigua no repita el descuento. Si la API confirma la venta pero falla la recarga del historial, la app informa que la venta sí quedó guardada y ofrece recargar. El registro espera a leer la configuración del historial antes de habilitarse. El saldo mostrado se toma de la variante seleccionada y no usa el saldo general como respaldo.

Se añadieron estados vacíos para productos, variantes activas e historial, una explicación cuando no hay ferias publicadas, una alerta al ingresar más unidades que el stock elegido y ejemplos para cantidad, lugar, fechas, atributos, variantes y ajustes de stock. El filtro ahora rechaza fechas inválidas o un rango invertido. Se corrigió el rótulo «ESPACIO EMPRENDEDORA» por «ESPACIO DE EMPRENDIMIENTO» y se pluralizan «variante(s)» y los conteos.

La prueba de ventas en Firestore Emulator cubre el descuento exclusivo de la variante elegida, precio/cantidad/fecha/lugar, reintentos idempotentes, stock insuficiente, historial apagado, requisito de lugar, copia del nombre de feria, las tres políticas al agotarse y retirada de una pieza única vendida. La compilación Android y la comprobación TypeScript se registran en `AGENTS.md`.

## Revisión de catálogo y ventas A12–A22 (2026-11-11)

A12 y A13 usan operaciones autenticadas con control de versión. La edición de campos sin conexión se conserva en una cola local; la eliminación quita la ficha del catálogo local mediante una superposición y sincroniza el tombstone cuando vuelve la red. Las imágenes no se editan ni se suben offline. A14 procesa lotes de hasta 20 y permite reintentar grupos pendientes. A15 consulta la configuración remota antes de habilitar una alta y usa la copia local solo si ya fue sincronizada. A16 representa modalidad, stock, regla de agotamiento y visibilidad por producto.

A17 ya tiene caché de lectura y cola persistente por tienda para disponibilidad, edición de campos, ajustes de stock y eliminaciones. Las operaciones conservan su ID en reintentos con resultado incierto; solo los conflictos de versión se rebasan leyendo Firestore del servidor. Los errores de validación u otros conflictos no se reescriben como éxito: se conservan en cola y su detalle se muestra a la emprendedora. La reconexión inicia el reintento y también se intenta sincronizar al abrir el espacio. Falta probar este ciclo en un dispositivo con sesión real. Las operaciones que requieren cargar fotos y la publicación remota de productos nuevos no están cubiertas por la cola.

A18 y A19 registran la venta con producto, variante cuando corresponde y cantidad; la API descuenta en transacción únicamente el stock elegido. Se corrigió ventas y ajustes para no volver a publicar productos marcados no disponibles y se añadió una prueba de regresión. A20 y A21 conservan el interruptor del historial y los datos de precio, fecha y lugar. A22 obtiene todos los registros ordenados por fecha y permite filtrar por rango, feria/lugar y producto.

`backend npm.cmd run typecheck` y `git diff --check` pasan. La compilación Android no se pudo verificar en esta sesión: Gradle inicia, pero no establece su canal IPC con el daemon dentro del entorno. No se ejecutaron suites de pruebas ni se hizo commit/push.

## Revisión del alta de productos A01–A11 (2026-11-11)

Se corrigió el guardado de productos cuyo tipo permite variantes pero para los que la emprendedora elige «Este producto no tiene variantes»: la capa Android antes infería `tieneVariantes` solo desde la definición del tipo, enviaba el precio base de forma incompatible y la API rechazaba el alta. Ahora envía la elección explícita y, cuando no hay variantes, manda precio base y stock inicial.

El paso de datos ya no exige un precio base antes de que se elija el tipo; explica que cada variante tendrá su precio y valida el precio base en el paso de variantes cuando corresponde. La descripción requerida se valida al continuar y al guardar. Se añadieron mensajes cuando no hay categorías o tipos activos.

Las fotos elegidas o tomadas se copian al almacenamiento privado antes de enlazarlas al borrador. Si la copia falla, se conserva la imagen anterior y se muestra el error; si no hay app de cámara disponible, el flujo no se cierra inesperadamente. Las fotos locales se limpian solo cuando termina el guardado completo y el borrador conserva los identificadores necesarios para reintentar tras reiniciar la app. El paso de foto permite reemplazar una imagen con cámara o galería, y la revisión resume control de stock, comportamiento al agotarse y disponibilidad inicial.

La app pasa `:app:assembleDebug` y el backend pasa `npm.cmd run typecheck`. No se pudo ejecutar `npm.cmd run test:productos` en esta sesión: el `npm.cmd` del entorno usa Node 20.20.2, pero el backend requiere Node 24.15 o superior y Node 20 no puede cargar directamente esos archivos `.ts`. La prueba manual con cámara, galería, cierre forzado y una sesión real sigue pendiente.

## Mockups

Los mockups 07 y 08 guían el paso de variantes y el paso final de disponibilidad del alta; los mockups 10–12 guían Organización del catálogo, Historial de encargos y Configuración/vista previa. El editor ofrece modalidad, control de stock, las tres políticas al agotarse y ahora selección/guardado de foto por variante. Los textos y ejemplos ilustrativos no se consideran datos reales ni sustituyen los contratos anteriores.
