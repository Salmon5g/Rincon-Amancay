# Administración: conexión real de lectura

Actualizado el 12 de octubre de 2026. Este modo permite probar Authentication y Firestore sin API desplegada. No habilita operaciones de negocio ni imágenes.

## Estado verificado en consola

La base `(default)` de `rincon-amancay` existe en `southamerica-west1`. En la revisión inicial de esta integración estaba vacía y sus reglas denegaban todas las lecturas/escrituras. Authentication aún mostraba el proveedor correo/contraseña sin habilitar. Estos requisitos de nube deben completarse antes de considerar verificado un inicio de sesión real.

## Preparación del responsable de Firebase

1. Habilitar Correo electrónico/contraseña en Authentication.
2. Crear una cuenta de prueba en Authentication. El propietario introduce la contraseña directamente en la consola; no se guarda en el repositorio ni se envía por chat.
3. Revisar y publicar las reglas acordadas de `firebase/firestore.rules`. No abrir la base indiscriminadamente. Revisar el cambio con el responsable del proyecto antes de publicarlo.
4. Autorizar la cuenta elegida mediante documentos `usuarios/{uid}` (nombreMostrar, creadoEn, actualizadoEn) y `accesos/{uid}` (estado `activo`, roles con `administrador`, creadoEn, actualizadoEn). Las fechas son Timestamp y el ID es el UID de Authentication. Esta preparación es una operación privilegiada del responsable, no una escritura desde el navegador de la aplicación.

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
- Asociaciones, ferias y configuración no se consultan: todavía requieren integración. Crear, editar, invitar y otras escrituras están deshabilitadas en esta interfaz.
- La prueba anónima `npm --prefix web run check:firebase` debe dar `permission-denied`; eso no verifica el acceso autenticado. Durante el login, ese mismo error al consultar `accesos/{uid}` indica revisar reglas y permisos.
- Cambios de roles se comprueban al iniciar/restaurar sesión y antes de consultar un listado. Las reglas son la autoridad para cada petición; la interfaz no observa cambios de rol en tiempo real.
- No hay conexión a Storage ni API de negocio en el modo real-lectura. La API completa seguirá usando `cliente-compartido.ts` cuando exista su URL HTTPS.

No considerar terminada la verificación remota hasta comprobar login, acceso administrador, lectura real y cierre de sesión con la cuenta autorizada.
