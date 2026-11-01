# Supabase Storage para imágenes del catálogo

Firebase Auth sigue identificando a emprendedoras y Firebase/Firestore conserva la cuenta, la tienda, el borrador y su proyección pública. Supabase Storage guarda los archivos. La app y la web nunca reciben la contraseña de la cuenta técnica de Supabase ni una clave `service_role`.

## Flujo de acceso

1. La app envía el logo o una imagen de producto a la API local con su ID token de Firebase.
2. La API verifica ese token y consulta `accesos/{uid}`, `emprendedoras/{uid}`, `tiendasPrivadas/{tiendaId}` y, para imágenes de producto, el borrador en Firestore. Rechaza si el rol, la tienda asignada o la propiedad no coinciden.
3. La API inicia sesión en Supabase Auth con una cuenta técnica normal, configurada solo en el archivo local del backend. Comprueba que el UID de Supabase sea `4a91b0f5-72ec-432a-ae3c-007a7eed1b8f`.
4. Supabase RLS deja operar en estos buckets únicamente a ese UID. El permiso de la emprendedora se decide en la API con Firebase/Firestore; la cuenta técnica no se comparte con los clientes.
5. El original queda privado. Al publicar una tienda o producto, la API valida y decodifica el archivo, elimina sus metadatos, crea WebP de 1600 y 320 píxeles y guarda rutas con hash en el bucket público.
6. Al cambiar el logo publicado o retirar una tienda/producto, Firestore registra las rutas públicas que deben borrarse. Si Storage falla, repetir la misma operación intenta completar la limpieza.

## Rutas

- Logo original privado: `tiendas/{tiendaId}/tienda/{uuid}.{jpg|png|webp}`.
- Logo público y miniatura: `catalogo/{tiendaId}/tienda/{sha256}_{1600|320}.webp`.
- Imagen original privada de producto: `tiendas/{tiendaId}/productos/{productoId}/{uuid}.{jpg|png|webp}`.
- Imagen pública grande y miniatura: `catalogo/{tiendaId}/productos/{productoId}/{sha256}_{1600|320}.webp`.
- Los IDs usan letras, números, guion o guion bajo; el nombre de cada original es UUID. El bucket original acepta JPEG y PNG y WebP. El catálogo solo acepta WebP procesado.
- Los buckets limitan cada objeto a 5 MiB. La ruta pública puede abrirse sin iniciar sesión y puede permanecer en caché después de retirar el producto.

## Configurar la cuenta técnica local

En `backend/.env.compartido.local`, creado desde `backend/.env.compartido.local.example`, completar:

- `AMANCAY_SUPABASE_URL`: URL HTTPS de la instancia del curso.
- `AMANCAY_SUPABASE_ANON_KEY`: la anon key entregada por el profesor. La API comprueba que el JWT tenga el rol `anon` y rechaza una clave con rol `service_role`.
- `AMANCAY_SUPABASE_MEDIA_EMAIL` y `AMANCAY_SUPABASE_MEDIA_PASSWORD`: correo y contraseña de la cuenta técnica de Supabase creada para el procesamiento de imágenes.
- `AMANCAY_SUPABASE_MEDIA_UID`: debe quedar con el UID indicado arriba.

El archivo `.env.compartido.local` está excluido de Git. No copiar estas credenciales a Android, Nuxt, Firebase, capturas o documentos compartidos. El uso de esa cuenta crea una sesión de Supabase Auth normal; la API renueva el token de sesión cuando vence.

## Aplicar las políticas definitivas

`infra/supabase/storage-imagenes.sql` reemplaza todas las políticas `amancay_*` de `storage.objects`, incluidas las políticas temporales de la prueba, y deja las rutas de logo y producto de esta guía. En el SQL Editor de la instancia del curso, ejecutar el archivo completo una vez. Es idempotente para los buckets y las políticas del proyecto.

Después, comprobar en **Storage → Files**:

- `amancay-originales`: privado, JPEG/PNG/WebP, 5 MiB.
- `amancay-catalogo`: público, WebP, 5 MiB.
- En cada bucket, las políticas muestran solo operaciones para la cuenta técnica; la descarga pública del catálogo se ofrece por la URL pública del bucket.

El script no borra archivos. Eliminar manualmente desde Storage los dos objetos `prueba-...` que se subieron durante la comprobación inicial, si aún aparecen en los buckets.

## Ejecutar la API local compartida

Desde `backend/`, con las credenciales ADC de Firebase ya preparadas y Firestore/Auth reales accesibles:

```powershell
npm.cmd run start:compartido:local
```

La API escucha en `http://localhost:8080`. Las rutas para cargar un logo o imagen de producto son:

```text
POST /api/v1/tiendas/{tiendaId}/logo
POST /api/v1/tiendas/{tiendaId}/productos/{productoId}/imagenes
Authorization: Bearer <Firebase ID token>
Content-Type: image/jpeg | image/png | image/webp
X-Archivo-Id: <UUID único para el reintento>
```

El cuerpo es el archivo binario, no JSON ni base64. La API genera el nombre final y lo sube al bucket privado. Para producto, agrega la ruta a `imagenes` en el borrador; si ya tiene cinco imágenes, la operación se rechaza e intenta retirar el original recién cargado. Para logo, guarda la ruta en la ficha privada y, si ya está publicada, sincroniza las derivadas públicas. Si Firestore falla después de la carga, podría quedar un original privado huérfano; no se hace borrado automático ante un resultado transaccional incierto.

Publicar usa las operaciones existentes `publicarTienda` y `publicarProducto`. La API verifica cada ruta privada, genera las dos copias y guarda sus rutas en la proyección pública de Firestore. `retirarProducto` y `retirarTienda` borran las derivadas públicas después de actualizar Firestore; una respuesta `503` durante esa limpieza indica que hay que reintentar la misma operación con el mismo `operacionId`.

## Alcance de este avance

La API compartida local integra Supabase Storage. Android carga fotos de productos a la API, que conserva el original en privado y genera derivadas WebP públicas al publicar; la emprendedora puede ver el original privado desde su vista previa. La web y el comprador Android resuelven las rutas públicas `catalogo/...` a URLs del bucket público. El adaptador Firebase para emuladores sigue separado. La carga de logo desde Android todavía está pendiente.
