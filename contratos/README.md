# Acuerdos compartidos de datos

Primer bloque disponible para revisión: [cuentas y tiendas](01-cuentas-tiendas.md). Ejemplo ilustrativo en ../datos-prueba/cuentas-tiendas.json. No se han implementado aún las validaciones o permisos descritos.

Aquí se incorporarán progresivamente los acuerdos revisados por el grupo: rutas de documentos, campos obligatorios/opcionales, tipos, estados, permisos y operaciones (entrada, salida y errores).

Se conserva el modelo provisional previamente trabajado; esta carpeta no lo reemplaza ni inventa otro. Esos documentos siguen separados hasta que el usuario decida incorporarlos. No hay contratos implementados en esta estructura inicial.

Las especificaciones deben poder leerse desde Kotlin y TypeScript, por ejemplo Markdown y ejemplos JSON ficticios. Cada cliente implementará sus propios tipos. Usar archivos compartidos TypeScript no valida por sí solo los datos de Firestore ni sirve como modelo Kotlin.

Un cambio de campo debe indicar qué clientes afecta y si exige transformar documentos existentes. Un modelo provisional puede evolucionar; se documenta el cambio para coordinar al equipo.
