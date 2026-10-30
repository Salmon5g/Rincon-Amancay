# Preparación para cambiar de proveedor de datos

Este documento registra una separación gradual entre las pantallas y Firebase. No configura Supabase ni cambia el proveedor que usa el proyecto.

## Qué quedó detrás de contratos

### Web comprador

- `web/app/repositories/catalogo-publico.ts` declara lecturas paginadas de tiendas y productos, variantes activas y lectura de imágenes.
- `web/app/repositories/firebase/catalogo-publico.ts` implementa esas operaciones con Firestore y Storage, convirtiendo los documentos a modelos de `web/app/types/comprador-publico.ts`.
- El plugin Nuxt crea el adaptador y las páginas reciben `$catalogoPublico`. El listado, detalle e imágenes ya no importan tipos ni SDK de Firebase.
- Los cursores son identificadores opacos de texto para la interfaz. El adaptador Firebase aplica el orden y el cursor de Firestore.

### App Android unificada

- `AmancayRepository` declara el acceso que usan los modos comprador y emprendedora: catálogo, ventas, preferencias, borrador guardado, invitaciones de feria y acciones asociadas.
- `DemoAmancayRepository` implementa el contrato con datos en memoria. `crearRepositorioDemostracion()` es el punto actual de composición.
- Las pantallas reciben el repositorio por parámetro. `DraftState` permanece como estado del formulario Android y no se mezcla con el repositorio persistente.
- El modelo `Product` aún contiene `Uri` y `Bitmap` para la interfaz; una futura implementación remota debe subir la imagen al proveedor de archivos y persistir solo su referencia, no esos objetos Android.
- La app continúa siendo una demostración local: no conserva esos datos al reiniciar ni sincroniza entre dispositivos.

## Qué sigue acoplado a Firebase

- El acceso web, el estado de sesión, la autorización y las lecturas administrativas aún exponen Authentication y Firestore en `cliente-base.ts`, `admin-lecturas.ts` y `repositories/admin.ts`.
- La API actual valida tokens de Firebase y usa Firebase Admin SDK. Cambiarla requiere sustituir la verificación de identidad y el acceso a datos de servidor.
- Las reglas desplegables, la estructura de colecciones y Storage siguen definidas para Firebase. No hay adaptador de Supabase, conexión, tablas, políticas RLS ni buckets configurados.

## Ruta de migración si el grupo elige Supabase

1. Confirmar con el profesor el uso de la instancia Supabase y las responsabilidades de API, Auth, tablas y Storage.
2. Acordar el esquema relacional a partir de los contratos de `contratos/`, incluidos claves, estados, versiones, relaciones e historial. No se debe convertir cada colección en una tabla sin revisar relaciones e invariantes.
3. Crear adaptadores Supabase para los repositorios web y Android. Las pantallas conservarán sus modelos de aplicación; los adaptadores transformarán filas, cursores, URLs de archivos y errores.
4. Migrar autenticación de forma coordinada y hacer que la API acepte y verifique el token del proveedor elegido. La API debe mantener la autorización por perfil y validar en servidor las operaciones que hoy son privilegiadas.
5. Definir políticas RLS equivalentes a las reglas por perfil, y políticas de Storage para lectura/escritura de imágenes. No trasladar la `service_role` a clientes web o Android.
6. Preparar datos ficticios reproducibles en un proyecto de desarrollo y comprobar lectura y escritura desde ambos clientes antes de retirar Firebase.

## Límite de este avance

La separación cubre el catálogo público web y el contrato de datos de la app Android. Facilita cambiar la persistencia de esos flujos, pero no elimina la adaptación de login, permisos, administración, API, seguridad, carga de imágenes ni configuración de despliegue. No se tocaron los datos o ajustes de Firebase, no se usaron las credenciales de Supabase y no se desplegó nada.
