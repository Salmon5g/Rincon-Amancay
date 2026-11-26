# Plan de migración a Coolify + Supabase

Fecha: 2026-11-25. Motivo: Google suspendió el proyecto `rinconamancay3` (`CONSUMER_SUSPENDED`), lo que deja sin servicio a Firestore, Firebase Authentication, Hosting y los avisos push.

Este documento es un plan. Todavía no se ha cambiado código ni infraestructura.

## Resumen

| Hoy (Google) | Después |
|---|---|
| Firestore | Postgres de Supabase (tablas + políticas RLS) |
| Firebase Authentication | Supabase Auth (correo/contraseña y Google) |
| Firebase Hosting | Coolify (sitio estático generado con `npm run generate`) |
| API local / Cloud Run propuesto | Coolify (contenedor Node con `backend/`) |
| Supabase Storage (imágenes) | Se mantiene igual |
| Firebase Cloud Messaging (push Android) | Se mantiene FCM en un proyecto Firebase **solo de mensajería**; la web pasa a Web Push estándar |

Idea central: **la API sigue siendo la única que escribe**. Hoy las 61 operaciones de escritura ya pasan por la API, con transacciones e idempotencia. Por eso el cambio principal está en el backend y en las **lecturas** de web y Android, no en la lógica de negocio.

## Antes de empezar (decisiones bloqueantes)

1. **Confirmar con el profesor** que se puede dejar Firebase. `docs/migracion-proveedores-datos.md` recuerda que el curso ofreció Supabase como servicio adicional (medios), no como reemplazo. Si la pauta exige Firebase, la salida es apelar la suspensión o crear otro proyecto Firebase con App Check y reglas con límites.
2. **Recuperar los datos.** Exportar Firestore y las cuentas de Authentication requiere acceso al proyecto, y está suspendido. Opciones:
   - Si Google levanta la suspensión, aunque sea temporalmente: exportar de inmediato (`firebase auth:export` y exportación de Firestore).
   - Si no: partir de cero con los respaldos disponibles. Hay un respaldo de catálogos (tipos, versiones, categorías y referencias) fuera del repo en AppData Local; los datos de prueba se pueden volver a crear. Las cuentas tendrían que registrarse de nuevo.
3. **Definir el servidor de Coolify.** Supabase alojado por cuenta propia usa varios contenedores (Postgres, Auth, REST, Realtime, Storage, Studio, Kong). Como referencia, 4 GB de RAM y 2 vCPU es un mínimo cómodo para Supabase, la API y la web juntos. La otra opción es usar la instancia del profesor (`supabase-fixmol.codepulse.cl`), si permite crear tablas y Auth propias.
4. **Elegir los dominios**, por ejemplo `amancay.<dominio>` (web), `api.amancay.<dominio>` (API) y `db.amancay.<dominio>` (Supabase), con HTTPS automático de Coolify.

## Tamaño del cambio

| Parte | Qué depende de Firebase |
|---|---|
| Backend | 24 archivos con `firebase-admin`, unas 5 400 líneas en módulos; 50 transacciones; 3 consultas `collectionGroup`; verificación de tokens en `http/api.ts`; envío push en `server-compartido.ts` |
| Web | 19 archivos con el SDK de Firebase: repositorios, `cliente-base.ts`, páginas de admin, emprendedora e invitación, avisos y seguimientos |
| Android | 21 archivos con `com.google.firebase`: repositorios de `data/`, inicio de sesión, seguimientos, avisos y 4 pantallas con listeners en tiempo real |
| Reglas e índices | `firebase/firestore.rules` (251 líneas, unas 30 rutas) y `firestore.indexes.json` pasan a políticas RLS e índices de Postgres |
| Contratos | 23 contratos en `contratos/` describen documentos de Firestore; hay que actualizarlos al esquema nuevo |

## Fases

### Fase 0 — Infraestructura (2–3 días)

