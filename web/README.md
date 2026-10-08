# Web

Espacio para una única aplicación Nuxt + Vue + TypeScript. Ya incluye el SDK Firebase, un módulo de inicialización y una comprobación técnica sin pantallas. Nuxt y su interfaz todavía no están inicializados; no existe npm run dev.

Con Node.js 24.15.0, desde web/: ejecutar `npm ci`, `npm run typecheck` y `npm run check:firebase`. Este último requiere .env y hace una lectura sin sesión que debe rechazarse mientras las reglas privadas permanezcan cerradas. No crea datos. No valida un flujo completo de inicio de sesión.

La configuración local está en .env, excluido de Git. Para otro equipo, copiar .env.example a .env y completar la configuración desde Firebase. Son parámetros del SDK cliente, no credenciales Admin. Su protección efectiva depende de reglas y autorización.

app/lib/firebase.ts centraliza app, auth y db. Al inicializar Nuxt, integrar esta función en un plugin .client.ts, declarar los campos en runtimeConfig.public y pasar la configuración desde useRuntimeConfig(). Las variables NUXT_PUBLIC del archivo .env no se conectan automáticamente a esta función. El script de comprobación sí las carga explícitamente. El tsconfig actual verifica solo este módulo y deberá integrarse con el tsconfig generado por Nuxt.

app/plugins/ inicializará Firebase para el navegador; app/repositories/ reunirá las consultas y llamadas al backend; app/types/ contendrá los tipos derivados del contrato común. Nunca incluir Firebase Admin SDK o credenciales de servidor en el navegador.

El equipo web creará pages/, layouts/, components/ y assets/ al iniciar su interfaz. Comprador y administrador trabajarán aquí; no deben crear dos proyectos web independientes. Cuando corresponda, el área emprendedora también formará parte de esta web.
