# Rincón Amancay — base compartida de desarrollo

Base compartida para web administrador, web comprador y app comprador. Incluye API autenticada, ciclo de cuentas, productos, stock con historial, publicación e imágenes procesadas, reglas y pruebas en emuladores. **Empezar por [arranque por equipo](docs/arranque-equipos.md)**. Todavía no hay interfaz Nuxt ni app Android compilable; los equipos las integran sobre estos contratos. El entorno remoto no está desplegado. Estado técnico y verificaciones en [verificación de la base](docs/verificacion-base.md).

## Abrir en Visual Studio Code

Para integrar las aplicaciones, comenzar por [la guía para los equipos](docs/integracion-equipos.md): arranque local, cuentas de práctica, cliente web y ejemplos Kotlin. Resumen de inicio por rol (web comprador, web administrador, app comprador) en [arranque por equipo](docs/arranque-equipos.md). Web y Android de un mismo computador usan la misma instancia; las instancias locales de distintos computadores no comparten datos automáticamente.

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

Hay emuladores, datos ficticios y adaptadores de integración. La documentación de [contratos](contratos/README.md) es el acuerdo común de campos y permisos; los documentos provisionales previos permanecen fuera del repositorio. Los próximos pasos son:

1. Inicializar una sola base Nuxt entre ambos equipos web y el proyecto Gradle del equipo Android, conservando adaptadores, pruebas y contratos.
2. Integrar catálogo sin sesión, gestión administrativa por invitaciones y manejo de errores según la guía de arranque.
3. Probar una modificación visible desde web y Android conectados al mismo computador; no requiere terminar las pantallas.
4. Revisar el modelo tras terreno y coordinar cambios de campos y migraciones con ambos clientes. Los límites actuales están expresos en la guía y contratos.
5. Preparar y validar el entorno remoto antes de desplegar. Hay adaptador compartido y contenedor propuesto para Cloud Run; quedan permisos, construcción del contenedor, costos, entrega real de invitaciones y validación operativa. Recuperación técnica de cuentas disponible en [este procedimiento](docs/recuperacion-cuentas.md); no hay reconciliación automática.

web/ tiene package.json y package-lock.json para la conexión Firebase; no tiene npm run dev todavía. backend/ sí dispone de npm run dev y pruebas automatizadas. Android está pendiente de inicialización. El archivo firebase.json no crea un proyecto en la nube ni colecciones de Firestore.

Se conserva la configuración Git del clon. Compartir cambios mediante commits y push no despliega servicios. No se desplegaron reglas, web o servidor. `.firebaserc` asocia development a rincon-amancay; los scripts locales fijan demo-rincon-amancay.

Preparación del entorno compartido: [propuesta de arquitectura, consumo y pendientes](docs/entorno-compartido.md). Todavía no hay despliegue.
