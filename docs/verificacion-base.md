# Verificación de la base — 9 de octubre de 2026

Estado: disponible para que los equipos comiencen **desarrollo local** con [arranque por equipo](arranque-equipos.md). No equivale a una aplicación terminada ni a un despliegue remoto verificado.

## Comprobaciones ejecutadas

Con dependencias instaladas, API local y emuladores del proyecto `demo-rincon-amancay` activos. Ejecutar las suites en serie: algunas reponen los 29 documentos del fixture. No ejecutarlas durante una demostración o edición de datos.

| Comando desde la raíz | Resultado |
| --- | --- |
| `npm --prefix backend run typecheck` | Sin errores. |
| `npm --prefix backend test` | 184 tests aprobados. |
| `npm --prefix web run typecheck` | Sin errores en `app/lib/`. |
| `npm --prefix web run test:local` | 18 tests aprobados contra SDK, API y emuladores. |
| `npm --prefix web run test:unit` | 2 tests aprobados del destino HTTPS y proyecto del cliente compartido; red simulada. |
| `npm --prefix firebase run test:rules` | 40 casos aprobados, incluidos bloqueo y auditoría técnica inaccesibles por clientes. |
| `npm --prefix firebase run test:storage` | 31 casos aprobados. |
| `npm --prefix backend run cuentas:recuperar -- --entorno local --uid revision_sin_cuenta` | Inspección sin reserva, sin modificación de Auth o catálogo. |
| `npm --prefix backend run plan:desarrollo` | Informe local coherente con los pendientes, sin conectar a la nube. |

Las cifras de Node incluyen los grupos padre. Las pruebas nuevas de recuperación usan dos procesos reales y terminan el que conserva la reserva. Auth se simula en esa prueba para controlar el punto de interrupción; las suites de cuentas y API usan Auth Emulator.

La suite de imágenes emite advertencias `MaxListenersExceededWarning` durante un caso de dimensiones excesivas y termina correctamente. No se ocultaron esas advertencias ni se demostró una fuga de memoria. Revisar trazas y comportamiento bajo carga antes de desplegar el procesamiento de imágenes.

## Qué NO queda verificado por estos resultados

- **Android:** ejemplos corregidos y organizados en main/debug, pero sin proyecto Gradle, compilación ni ejecución en dispositivo. El equipo debe integrar y compilar debug como primer paso; los tests TypeScript no validan Kotlin.
- **Nuxt:** la biblioteca cliente pasa typecheck; los plugins `.example` y pantallas deberán comprobarse al inicializar el único proyecto Nuxt. No hay build ni interfaz web terminados.
- **Nube y Docker:** el contenedor no se construyó, el servidor no se desplegó y no se probaron IAM, CORS del bucket, cuentas o recuperación contra servicios reales. Los adaptadores compartidos no significan un entorno remoto activo.
- **Catálogo ampliado:** cantidades fraccionarias, precios ocultos, nuevos tipos de atributos, migraciones y ventas siguen fuera del alcance actual. Los límites se mantienen documentados para revisión tras terreno.

## Criterio de inicio por equipo

Web comprador puede comenzar por el catálogo sin sesión; web administrador por acceso administrativo e invitaciones; Android comprador por un proyecto debug que consulte el mismo catálogo. Ambos equipos web deben coordinar una sola inicialización Nuxt. Al tener una pantalla mínima por plataforma, realizar el escenario común de integración en el mismo computador. El backend local no exige activar facturación ni registrar Android en Firebase para este arranque.
