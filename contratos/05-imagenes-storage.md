# Imágenes de productos — desarrollo local

Implementado y probado únicamente en los emuladores de demo-rincon-amancay. No se creó un bucket ni se desplegaron reglas en la nube.

## Acuerdo compartido para web y Android

Firestore guarda rutas de objetos, no archivos, base64 ni URLs de descarga. El campo imagenes es una lista ordenada de hasta cinco rutas; la primera es la portada. Publicar exige al menos una imagen y no admite rutas repetidas.

Ruta: `tiendas/{tiendaId}/productos/{productoId}/{archivoId}.png`, `.jpg` o `.webp`. Los identificadores contienen letras, números, guion o guion bajo (1–128 caracteres); generar un archivoId nuevo, por ejemplo UUID. Máximo 5 MiB por archivo. No usar nombres personales ni el nombre original de la foto.

El bucket local es `demo-rincon-amancay.appspot.com`, con Storage en `127.0.0.1:9199`. Configurar explícitamente los tres SDK cliente contra los emuladores: Auth 9099, Firestore 8080 y Storage 9199. El inicializador web existente aún no configura este entorno; no usar la configuración real para estas pruebas. Android y su inicialización siguen pendientes.

## Flujo

1. Iniciar sesión. La cuenta debe tener acceso activo, rol emprendedora, tienda asignada y ser su propietaria. El producto privado debe existir.
2. Subir un archivo con el SDK cliente de Storage a una ruta nueva. Enviar contentType concordante con la extensión, sin customMetadata ni contentEncoding.
3. Guardar las rutas seleccionadas en imagenes del producto privado, junto con actualizadoEn usando serverTimestamp. Esta escritura solo modifica esos dos campos. El contenido público anterior permanece igual.
4. Releer el producto privado para obtener su nueva versionEsperada y llamar a publicarProducto con ID token y operacionId, según 04-api-local.md.
5. El servidor comprueba cada objeto antes de publicar: ruta de la misma tienda y producto, existencia, tamaño, MIME y firma inicial PNG/JPEG/WebP. Luego publica la proyección dentro de la transacción Firestore.
6. Leer las imágenes con el SDK aplicando reglas: getBlob/getBytes en web o getBytes/getStream en Android. Para la vista web puede usarse una URL de objeto temporal, revocándola al desmontar el componente.

Seleccionar una ruta en el borrador no prueba que el objeto exista; el backend hace esa comprobación al publicar. Borrador puede tener cero imágenes. Crear productos, editar sus demás campos y gestionar stock son operaciones posteriores.

## Permisos y límites

La propietaria activa puede subir y leer las imágenes de sus productos. El visitante solo puede leer una imagen que figure en la proyección pública del producto, con producto publicado y tienda publicada, habilitada y con precios visibles. No se permite listar archivos, sobrescribirlos, cambiar metadatos ni borrarlos desde un cliente. Reemplazar implica subir a otra ruta; se conservan los archivos anteriores hasta implementar limpieza desde servidor.

Retirar un producto o tienda bloquea nuevas lecturas públicas sujetas a reglas. No elimina archivos, copias descargadas ni cachés. No guardar ni compartir getDownloadURL o URLs con token: son enlaces de acceso duradero y no deben usarse para garantizar retirada inmediata. La integración de entrega/caché y revocación de enlaces debe revisarse antes del despliegue.

La firma inicial detecta, por ejemplo, texto renombrado a .png; no es una decodificación completa ni garantiza que el archivo no esté truncado o manipulado. Normalización, eliminación de EXIF, miniaturas y validación completa del contenido quedan pendientes. No se admiten SVG ni otros documentos. Las reglas validan metadatos; el servidor complementa esa validación leyendo una cabecera acotada.

Storage no participa en la transacción Firestore. La inmutabilidad de los archivos evita cambios por clientes durante la publicación; los procesos futuros de limpieza con SDK Admin deberán coordinarse con las referencias privadas/públicas y las publicaciones. No eliminar archivos por antigüedad sin comprobar referencias.

Las reglas consultan Firestore para permisos. En producción habrá que configurar y verificar esa integración, permisos del servicio, bucket, costos y CORS; las pruebas locales no equivalen a ese despliegue.

## Verificación reproducible

Con los emuladores iniciados mediante `npm run emulators` desde firebase/:

- En firebase/: `npm run test:rules` y luego `npm run test:storage`.
- En backend/: `npm run typecheck` y `npm test`.

No ejecutar suites simultáneas: reponen los mismos documentos ficticios. Las pruebas de Storage limpian sus archivos; las de API eliminan su usuario temporal. Las 28 comprobaciones de Storage incluyen selección privada, lectura pública, retiro, tamaño, MIME, propiedad e inmutabilidad. Backend cubre además publicación HTTP con un objeto del emulador y rechazo de objetos inexistentes o inválidos.

Referencias oficiales: [emulador Storage](https://firebase.google.com/docs/emulator-suite/connect_storage), [reglas y consultas a Firestore](https://firebase.google.com/docs/storage/security/rules-conditions), [descargas mediante SDK](https://firebase.google.com/docs/storage/web/download-files).
