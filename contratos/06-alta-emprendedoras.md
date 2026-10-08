# Alta administrativa de emprendedoras — local

Implementada en los emuladores; no desplegada. Authentication identifica a la persona; Firestore conserva los permisos y la tienda asignada. Crear una identidad de correo/contraseña no concede permisos de gestión.

## Operación

`POST /api/v1/altaEmprendedora`, con `Content-Type: application/json` y `Authorization: Bearer <ID_TOKEN>` de una administradora activa.

```json
{
  "operacionId": "uuid-de-la-intencion",
  "uidDestino": "uid-existente-en-Authentication",
  "tiendaId": "id-nuevo-de-tienda",
  "nombreMostrar": "Nombre de la emprendedora",
  "nombreTienda": "Nombre de la tienda",
  "descripcion": "Descripción inicial",
  "sectorId": "sector_demo",
  "tipoEmprendimientoId": "mixto_demo",
  "mostrarPrecios": true,
  "historialVentasActivo": false,
  "formaContacto": "formulario"
}
```

Todos los campos son obligatorios; no hay valores por defecto silenciosos. Los IDs admiten letras, números, guion y guion bajo, hasta 128 caracteres. nombreMostrar admite 100 caracteres, nombreTienda 120 y descripcion 2000; textos no vacíos. formaContacto: whatsapp, formulario o ambos. Las preferencias se guardan, pero no implementan todavía formularios, WhatsApp ni ventas.

El cliente no envía roles, estado, propietarioUid, creadaPor, fechas, correo ni contraseña. uidDestino identifica a la cuenta que recibirá la tienda; la identidad administradora siempre procede del token verificado. No permite darse de alta a sí misma.

La cuenta destino debe existir en Auth, tener correo y estar habilitada. Se admite una cuenta sin accesos o con el único rol comprador activo y sin tienda. Se rechazan cuentas desactivadas o ya asignadas. No se concede el rol administrador por esta API.

## Escritura y reintentos

En una transacción se comprueban otra vez los permisos administrativos y se crean:

- usuarios/{uid}: perfil inicial, solo si no existe. Se conserva el nombre elegido en un perfil existente.
- accesos/{uid}: rol emprendedora, estado activo y tiendaId; si ya era compradora, conserva ese rol y su creadoEn.
- emprendedoras/{uid}: tiendaId y creadaPor.
- tiendasPrivadas/{tiendaId}: propietaria y ficha en estado borrador.
- tiendasPrivadas/{tiendaId}/configuracion/general: preferencias explícitas.
- operacionesCuentas/{administradoraUid}_{operacionId}: comprobante privado del servidor, sin contraseñas ni tokens.

Sector y tipo deben estar activos. No se sobrescriben tiendas, configuraciones ni perfiles de emprendedora existentes; se rechazan rutas con productos o ventas huérfanos. No se crea proyección pública. Publicar es una operación posterior de la propietaria.

Respuesta 200: `{"datos":{"uid":"...","tiendaId":"...","operacionId":"..."}}`. La misma solicitud con el mismo operacionId devuelve el mismo resultado, sin cambiar fechas ni duplicar documentos. Reutilizarlo con otros datos devuelve 409. Dos asignaciones concurrentes a una cuenta solo pueden producir una tienda.

400: campos o catálogo inválidos; 401: sesión inválida; 403: falta de permisos; 404: identidad destino inexistente; 409: cuenta/tienda incompatible o ID reutilizado; 503: Auth temporalmente no disponible. El transporte conserva los límites de tamaño y CORS de 04-api-local.md.

## Cuentas de práctica

Con emuladores activos, ejecutar primero `npm run seed` desde firebase/ y después `npm run cuentas:demo` desde backend/.

Cada ejecución crea dos identidades nuevas: una administradora y una candidata sin permisos. Guarda correos, contraseñas ficticias y una solicitud de ejemplo en `backend/.local/cuentas-<uuid>.json`, excluido de Git. No imprime contraseñas, no cambia cuentas existentes ni utiliza credenciales de producción. Esta herramienta técnica puede preparar el primer administrador local; no es un endpoint ni un mecanismo de altas de producción.

Para probar desde una terminal PowerShell ubicada en backend/, reemplazar la ruta por la que informó el comando:

```powershell
$demo = Get-Content -Raw '.local/cuentas-<uuid>.json' | ConvertFrom-Json
$loginBody = @{email=$demo.administradora.email; password=$demo.administradora.password; returnSecureToken=$true} | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key' -ContentType 'application/json' -Body $loginBody
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/api/v1/altaEmprendedora' -Headers @{Authorization="Bearer $($login.idToken)"} -ContentType 'application/json' -Body ($demo.solicitudAlta | ConvertTo-Json)
```

La candidata podrá iniciar sesión con sus propias credenciales y leer su tienda privada; la administradora no obtiene acceso a esa ficha ni a ventas. La interfaz del emulador sí es una herramienta técnica privilegiada, por lo que no prueba permisos de cliente.

Los datos del emulador no persisten tras reiniciarlo. El JSON local puede quedar obsoleto: volver a ejecutar seed y cuentas:demo después de un reinicio. Repetir cuentas:demo con los emuladores activos crea otro escenario, no restaura el anterior. No ejecutar seed durante ediciones del catálogo ficticio.

## Alcance y pendientes

El flujo de invitación, recuperación/verificación de correo, alta de compradores y creación del primer administrador de producción siguen pendientes. Este incremento trabaja con identidades ya existentes; no manda correos ni gestiona contraseñas desde la API.

Auth y Firestore no comparten transacción. Se comprueba Auth antes de escribir; una deshabilitación o eliminación simultánea de la identidad requiere coordinación/reconciliación futura. Desactivación administrativa coordinada, reasignación de tiendas, eliminación de cuentas y retención de comprobantes no están implementadas. No se considera este flujo listo para producción.

## Pruebas

`npm run typecheck` y `npm run test:cuentas` desde backend/. 19 casos (20 contando el grupo padre): autenticación, autorización, datos inválidos, estado de cuenta, ausencia de altas parciales, relaciones, reintentos, concurrencia, rutas huérfanas y lectura privada usando tokens reales del emulador. La suite crea IDs aislados y elimina únicamente sus datos y usuarios temporales.

Referencia: [gestión de usuarios con Firebase Admin](https://firebase.google.com/docs/auth/admin/manage-users).
