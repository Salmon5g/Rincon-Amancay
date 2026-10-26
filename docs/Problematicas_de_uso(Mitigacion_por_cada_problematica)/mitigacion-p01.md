# P01 — La foto del producto sale mal

Propuesta de diseño para que la emprendedora logre una foto presentable de su producto con el solo celular, sin caja de luz, sin trípode y sin criterio fotográfico. No implementa: define qué debe existir en la cámara y en el flujo de ficha de la app.

## Problema delimitado

La emprendedora toma la foto del producto cuando ya está listo, de noche, apoyado en la mesa de la cocina bajo la luz del fluorescente: una mano sostiene el frasco y la otra el teléfono. La cámara del celular está hecha para escenas cotidianas, no para retratar un objeto pequeño sobre una mesa: decide el foco y la exposición por su cuenta y enfoca el mantel en vez de la etiqueta.

- **Quién:** mujer adulta, dueña de casa, con poca formación tecnológica; no tiene equipamiento de foto ni costumbre de retratar objetos.
- **Cuándo:** al crear la ficha de un producto o agregarle la foto, casi siempre la noche anterior a la feria, con las manos ocupadas.
- **Qué quiere lograr:** que quien recibe el enlace entienda en la primera mirada qué se vende (qué es, cuánto trae y que la etiqueta se lea) y se fíe del producto.
- **Qué se lo impide:** no sabe cómo repetir la foto para que salga mejor, la cámara decide por ella y entonces la sube igual. Esa misma imagen es la que después viaja a WhatsApp y a las redes.

El problema no es solo la foto en sí, sino que no exista un momento en que alguien le muestre qué sale mal y cómo repetirla: ella publica lo que tiene y descubre por los comentarios del grupo que el frasco no se entiende.

## Mitigación propuesta

La app no puede darle luz a su cocina, pero puede convertir "subir la foto que salió" en "elegir la mejor foto posible en ese momento".

1. **Ejemplo de foto buena y foto mala antes de la cámara.** La primera vez que toca "Tomar foto", la app muestra dos imágenes grandes con el ejemplo del caso: "Así sí" (producto solo en la mesa, luz de ventana o sala, etiqueta mirando al teléfono) y "Así no" (objeto apretado entre cosas, fondo de cocina, oscura). Con un botón "No volver a mostrar" para no repetir la explicación en cada producto.
2. **Cámara preparada para el objeto.** La cámara interna de la app muestra un recuadro guía donde va a ir el producto ("deja la mermelada dentro de este cuadro") y permite tocar la etiqueta para afilar el foco ahí y no en el mantel. Un texto corto, en español simple, recuerda lo esencial: "acércate al frasco en vez de dejarlo lejos" y "si está a oscuras, usa la luz de una ventana o prende el flash". El recuadro asegura además que la foto salga con un formato único, útil para la vitrina y para compartir (P09).
3. **Chequeo simple de la foto, sin IA.** Al revisar la foto antes de guardar, la app mide dos cosas con un cálculo simple sobre la imagen: si está oscura (brillo promedio bajo) y si parece movida (bordes imprecisos). Si detecta algo, muestra un aviso con dos opciones: "La foto salió oscura. ¿La tomas otra vez con más luz? → Repetir o Usar igual". Nunca bloquea: si ella dice "usar igual", se guarda. Este aviso cumple la misma función que las "observaciones de la foto" de RF-IA-01, pero sin depender de fondos para IA.
4. **Corrección mínima y vista previa antes de publicar.** En la revisión hay un solo control de brillo (un deslizador que ve el efecto al instante) y, debajo, la tarjeta tal como la verá quien compra: "Así la ve quien compra". Esto la saca de la duda de si la foto alcanza o no, sin entender nada de fotografía. Antes de subir, la imagen se comprime para gastar pocos datos (RNF-REN-01).
5. **Repetir la foto nunca cuesta nada.** En la ficha del producto guardado siempre existe "Repetir foto", disponible cuando quiera, incluso días después. Al repetir se rescata todo lo demás: el nombre, el precio y la descripción no se tocan ni se pierden (RNF-USA-04). La foto tampoco obliga a completar la ficha: si ninguna salió bien, puede guardar y agregarla otro día, para no abandonar el flujo a medias (se relaciona con P05).
6. **La web como segunda oportunidad.** Si las fotos del teléfono no resultan, en la web (RF-TIE-02) se puede subir una imagen desde el galería de un computador, por ejemplo con ayuda de un familiar, y la foto nueva reemplaza la anterior sin rehacer la ficha.

