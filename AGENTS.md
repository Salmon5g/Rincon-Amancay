# Contexto para nuevas sesiones

Mantener la sección "Estado actual" al día al terminar una tarea. La documentación y los comentarios del repo se escriben en español.

## Proyecto

Rincón Amancay: web Nuxt en `web/`, API en `backend/`, configuración Firebase en `firebase/`, contratos en `contratos/` y documentación en `docs/`.

## Android

- App unificada que debe probar el equipo: abrir `android/app-combinada` en Android Studio y ejecutar la configuración `app`.
- Compilar la app unificada: `./gradlew.bat :app:assembleDebug --console=plain` (workdir `android/app-combinada`).
- La app unificada usa Kotlin 2.2.10, AGP 9.0.1, compileSdk/targetSdk 36 y minSdk 28.
- El catálogo demo se comparte en memoria entre sus dos modos mientras la app está abierta. No sincroniza productos entre teléfonos ni los conserva al reiniciar.
- El proyecto Android comprador anterior se conserva en `android/` y tiene su propia configuración Gradle; no confundirlo con la app unificada.
- SDK Android habitual: `C:\Users\asus\AppData\Local\Android\Sdk`.

## Estado actual

- 2026-10-30: se separó la capa pública del comprador web del SDK de Firebase mediante `CatalogoPublicoRepository`; Firestore y Storage quedan en un adaptador, y la página e imágenes usan el contrato. En Android, las pantallas de ambos modos reciben `AmancayRepository`, con `DemoAmancayRepository` como proveedor actual en memoria y el borrador como estado de formulario. Se añadió `docs/migracion-proveedores-datos.md` para documentar lo que ya se puede reemplazar y los puntos que siguen ligados a Firebase. Web pasa `typecheck` y `build`; Android `:app:assembleDebug` compila. No se ejecutaron pruebas, no se conectó Supabase ni se modificó Firebase, y no se hizo commit/push. La autenticación, administración y API siguen acopladas a Firebase; la app conserva datos demo solo en memoria.
- 2026-10-30: se implementaron localmente los módulos de **Ferias**, **Configuración general** y **Administradores** en la web y la API autenticada. Ferias administra borradores, publicación/cancelación, participantes, respuestas y asistencia; Configuración guarda la política de navegación con control de versión; Administradores asigna o retira el rol de cuentas Firebase Auth existentes y conserva al menos una cuenta activa. Se añadieron contratos 15/16, reglas de lectura pública solo para ferias publicadas y configuración, y un índice por estado/fecha. Backend/web pasan typecheck y web build. No se ejecutaron pruebas, no se modificó Firebase real ni se publicaron reglas/índices/API/Hosting; no se hizo commit ni push. La página pública de ferias, avisos automáticos, carga de fotos a Storage y consumo de la política por web/Android siguen pendientes. Revisar `docs/admin-firebase-real-api-local.md` antes de operar con datos reales.
- 2026-10-30: se completó en web el flujo de aceptación de invitaciones para emprendedoras: el panel comparte un enlace, la destinataria crea su propia contraseña o inicia sesión, verifica su correo con Firebase Auth y acepta la invitación mediante la API. No se envía un correo de invitación automáticamente. El dominio debe estar autorizado en Firebase Authentication. La ruta fija se incluye en la generación estática para Firebase Hosting. Web pasa typecheck y build; la compilación confirma la prerenderización de la ruta. Pendiente configurar/verificar la URL y dominios en el proyecto Firebase y probar el flujo con una invitación controlada. No se hicieron cambios en Firebase, reglas, datos, Hosting ni API desplegada; no se ejecutaron pruebas ni commits/push.
- 2026-10-30: se agregó en local la edición administrativa del nombre visible de cuentas emprendedoras, con listado enriquecido desde API, control de versión, autorización de administrador, validación de coherencia de la tienda, reintentos idempotentes y auditoría. El correo y contraseña permanecen en sus flujos de Authentication; la ficha de tienda se edita aparte. Backend y web pasan typecheck. No se ejecutaron pruebas ni se modificó Firebase real, reglas o Hosting. Ver `contratos/14-edicion-cuentas-emprendedoras.md`.
- 2026-10-30: se agregó en local la administración de asociaciones e integrantes desde el panel web y la API autenticada. Se pueden crear, editar, desactivar/reactivar asociaciones y asignar/retirar emprendedoras manteniendo el historial de relación; el contrato está en `contratos/13-administracion-asociaciones.md`. Backend y web pasan typecheck. No se ejecutaron pruebas ni se modificó Firebase real, sus reglas o Hosting. La vista pública de asociaciones (RF-ASO-03) y la carga de logos en Storage siguen pendientes.
- 2026-10-30: se agregó en local la administración de sectores, categorías, tipos de emprendimiento y tipos de producto desde el panel web y la API autenticada. Los tipos de producto crean versiones inmutables para conservar referencias históricas; se registran auditorías. Backend y web pasan typecheck. No se ejecutaron pruebas ni se conectó/desplegó a Firebase real; reglas y Hosting aún requieren publicación/configuración. Ver `contratos/12-administracion-catalogos.md`.
- 2026-10-26: se agregó la ficha de mitigación de P01 (la foto del producto sale mal) en `docs/Problematicas_de_uso(Mitigacion_por_cada_problematica)/mitigacion-p01.md`; es propuesta de diseño sin cambios de código.
- 2026-10-25: el menú del comprador web se ubicó en una barra superior adaptable a móvil mediante cambios de CSS; se conservó la lógica de consultas y navegación.
- 2026-10-24: `android/APP` se reubicó a `android/app-combinada` para evitar colisión de mayúsculas/minúsculas con `android/app` en Windows. Se restauró el proyecto comprador y sus cambios locales.
- 2026-10-24: se retiró la sincronización Firebase de demostración de la app unificada. `:app:assembleDebug` comprobado con éxito usando el JDK 25 de Android Studio.
- 2026-10-24: En el comprador anterior se corrigieron los imports Compose y la tarjeta de producto de `DetalleTiendaScreen.kt`; la compilación completa de `android/` no pudo verificarse aquí porque Gradle 9.6 reportó el directorio raíz como no escribible en el entorno aislado.
- 2026-10-24: Rama de revisión `codex/app-combinada-verificada` preparada desde `origin/main`; incluye la app unificada, sus pruebas y la aclaración de datos de demostración en memoria. Los `google-services*.json` locales quedan excluidos. El `main` remoto no se modifica con esta rama.

## Convenciones

- No agregar comentarios al código salvo que se pidan.
- No hacer commit/push sin pedido explícito.
- Seguir el estilo existente de cada módulo.