- Instalar Supabase en Coolify (o confirmar la instancia del profesor) y crear un entorno de desarrollo separado.
- Configurar Supabase Auth: correo/contraseña, proveedor Google (nuevo cliente OAuth web y Android), plantillas de correo en español y URL de retorno `/auth/accion`.
- Crear en Coolify los servicios `web` (estático) y `api` (Node 24 con `backend/`), sin datos todavía.
- Mantener el Storage de Supabase actual con las mismas rutas (`infra/supabase/storage-imagenes.sql`).
- Proyecto Firebase nuevo **solo para FCM** (sin Firestore ni Auth), con su `google-services.json` para Android.

### Fase 1 — Esquema de datos (3–5 días)

Diseñar las tablas a partir de `contratos/`, no copiando colección por colección:

- **Núcleo:** `usuarios`, `accesos` (rol y estado), `tiendas` (privada) con columnas públicas, `productos`, `variantes`, `movimientos_stock`, `ventas`, `consultas`, `calificaciones`.
- **Catálogos:** `sectores`, `categorias`, `tipos_emprendimiento`, `tipos_producto` y `tipos_producto_versiones`.
- **Comunidad:** `asociaciones`, `integrantes`, `ferias`, `participantes_feria`, `invitaciones`, `tiendas_seguidas`, `asociaciones_seguidas`, `dispositivos_push`.
- **Operación:** `operaciones` (idempotencia, unificando las `operaciones*` actuales), `auditoria_administracion`, `configuracion_aplicacion`, `desactivaciones` y `reactivaciones`.
- **Proyecciones públicas como vistas:** `tiendasPublicas`, `productosConsulta` (sin precios) y `asociacionesPublicas` pasan a **vistas SQL** sobre las tablas privadas. Esto elimina el código de sincronización de proyecciones del backend (`publicacion.ts`, `vista-sin-precios.ts` y `proyecciones-asociaciones-publicas.ts`).
- **Seguridad:** políticas RLS equivalentes a `firestore.rules`. Público: solo las vistas publicadas. Dueña: lectura de lo suyo. Admin: lectura completa. Escrituras: solo la API. Límites con paginación obligatoria y `max_rows` de PostgREST, para que una consulta no pueda descargar todo.
- **Índices:** los compuestos actuales más los que reemplazan a `collectionGroup` (participantes por `entidad_id` y `estado`, integrantes por `tienda_id`).
- Guardar el esquema como migraciones SQL en el repo, por ejemplo `infra/supabase/migraciones/`, con datos semilla ficticios.

### Fase 2 — Backend (1,5–2 semanas)

- Reemplazar `firebase-admin` por una capa de acceso a Postgres (`postgres` o `pg`, con transacciones SQL). Conviene un módulo `db.ts` que exponga transacciones, para cambiar los módulos uno por uno.
- Verificar los JWT de Supabase en `http/api.ts` en lugar de `verifyIdToken`, conservando `auth_time` para las operaciones sensibles.
- Migrar los módulos en este orden, con sus pruebas: cuentas y sesiones → catálogos → productos y stock → publicación (simplificada por las vistas) → ventas → consultas → asociaciones → ferias → administración y auditoría.
- Las pruebas actuales usan el emulador de Firestore. Reescribirlas contra un Postgres de prueba (contenedor local) y mantener los mismos casos.
- Envío de avisos: conservar `firebase-admin/messaging` solo con el proyecto de FCM para Android; agregar Web Push (VAPID, librería `web-push`) para la web.
- Mantener sin cambios el contrato HTTP de las 61 operaciones, para que web y Android no tengan que cambiar sus llamadas a la API.

### Fase 3 — Web (1–1,5 semanas)

