# Rincón Amancay

Plataforma para apoyar a las emprendedoras de la feria Rincón Amancay y facilitar que las personas conozcan sus productos. El repositorio reúne la web para compradores y administración, la aplicación Android, la API y la configuración de Firebase.

La web está publicada en [rincon-amancay.web.app](https://rincon-amancay.web.app). El catálogo comprador se puede recorrer sin iniciar sesión; el acceso administrativo se encuentra en `/admin`.

## Estado del proyecto

- **Web:** una aplicación Nuxt 4, Vue y TypeScript con área pública de comprador y panel de administración. Incluye inicio de sesión administrativo, lectura de datos autorizados e interfaces para gestionar cuentas e invitaciones.
- **Android:** módulo compilable Kotlin y Jetpack Compose en `android/APP`. Incluye una experiencia móvil para emprendedoras con inicio, productos, registro de productos con fotografía y variantes, vista previa del catálogo, registro de ventas, historial y ajustes. Parte con productos de demostración para recorrer los flujos.
- **Backend:** API HTTP autenticada con operaciones de cuentas, invitaciones, publicación, productos, stock e imágenes. Tiene pruebas automatizadas y puede ejecutarse con los emuladores locales.
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

Abre `android/APP` en Android Studio, sincroniza Gradle y ejecuta la configuración `app` en un emulador o dispositivo. También puedes compilar y ejecutar las pruebas unitarias desde PowerShell:

```powershell
cd android/APP
.\gradlew.bat testDebugUnitTest assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura del repositorio

```text
web/                    Aplicación Nuxt para comprador y administración
android/APP/             Aplicación Android Kotlin y Jetpack Compose
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
