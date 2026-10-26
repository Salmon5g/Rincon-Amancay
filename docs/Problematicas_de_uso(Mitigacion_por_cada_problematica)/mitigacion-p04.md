# P04 — Quien compra no va a instalar la aplicación

## Problema delimitado

La emprendedora difunde sus productos por WhatsApp, Facebook o en la feria. La persona que recibe un enlace quiere entender rápidamente qué se vende, cuánto cuesta, si está disponible y cómo consultar. Si para responder esas preguntas debe instalar Rincón Amancay, crear una cuenta o pedir fotos por separado, vuelve al chat y la emprendedora mantiene información duplicada.

## Mitigación propuesta

La persona compradora debe poder abrir una ficha pública desde el navegador del teléfono, sin instalar la app y sin iniciar sesión.

1. **Enlace directo desde la web.** El catálogo comprador acepta `tienda`, `producto` y `variante` como parámetros de `/comprador`. Para los productos que las reglas actuales permiten leer, abre la tienda y la ficha desde Firestore, selecciona la variante compartida y muestra fotos disponibles, precio, descripción y disponibilidad. La ruta se puede copiar, pegar y volver a abrir.
2. **Compartir desde la ficha.** “Compartir producto” prepara el nombre, la tienda, la variante seleccionada, el precio visible y la disponibilidad junto al enlace directo. “Consultar por WhatsApp” prepara una pregunta sobre precio y disponibilidad. En teléfonos se abre el menú para elegir WhatsApp y el chat o grupo; en navegadores sin ese menú se copia el mensaje y el enlace para pegarlos.
3. **QR comprensible.** El QR de la vista pública de Android abre el catálogo comprador publicado en Firebase Hosting. El texto junto al QR explica que se apunta la cámara normal del celular, que se abrirá el navegador y que no hace falta instalar la app ni iniciar sesión. El mensaje compartido desde Android incluye los datos de referencia del producto y el enlace al catálogo.
4. **Uso público y accesible.** El catálogo sigue siendo de solo lectura y las reglas de Firestore permiten leer únicamente tiendas y productos publicados. Ninguna medida depende de IA. Si no está disponible el menú para compartir, se puede copiar el texto o abrir directamente el enlace en el navegador.

## Lo que esta mitigación todavía no resuelve

- Los productos de la app Android combinada se guardan en memoria y sus identificadores no corresponden todavía a documentos públicos de Firestore. Por eso el QR de esa vista abre el directorio general y no puede llegar a la ficha exacta de ese producto. El enlace profundo exacto funciona para productos que ya estén publicados en Firestore y se compartan desde la web.
- Hoy las reglas bloquean la lectura de productos y variantes cuando `mostrarPrecios` es `false`, para no exponer precios que la dueña quiere ocultar. El contrato deja la consulta sin precio para un siguiente incremento. Para cumplir ese caso de P04, primero hay que publicar una proyección sin campos de precio y permitir su lectura pública de forma segura; no se debe relajar la regla sobre los datos actuales.
- El modelo público no tiene un número de WhatsApp de la emprendedora con consentimiento explícito. La consulta se comparte para que quien compra elija el chat o grupo donde encontró la tienda; no se afirma que el sistema la envíe directamente a la vendedora. El número solo debería agregarse después de acordar su publicación con la asociación.
- La vista previa automática que WhatsApp genera al pegar un enlace puede mostrar el título general de la web, porque Hosting publica archivos estáticos. El mensaje preparado sí incluye los datos del producto; una vista previa social distinta por producto requiere metadatos generados por servidor.
- La foto solo aparecerá si fue subida a Storage y está incluida en la proyección pública del producto. La ficha ofrece un estado alternativo si no hay una imagen accesible.

## Comprobación de aceptación

1. Compartir desde la web una tienda, un producto con variantes y una variante específica. Abrir cada URL en una ventana privada de un teléfono: debe mostrar el destino correcto sin login ni instalación.
2. Confirmar que el detalle indique nombre, precio o “Consultar precio en la tienda”, disponibilidad y variante; si hay imagen pública debe cargar.
3. Tocar “Consultar por WhatsApp”. En el menú del teléfono, elegir WhatsApp y un chat de prueba. El borrador debe identificar el producto y preguntar por su precio/disponibilidad; la persona confirma el envío dentro de WhatsApp.
4. En un navegador sin menú de compartir, comprobar que el mensaje y enlace se copien y que la interfaz indique dónde pegarlos.
5. Escanear el QR de Android con la cámara normal. Antes de abrirlo, la instrucción debe explicar qué ocurrirá y que no se necesita instalar la app ni iniciar sesión. La limitación de que abre el directorio general debe comunicarse hasta que la app comparta IDs de Firestore.
6. Probar una tienda o producto retirado: las reglas deben impedir su lectura y la web debe explicar que ya no está disponible.

## Implementación en esta revisión

- La web comprador restaura enlaces directos a tienda, producto y variante, y ofrece compartir la ficha o preparar una consulta.
- Android reemplaza el dominio ficticio por la URL publicada de Firebase Hosting, añade información del producto al texto compartido y explica el uso del QR.
- No se desplegaron cambios ni se modificaron datos de Firebase. Para que la web publicada refleje los cambios, hace falta publicar Hosting después de revisar y aceptar esta modificación.
