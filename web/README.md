# Web

Espacio para una única aplicación Nuxt + Vue + TypeScript. Ya incluye el SDK Firebase, un módulo de inicialización y una comprobación técnica sin pantallas. Nuxt y su interfaz todavía no están inicializados; no existe npm run dev.

Para el trabajo del equipo, comenzar por [la guía de integración local](../docs/integracion-equipos.md). app/lib/firebase-local.ts conecta los emuladores y app/lib/cliente-local.ts ofrece sesión, catálogo, imágenes y API. El plugin de ejemplo está en ejemplos/nuxt/. No requiere .env real. Con emuladores y API activos: npm run typecheck y npm run test:local. Este último requiere también las dependencias de backend/ instaladas y crea/elimina datos de prueba aislados. No ejecutar simultáneamente con otras suites.

La comprobación anterior contra la nube sigue disponible por separado: npm run check:firebase requiere .env y hace una lectura sin sesión que debe rechazarse mientras las reglas permanezcan cerradas. No crea datos ni valida login. No es necesaria para probar el cliente local.

La configuración del proyecto real está en .env, excluido de Git. .env.example sirve para esa conexión; no usarlo para los emuladores. Los parámetros SDK no son credenciales Admin; la protección efectiva depende de reglas y autorización.

app/lib/firebase.ts conserva el inicializador del proyecto real. app/lib/cliente-base.ts concentra la lógica del cliente web; cliente-local.ts (emuladores, rechaza el proyecto real) y cliente-compartido.ts (proyecto `rincon-amancay` + API HTTPS, rechaza el demo) la usan. Antes de un despliegue habrá que definir el plugin y runtimeConfig; las variables NUXT_PUBLIC no se conectan automáticamente. El ejemplo compartido está en ejemplos/nuxt/firebase-compartido.client.ts.example. El tsconfig actual comprueba los módulos app/lib/ y deberá integrarse con el generado por Nuxt. El plugin local se niega a iniciar una compilación de producción.

app/plugins/ inicializará Firebase para el navegador; app/repositories/ reunirá las consultas y llamadas al backend; app/types/ contendrá los tipos derivados del contrato común. Nunca incluir Firebase Admin SDK o credenciales de servidor en el navegador.

El equipo web creará pages/, layouts/, components/ y assets/ al iniciar su interfaz. Comprador y administrador trabajarán aquí; no deben crear dos proyectos web independientes. Cuando corresponda, el área emprendedora también formará parte de esta web.

`npm run test:unit` valida el adaptador compartido sin conectar a Firebase real. La URL compartida es un origen HTTPS (sin `/api/v1/`, parámetros ni fragmentos); el cliente agrega la ruta y rechaza HTTP también en localhost. Para desarrollo local usar el adaptador local. No instalar ambos plugins a la vez: uno solo proporciona `$amancay` por entorno. Resultados de pruebas y límites en [verificación de la base](../docs/verificacion-base.md).
