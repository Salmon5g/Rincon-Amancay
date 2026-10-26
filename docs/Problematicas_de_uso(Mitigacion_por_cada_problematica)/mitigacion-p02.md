# P02 — A la emprendedora le cuesta redactar la descripción

## Problema delimitado

La emprendedora puede explicar fácilmente su producto hablando, especialmente cuando conversa con una clienta por WhatsApp, pero escribir esa misma información en el teclado del teléfono le resulta difícil y lento. Debe decidir qué decir, corregir errores, revisar la escritura y completar un texto sin tener necesariamente el hábito de redactar descripciones para vender.

Por ejemplo, puede escribir “chaleco” como nombre y “lana” como descripción, pero detenerse antes de explicar que es de lana de oveja, que tiene talla única y que se puede realizar por encargo en otros colores. En ese caso, la ficha queda incompleta y la persona interesada debe escribirle para obtener información que podría estar publicada directamente en la tienda.

## Mitigación propuesta

La aplicación debe ayudar a completar la información del producto sin exigir que la emprendedora redacte un párrafo completo desde cero.

1. **Descripción mediante preguntas simples.** En lugar de mostrar solamente un campo vacío llamado “Descripción”, cambiamos ciertas cosas para que se vea mas interactivo y claro, pueden ser preguntas breves como “¿De qué está hecho?”, “¿Qué tamaño o talla tiene?” y “¿Se puede encargar en otro color?”. Donde la emprendedora pueda seleccionar respuestas cortas mediante botones (para agilizar el proceso), y así al terminar,  la aplicación reúne esas respuestas en una descripción ordenada.

2. **Uso de opciones para seleccionar.** Para la información más frecuente, podemos implementar botones con alternativas listas para elegir, como lana, algodón, madera, cerámica, talla única, otros colores o diferentes tamaños. Esto reduce la cantidad de texto que debe escribir y permite que la ficha quede más clara y consistente.

3. **Dictado por voz.** La emprendedora puede explicar el producto hablando, de una manera similar a como enviaría un audio por WhatsApp. La aplicación puede utilizar el dictado del teléfono para transformar sus palabras en texto. Antes de publicar, debe mostrar lo que entendió para que ella pueda aceptarlo, modificarlo o intentarlo nuevamente.


## Problemas que pueden surgir

- El dictado por voz puede cometer errores, especialmente con nombres de materiales, lugares o palabras propias de cada emprendimiento. Por eso, siempre debe existir una pantalla de revisión antes de publicar.
- No todas las emprendedoras tendrán activado el micrófono o se sentirán cómodas hablando frente al teléfono. La escritura y las opciones seleccionables deben mantenerse como alternativas.
- Las preguntas y opciones iniciales pueden no representar todos los tipos de productos de la asociación. Será necesario confirmar en la visita qué información necesitan las emprendedoras de alimentos, textiles, cerámica, plantas u otros rubros.
- La generación automática de la descripción no reemplaza la decisión de la emprendedora. Ella debe poder cambiar, eliminar o dejar fuera cualquier información sugerida.

## Comprobación de aceptación

1. Crear un producto de ejemplo, como un chaleco tejido, y comprobar que la emprendedora pueda completar la información respondiendo preguntas simples sin tener que escribir un párrafo completo.
2. Seleccionar material, talla, color y posibilidad de encargo, y confirmar que esos datos aparezcan en la descripción preliminar.
3. Usar el dictado por voz para explicar el producto y verificar que el texto transcrito pueda revisarse antes de continuar.
4. Modificar una palabra de la descripción generada y comprobar que la emprendedora pueda corregirla sin tener que volver a completar todo el formulario.
5. Probar el flujo con una emprendedora adulta mayor y observar si entiende las preguntas, los botones y la descripción final sin recibir instrucciones sobre qué debe tocar.

