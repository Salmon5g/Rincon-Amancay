# Firebase de desarrollo — estado al 8 de octubre de 2026

## Configurado y comprobado

Backend local: cuatro operaciones de publicación/retiro y API HTTP autenticada contra emuladores implementadas y probadas; ver ../backend/README.md y ../contratos/04-api-local.md. Incluye validación básica de objetos de Storage local; ver ../contratos/05-imagenes-storage.md. No hay API desplegada ni bucket en la nube. Las pruebas HTTP usan cuentas temporales de Auth local que se eliminan al terminar.

Actualización local: reglas de catálogo público y permisos básicos incorporadas al emulador, con suite de sesiones simuladas. Ver permisos-locales.md. No se publicaron a la nube ni se crearon cuentas de prueba reales.

- Proyecto existente del usuario: rincon-amancay. Plan Spark conservado.
- App web registrada: rincon-amancay-web; appId 1:980190819335:web:944ac61168ac9c7ad5a6a2.
- Firestore Standard, base (default), ubicación southamerica-west1 (Santiago). Base vacía y reglas iniciales que deniegan todas las lecturas/escrituras cliente. La región elegida no se cambia en esta base.
- Authentication con correo electrónico/contraseña habilitado y confirmado en la consola. Acceso mediante vínculo de correo y proveedor anónimo no habilitados. No se crearon usuarios ni se probó un inicio de sesión completo.
- Alias local development en .firebaserc. No se inició sesión de Firebase CLI ni se descargaron credenciales Admin.
- SDK cliente Firebase 13.0.0 y TypeScript 5.9.3 instalados en web/. Configuración local en .env ignorado por Git, plantilla en .env.example.
- Comprobación real contra Firestore: una lectura sin sesión a usuarios/comprobacion_sin_sesion respondió permission-denied. No se creó ese documento ni se escribieron datos. Esto verifica comunicación y rechazo de esa petición, no el conjunto futuro de permisos.
- npm run typecheck pasó. npm audit terminó con 0 vulnerabilidades reportadas tras el ajuste descrito abajo.

## Ajuste de dependencia

Firebase incluyó @grpc/grpc-js ~1.9.0 con avisos altos de npm audit. Se añadió override a 1.14.5. Se repitieron typecheck y la lectura real denegada correctamente. Mantener el lockfile, revisar este override cuando Firebase actualice su dependencia y ampliar las pruebas al implementar operaciones reales. No se hizo downgrade forzado de Firebase.

## Pendiente

Avance local posterior: ver emuladores.md. Authentication y Firestore se ejecutaron con demo-rincon-amancay, se cargaron 29 documentos ficticios y se verificaron la repetición de carga y denegaciones iniciales. La base en la nube no recibió datos ni cambios de reglas. La auditoría de la CLI local es independiente de la auditoría de web/.

- Alta administrativa y sesión probadas localmente: ../contratos/06-alta-emprendedoras.md. Desactivación coordinada local: ../contratos/09-desactivacion-cuentas.md. Invitaciones, registro de compradores y reactivación locales: ../contratos/10-ciclo-cuentas.md. Queda la preparación para producción.
- Registrar Android cuando el equipo tenga su applicationId definitivo. Una app con ambos modos, no registros distintos por rol.
- Integrar el módulo de conexión en un plugin Nuxt cliente cuando se inicialice Nuxt. No se han creado pantallas ni un proyecto Nuxt ejecutable.
- Ampliar reglas y pruebas con cada operación nueva. Ya hay permisos específicos de Firestore y Storage probados localmente.
- Acordar una carga ficticia controlada para probar lecturas autorizadas desde web y Android.
- Storage no está creado: que el SDK incluya storageBucket no confirma que exista un bucket utilizable.
- Sin Hosting, Functions, nuevos usuarios, documentos, facturación Blaze ni publicación web. Analytics no se inicializó en el código cliente aunque la consola proporcionó measurementId.

El catálogo ficticio solo se cargó en Firestore local, nunca en la nube. Hay commits locales autorizados; el push queda a cargo del usuario.

## Verificación de consola — 9 de octubre de 2026

Se revisó la consola del proyecto `rincon-amancay`: plan **Spark**, sin facturación. Se confirmó la app web `rincon-amancay-web` (appId 1:980190819335:web:944ac61168ac9c7ad5a6a2) y su configuración SDK, que coincide con web/.env y .env.example. Authentication con correo/contraseña habilitado y dominios autorizados `localhost`, `rincon-amancay.firebaseapp.com` y `rincon-amancay.web.app`. Firestore base (default) en `southamerica-west1`, lista y sin reglas desplegadas. **Storage no está creado**: la consola indica que requiere Blaze; el nombre `rincon-amancay.firebasestorage.app` solo aparece en la configuración del SDK.

La lectura real sin sesión (`npm run check:firebase` en web/) respondió permission-denied, confirmando comunicación y rechazo. No se crearon usuarios, datos, bucket ni despliegues, y no se activó facturación. Ver [entorno compartido](entorno-compartido.md) para la propuesta y los bloqueos vigentes (activar Blaze, desplegar la API y registrar Android).
