# 5. Reglas de seguridad de Firestore y almacenamiento

## Alcance y estado

La identidad y autorización de la aplicación se mantienen en Firebase Authentication y Firestore. Las fotos, logos y derivadas visibles del catálogo se gestionan en Supabase Storage.

El 1 de noviembre de 2026 se consultó el release activo de reglas de `rincon-amancay` mediante Firebase Rules API. El release `projects/rincon-amancay/releases/cloud.firestore` apunta al ruleset `4005f7fb-84a4-43ce-bedc-166b2ae2431c`, y su contenido coincide con `firebase/firestore.rules`. Por tanto, las reglas de Firestore de este repositorio están publicadas en ese proyecto.

El grupo confirmó que ejecutó `infra/supabase/storage-imagenes.sql` en el Studio de la instancia del curso y obtuvo `Success. No rows returned`. En las comprobaciones manuales con una cuenta normal de Supabase, el inicio de sesión funcionó y Storage rechazó con HTTP 400 la lectura de un original privado, la carga de un PNG nuevo en `amancay-originales` y la carga de un WebP nuevo en `amancay-catalogo`. Como control de lectura pública, una solicitud sin sesión descargó una derivada del catálogo con HTTP 200.

La política restrictiva `amancay_restringe_storage_cuenta_tecnica`, aplicada `FOR ALL` a ambos buckets, exige que el UID autenticado sea el de la cuenta técnica. Por esa condición, las operaciones de reemplazo y borrado de una cuenta también quedan bloqueadas por las reglas. Las políticas específicas de borrado conceden esa operación únicamente al mismo UID técnico. Los resultados HTTP anteriores corresponden a las solicitudes que se ejecutaron; la cobertura de reemplazo y borrado queda descrita por las políticas aplicadas.

## Permisos por perfil

| Perfil o servicio | Firestore | Imágenes |
|---|---|---|
| Visitante anónimo | Puede leer tiendas publicadas y habilitadas, productos publicados cuando la tienda muestra precios, variantes activas y catálogos activos. No escribe. | Puede descargar las derivadas WebP del bucket público `amancay-catalogo`. No puede leer ni cargar originales. |
| Comprador con sesión | Conserva las mismas lecturas públicas; puede leer su propio documento de usuario y acceso. No recibe permisos para modificar catálogos, cuentas o tiendas privadas. | Igual que el visitante para imágenes públicas; no puede administrar archivos. |
| Emprendedora activa | Puede leer su perfil y los datos privados de su tienda si su UID coincide con el rol, `tiendaId` y `propietarioUid`. Sus cambios directos están limitados a campos de perfil/texto e imágenes seleccionadas del borrador. No puede asignarse roles, modificar stock, publicar ni escribir proyecciones públicas desde el cliente. | La aplicación envía archivos a la API. La API valida el ID token Firebase, el rol, la tienda y la propiedad; luego usa la cuenta técnica normal de Supabase. La emprendedora no recibe esas credenciales. |
| Administrador activo | Puede leer los documentos administrativos permitidos por las reglas. Altas, cambios de roles, asociaciones, cuentas y otros cambios privilegiados pasan por la API autenticada; el cliente no escribe roles directamente. | No tiene acceso directo a los buckets ni a la cuenta técnica. |
| API local | Valida cada operación y mantiene sincronizados los documentos privados y públicos. Usa Firebase Admin, que no está sujeto a reglas Firestore; por eso las comprobaciones de rol, estado, propiedad y validación de entrada deben ocurrir en la API. | Usa la cuenta Auth técnica de Supabase. RLS restringe operaciones de los buckets de Amancay a ese UID y a las rutas autorizadas. La clave `service_role` no se usa. |

Las reglas consultan documentos de `accesos/{uid}` para confirmar estado, roles y revocación de sesiones. Las proyecciones públicas se leen solo con sus estados habilitados. El código de reglas falla cerrado para documentos no contemplados.

## Política de las imágenes

La política efectiva se define en [`infra/supabase/storage-imagenes.sql`](../../infra/supabase/storage-imagenes.sql) y el flujo de aplicación en [`docs/supabase-storage-imagenes.md`](../supabase-storage-imagenes.md):

- `amancay-originales` es privado. Solo la cuenta técnica puede cargar, leer o borrar originales y únicamente dentro de las rutas de tienda/producto admitidas.
- `amancay-catalogo` es público para lectura por URL. Solo la cuenta técnica puede crear, leer autenticadamente o borrar derivadas procesadas en las rutas permitidas.
- Una cuenta normal de Supabase no puede operar sobre ninguno de los dos buckets: la política restrictiva global exige el UID de la cuenta técnica para toda operación. En particular, reemplazos y borrados también quedan bloqueados para cuentas normales.
- La API comprueba la identidad Firebase de la emprendedora antes de usar la cuenta técnica de Supabase. RLS por sí sola identifica esa cuenta técnica, no el perfil Firebase que inició la solicitud.
- Una URL del bucket público puede compartirse y quedar en caché; retirar un producto elimina sus derivadas desde la API, pero no revoca copias ya descargadas o almacenadas en caché.
- El bucket de catálogo solo admite WebP procesado y los buckets limitan cada archivo a 5 MiB. Las imágenes elegidas por la emprendedora no se publican hasta validarse y procesarse.

La fuente ejecutable de las políticas de imágenes es el SQL de Supabase. Volver a aplicarlo elimina y recrea las políticas `amancay_*` de `storage.objects`, así que hay que revisar el archivo antes de ejecutarlo.


