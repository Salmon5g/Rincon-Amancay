# Responsabilidades y comparación con el ejemplo

El proyecto de referencia usa client/ para Nuxt y src/ para un servidor Express con rutas, controladores, middlewares y modelos Sequelize. Se revisaron su estructura, manifiesto y composición del servidor; no se revisó su archivo .env ni se modificó el proyecto.

Para Amancay conservamos la separación entre interfaz y datos. La organización propuesta cambia porque la persistencia acordada es Firestore y la identidad será Firebase Authentication.

| Ejemplo anterior | Rincón Amancay |
|---|---|
| client/ | web/, una web para todos los perfiles |
| src/ del servidor | backend/src/, operaciones privilegiadas compartidas |
| models/ Sequelize | contratos/ describe documentos; repositories de cada plataforma accede a Firestore |
| migrations/ SQL | Futuros scripts de transformación de documentos cuando hagan falta, con respaldo y revisión |
| auth propia y JWT | SDK de Firebase Authentication en clientes y validación de identidad/permisos en backend |
| No tiene app Android | android/, mismo proyecto Firebase y contratos |

La capa de datos web y la de Android son implementaciones diferentes. Comparten UID, nombres de colecciones, campos, estados y operaciones; Android no importa archivos TypeScript.

Una lectura permitida podrá ir del cliente a Firestore y pasar por sus reglas. Una operación privilegiada irá al servidor, que comprobará identidad, rol, propiedad y datos antes de escribir. Por ejemplo, publicar una tienda requerirá controlar los campos que pasan de tiendasPrivadas a tiendasPublicas. Registrar una venta y modificar stock requerirá consistencia e idempotencia según el acuerdo provisional.

Los SDK de servidor pueden omitir las reglas de Firestore: la autorización debe comprobarse también en backend. No basta con ocultar un botón o proteger una ruta Nuxt.

Firebase comprende servicios administrados, reglas e índices, además del eventual código de servidor. backend/ no significa un servidor distinto para cada perfil. No habrá un backend web y otro Android.

La propuesta de ejecución remota es Cloud Run: existen entrada compartida y Dockerfile, pendientes de construcción y despliegue según [entorno compartido](entorno-compartido.md). La API local sigue siendo el entorno de arranque de los equipos. No se incorporan Express, Sequelize, SQL ni un ORM por imitación del ejemplo.

Referencias oficiales consultadas:
- https://firebase.google.com/docs/emulator-suite/install_and_configure
- https://firebase.google.com/docs/firestore/security/rules-conditions
