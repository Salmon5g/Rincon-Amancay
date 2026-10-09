# Conexión para los equipos — desarrollo local

Atajo de inicio por rol (web comprador, web administrador, app comprador): [arranque por equipo](arranque-equipos.md).

Punto de entrada para web comprador, web administrador y Android. Todos usan el mismo modelo, reglas y API del repositorio. En esta etapa cada computador ejecuta su propia instancia de emuladores: no comparte automáticamente sus datos con los otros computadores. Web y Android conectados al mismo computador sí ven los mismos datos.

No se necesita una cuenta de consola Firebase para estas pruebas. No usar el correo del proyecto como cuenta compartida de la aplicación. No se desplegaron servicios ni se abrieron las reglas de la nube.

## Arranque desde un clon del repositorio

Requisitos usados: Node.js 24.15.0 o superior, npm y Java disponible en PATH (se probó con Java 25). Los comandos siguientes se ejecutan desde la raíz, en terminales separadas de VS Code. En PowerShell, usar npm.cmd si npm.ps1 está bloqueado.

```powershell
# Instalar una vez y repetir si cambian los lockfiles
npm --prefix firebase ci
npm --prefix backend ci
npm --prefix web ci
```

```powershell
# Terminal 1: dejar abierta y esperar el mensaje All emulators ready
npm --prefix firebase run emulators
```

```powershell
# Terminal 2: preparar datos y luego dejar la API abierta
npm --prefix firebase run seed
npm --prefix backend run cuentas:demo
npm --prefix backend run dev
```

Interfaz técnica local: http://127.0.0.1:4000. Salud de la API: http://127.0.0.1:8787/health. Proyecto obligatorio: demo-rincon-amancay. Auth 9099, Firestore 8080, Storage 9199. La API solo acepta tokens de este proyecto demo.

seed restablece los 29 documentos conocidos del catálogo ficticio. cuentas:demo crea una administradora y una candidata con correos/contraseñas ficticios, en un archivo backend/.local/cuentas-<uuid>.json excluido de Git. Cada ejecución crea otro escenario. El archivo también contiene solicitudAlta para convertir a la candidata en emprendedora. Sus IDs no son los UID del fixture.

Los emuladores pierden sus datos al reiniciarse. Un archivo de credenciales guardado puede quedar obsoleto: ejecutar seed y cuentas:demo otra vez. No ejecutar suites de pruebas ni seed mientras alguien edita el catálogo ficticio. La consola del emulador tiene privilegios técnicos: sus pantallas no comprueban los permisos del usuario de la app.

## Reparto de responsabilidades

| Equipo | Qué integrar primero |
|---|---|
| Web comprador | Cliente local, consulta pública de tiendas/productos/variantes, imágenes y paginación. No requiere iniciar sesión. |
| Web administrador | Sesión, lectura de accesos, altaEmprendedora y desactivarEmprendedora. El rol administrador no permite leer fichas privadas o ventas de emprendedoras. |
| Web emprendedora | Sesión y acceso asignado, ficha privada, crear/editar productos, imágenes, publicación y ajustes de stock. |
| Android comprador | Inicializador debug y las mismas consultas públicas. No inventar otro modelo o nombres de campos. |
| Backend | Contratos, permisos, validaciones, operaciones privilegiadas y configuración de entorno. |

Comprador y administrador web integran un único proyecto Nuxt. Los repositorios de datos llaman al cliente; los componentes no duplican inicializadores. Elegir un modo de pantalla no concede roles.

## Nuxt/Vue

web/app/lib/firebase-local.ts inicializa los tres SDK en localhost. web/app/lib/cliente-local.ts contiene sesión, consulta de accesos, catálogo paginado, lectura privada, subida/selección de imágenes y llamadas a todas las operaciones de la API. No lee .env ni usa Firebase Admin.

Copiar web/ejemplos/nuxt/firebase-local.client.ts.example a app/plugins/firebase-local.client.ts cuando el equipo inicialice Nuxt 4. Es un plugin solo cliente y solo desarrollo; aún no hay aplicación Nuxt ni npm run dev en web/. Adaptar el tsconfig al generado por Nuxt conservando comprobación de los módulos. Iniciar la futura web en http://localhost:3000 o http://127.0.0.1:3000, que son los orígenes permitidos por la API. No usar 3001 sin coordinar el cambio.

Uso dentro de setup, con consultas en onMounted o eventos del cliente (no SSR):

```ts
const { $amancay } = useNuxtApp();
onMounted(async () => {
  const pagina = await $amancay.listarTiendas();
  const tiendas = pagina.docs.map(d => ({ id: d.id, ...d.data() }));
  // Guardar tiendas en el estado de la pantalla; para otra página:
  // await $amancay.listarTiendas(pagina.docs.at(-1));
});
```

Una página vacía finaliza la paginación; no enviar undefined para reiniciar una consulta cuando ya se llegó al final. Conservar el DocumentSnapshot, no solo el ID, como cursor. Los resultados son documentos del contrato; dar formato de UI sin escribirlos de vuelta como copias completas.

Para iniciar sesión, obtener correo y clave del formulario y llamar iniciarSesion(correo, clave). Después consultarAcceso(): null significa identidad sin permisos asignados; no debe tratarse como administradora. Al cerrar sesión, limpiar de la interfaz cualquier estado de gestión privada. No guardar contraseñas ni tokens en variables persistentes de UI, logs o repositorio.

Ejemplo de publicación después de leer la ficha:

