# Propuesta del entorno compartido — 9 de octubre de 2026

Esta es una propuesta revisable, no un despliegue ni una autorización de gasto. La consola real no se inspeccionó en esta etapa: el último estado registrado es el del 8 de octubre en firebase-desarrollo.md. Los emuladores y sus datos ficticios siguen siendo el entorno ejecutable.

## Decisión recomendada

Usar **Cloud Run para una API compartida** y mantener Firebase Authentication, Firestore y Storage para ambos clientes. La API actual es un servidor Node HTTP y usa Sharp; un contenedor permite conservar ese código y controlar su entorno nativo. Esta elección es una recomendación técnica, pendiente de tu revisión. Cloud Functions también es viable, pero exigiría adaptar la entrada HTTP y no elimina la necesidad de coordinar operaciones entre procesos.

Proponer Santiago (`southamerica-west1`) para API y Storage, junto al Firestore existente. Confirmar disponibilidad, tarifas y ubicación del bucket antes de crearlo. No trasladar Firestore ni crear otro proyecto automáticamente. El proyecto `rincon-amancay` se usaría como desarrollo compartido con cuentas/datos de prueba; separar producción antes del uso con datos reales.

| Parte | Dónde se ejecutaría | Qué comparte el grupo |
| --- | --- | --- |
| Web Nuxt | Por ahora, computador de cada equipo; alojamiento web posterior. | Configuración pública Firebase y URL de la API. |
| Android | Emulador/dispositivo del equipo. | Mismo proyecto Firebase y URL de API; applicationId propio pendiente. |
| API | Cloud Run, servicio propuesto `amancay-api-dev`. | Validación de tokens, permisos y operaciones de negocio. |
| Identidades/datos/fotos | Auth, Firestore y Storage del mismo proyecto. | UID, colecciones, reglas y contratos comunes. |

