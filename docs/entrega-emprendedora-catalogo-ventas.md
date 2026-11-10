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
| A15 | Pendiente | No hay preferencias persistidas de valores por defecto por emprendedora. |
| A16 | Parcial | Existe vista previa de producto, pero no una vista de catálogo que represente el conjunto de políticas de disponibilidad y visibilidad. |
| A17 | Pendiente | Firestore mantiene lecturas en caché, pero las mutaciones del flujo conectado dependen de API en línea; no hay cola de cambios offline con reintento seguro. |
| A18–A21 | Implementación local conectada | Pantalla de venta captura producto, variante, cantidad y lugar; API guarda precio/fecha, descuenta el saldo exacto y sincroniza la disponibilidad pública. El historial se activa desde la app y desactivarlo conserva registros anteriores. |
| A22 | Implementación local inicial | Historial filtra por fechas, feria/lugar y producto. La primera versión carga hasta 200 ventas recientes; falta paginación para historiales mayores. |

## Secciones de interfaz previstas

Tomando los mockups como referencia, **Productos → Organizar catálogo** ya usa tarjetas marfil, acentos ciruela, títulos serif, filtros por categoría y controles visibles para disponibilidad; el asistente de alta conserva sus pasos visuales para foto, datos, tipo, variantes, stock y revisión. Aún falta **Más → Configuración y vista previa** con valores por defecto, disponibilidad sin conexión y vista previa conjunta del catálogo.

Estas pantallas deben leer y escribir datos persistidos de la cuenta autenticada. No se deben reutilizar las listas locales de ejemplo como fuente de ventas reales. Para la comprobación web, el flujo de stock de la app debe dejar en la proyección pública los estados que prueban W09–W12; en especial, la última venta de una pieza única debe retirar su ficha pública de manera permanente.

## Dependencias de implementación

Para completar 1.2 de forma coherente, ya se inició el contrato autenticado para ventas e historial. Aún se necesitan:

1. Preferencias privadas por tienda para historial de ventas y valores por defecto.
2. Paginación del historial para cuentas con más de 200 ventas.
3. Cola local de mutaciones offline, con identificador idempotente y resolución visible de conflictos.

La venta guarda una feria publicada de la plataforma o texto libre; la fecha se muestra en la zona horaria local y se conserva como instante. Desactivar el historial detiene nuevas capturas y conserva las anteriores. El contrato 07 describe las fotos independientes por variante.

## Mockups

Los mockups 07 y 08 guían el paso de variantes y el paso final de disponibilidad del alta; los mockups 10–12 guían Organización del catálogo, Historial de encargos y Configuración/vista previa. El editor ofrece modalidad, control de stock, las tres políticas al agotarse y ahora selección/guardado de foto por variante. Los textos y ejemplos ilustrativos no se consideran datos reales ni sustituyen los contratos anteriores.
