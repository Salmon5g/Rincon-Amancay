# Acuerdos compartidos de datos

Acuerdos disponibles: [cuentas y tiendas](01-cuentas-tiendas.md), [catálogo](02-catalogo-local.md), [publicación](03-publicacion.md), [API local](04-api-local.md) e [imágenes](05-imagenes-storage.md). Los tres últimos describen operaciones implementadas y probadas localmente con sus límites; no son un backend completo desplegado.

Aquí se incorporarán progresivamente los acuerdos revisados por el grupo: rutas de documentos, campos obligatorios/opcionales, tipos, estados, permisos y operaciones (entrada, salida y errores).

Se conserva el modelo provisional previamente trabajado; esta carpeta no lo reemplaza. Los documentos anteriores siguen separados hasta que el usuario decida incorporarlos.

Las especificaciones deben poder leerse desde Kotlin y TypeScript, por ejemplo Markdown y ejemplos JSON ficticios. Cada cliente implementará sus propios tipos. Usar archivos compartidos TypeScript no valida por sí solo los datos de Firestore ni sirve como modelo Kotlin.

Un cambio de campo debe indicar qué clientes afecta y si exige transformar documentos existentes. Un modelo provisional puede evolucionar; se documenta el cambio para coordinar al equipo.
