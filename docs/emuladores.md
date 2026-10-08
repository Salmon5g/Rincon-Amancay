# Firebase local: primer catálogo de prueba

El catálogo publicado es legible sin sesión en el emulador y las rutas privadas siguen permisos por rol. Storage local admite imágenes según ../contratos/05-imagenes-storage.md. La nube sigue cerrada.

## Ejecutar

Herramientas del equipo usadas: Node.js 24.15.0, Java 25 y Firebase CLI 15.33.0. Java debe estar en PATH. Desde la carpeta firebase/:

```sh
npm ci
npm run check
```

check inicia Authentication, Firestore y Storage con el proyecto ficticio demo-rincon-amancay, carga dos veces los mismos documentos, comprueba datos, permisos de Firestore e imágenes de Storage y solicita el cierre de los emuladores. No requiere iniciar sesión en Firebase CLI. La primera ejecución descarga los emuladores oficiales.

Para explorar los documentos, usar dos terminales ubicadas en firebase/:

```sh
# Terminal 1: dejar abierta
npm run emulators

# Terminal 2: con los emuladores ya iniciados
npm run seed
```

Abrir http://127.0.0.1:4000 y seleccionar Firestore. La consola local administra datos de prueba; no demuestra que un visitante de la aplicación pueda leerlos. Detener con Ctrl+C. Los datos están en memoria: al reiniciar se vuelve a ejecutar seed.

En PowerShell puede usarse npm.cmd si npm.ps1 está bloqueado. Si un puerto está ocupado, no detener procesos ajenos: revisar qué los usa. Los puertos están fijados en firebase.json; el cargador exige 127.0.0.1:8080.

## Qué contiene

datos-prueba/catalogo-local.json es la instantánea ejecutable local: 29 documentos con IDs estables. Incluye sector, tipo de emprendimiento, tres categorías, tres tipos de producto con su versión 1, perfiles administrativos ficticios, una tienda, configuración y vistas públicas/privadas de tres productos.

- Gorro: dos variantes talla/color. Precio y stock en cada variante, no duplicados en el producto padre.
- Alfajores: a pedido, sin manejo de stock. PrecioBase en producto; atributos de ingredientes y alérgenos meramente ficticios.
- Planta: sin variantes, precioBase y stock en producto.
- Las vistas públicas no contienen propietarioUid ni stock exacto. Los precios están visibles porque mostrarPrecios es true en esta instantánea.

Los ejemplos anteriores en cuentas-tiendas.json siguen siendo ilustrativos y no se cargan: muestran una tienda borrador. catalogo-local.json representa otro escenario, publicado, exclusivamente local. Los UID de sus perfiles no tienen cuentas Authentication creadas todavía; no hay usuarios ni contraseñas de prueba disponibles para iniciar sesión.

## Límites de este incremento

Los documentos publicados se precargan como instantánea. La API en backend/ implementa publicarTienda/publicarProducto; consultar ../contratos/04-api-local.md. Las imágenes vacías son una excepción del fixture local y no cambian el requisito de validación de fotos para una publicación real. No cargar esta instantánea en la nube.

Los Timestamp se expresan como {"$timestamp":"2026-10-08T12:00:00Z"} en el JSON y el cargador los convierte al tipo Firestore correspondiente. Son fechas fijas para pruebas reproducibles, no horas elegidas por un cliente en operaciones reales.

seed reemplaza el contenido de los 29 documentos conocidos por el del fixture. No elimina documentos ajenos ni limpia toda la base. Repetirlo no crea IDs nuevos; cambios manuales en esos 29 documentos se restablecen al ejemplo.

La carga REST usa la autoridad especial del emulador para preparar fixtures. Está fijada a localhost y al proyecto demo; no lee .env ni credenciales del proyecto real, rechaza destinos de emulador distintos y no sigue redirecciones. No reutilizar este mecanismo como backend de producción.

Las reglas permiten catálogo público y acceso privado por rol. La propietaria puede editar textos de tienda y seleccionar imágenes de productos. Publicar/retirar requiere la API. Ventas, stock y conexión Android siguen pendientes.

Referencia oficial: https://firebase.google.com/docs/emulator-suite/connect_firestore

## Resultado verificado

El 8 de octubre de 2026, `npm run check` terminó correctamente con Firestore Emulator 1.22.0: carga de 29 documentos dos veces, comparación de todos los documentos recuperados, referencias de tipos/categorías, tres productos sin duplicados, variantes y lecturas/escritura sin sesión denegadas. Posteriormente se verificaron 38 casos de permisos Firestore, 28 de Storage/selección de imágenes y 52 tests del backend (incluyendo grupos padre), con Authentication local.

En este Windows se observó que el proceso Java podía seguir ocupando el puerto 8080 después de que la CLI anunciara el cierre. Se comprobó su línea de comandos y se detuvo únicamente el emulador de este proyecto antes de repetir la prueba. Si ocurre de nuevo, verificar el proceso antes de detenerlo; no terminar todos los procesos Java.

## Auditoría de herramientas

La CLI local está aislada de las dependencias de web/. En la instalación inicial npm audit reportó 11 paquetes afectados (7 altos y 4 moderados), incluyendo dependencias transitivas de firebase-tools. No se aplicaron downgrades forzados. Revisar actualizaciones de CLI; este informe no equivale a vulnerabilidades demostradas en la aplicación web. No exponer los emuladores fuera de localhost.
