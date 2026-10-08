# Rincón Amancay — estructura inicial

Base local para revisar en VS Code. No incluye la web de demostración anterior. Se registró la app web en Firebase y se creó Firestore con reglas cerradas. web/ ya incluye el SDK y una prueba de comunicación sin sesión; aún no hay interfaz Nuxt, app Android compilable ni operaciones de servidor. Estado detallado en docs/firebase-desarrollo.md.

## Abrir en Visual Studio Code

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

1. Revisar esta estructura, añadida al clon local del grupo que solo contenía .git. Todos los archivos están pendientes de revisión y commit por el usuario.
2. Pasar los acuerdos aprobados del modelo provisional a contratos/. Los documentos anteriores siguen fuera del repositorio hasta que el usuario decida incorporarlos.
3. Preparar Firebase de desarrollo y la primera conexión de web y Android.
4. Implementar y probar permisos por recurso. Las reglas iniciales aquí deniegan todos los accesos de clientes.
5. Elegir el servicio para operaciones privilegiadas y preparar su entorno ejecutable. Cloud Functions es una posibilidad pendiente de evaluar, no una decisión tomada.

web/ tiene package.json y package-lock.json para la conexión Firebase; ver su README para instalar y comprobar. No hay npm run dev todavía. Android y backend tendrán sus propios entornos al inicializarse. El archivo firebase.json no crea un proyecto en la nube ni colecciones de Firestore.

Se conserva la configuración Git existente del clon. No se hicieron commits, push ni cambios a sus remotos. .firebaserc asocia el alias development al proyecto rincon-amancay. No se desplegó la web ni código de servidor. Los commits y push los realiza el usuario después de revisar.
