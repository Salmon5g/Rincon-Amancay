# Imágenes de tiendas y productos

Firestore guarda rutas relativas, no archivos, base64 ni URLs de descarga. La ficha privada guarda originales; las proyecciones públicas guardan las rutas WebP procesadas. El comprador anónimo solo necesita acceder a las derivadas del bucket público.

## Rutas reales

| Uso | Ruta en `amancay-originales` | Ruta en `amancay-catalogo` |
|---|---|---|
| Logo de tienda | `tiendas/{tiendaId}/tienda/{archivoId}.{jpg|png|webp}` | `catalogo/{tiendaId}/tienda/{sha256}_{1600|320}.webp` |
| Imagen de producto | `tiendas/{tiendaId}/productos/{productoId}/{archivoId}.{jpg|png|webp}` | `catalogo/{tiendaId}/productos/{productoId}/{sha256}_{1600|320}.webp` |

`tiendaId` y `productoId` admiten letras, números, guion y guion bajo, entre 1 y 128 caracteres. `archivoId` es UUID. Los originales pueden ser JPEG, PNG o WebP y miden hasta 5 MiB. El bucket público solo acepta WebP generado por la API.

Para la tienda, `tiendasPrivadas/{tiendaId}.logo` contiene la ruta privada. Si se publica, `tiendasPublicas/{tiendaId}.logo` y `logoMiniatura` contienen las rutas públicas grande y pequeña. Para productos, `imagenes` conserva las rutas originales en privado y recibe las rutas públicas en la proyección del catálogo; `miniaturas` conserva las rutas públicas pequeñas en esa proyección.

## Modo compartido local

Firebase Authentication identifica a la emprendedora y Firestore conserva sus datos. La app envía el archivo binario a la API local, con el ID token de Firebase y un `X-Archivo-Id` UUID:

```text
POST /api/v1/tiendas/{tiendaId}/logo
POST /api/v1/tiendas/{tiendaId}/productos/{productoId}/imagenes
Authorization: Bearer <Firebase ID token>
Content-Type: image/jpeg | image/png | image/webp
X-Archivo-Id: <UUID>
```

La API verifica sesión, rol activo, tienda asignada y propiedad en Firestore. Usa una sesión normal de Supabase Auth con una cuenta técnica guardada solo en el entorno del backend, comprueba su UID y sube el original privado. El cliente nunca recibe credenciales Supabase.

La subida de logo actualiza `tiendasPrivadas/{tiendaId}.logo`. Si la tienda ya está publicada, procesa el logo y actualiza sus dos rutas públicas dentro de Firestore; la derivada anterior se elimina después de la transacción. Si aún es borrador, el procesamiento público se hace al publicar la tienda.

La carga de imagen de producto agrega la ruta privada a `tiendasPrivadas/{tiendaId}/productos/{productoId}.imagenes`. Al publicar, la API vuelve a leer y decodificar el original, elimina metadatos y crea WebP de hasta 1600 × 1600 y 320 × 320. La imagen principal intenta calidad 90 y baja a 86/82 solo si debe respetar el límite de 5 MiB; la miniatura se genera directamente desde el original. Los objetos se guardan con nombres derivados del hash del contenido y no se sobrescriben.

Las políticas RLS permiten a la cuenta técnica operar solo en las rutas reales indicadas y solo dentro de los dos buckets Amancay. La autorización de la emprendedora ocurre en la API mediante Firebase/Firestore. No se utiliza `service_role`.

Los originales son privados. El bucket de derivadas es público: quien conozca una ruta válida puede abrirla sin iniciar sesión, incluso si el producto o tienda se retira o existe una copia en caché. La API elimina las derivadas al retirar una publicación o reemplazar una imagen y conserva las rutas para reintentar la limpieza si Storage falla. El borrado no invalida descargas ni todas las cachés. Los originales reemplazados se conservan privados por ahora.

## Emuladores Firebase

El flujo de emulador mantiene Firebase Storage en `127.0.0.1:9199`, proyecto `demo-rincon-amancay`. Las reglas de `firebase/storage.rules` y los adaptadores actuales corresponden al entorno de emuladores. La API compartida local usa Supabase Storage y no apunta a ese bucket.

## Lectores de imagen

Las rutas persistidas por Firestore siguen siendo relativas. El comprador web convierte las rutas validadas `catalogo/...` en URLs públicas del bucket `amancay-catalogo` de Supabase y las carga como imágenes públicas sin enviar credenciales. Las rutas del emulador continúan leyéndose mediante Firebase Storage. Android debe aplicar la misma conversión para las pantallas compradoras. Los originales solo los necesita la API; los clientes no deben leerlos directamente.

El SQL de buckets y políticas está en [infra/supabase/storage-imagenes.sql](../infra/supabase/storage-imagenes.sql). Pasos y variables locales están en [docs/supabase-storage-imagenes.md](../docs/supabase-storage-imagenes.md).
