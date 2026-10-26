# P06 — Los números cuestan tanto como el texto

*Borrador de ficha de mitigación, basado en el formato de P04.*

## Problema delimitado

La emprendedora maneja precio, unidades y resultado del día en la memoria o en un cuaderno. Pasarlos a la ficha choca con el formato (punto de miles, peso), con la duda de dejar un precio fijo cuando a veces cobra distinto según la persona, y con la falta de hábito de anotar cada venta en el momento. Si escribir el precio es confuso, anotar una venta toma muchos pasos o el resultado del día llega como una tabla, vuelve al cuaderno y el catálogo queda desconectado de lo que realmente vende.

## Mitigación propuesta

La emprendedora debe poder escribir un precio sin pensar en el formato, registrar una venta con un toque y leer el resultado del día en una frase.

1. **Precio con formato asistido y vista previa.** El campo de precio abre el teclado numérico, acepta solo dígitos y muestra el punto de miles mientras se escribe (`2500` → `2.500`). Se guarda siempre como número entero en pesos, nunca como texto con formato. Debajo del campo aparece "Así se verá: $2.500", tal como lo verá quien compra, para que no quede duda antes de publicar.
2. **Precio visible, oculto o ajustable.** Cada producto conserva un precio de lista, editable en cualquier momento. Respetando `mostrarPrecios`, la dueña puede ocultarlo y mostrar "Consultar precio en la tienda" sin sacar el producto del catálogo, de modo que no complique la relación con la clientela de siempre. Al registrar una venta, el precio de lista viene por defecto, pero puede cambiarse en ese momento ("otro precio") sin modificar el precio publicado.
3. **Registro de venta en un toque.** Cada producto tiene un botón grande "+1" y otro "−1" para corregir. Una venta guarda producto, precio cobrado y hora. Funciona desde el celular, sin iniciar sesión y sin depender de la señal de la feria; los datos se guardan en el dispositivo y no se pierden si se cierra la app.
4. **Resumen del día en una frase.** La emprendedora ingresa una sola vez cuánto le cuesta reponer los ingredientes (por unidad o por tanda). Con eso, el resumen dice algo como: "Vendiste 10 queques, ingresaste $25.000 y te quedaron $11.000 después de reponer. ¡Alcanzó!". Se muestra en verde si alcanzó y en rojo si no, con un número grande, una frase y un color, sin tablas ni gráficos.
5. **Uso accesible y sin IA.** Ninguna medida depende de IA. Los montos usan formato chileno (`es-CL`, sin decimales) y el color del resumen siempre va acompañado de texto, para que no dependa solo de distinguir colores.

## Lo que esta mitigación todavía no resuelve

- Los productos de la app Android combinada se guardan en memoria, por lo que las ventas registradas ahí no persisten ni llegan a Firestore. Mientras no haya persistencia, el registro del día solo existe en el dispositivo.
- El costo de reposición es una cifra aproximada que ingresa la dueña. No calcula costos reales por receta, ingrediente ni merma; responde "¿alcanzó?", no "¿cuánto gané exactamente?".
- Las ventas con precio distinto se registran, pero la app no sugiere ni valida el precio cobrado. La decisión de a quién cobrar qué sigue siendo solo de ella.
- Registrar sigue dependiendo del hábito. El botón baja el costo de anotar, pero no garantiza que lo use en plena feria; hay que observarlo con ella.
- Las ventas guardadas solo en el dispositivo se pierden si cambia de celular o borra los datos. Un respaldo en la nube requiere definir qué datos privados se guardan y con qué reglas, sin mezclarlos con la proyección pública del catálogo.
- El resumen no distingue ventas de distintos días ni puntos de venta (feria vs. casa) más allá de la fecha.

## Comprobación de aceptación

1. Escribir `2500` en el campo de precio desde un teléfono: debe aparecer el teclado numérico, mostrarse `2.500` y la vista previa "Así se verá: $2.500". Al guardar, el valor almacenado debe ser el entero 2500.
2. Intentar escribir letras, espacios o símbolos: no deben aceptarse ni romper el formato.
3. Ocultar el precio de un producto y abrir su ficha pública: debe mostrar "Consultar precio en la tienda" y el producto debe seguir en el catálogo.
4. Registrar una venta al precio de lista y otra con "otro precio": el precio de lista publicado no debe cambiar y ambas ventas deben quedar con su monto real.
5. Tocar "+1" tres veces y "−1" una vez: el contador debe marcar 2 y el total debe coincidir.
6. Poner el teléfono en modo avión, registrar ventas y cerrar la app: al volver a abrirla, las ventas deben seguir ahí.
7. Con 10 queques a $2.500 y reposición de $14.000, el resumen debe decir que ingresó $25.000, que le quedaron $11.000 y que alcanzó. Repetir con ventas insuficientes y confirmar que aparece "No alcanzó a reponer" en rojo.
8. Pedirle a una persona real que registre una venta y lea el resumen sin ayuda: debe lograrlo en pocos segundos y sin preguntar qué significa algo.

## Implementación en esta revisión

- Esta ficha es la propuesta de mitigación; el código aún no se ha implementado.
- No se desplegaron cambios ni se modificaron datos de Firebase.
