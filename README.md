# Rincón Amancay

Plataforma para apoyar a las emprendedoras de la feria Rincón Amancay y facilitar que las personas conozcan sus productos. El repositorio reúne la web para compradores y administración, la aplicación Android, la API y la configuración de Firebase.

La web está publicada en [rincon-amancay.web.app](https://rincon-amancay.web.app). El catálogo comprador se puede recorrer sin iniciar sesión; el acceso administrativo se encuentra en `/admin`.

## Estado del proyecto

- **Web:** una aplicación Nuxt 4, Vue y TypeScript con área pública de comprador y panel de administración. El panel permite gestionar emprendedoras, catálogos, asociaciones, ferias y cuentas administradoras mediante API autenticada en modo `real-api`; también guarda la preferencia de navegación del comprador. La web y Android todavía no aplican esa preferencia; la navegación libre sigue siendo el valor seguro. Las invitaciones a ferias se registran para coordinación manual y no envían mensajes. La página pública de ferias, cambio administrativo de credenciales y carga de imágenes a Storage siguen pendientes. La publicación de Hosting debe reconstruirse con `real-api` y una URL HTTPS para habilitar estas operaciones desde el sitio público.
- **Android:** aplicación unificada Kotlin y Jetpack Compose en `android/app-combinada`, con entrada por modo comprador o emprendedora. El modo comprador lee tiendas y productos publicados desde Firestore en tiempo real y es de solo lectura. El modo emprendedora conserva por ahora un repositorio de demostración en memoria y no escribe en Firebase. El proyecto comprador anterior se conserva en `android/`.
- **Backend:** API HTTP autenticada con operaciones de cuentas, invitaciones, catálogos, asociaciones, ferias, configuración de acceso y administración de roles, además de publicación, productos, stock e imágenes. Puede ejecutarse con emuladores locales o contra Firebase real mediante ADC; la API compartida todavía debe estar activa o desplegada para que otros equipos la consuman.
- **Firebase y modelo compartido:** reglas de Firestore y Storage, índices, datos de prueba y contratos que documentan entidades, permisos y operaciones del proyecto.

El modelo de catálogo es provisional y puede evolucionar tras el levantamiento de requerimientos en terreno. Coordinen cualquier cambio de campos o estados entre web, Android, API y contratos.

## Inicio rápido

Requisitos de desarrollo: Node.js 24.15 o posterior, npm, Java y Android Studio para trabajar con la app móvil. En Windows PowerShell, se puede usar `npm.cmd` si la política local bloquea `npm.ps1`.

### Web y servicios locales

La guía [Arranque por equipo](docs/arranque-equipos.md) explica la preparación común, los emuladores, los datos ficticios y la ejecución de cada área. Para empezar, instala las dependencias desde la raíz:

```powershell
npm.cmd --prefix firebase ci
npm.cmd --prefix backend ci
npm.cmd --prefix web ci
```

Configura `web/.env` desde `web/.env.example` según el entorno de trabajo. Para desarrollo local, sigue la guía y usa el modo de emuladores. Luego inicia la web:

```powershell
npm.cmd --prefix web run dev
```

Abre `http://localhost:3000` para el catálogo o `http://localhost:3000/admin` para administración.

### Aplicación Android

Abre `android/app-combinada` en Android Studio, sincroniza Gradle y ejecuta la configuración `app` en un emulador o dispositivo. También puedes compilar y ejecutar las pruebas unitarias desde PowerShell:

```powershell
cd android/app-combinada
.\gradlew.bat testDebugUnitTest assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Conexión Android a Firebase

La app unificada usa el proyecto Firebase configurado por `android/app-combinada/app/google-services.json`, archivo local ignorado por Git. Debe corresponder al ID Android `com.example.amancay`. El modo comprador solo puede mostrar las proyecciones públicas permitidas por las reglas de Firestore; la guía [Arranque por equipo](docs/arranque-equipos.md#2-equipo-app-android) documenta las condiciones de publicación. El modo emprendedora aún requiere integrar su acceso y operaciones para sincronizar cambios con Firestore.

## Estructura del repositorio

```text
web/                    Aplicación Nuxt para comprador y administración
android/app-combinada/   Proyecto Android que contiene la app unificada (ejecutar módulo app)
android/                 Proyecto comprador anterior
android/ejemplos/        Ejemplos de integración para equipos Android
backend/                 API y operaciones de negocio
firebase/                Reglas, índices, emuladores y pruebas de seguridad
contratos/               Modelo y acuerdos de datos y operaciones
datos-prueba/            Datos ficticios para desarrollo y demostraciones
docs/                    Guías de equipo, arquitectura, entorno y despliegue
firebase.json            Configuración Firebase y Firebase Hosting
rincon-amancay.code-workspace
```

## Comprobaciones útiles

Las pruebas del backend y de reglas requieren el entorno indicado en sus guías. No ejecutes suites que restauran datos mientras otra persona está editando o demostrando el mismo catálogo.

```powershell
npm.cmd --prefix web run typecheck
npm.cmd --prefix web run test:unit
npm.cmd --prefix backend run typecheck
npm.cmd --prefix backend test
```

Para conocer los comandos de emuladores y pruebas de reglas, consulta [Firebase local](firebase/README.md) y [arranque por equipo](docs/arranque-equipos.md).

## Publicación y coordinación

La web está desplegada en Firebase Hosting. Para generar y publicar una versión, sigue [Publicación web en Firebase Hosting](docs/hosting-web.md). La API tiene una guía para la ejecución local y otra para preparar un entorno compartido en [Entorno compartido](docs/entorno-compartido.md).

La [guía de integración](docs/integracion-equipos.md) y los archivos de [contratos](contratos/README.md) son el punto de coordinación entre las áreas. Las credenciales locales se mantienen fuera de Git: no subas archivos `.env`, `google-services.json` ni claves de cuentas de servicio.