**Sin IA:** todo lo anterior funciona sin asistencia de IA (RN-13). El chequeo de oscuridad y enfoque es un cálculo determinista sobre la imagen, no un modelo. Si hay fondos, RF-IA-01 puede sumar observaciones más finas ("la etiqueta sale borrosa", "el producto ocupa poco espacio") que se aceptan o descartan igual que cualquier sugerencia (RF-IA-02); sin fondos, el aviso simple del punto 3 sigue existiendo.

## Lo que esta propuesta todavía no resuelve

- No inventa la luz que no hay: si la cocina de noche es el único lugar y momento, la foto seguirá siendo mediocre. El aviso y el flash mejoran quizás un poco, no reemplazan una buena luz.
- El chequeo simple no entiende la foto: no puede decirle "la etiqueta no se lee", que es el problema del caso, y puede equivocarse (avisar de más o no avisar). La observación fina solo llegaría con IA (RF-IA-01), condicionada a financiamiento.
- No recorta el objeto del fondo: un "fondo blanco automático" necesita una IA de segmentación. Sin fondos, la app solo lo sugiere en el ejemplo "Así sí" y deja el fondo como salió.
- Los consejos sirven si se leen: la dependencia de un familiar (la hija que saca mejores fotos) se reduce, pero no desaparece mientras ella no adopte la rutina. Hay que observarlo con ella.
- La foto ya publicada y ya compartida viaja igual: P09 arma las piezas de WhatsApp y Facebook con la foto que esté en la ficha; una foto mala, compartida mala, no la arregla esta mitigación.
- En la demo actual (app combinada de Android) el flujo de cámara con recuadro, chequeo y vista previa no existe todavía; esta ficha define qué debe existir.

## Comprobación de aceptación

1. Abrir "Tomar foto" por primera vez: aparecen los ejemplos "Así sí" y "Así no"; marcar "No volver a mostrar" y comprobar que la vista no reaparece en el siguiente producto.
2. En la cámara interna: el recuadro guía se ve donde va a quedar el producto; tocar la zona de la etiqueta mueve el foco y al revisar la foto la etiqueta está nítida y el mantel desenfocado.
3. Tomar una foto oscura de noche: aparece el aviso "La foto salió oscura. ¿La tomas otra vez con más luz?" con las opciones Repetir y Usar igual; al elegir "Usar igual" la foto se guarda sin bloquear nada.
4. Tomar una foto movida: aparece un aviso Parecido; al elegir "Repetir", el nombre, el precio y la descripción ya completados siguen intactos.
5. Mover el control de brillo: la vista previa cambia al instante; guardar y verificar que la foto quedó comprimida (peso máximo acordado, por ejemplo bajo 500 KB) y que sube con señal de datos regular.
6. Revisar la ficha antes de publicar: la tarjeta "Así la ve quien compra" muestra la foto con el formato final del catálogo y la foto viaja idéntica al compartir (P09).
7. Inhabilitar el módulo de IA y repetir el flujo completo: los avisos de oscuridad y enfoque siguen funcionando y ninguna pantalla pide la IA (RN-13).
8. Validar con una emprendedora de la contraparte, en su casa y con su producto real: termina sola la foto y la ficha, sin que nadie le diga qué tocar, y la etiqueta de su producto se lee en la foto final.

## Relación con lo ya definido

- **RF-PRO-01** (flujo guiado): la foto es el primer paso de la creación de un producto, y ahí viven las medidas 1 a 5.
- **RF-IA-01/RF-IA-02** (condicionado): las observaciones finas de la foto solo llegan con fondos; el chequeo simple del punto 3 es su alternativa sin IA.
- **RNF-REN-01** (compresión de imágenes): la foto se comprime antes de subir, porque la señal y el plan de datos son limitados.
- **RNF-USA-04** (no perder lo ingresado): repetir la foto nunca borra el resto de la ficha.
- **RNF-USA-01** (lenguaje simple): los avisos usan las palabras del caso ("oscura", "movida"), no vocabulario técnico.
- **RN-13** (funcionar completa sin IA): toda la mitigación se sostiene sin IA.
- **P09**: la pieza para WhatsApp y redes se arma con la foto de la ficha; una buena foto ahí es una foto buena en todo. (Ver el punto "No arregla la foto (P01)" en `P09-Solucion-Redes.md`.)

> Documento de propuesta de diseño. No implementa la solución: define qué debe existir para que la emprendedora tome, revise y publique la mejor foto posible de su producto desde la casa y sin ayuda.
