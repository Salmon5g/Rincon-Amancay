# Entrega de emprendedora: catálogo y ventas

## Alcance revisado

La app unificada es el destino de esta entrega. La sesión de emprendedora valida Firebase Auth, rol y tienda. Los productos se leen de Firestore y se modifican mediante la API autenticada local. Las pantallas legadas de ventas y ferias siguen usando `DemoAmancayRepository`; sus datos no son persistentes y no sirven como evidencia de cumplimiento.

## Estado por criterio

| Criterios | Estado actual | Brecha principal |
| --- | --- | --- |
| A01–A05 | Parcialmente implementados | El editor conectado tiene pasos, cámara/galería, datos y campos por tipo. El estado del alta ahora se restaura localmente si se interrumpe; falta comprobar el flujo en dispositivo. |
| A06 | Implementación local inicial | Precio y stock siguen siendo propios; se agregó selección desde cámara/galería y asociación privada de foto por variante, y el publicador genera `imagen`/`miniatura` públicas independientes. La compilación Kotlin pasa; falta comprobación conectada en dispositivo y en la web lectora. |
| A07–A09 | Implementados en el editor | Modalidad, control de stock y las tres políticas de agotamiento se guardan en la ficha privada. |
| A10 | Implementado localmente | Se puede marcar disponible/no disponible desde Organizar catálogo, incluso seleccionando un solo producto. La API autenticada retira la proyección pública al desactivar y conserva la ficha privada para la tienda de la emprendedora. |
| A11 | Implementado localmente | El asistente guarda en almacenamiento privado local los campos, paso actual, atributos, variantes y fotos seleccionadas; al volver a Agregar producto restaura el borrador. Guardar o cancelar explícitamente lo elimina. Falta comprobar en dispositivo tras cierre forzado. |
| A12–A13 | Implementados | Editar usa `editarProducto`; eliminar conserva tombstone privado y retira su proyección pública. |
| A14 | Implementado localmente | Organizar catálogo permite seleccionar productos visibles por categoría o todo el catálogo y aplicar disponibilidad por lotes con control de versión. La API procesa grupos de hasta 20 productos y la app fragmenta selecciones mayores. Falta comprobación con sesión real y web. |
| A15 | Implementado localmente | Configuración guarda modalidad, control de stock y regla de agotamiento en preferencias del dispositivo; el asistente las aplica a cada alta nueva. Aún no se sincronizan entre dispositivos. |
| A16 | Parcial | Existe vista previa de producto, pero no una vista de catálogo que represente el conjunto de políticas de disponibilidad y visibilidad. |
| A17 | Pendiente | Firestore mantiene lecturas en caché, pero las mutaciones del flujo conectado dependen de API en línea; no hay cola de cambios offline con reintento seguro. |
| A18–A19 | Implementado localmente | La sección **Ventas → Registrar venta** captura producto, variante cuando corresponda y cantidad. La API descuenta en una transacción solo la variante vendida (o el stock del producto) y actualiza el catálogo público. Esto funciona aunque el historial esté apagado. |
| A20 | Implementado localmente | El interruptor de historial está en **Más → Configuración y vista previa**; también se conserva un acceso desde Ventas. Apagarlo conserva el historial anterior y no bloquea las ventas ni el descuento de stock. |
| A21 | Implementado localmente | Con el historial activo, la venta conserva precio vigente, fecha y feria/lugar; el nombre de la feria también queda como copia para mantener la referencia histórica. |
| A22 | Implementación local inicial | Historial filtra por fechas, feria/lugar y producto. La primera versión carga hasta 200 ventas recientes; falta paginación para historiales mayores. |

## Secciones de interfaz previstas

Tomando los mockups como referencia, **Productos → Organizar catálogo** usa tarjetas marfil, acentos ciruela, títulos serif, filtros por categoría y controles visibles para disponibilidad; el asistente de alta conserva sus pasos visuales para foto, datos, tipo, variantes, stock y revisión. **Más → Configuración y vista previa** ofrece valores por defecto, el interruptor del historial y una vista previa de producto con modalidad, stock, agotamiento y visibilidad. La opción offline todavía solo guarda una preferencia: falta la caché con cola de sincronización para A17. La vista previa conjunta de todo el catálogo también queda pendiente para cerrar A16.

Estas pantallas deben leer y escribir datos persistidos de la cuenta autenticada. No se deben reutilizar las listas locales de ejemplo como fuente de ventas reales. Para la comprobación web, el flujo de stock de la app debe dejar en la proyección pública los estados que prueban W09–W12; en especial, la última venta de una pieza única debe retirar su ficha pública de manera permanente.

## Dependencias de implementación

Para cerrar los puntos pendientes de 1.2 y completar la verificación, aún se necesitan:

1. Persistir los valores por defecto en la cuenta, no solo en el dispositivo (A15).
2. Paginación del historial para cuentas con más de 200 ventas (A22).
3. Caché/cola local de mutaciones offline, con reintento idempotente y resolución visible de conflictos (A17).
4. Vista previa conjunta del catálogo completo (A16).
5. Comprobación manual en un dispositivo con sesión real y de los resultados de stock en la web.

La venta guarda una feria publicada de la plataforma o texto libre; la fecha se muestra en la zona horaria local y se conserva como instante. Al desactivar el historial no se guardan nuevas ventas históricas, pero el registro manual y el descuento de stock siguen funcionando; las ventas anteriores se conservan. El contrato 07 describe las fotos independientes por variante.

## Revisión de flujos y textos (2026-11-11)

La pantalla de ventas conserva la misma fecha y el mismo identificador durante un reintento, para que una respuesta de red ambigua no repita el descuento. Si la API confirma la venta pero falla la recarga del historial, la app informa que la venta sí quedó guardada y ofrece recargar. El registro espera a leer la configuración del historial antes de habilitarse. El saldo mostrado se toma de la variante seleccionada y no usa el saldo general como respaldo.

Se añadieron estados vacíos para productos, variantes activas e historial, una explicación cuando no hay ferias publicadas, una alerta al ingresar más unidades que el stock elegido y ejemplos para cantidad, lugar, fechas, atributos, variantes y ajustes de stock. El filtro ahora rechaza fechas inválidas o un rango invertido. Se corrigió el rótulo «ESPACIO EMPRENDEDORA» por «ESPACIO DE EMPRENDIMIENTO» y se pluralizan «variante(s)» y los conteos.

La prueba de ventas en Firestore Emulator cubre el descuento exclusivo de la variante elegida, precio/cantidad/fecha/lugar, reintentos idempotentes, stock insuficiente, historial apagado, requisito de lugar, copia del nombre de feria, las tres políticas al agotarse y retirada de una pieza única vendida. La compilación Android y la comprobación TypeScript se registran en `AGENTS.md`.

## Mockups

Los mockups 07 y 08 guían el paso de variantes y el paso final de disponibilidad del alta; los mockups 10–12 guían Organización del catálogo, Historial de encargos y Configuración/vista previa. El editor ofrece modalidad, control de stock, las tres políticas al agotarse y ahora selección/guardado de foto por variante. Los textos y ejemplos ilustrativos no se consideran datos reales ni sustituyen los contratos anteriores.