La URL HTTPS del servicio no equivale a acceso libre a operaciones: la API debe seguir verificando el ID token Firebase y el acceso Firestore. El patrón de autenticación de usuarios está descrito en [Cloud Run con Firebase Auth](https://docs.cloud.google.com/run/docs/authenticating/end-users). CORS limita orígenes de navegador; no sustituye la autorización de Android ni del backend.

## Facturación y consumo

Cloud Storage for Firebase requiere Blaze y una cuenta de facturación asociada. Sus buckets nuevos usan el esquema `proyecto.firebasestorage.app`; la región gratuita de Storage está restringida a ubicaciones de Estados Unidos, por lo que no se debe presentar Santiago como almacenamiento gratuito. [Requisitos oficiales de Storage](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024?authuser=2).

Para Cloud Run propongo inicialmente facturación por solicitud, mínimo cero instancias, máximo inicial uno, concurrencia uno, 1 vCPU y 1 GiB de memoria. Son parámetros para pruebas de carga, no una garantía de capacidad ni un límite monetario. Hay cuotas gratuitas, agregadas por cuenta de facturación, y tarifas que dependen de la región; no se promete factura cero. [Precios de Cloud Run](https://cloud.google.com/run/pricing).

Escenario ilustrativo del plan JSON: 300 productos, tres fotos por producto, originales de 1 MiB, copias principales de 200 KiB y miniaturas de 30 KiB. Resulta en unos **1,08 GiB almacenados**. Con 30.000 descargas de miniaturas y 5.000 de fotos principales al mes, son unos **1,81 GiB de descarga**. Son supuestos calculados, no tráfico medido ni pronóstico.

Las tarifas publicadas tienen selectores regionales. No usar el precio por defecto de Iowa como tarifa de Santiago. Falta cotizar el escenario en la calculadora con región, destino de descargas y cuenta de facturación reales. Añadir operaciones Storage, Firestore, Auth, compilaciones, Artifact Registry, logs y eventualmente correo/cola. También cuentan fotos sustituidas y copias sin referencia mientras no exista limpieza. [Precios de Storage](https://cloud.google.com/storage/pricing), [calculadora](https://cloud.google.com/products/calculator).

Firestore tiene una cuota gratuita para una base por proyecto: 50.000 lecturas, 20.000 escrituras y 20.000 eliminaciones diarias, 1 GiB almacenado y 10 GiB de transferencia mensual. Las reglas, consultas, listeners y reintentos pueden aumentar el consumo; una operación de nuestra API no equivale necesariamente a una escritura. [Facturación de Firestore](https://firebase.google.com/docs/firestore/pricing).

Propongo **una alerta inicial de US$5 al mes**, con avisos al 50%, 80% y 100%, para que el grupo revise consumo. Esa cifra es una propuesta de seguimiento, no una cotización ni un máximo garantizado. Las alertas no detienen automáticamente todos los servicios. Google también documenta presupuestos con spend caps: comprobar elegibilidad y servicios cubiertos antes de atribuirles protección sobre todo Firebase. [Presupuestos y tipos de límites](https://docs.cloud.google.com/billing/docs/how-to/budgets).

## Bloqueos técnicos encontrados

1. **Cuentas:** desactivar/reactivar ya usa una reserva persistente en `ejecucionesCuentas/{uid}` además de una cola por UID, de modo que dos instancias no reservan la misma cuenta a la vez (probado con una carrera concurrente). Queda pendiente la recuperación de reservas huérfanas: la reserva no vence y un proceso que muera entre fases exige revisión técnica, porque Auth no ofrece un *fencing token*. Antes de la nube falta probar la terminación entre fases con dos procesos reales y definir ese procedimiento. [Configuración del máximo de instancias](https://docs.cloud.google.com/run/docs/configuring/max-instances).
2. **Servidor:** agregar un adaptador compartido con credenciales del entorno (ADC), proyecto/bucket explícitos, rechazo de variables de emuladores y escucha en `0.0.0.0:$PORT`. El servidor actual continúa ligado al emulador y rechaza producción. No empaquetarlo y desplegarlo como está.
3. **Contenedor:** preparar y probar Node 24 y Sharp en Linux con instalación por lockfile, usuario sin privilegios y exclusión de .env, credenciales, .local y datos de prueba. Docker y gcloud no se encontraron en PATH en esta revisión; no se probó ni construyó una imagen.
4. **Clientes:** los adaptadores compartidos ya existen separados de los locales — web `cliente-compartido.ts` (sobre `cliente-base.ts`, proyecto real + API HTTPS) y Android `ConexionCompartida.kt`/`ClienteCompartido.kt` — y se verificaron los locales (18 tests) y `typecheck`. Falta la URL de API y el applicationId Android; el cliente local y el compartido rechazan sus proyectos opuestos por diseño.
5. **Operación:** límites de peticiones/procesamiento de fotos, observabilidad sin datos sensibles, entrega real de invitaciones y revisión de plantillas de Auth. La creación del primer administrador ya tiene herramienta (`npm --prefix backend run admin:compartido` con ADC y `--confirmar`), todavía sin ejecutar. No ejecutar cuentas:demo ni seed local contra la nube.

La configuración HTTP ya permite inyectar proyecto, entorno y orígenes exactos sin cambiar los valores locales. Se probaron salud, CORS y rechazo de solicitudes sin token; esto **no inicializa Firebase real** ni resuelve los bloqueos anteriores.

## Permisos que se revisarán al preparar el despliegue

Crear una identidad de servicio exclusiva para la API, con acceso de datos Firestore, gestión Auth necesaria y lectura/creación de objetos en el bucket específico. No usar el rol Owner/Editor para el runtime ni descargar una clave JSON para compartirla con compañeros. La identidad de despliegue y la de ejecución son distintas; definir permisos mínimos concretos al implementar el adaptador y comprobarlos en el entorno elegido.

Las reglas de Firebase controlan SDK cliente; el SDK Admin las omite y depende de IAM y de nuestras comprobaciones. Los equipos reciben configuración pública cliente, nunca credenciales Admin. Revisar también la integración de reglas Storage con Firestore y CORS del bucket para descargas web.

## Secuencia de preparación y revisión

1. Revisar esta arquitectura y el escenario de consumo. Mantener todos los trabajos actuales en emuladores.
2. Resolver la coordinación distribuida de cuentas y probar interrupciones/reintentos. Después preparar adaptador real y contenedor, conexiones cliente y permisos propuestos.
3. Con esos cambios probados, entregar manifiesto, comandos concretos, permisos, recursos y estimación regional para revisión final. Solo entonces solicitar autorización de facturación/despliegue; no confundir aprobar la propuesta con activar Blaze.
4. Tras autorización: confirmar estado de la consola, vincular facturación y avisos, crear bucket/servicio e identidad, desplegar reglas e índices revisados, API y datos ficticios controlados.
5. Validar HTTPS, roles, fotos y una actualización desde dos clientes. La prueba de pantallas completas espera al desarrollo de Nuxt/Android; la conexión y los permisos pueden probarse antes.

## Contenedor propuesto (pendiente de construir)

`backend/Dockerfile` usa `node:24.15.0-bookworm-slim`, instala por lockfile sin dependencias de desarrollo, arranca `src/server-compartido.ts` y ejecuta como usuario `node`. `backend/.dockerignore` limita el contexto a `package.json`, `package-lock.json` y `src/`, excluyendo `.env`, credenciales, `.local` y datos de prueba. El arranque sin `AMANCAY_ENTORNO=compartido` o con variables de emulador se rechaza (probado con `npm --prefix backend run start:compartido`).

Docker y gcloud no están disponibles en el equipo de preparación, así que la imagen **no se construyó ni probó**. Comandos previstos para la revisión final:

```sh
docker build -t amancay-api-dev ./backend
docker run --rm -p 8080:8080 \
  -e AMANCAY_ENTORNO=compartido \
  -e AMANCAY_PROJECT_ID=rincon-amancay \
  -e AMANCAY_STORAGE_BUCKET=<bucket-confirmado> \
  -e AMANCAY_ORIGENES_WEB=<origen-https> \
  amancay-api-dev
```

Falta construir en Linux, verificar el binario nativo de Sharp, revisar el tamaño de la imagen y probar la terminación por SIGTERM que ya espera el servidor compartido.

## Primer administrador (procedimiento propuesto)

`backend/scripts/admin-compartido.ts` crea la primera cuenta administradora real usando Application Default Credentials. Rechaza emuladores y `GOOGLE_APPLICATION_CREDENTIALS`, exige el proyecto `rincon-amancay` y `--confirmar`, no modifica identidades existentes y revierte lo creado si falla. Genera el enlace de configuración de contraseña (un solo uso) sin escribir credenciales en disco.

```sh
cd backend
$env:AMANCAY_ENTORNO='compartido'; $env:AMANCAY_PROJECT_ID='rincon-amancay'
$env:AMANCAY_ADMIN_EMAIL='<correo real>'; $env:AMANCAY_ADMIN_NOMBRE='<nombre>'
npm run admin:compartido -- --confirmar
```

Requiere facturación/ADC resueltos y reglas desplegadas; todavía no se ejecutó. Las plantillas de Auth y la entrega de invitaciones se revisan al desplegar.

## Archivos revisables

`infra/plan-desarrollo.json` conserva decisiones propuestas, supuestos y campos por confirmar. `infra/backend-compartido.env.example` enumera parámetros futuros sin credenciales. No son manifiestos ejecutables. Desde la raíz:

```sh
npm --prefix backend run plan:desarrollo
```

Ese comando calcula el escenario y muestra pendientes. No inicia sesión, no importa Firebase Admin, no consulta ni modifica la nube y no estima un total monetario sin tarifas verificadas.