```ts
import { doc, getDoc } from 'firebase/firestore';
import { versionDocumento } from '~/lib/cliente-local';

const ficha = await getDoc(doc($amancay.firebase.db, 'tiendasPrivadas', tiendaId));
if (!ficha.exists()) throw new Error('Tienda inexistente');
const solicitud = {
  tiendaId,
  operacionId: crypto.randomUUID(),
  versionEsperada: versionDocumento(ficha.data().actualizadoEn),
};
await $amancay.llamar('publicarTienda', solicitud);
// Ante interrupción de conexión, conservar solicitud para el reintento.
```

Para productos, usar leerProductoPrivado y el mismo formato de versión. subirImagen devuelve una ruta; seleccionarImagenes guarda hasta cinco rutas con control de versión. Releer el producto antes de publicar. leerImagen devuelve un Blob: usar URL.createObjectURL en la vista y URL.revokeObjectURL al reemplazarlo o desmontar. No almacenar URLs de descarga con token en los documentos.

## Cuentas: nuevo flujo compartido

Consultar [ciclo de cuentas](../contratos/10-ciclo-cuentas.md) para cuerpos, permisos, reintentos y límites. Desde una acción del usuario en el cliente Nuxt:

```ts
await $amancay.crearIdentidad(correo, clave);
await $amancay.enviarVerificacion();
// En otra acción, tras recibir el enlace/código local:
await $amancay.confirmarCorreo(codigo);
await $amancay.llamar('registrarComprador', { nombreMostrar });
// Si recibió invitación, con sesión y el correo destinatario verificado:
await $amancay.llamar('aceptarInvitacion', { invitacionId });
```

Verificar identidad no concede permisos de emprendedora. No repetir crearIdentidad si únicamente falló el envío: usar enviarVerificacion. Para recuperación: solicitarRecuperacion, comprobarRecuperacion y confirmarRecuperacion; luego iniciar sesión de nuevo. Las contraseñas/códigos van únicamente al SDK Auth. Todavía deben construirse las pantallas.

## Android

Ver ../android/ejemplos/README.md: inicialización debug, dependencias, red local y llamadas Kotlin. El proyecto Android/Gradle y su applicationId todavía no existen en este repositorio; los ejemplos no se han compilado ni probado en dispositivo. No se registró una app Android real en Firebase.

## Escenario común para revisar juntos

1. Comprador: consultar las tiendas y productos del fixture sin sesión. Sus imágenes están vacías; mostrar un placeholder de UI, no asumir una URL.
2. Administradora: iniciar sesión con la cuenta local generada, llamar altaEmprendedora con solicitudAlta del archivo y conservar su operacionId para reintentos.
3. Emprendedora: cerrar la sesión administrativa, iniciar con la candidata y comprobar que consultarAcceso devuelve su tiendaId.
4. Publicar esa tienda; crear un producto siguiendo el contrato 07, subir/seleccionar su foto y publicarlo según contratos 03/05.
5. Comprador: consultar esa tienda desde la web sin sesión y, cuando se integre Android, desde el mismo computador anfitrión. Verificar coincidencia de nombre, precio e imagen.
6. Ajustar stock: conserva la ficha aprobada y actualiza su disponibilidad (contrato 11); solo los datos anteriores sin aprobación requieren republicar. Desactivar la cuenta: toda la tienda deja de ser pública.

La configuración mostrarPrecios=false todavía bloquea la lectura del catálogo de productos. Leer la tienda primero y manejar esa condición; no solicitar una consulta de productos como si fueran visibles. Esto es una limitación implementada, no un catálogo sin precios terminado.

## Errores y reintentos

Los adaptadores no reintentan escrituras automáticamente ni generan operacionId. Generarlo una vez por intención y conservar el cuerpo completo ante pérdida de conexión o 5xx. Un 503 de desactivación puede significar que Firestore ya quedó bloqueado y falta completar Auth.

401: volver a gestionar la sesión. 403: no hay permiso. 409: releer y revisar el conflicto; no convertirlo en una nueva escritura automática. 400/422: corregir datos según mensaje y contrato. resultadoIncierto=true significa que la respuesta no permite asegurar si hubo efectos; no equivale a “no se guardó”. El SDK Firebase emite sus propios errores para login/lecturas; ErrorApi cubre llamadas HTTP y conflictos de selección de imágenes.

## Verificación y límites

Con API y emuladores listos: npm --prefix web run typecheck y npm --prefix web run test:local. La prueba usa los SDK cliente de Auth, Firestore y Storage contra la API local, crea IDs aislados y limpia sus datos. No requiere copiar credenciales. Necesita dependencias instaladas también en backend/ para preparar y limpiar fixtures exclusivamente desde el proceso de test; no importa Admin en código del navegador.

Se verificaron dieciséis casos de integración del cliente web (18 tests incluyendo los dos grupos padre), incluidos cuentas, copias procesadas y disponibilidad sin publicar borradores. No se probó una interfaz Nuxt ni un navegador móvil; la descarga getBlob es de navegador y el test Node verifica la lectura equivalente con getBytes. Android queda pendiente de integrar, compilar y probar con el proyecto del equipo.

La nube sigue cerrada. Compartir datos entre computadores exige un entorno remoto de desarrollo con API desplegada y configuración propia, que se preparará tras revisión. No abrir los emuladores a internet ni reemplazar estos hosts por direcciones remotas sin cambiar el diseño de entorno.

Referencias oficiales: [plugin cliente Nuxt](https://nuxt.com/docs/4.x/guide/concepts/nuxt-lifecycle), [Auth Emulator y Android](https://firebase.google.com/docs/emulator-suite/connect_auth), [reglas de descarga de imágenes](https://firebase.google.com/docs/storage/web/download-files).

Imágenes públicas: leer imagenes y miniaturas de la proyección; los originales en tiendas/ son privados. Detalle y compatibilidad: [contrato 11](../contratos/11-disponibilidad-e-imagenes.md).
