# P03 — El día de la feria el catálogo queda desfasado

## Problema delimitado

La emprendedora vende en persona durante la feria, a veces en momentos de mucho movimiento y con el teléfono guardado o sin buena señal. Quiere que el catálogo muestre qué productos todavía tiene, pero si para registrar cada venta debe abrir varias pantallas o esperar a que cargue la aplicación, probablemente lo deje para después. Mientras tanto, el catálogo puede seguir ofreciendo productos que ya se vendieron.

## Mitigación propuesta

La aplicación debería permitir actualizar la disponibilidad con pocos pasos, incluso cuando la conexión sea inestable, y dejar claro al comprador cuándo se revisó la información.

1. **Registrar una venta con un toque.** En el modo emprendedora, cada producto tendría un botón sencillo como “Vendí 1” y una opción para elegir otra cantidad. La aplicación restaría lo vendido y guardaría el cambio en el teléfono si no hay internet. Cuando vuelva la conexión, enviaría el cambio al servidor para actualizar el catálogo comprador. Para que esto funcione sin problemas, convendría que una sola persona registre las ventas desde un teléfono principal.
2. **Mostrar el estado del producto y la última actualización.** Cada producto podría indicar “Disponible”, “Quedan pocas unidades”, “Agotado” o “A pedido”. También mostraría cuándo se actualizó por última vez. Si la información lleva varias horas sin revisarse, el comprador podría confirmarla por WhatsApp antes de ir a la feria. Así se evita dar a entender que la cantidad está garantizada cuando podría haber cambiado.
3. **Agregar un modo feria y una revisión al cierre.** Antes de empezar, la emprendedora anotaría cuántas unidades lleva. Durante la feria podría marcar las ventas con botones simples y, al final, revisar cuántas quedan y corregir el total si se le pasó registrar alguna. Esto permitiría ordenar el inventario del día sin depender de hacer un registro largo por cada venta.

## Lo que esta mitigación todavía no resuelve

- La aplicación Android combinada que está en el repositorio usa datos de demostración guardados en memoria. No los comparte entre teléfonos ni los conserva al cerrar la app; por eso, antes de que el catálogo comprador vea cambios reales, habría que conectar ambos modos con un servidor compartido y guardar ahí los productos y las ventas.
- Si el teléfono no tiene internet, los cambios guardados en ese equipo no se reflejarán de inmediato para quien mira el catálogo desde otro celular. Mostrar la hora de la última actualización y ofrecer una consulta por WhatsApp ayuda a explicar esa demora, pero no la elimina.
- Registrar las ventas sigue dependiendo de que la emprendedora tenga un momento para tocar el botón. El diseño debe ser rápido y fácil de entender, pero no puede asegurar que cada venta se registre.
- Si varias personas actualizan el mismo producto desde distintos teléfonos sin sincronización, podrían quedar cantidades distintas. La primera versión debería usar un teléfono principal o definir cómo se combinarán los cambios pendientes.

## Comprobación de aceptación

1. Durante una prueba de feria, registrar una venta de un producto con un toque. La cantidad disponible debe disminuir en una unidad y el resto de los productos no debe cambiar.
2. Elegir una cantidad mayor y comprobar que la aplicación descuente exactamente las unidades indicadas.
3. Activar el modo avión, registrar una venta y cerrar y volver a abrir la aplicación. En una versión con guardado local, el cambio debe seguir pendiente y no perderse.
4. Recuperar la conexión y comprobar que el cambio pendiente llegue al servidor y aparezca en el catálogo comprador. No debe descontarse dos veces si se reintenta el envío.
5. Revisar que cada producto muestre claramente su estado y la hora de la última actualización. Si la información está antigua, debe invitar a confirmar disponibilidad antes de trasladarse.
6. Pedir a una emprendedora que registre una venta de prueba sin ayuda. Debe poder encontrar el producto y actualizarlo rápidamente desde el teléfono.

## Implementación en esta revisión

- Esta propuesta describe tres alternativas teóricas; no se implementó ninguna en el código.
- La opción más sencilla para comenzar sería mostrar el estado del producto y la hora de actualización. Después se podría agregar el registro rápido de ventas y la sincronización con un servidor compartido.
- No se desplegaron cambios ni se modificaron datos del proyecto.

