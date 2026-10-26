# P09 — No maneja las redes donde la podrían encontrar

Propuesta de diseño para la emprendedora de Puerto Montt que vende por WhatsApp, feria y redes. No implementa: define qué debe existir para que pueda publicar sola, desde el celular y sin ayuda de un familiar.

## Problema delimitado

La emprendedora ya tiene WhatsApp, Facebook e Instagram, pero están dispersos, desactualizados y dependen de un familiar. Cada canal le exige volver a escribir y decidir desde cero lo que ya cargó en la ficha.

- **Quién:** mujer adulta, sin computador y con poca formación tecnológica; vende alimentos o tejidos en la feria, por WhatsApp y a veces por redes.
- **Cuándo:** al terminar de cargar un producto, de noche, y el día de la feria cuando quiere avisar que va a estar.
- **Qué se lo impide:** cada canal pide un formato distinto, no recuerda cómo entrar a las cuentas creadas por un familiar y no tiene rutina de qué publicar dónde.

Hoy la ficha termina en la plataforma y no produce una pieza lista para publicar; duplicar el trabajo cuesta más que el beneficio. Los canales que no puede mantener quedan abandonados y quien no está en el grupo de la asociación no se entera de la feria ni del producto.

## Mitigación propuesta

Una sola idea: que el producto ya cargado se convierta en una publicación lista para WhatsApp o Facebook, sin escribir nada nuevo.

1. **Compartir desde la ficha (medida principal).** Al publicar o editar, la app arma una pieza con foto, nombre y precio (o «consultar»). La imagen se genera en vertical 9:16, el formato del estado de WhatsApp y las historias, y el texto ya viene escrito con lo esencial y el enlace público de la tienda. «Compartir» abre el menú del teléfono para elegir estado, chat o Facebook. Si la foto no es buena, la pieza se genera igual y se sugiere repetirla; nunca se bloquea la publicación.
2. **Kit de feria.** Pantalla web que abre sin cuenta ni instalación y muestra la pieza del próximo evento: fecha, lugar, qué lleva y enlace de la tienda. Sirve para el grupo de la asociación o como estado, y se actualiza sola si cambia la feria.
3. **Mis canales.** Lista simple con WhatsApp, Facebook (página y quién la maneja) e Instagram. Muestra la última publicación de cada uno, avisa si quedó abandonado y permite declararlo «en pausa» en vez de simular que se mantiene.
4. **Criterio de canales.** Una tarjeta de una frase por canal: estado de WhatsApp para lo del día (imagen 9:16), grupo de la feria para logística (texto y enlace), muro de Facebook para el catálogo (imagen, frase y enlace) e historia de Instagram como recordatorio (misma imagen).

**Flujo:** cargar o editar el producto → tocar «Compartir» → elegir estado, chat o Facebook. Para la feria: abrir el kit, compartir y elegir grupo o estado.

**Sin IA:** las piezas se arman con plantillas fijas y los datos de la ficha («Mermelada de murta, $2.500. Este sábado en la feria de la plaza»). La IA solo mejoraría la redacción; no es un requisito.

## Lo que esta propuesta todavía no resuelve

- No recupera contraseñas ni devuelve cuentas creadas por un familiar.
- No garantiza alcance ni crea audiencia: el algoritmo no depende de la plataforma.
- No publica sola en Instagram: sin credenciales y permisos no hay automatización.
- No arregla la foto (P01) ni la descripción (P02): hereda lo que haya en la ficha.
- No hace que el comprador instale la app (P04): el enlace debe abrir y entenderse sin cuenta.
- Reduce la dependencia de la hija, pero no la elimina.

## Comprobación de aceptación

- Compartir un producto en tres toques o menos, sin escribir texto nuevo, y elegir estado, chat o Facebook.
- Abrir el enlace en una ventana privada: debe entenderse sin instalar nada ni crear cuenta.
- Cambiar el precio y volver a compartir sin ayuda.
- Abrir el kit sin sesión, comprobar fecha, lugar, qué lleva y enlace, y verificar que se actualiza al cambiar la feria.
- Declarar un canal «en pausa» y comprobar que la app avisa cuando otro queda abandonado.
- Verificar que la página de Facebook ya no muestra la feria de marzo.
- Comprobar que una foto deficiente no bloquea la publicación.

## Relación con lo ya definido

Se apoya en el flujo guiado de ficha y en la vista pública que se comparte, y toca los requisitos de usabilidad y compresión de imágenes ya definidos. Falta confirmar en la ficha del proyecto el requisito o riesgo de P09; si no existe, corresponde proponer «compartir sin reescribir».

> Documento de propuesta de diseño. No implementa la solución: define qué debe existir para que la emprendedora publique en sus redes sin ayuda y sin volver a escribir lo que ya cargó.
