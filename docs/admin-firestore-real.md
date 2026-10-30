# Administración: conexión real de lectura

Para ejecutar operaciones reales de backend en una demo local, usar [API local contra Firebase real](admin-firebase-real-api-local.md); este documento cubre solo login y lecturas.

Actualizado el 13 de octubre de 2026. Este modo permite usar Authentication y Firestore reales sin API desplegada; por sí solo no habilita operaciones de negocio ni imágenes.

## Estado verificado en consola

La base `(default)` de `rincon-amancay` existe en `southamerica-west1`. El responsable confirmó que habilitó correo/contraseña, creó una cuenta con sus documentos `usuarios/{uid}` y `accesos/{uid}`, publicó reglas y pudo entrar a la web y leer su acceso de administrador. El contenido actual de las reglas en consola debe compararse con `firebase/firestore.rules`; no se verificó aquí que ambos archivos estén sincronizados.

## Preparación del responsable de Firebase

1. Verificar que Correo electrónico/contraseña permanezca habilitado.
2. Usar la cuenta ya creada; no recrearla ni compartir su contraseña.
3. Comparar y publicar las reglas acordadas de `firebase/firestore.rules`. No abrir la base indiscriminadamente.
4. Comprobar que la cuenta autorizada tenga `usuarios/{uid}` y `accesos/{uid}` con estado `activo` y el rol `administrador`. Las fechas son Timestamp y el ID es el UID de Authentication. La asignación del rol es una operación privilegiada del responsable, no una escritura desde el navegador.

Las reglas permiten al usuario leer su propio acceso y al administrador consultar accesos y perfiles de emprendedoras. No permiten leer perfiles privados de otros usuarios ni otorgarse roles desde el cliente. Los catálogos se consultan con `activo == true`. No ejecutar los scripts seed/cuentas:demo contra la nube.

## Preparación de cada desarrollador

1. Instalar dependencias con `npm --prefix web ci` desde la raíz.
2. Crear `web/.env` a partir de `.env.example` si aún no existe. Completar los valores SDK obtenidos en Configuración del proyecto → Tus apps → aplicación web.
3. Poner `NUXT_PUBLIC_FIREBASE_MODO=real-lectura`. Mantener `NUXT_PUBLIC_API_URL` vacía. No añadir credenciales de cuentas de servicio.
4. Ejecutar `npm --prefix web run dev` y abrir `http://localhost:3000/admin`.
5. Verificar la etiqueta «Firebase real · solo lectura», ingresar y abrir Administradores. Una consulta exitosa debe mostrar el UID de la cuenta autorizada. En Emprendedoras o Catálogos una lista vacía puede ser correcta si todavía no hay datos.
6. Usar «Actualizar listado» para repetir la consulta desde servidor. Cerrar sesión y comprobar que el panel y los listados dejan de estar visibles.

El único plugin es `web/app/plugins/firebase.client.ts`. Selecciona el entorno; no instalar simultáneamente los ejemplos de plugins. Cambiar el modo exige reiniciar Nuxt y recargar la página. Los emuladores siguen disponibles con `NUXT_PUBLIC_FIREBASE_MODO=local`.

## Alcance

- El adaptador de lectura usa Authentication y Firestore directamente, bajo las reglas publicadas.
- Listados de hasta 50 registros por colección, sin paginación todavía. Los contadores representan los registros cargados, no totales globales. Administradores y emprendedoras se identifican por UID cuando el contrato no permite leer el nombre privado.
- Asociaciones, ferias y configuración todavía requieren modelo y operaciones de servidor. En el modo `real-lectura` las escrituras están deshabilitadas; el modo `real-api` permite crear/cancelar invitaciones, desactivar/reactivar emprendedoras y administrar sectores, categorías, tipos de emprendimiento y tipos de producto desde la web, como explica la [guía de API local real](admin-firebase-real-api-local.md). Las reglas locales deben publicarse para que la web lea los catálogos inactivos.
- La prueba anónima `npm --prefix web run check:firebase` debe dar `permission-denied`; eso no verifica el acceso autenticado. Durante el login, ese mismo error al consultar `accesos/{uid}` indica revisar reglas y permisos.
- Cambios de roles se comprueban al iniciar/restaurar sesión y antes de consultar un listado. Las reglas son la autoridad para cada petición; la interfaz no observa cambios de rol en tiempo real.
- No hay conexión a Storage ni API de negocio en el modo real-lectura. Para operaciones, `real-api` conecta con la API local por loopback o con un servicio desplegado mediante HTTPS.

No considerar terminada la verificación remota hasta comprobar login, acceso administrador, lectura real y cierre de sesión con la cuenta autorizada.
