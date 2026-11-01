# P03 — El día de la feria el catálogo queda desfasado

## Problema delimitado

La emprendedora vende en persona durante la feria, a veces en momentos de mucho movimiento y con el teléfono guardado o sin buena señal. Quiere que el catálogo muestre qué productos todavía tiene, pero si para registrar cada venta debe abrir varias pantallas o esperar a que cargue la aplicación, probablemente lo deje para después. Mientras tanto, el catálogo puede seguir ofreciendo productos que ya se vendieron.

## Mitigación propuesta

La aplicación debería permitir actualizar la disponibilidad con pocos pasos, incluso cuando la conexión sea inestable, y dejar claro al comprador cuándo se revisó la información.

1. **Registrar una venta con un toque.** En el modo emprendedora, cada producto tendría un botón sencillo como “Vendí 1” y una opción para elegir otra cantidad. La aplicación restaría lo vendido y guardaría el cambio en el teléfono si no hay internet. Cuando vuelva la conexión, enviaría el cambio al servidor para actualizar el catálogo comprador. Para que esto funcione sin problemas, convendría que una sola persona registre las ventas desde un teléfono principal.
2. **Mostrar el estado del producto y la última actualización.** Cada producto podría indicar “Disponible”, “Quedan pocas unidades”, “Agotado” o “A pedido”. También mostraría cuándo se actualizó por última vez. Si la información lleva varias horas sin revisarse, el comprador podría confirmarla por WhatsApp antes de ir a la feria. Así se evita dar a entender que la cantidad está garantizada cuando podría haber cambiado.
3. **Agregar un modo feria y una revisión al cierre.** Antes de empezar, la emprendedora anotaría cuántas unidades lleva. Durante la feria podría marcar las ventas con botones simples y, al final, revisar cuántas quedan y corregir el total si se le pasó registrar alguna. Esto permitiría ordenar el inventario del día sin depender de hacer un registro largo por cada venta.

## Lo que esta mitigación todavía no resuelve

- Android ya conecta la cuenta emprendedora, su tienda y el catálogo privado con Firebase; edición de productos y ajustes de stock pasan por la API local. Un ajuste de stock no registra una venta: la pantalla de ventas del código anterior todavía no está conectada al nuevo repositorio. La API local debe estar ejecutándose para estas escrituras; aún no hay URL HTTPS compartida. Los borradores de producto se conservan en Firestore, pero no se publican sin una imagen validada.
- Si el teléfono no tiene internet, los cambios guardados en ese equipo no se reflejarán de inmediato para quien mira el catálogo desde otro celular. Mostrar la hora de la última actualización y ofrecer una consulta por WhatsApp ayuda a explicar esa demora, pero no la elimina.
- Registrar las ventas sigue dependiendo de que la emprendedora tenga un momento para tocar el botón. El diseño debe ser rápido y fácil de entender, pero no puede asegurar que cada venta se registre.
- Si varias personas actualizan el mismo producto, la API detecta cambios por versión y rechaza una solicitud obsoleta; la emprendedora debe recargar y revisar antes de reintentar. No hay cola offline ni registro de venta rápido, por lo que una mala conexión sigue impidiendo actualizar desde la app.

## Comprobación de aceptación

1. Durante una prueba de feria, registrar una venta de un producto con un toque. La cantidad disponible debe disminuir en una unidad y el resto de los productos no debe cambiar.
2. Elegir una cantidad mayor y comprobar que la aplicación descuente exactamente las unidades indicadas.
3. Activar el modo avión, registrar una venta y cerrar y volver a abrir la aplicación. En una versión con guardado local, el cambio debe seguir pendiente y no perderse.
4. Recuperar la conexión y comprobar que el cambio pendiente llegue al servidor y aparezca en el catálogo comprador. No debe descontarse dos veces si se reintenta el envío.
5. Revisar que cada producto muestre claramente su estado y la hora de la última actualización. Si la información está antigua, debe invitar a confirmar disponibilidad antes de trasladarse.
6. Pedir a una emprendedora que registre una venta de prueba sin ayuda. Debe poder encontrar el producto y actualizarlo rápidamente desde el teléfono.

## Implementación en esta revisión

- La app muestra el catálogo privado y permite ajustar stock con motivo; no implementa el botón de venta rápida, modo feria, hora entendible de última revisión ni sincronización offline descritos aquí.
- El ajuste de stock ya persiste mediante la API local y actualiza disponibilidad pública desde la última ficha aprobada. Para que esto sea compartido, la API debe estar encendida en el mismo computador o desplegarse más adelante.
- No se modificaron datos de Firebase ni se desplegó la API.

