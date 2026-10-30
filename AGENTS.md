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