- Reemplazar `lib/firebase.ts` y `cliente-base.ts` por un cliente `@supabase/supabase-js` (sesión, token para la API y lecturas).
- Reescribir los adaptadores de `repositories/firebase/` como `repositories/supabase/`, conservando sus interfaces (`CatalogoPublicoRepository`, emprendedora, admin, consultas y ventas). Las páginas casi no deberían cambiar.
- Los listeners en tiempo real (`onSnapshot`) pasan a Supabase Realtime solo donde hoy se usan: fichas abiertas y seguimientos.
- Acceso: correo/contraseña, Google, recuperación de contraseña (`/auth/accion`), invitación de emprendedoras y creación de cuentas desde admin. Esta última hoy usa una segunda app Firebase en memoria; con Supabase debe hacerse desde la API con la clave de servicio, solo en el servidor.
- Avisos web: reemplazar `firebase-messaging-sw.js` por un service worker de Web Push estándar.
- Generar con `npm run generate` y publicar en Coolify.

### Fase 4 — Android (1,5–2 semanas)

- Reemplazar Firestore y Auth por `supabase-kt` (Auth, PostgREST y Realtime). El cambio se concentra en `data/` (repositorios, `AutenticacionCorreo`, `AutenticacionGoogleComprador` y `FirebaseClientServices`).
- Los 4 listeners (`addSnapshotListener`) pasan a Realtime o a lecturas puntuales con actualización manual, que además ahorra recursos.
- Inicio con Google: Credential Manager con el ID token entregado a Supabase Auth.
- Mantener `firebase-messaging` con el proyecto de solo FCM; los temas de suscripción (invitaciones, consultas y seguidores) se conservan o pasan a envíos por token desde la API.
- La URL de la API pasa de `127.0.0.1:8080` (USB) a la URL HTTPS de Coolify: el APK funciona sin el computador.
- Compilar y probar en dispositivo al final de la fase, no en cada cambio.

### Fase 5 — Datos, pruebas y corte (3–5 días)

- Importar los datos exportados (si se pudieron recuperar) o cargar la semilla, y los usuarios a Supabase Auth. Las contraseñas de Firebase usan un hash propio (scrypt modificado); si no se pueden importar, enviar un correo de restablecimiento a cada cuenta.
- Recorrido completo por perfil: compradora (con y sin sesión), emprendedora (tienda, productos, ventas, consultas, ferias) y administración.
- Prueba de carga simple: confirmar que la paginación y los límites impiden descargas masivas, y que el servidor aguanta.
- Respaldos automáticos de Postgres en Coolify (diarios, guardados fuera del servidor).
- Actualizar `contratos/`, `docs/` y `AGENTS.md`, y retirar `firebase/` (reglas e índices) cuando todo funcione.

## Tiempo estimado

Con 3–4 personas trabajando en paralelo tras la Fase 1:

| Fase | Duración |
|---|---|
| 0. Infraestructura | 2–3 días |
| 1. Esquema | 3–5 días |
| 2. Backend | 1,5–2 semanas |
| 3. Web | 1–1,5 semanas (en paralelo con Android, después de que el backend tenga cuentas y catálogos) |
| 4. Android | 1,5–2 semanas (en paralelo con web) |
| 5. Datos, pruebas y corte | 3–5 días |
| **Total** | **unas 4–6 semanas** |

## Riesgos

- **Datos atrapados en el proyecto suspendido.** Sin acceso no hay exportación; puede que haya que empezar con datos nuevos.
- **Servidor propio.** Si alguien repite las ráfagas de lecturas, ya no corta Google: se satura el servidor. Hacen falta límites por IP en la API y en Kong/PostgREST, paginación obligatoria y monitoreo básico.
- **Mantenimiento.** Actualizaciones de Supabase y Coolify, certificados, respaldos y espacio en disco quedan a cargo del equipo.
- **Push en Android** sigue dependiendo de Google (FCM). Es gratis y de bajo riesgo, pero no se elimina.
- **Plazo de la entrega.** Si la entrega está a menos de 4 semanas, conviene presentar con el emulador local de Firebase y migrar después.

## Alternativa mínima, si el plazo no alcanza

1. Apelar la suspensión.
2. Mientras tanto, usar Coolify solo para **publicar la web y la API** (que ya funcionan con Firebase), y demostrar con el emulador de Firebase.
3. Si se reactiva el proyecto: exportar datos de inmediato, activar App Check y desplegar las reglas con límites que ya están en el repo.
