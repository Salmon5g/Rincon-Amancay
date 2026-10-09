# Rincón Amancay — estructura inicial

Base compartida para trabajar en VS Code. Incluye API autenticada de publicación/retiro, reglas de permisos y validación básica de imágenes, probadas en emuladores de Auth, Firestore y Storage. web/ contiene el SDK; todavía no hay interfaz Nuxt ni app Android compilable. La nube mantiene Firestore cerrado y no tiene Storage provisionado. Estado detallado en docs/firebase-desarrollo.md.

## Abrir en Visual Studio Code

Para integrar las aplicaciones, comenzar por [la guía para los equipos](docs/integracion-equipos.md): arranque local, cuentas de práctica, cliente web y ejemplos Kotlin. Web y Android de un mismo computador usan la misma instancia; las instancias locales de distintos computadores no comparten datos automáticamente.

Usar Archivo → Abrir carpeta y seleccionar esta carpeta completa, o abrir rincon-amancay.code-workspace. El explorador mostrará las áreas del proyecto juntas. Android podrá abrirse también en Android Studio cuando el equipo cree el proyecto Gradle.

```text
rincon-amancay-estructura/
├── web/                   Una web Nuxt para todos los perfiles
│   └── app/
│       ├── plugins/       Inicialización del SDK cliente Firebase
│       ├── repositories/ Acceso a datos desde la web
│       └── types/        Tipos TypeScript de la web
├── android/               Una app Kotlin con ambos modos
├── backend/
│   ├── src/
│   │   ├── config/        Conexiones de servidor
│   │   ├── modules/       Operaciones organizadas por función del negocio
│   │   └── shared/        Autorización y errores comunes del servidor
│   └── tests/             Pruebas de operaciones del servidor
├── firebase/
│   ├── firestore.rules   Permisos de documentos
│   ├── firestore.indexes.json
│   ├── storage.rules     Permisos de imágenes y archivos
│   └── tests/             Pruebas de reglas
├── contratos/             Acuerdos independientes de Kotlin/TypeScript
├── datos-prueba/           Datos ficticios para pruebas locales
├── docs/                  Arquitectura y organización del equipo
├── firebase.json          Rutas de reglas y configuración local de emuladores
├── rincon-amancay.code-workspace
├── .editorconfig
└── .gitignore
```

## Qué hacemos ahora

Avance local: ya están disponibles los emuladores y una carga reproducible de 29 documentos ficticios. Ver [guía de ejecución y pruebas](docs/emuladores.md). Esta carga no se ha enviado al proyecto real; los siguientes puntos conservan el plan general.

1. Revisar los avances locales y sus commits antes de compartirlos con el grupo.
2. Pasar los acuerdos aprobados del modelo provisional a contratos/. Los documentos anteriores siguen fuera del repositorio hasta que el usuario decida incorporarlos.
3. Preparar Firebase de desarrollo y la primera conexión de web y Android.
4. Completar invitaciones/registro de producción y la integración de clientes. El alta administrativa local está descrita en contratos/06-alta-emprendedoras.md; productos y variantes en contratos/07-edicion-productos.md. Los ajustes de stock están implementados según contratos/08-ajustes-stock.md y la desactivación coordinada según contratos/09-desactivacion-cuentas.md. Registro, invitaciones y reactivación locales están implementados en [contrato 10](contratos/10-ciclo-cuentas.md). Reconciliación automática, correos reales y despliegue siguen pendientes.
5. Elegir el alojamiento del backend, completar el procesamiento de imágenes y evaluar costos antes del despliegue. Cloud Functions es una posibilidad pendiente de evaluar.

web/ tiene package.json y package-lock.json para la conexión Firebase; no tiene npm run dev todavía. backend/ sí dispone de npm run dev y pruebas automatizadas. Android está pendiente de inicialización. El archivo firebase.json no crea un proyecto en la nube ni colecciones de Firestore.

Se conserva la configuración Git existente del clon. Los avances se guardan en commits locales por autorización del usuario; el usuario realizará el push. No se modificaron remotos ni se desplegaron reglas, web o servidor. .firebaserc asocia development a rincon-amancay; los scripts de emuladores fijan demo-rincon-amancay.
