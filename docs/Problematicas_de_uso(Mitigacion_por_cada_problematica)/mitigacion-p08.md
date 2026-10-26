# P08 — La accesibilidad de los textos y botones


## Problema delimitado

La aplicación permite gestionar productos desde un dispositivo Android, pero los textos pequeños y los botones con áreas táctiles reducidas pueden dificultar su uso, especialmente al leer información o intentar realizar acciones como agregar, editar o eliminar productos. Si las opciones principales no son fáciles de identificar o pulsar, la persona usuaria puede equivocarse, tardar más en completar una tarea o necesitar ayuda para utilizar la aplicación.

## Mitigación propuesta

La interfaz debe permitir que la persona usuaria identifique las acciones principales, lea sus textos y pulse los controles sin dificultad innecesaria.

1. **Aumentar el tamaño de los textos.** Revisar los textos de las pantallas principales y de gestión de productos para que sean legibles en un teléfono. Los nombres de las acciones y la información importante deben distinguirse del contenido secundario.
2. **Ampliar las áreas táctiles.** Los botones principales y las acciones de gestión de productos deben disponer de un área de pulsación suficiente para facilitar su uso desde una pantalla táctil y reducir los errores al seleccionarlos.
3. **Hacer más visibles las acciones de los productos.** Las opciones para agregar, editar y eliminar productos deben identificarse claramente mediante textos comprensibles y controles fáciles de localizar. La persona usuaria no debería tener que adivinar la función de un botón.
4. **Mantener una interfaz coherente.** Los botones principales deben conservar criterios similares de tamaño, presentación y ubicación entre las pantallas. Esto permite reconocer patrones y reduce la necesidad de aprender nuevamente cómo utilizar cada sección.
5. **Comprobar la accesibilidad mediante pruebas.** No basta con aumentar el tamaño de los textos o botones en el código. Es necesario ejecutar la aplicación en un dispositivo o emulador y comprobar que los elementos se leen correctamente, se pueden pulsar y no quedan cortados ni se superponen con otros componentes.

## Lo que esta mitigación todavía no resuelve

- Aumentar el tamaño de los textos y las áreas táctiles no garantiza por sí solo que toda la aplicación sea accesible.
- La legibilidad puede variar según el tamaño de pantalla, la resolución y la configuración de tamaño de fuente del dispositivo.
- Es necesario comprobar que los botones ampliados no provoquen superposiciones, desbordamientos o problemas de distribución.
- Esta mitigación no confirma que la aplicación disponga de compatibilidad completa con lectores de pantalla, navegación por teclado u otras tecnologías de asistencia.
- La mejora visual no resuelve por sí sola los problemas de persistencia de datos, sincronización con Firebase ni otras limitaciones funcionales de la aplicación.

## Comprobación de aceptación

1. Abrir la aplicación en Android Studio mediante un emulador o en un dispositivo Android.
2. Revisar las pantallas principales y comprobar que los textos importantes sean legibles.
3. Entrar en la gestión de productos e identificar las acciones para agregar, editar y eliminar.
4. Pulsar los botones principales y comprobar que respondan correctamente sin necesidad de realizar varios intentos.
5. Revisar que los textos no queden cortados y que los botones no se superpongan entre sí.
6. Repetir las pruebas con un tamaño de fuente mayor en la configuración del dispositivo y comprobar que la interfaz siga siendo utilizable.
7. Confirmar que las mejoras de accesibilidad no hayan eliminado ni alterado las funciones que ya existían en la aplicación.

## Implementación en esta revisión

- **Objetivo de los cambios:** mejorar la legibilidad de los textos y facilitar la interacción con los botones principales y las acciones de gestión de productos.
- **Archivos involucrados:** la implementación de la interfaz se relaciona con `AmancayScreens.kt`, dentro del proyecto Android. Debe verificarse qué modificaciones quedaron incorporadas en la versión final.
- **Estado de validación:** la compilación y las pruebas de aceptación deben confirmarse antes de declarar la mitigación como completada.
- **Firebase y despliegue:** esta ficha no acredita cambios en Firebase ni un despliegue de la aplicación.
