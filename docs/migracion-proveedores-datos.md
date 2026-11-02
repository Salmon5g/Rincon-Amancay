# Separación de proveedores de datos

## Estado actual (2026-11-01)

Firebase Authentication y Firestore siguen siendo la identidad y base compartida. Supabase se incorporó como almacenamiento de imágenes: originales privados y derivadas WebP públicas. Las políticas están en `infra/supabase/storage-imagenes.sql`; la API local valida Firebase y usa una cuenta Supabase técnica normal, sin `service_role`. No se migraron Authentication ni los datos de Firestore a tablas Supabase.

Este documento se conserva como mapa de límites entre pantallas y proveedores. Para operar las imágenes, consultar [Supabase Storage para imágenes](supabase-storage-imagenes.md); para las responsabilidades por perfil, ver [Entrega 4 — Seguridad](Entrega%204%20%E2%80%94%20Acceso,%20administraci%C3%B3n%20y%20tienda/5-seguridad-firestore-storage.md).

## Qué quedó detrás de contratos

### Web comprador

- `web/app/repositories/catalogo-publico.ts` declara las lecturas paginadas de tiendas y productos, variantes activas y lectura de imágenes.
- `web/app/repositories/firebase/catalogo-publico.ts` implementa consultas Firestore y resuelve las rutas públicas de imágenes procesadas Supabase, convirtiendo documentos a `web/app/types/comprador-publico.ts`.
- El plugin Nuxt crea el adaptador y las páginas reciben `$catalogoPublico`. Las páginas de catálogo no importan el SDK de Firebase.
- Los cursores son identificadores opacos para la interfaz; el adaptador aplica orden y cursor de Firestore.

### App Android unificada

- El comprador usa `CatalogoCompradorRepository` para leer las proyecciones públicas de Firestore.
- La emprendedora usa `CuentaEmprendedoraRepository` para sesión, acceso y tienda, `ProductosEmprendedoraRepository` para las lecturas privadas y `ApiEmprendedoraClient` para escrituras privilegiadas.
- Las fotos de productos pasan por la API local; esta conserva el original privado en Supabase y genera WebP públicas al publicar. La carga de logo desde Android sigue pendiente.
- `EmprendedoraApp` y `DemoAmancayRepository` permanecen como código legado; `MainActivity` usa el flujo conectado actual. Ventas e invitaciones a ferias en la app siguen pendientes.

## Qué sigue acoplado a Firebase

- Authentication, perfiles, autorización y colecciones de Firestore.
- Los clientes web administran el estado de Authentication/Firestore a través de `cliente-base.ts`, `admin-lecturas.ts` y los repositorios; las pantallas no deben llamar al SDK directamente.
- La API verifica tokens Firebase y usa Firebase Admin SDK. Migrar a otra base o proveedor de identidad requiere cambiar la autorización del backend y probar todas las invariantes.
- Los contratos y reglas actuales describen Firestore. Supabase se usa solo para Storage de imágenes; no hay tablas de negocio Supabase.

## Si se evalúa migrar los datos a Supabase en el futuro

1. Confirmar el alcance y responsabilidades con el profesor y el equipo; el correo recibido ofrece Supabase para servicios adicionales como medios, no reemplaza Firebase.
2. Acordar un esquema relacional desde `contratos/`, incluidas relaciones, estados, versiones e historial. No convertir cada colección en una tabla sin revisar sus invariantes.
3. Mantener las pantallas detrás de repositorios y crear adaptadores solo después de aprobar esquema, autenticación y API.
4. Definir políticas RLS equivalentes a los permisos actuales. Nunca trasladar `service_role` a web o Android.
5. Preparar datos ficticios en un proyecto de desarrollo y comprobar lectura/escritura desde ambos clientes antes de una migración.

## Límite actual

La separación cubre el catálogo comprador web y los repositorios de la app unificada, pero no desacopla Auth, administración, API ni reglas Firestore. Supabase está integrado para archivos, no como reemplazo de la base. La API opera localmente con ADC y todavía no tiene una URL HTTPS compartida.
