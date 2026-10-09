# Acuerdos compartidos de datos

Acuerdos disponibles: [cuentas y tiendas](01-cuentas-tiendas.md), [catálogo](02-catalogo-local.md), [publicación](03-publicacion.md), [API local](04-api-local.md), [imágenes](05-imagenes-storage.md) y [alta de emprendedoras](06-alta-emprendedoras.md). Los contratos 03–06 describen operaciones implementadas y probadas localmente con sus límites; no son un backend completo desplegado.

También está implementada la [creación y edición de productos y variantes](07-edicion-productos.md), con stock inicial y publicación explícita.

Los [ajustes de stock](08-ajustes-stock.md) registran cambios con historial privado y disponibilidad sincronizada desde la ficha aprobada. Ver [disponibilidad e imágenes](11-disponibilidad-e-imagenes.md) para compatibilidad con datos antiguos y procesamiento de fotos.

La [desactivación de emprendedoras](09-desactivacion-cuentas.md) coordina el bloqueo de gestión, catálogo y Authentication, con reintentos para fallos parciales.

El [ciclo de cuentas](10-ciclo-cuentas.md) define registro, invitaciones, recuperación de contraseña y reactivación. Las pantallas administrativas usan invitaciones; el alta directa del contrato 06 queda como apoyo técnico local. Las reservas interrumpidas tienen [procedimiento técnico](../docs/recuperacion-cuentas.md), fuera de las pantallas.

Aquí se incorporarán progresivamente los acuerdos revisados por el grupo: rutas de documentos, campos obligatorios/opcionales, tipos, estados, permisos y operaciones (entrada, salida y errores).

Se conserva el modelo provisional previamente trabajado; esta carpeta no lo reemplaza. Los documentos anteriores siguen separados hasta que el usuario decida incorporarlos.

Las especificaciones deben poder leerse desde Kotlin y TypeScript, por ejemplo Markdown y ejemplos JSON ficticios. Cada cliente implementará sus propios tipos. Usar archivos compartidos TypeScript no valida por sí solo los datos de Firestore ni sirve como modelo Kotlin.

Un cambio de campo debe indicar qué clientes afecta y si exige transformar documentos existentes. Un modelo provisional puede evolucionar; se documenta el cambio para coordinar al equipo.
