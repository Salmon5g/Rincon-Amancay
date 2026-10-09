# Arranque por equipo (desarrollo local)

Guía corta para que los tres equipos empiecen **en local** sin depender de la nube. Primero todos hacen la **preparación común**; después cada equipo sigue su sección. Para el detalle, ver [integración de equipos](integracion-equipos.md) y los [contratos](../contratos/).

> La conexión a la nube todavía **no** está activa. No usar los adaptadores `*-compartido` (`cliente-compartido.ts`, `ConexionCompartida.kt`), ni el `admin:compartido`, ni pedir Blaze. Todo el trabajo inicial es contra los emuladores.

## 0. Preparación común (una vez por computador)

Requisitos: **Node.js 24.15.0 o superior**, npm y **Java 21 o superior en PATH** (verificado aquí con Java 25). En PowerShell, usar `npm.cmd` si `npm.ps1` está bloqueado.

```powershell
# Desde la raíz del repositorio (solo la primera vez, o si cambian los lockfiles)
npm --prefix firebase ci
npm --prefix backend ci
npm --prefix web ci
```

```powershell
# Terminal 1 — dejar abierta hasta ver "All emulators ready"
npm --prefix firebase run emulators
```

```powershell
# Terminal 2 — preparar datos y dejar la API corriendo
npm --prefix firebase run seed
npm --prefix backend run cuentas:demo
npm --prefix backend run dev
```

Servicios y convenciones:

| Qué | Dirección |
|---|---|
| API local | http://127.0.0.1:8787 (`/health` para comprobar) |
| Consola de emuladores | http://127.0.0.1:4000 |
| Auth / Firestore / Storage | 9099 / 8080 / 9199 |
| Proyecto obligatorio | `demo-rincon-amancay` |
| Origen permitido para la web | `http://localhost:3000` o `http://127.0.0.1:3000` |

Reglas de convivencia:
- Los emuladores pierden datos al reiniciar: repetir `seed` y `cuentas:demo`.
- No correr pruebas ni `seed` mientras alguien edita el catálogo ficticio.
- Web y Android en **el mismo computador** ven los mismos datos; entre computadores **no** se comparte nada todavía.
- No inventar nombres de campos: seguir `contratos/`.

## 1. Equipo web (base común a comprador y admin)

Un solo proyecto Nuxt para todos los perfiles. Al inicializar **Nuxt 4** en `web/`:

Coordinar un único commit de inicialización entre ambos equipos web; luego desarrollar áreas separadas dentro de ese proyecto. Conservar `app/lib/`, ejemplos, pruebas, dependencias Firebase y scripts existentes: no sobrescribir la carpeta con una plantilla. Propuesta de rutas: `/` y `/tiendas/[tiendaId]` para comprador; `/admin` para administración. No existe aún una interfaz ejecutable ni `npm run dev` web. El responsable de la base Nuxt confirma el arranque antes de repartir componentes.

1. Copiar `web/ejemplos/nuxt/firebase-local.client.ts.example` a `web/app/plugins/firebase-local.client.ts`.
   Instalar solo ese plugin en esta etapa: el compartido también proporciona `$amancay` y no deben coexistir.
2. Conservar la comprobación de tipos de `app/lib/` al adaptar el `tsconfig`.
3. Correr la web en **localhost:3000** (la API rechaza otros orígenes por CORS).

Uso del cliente en componentes (consultas en cliente, no SSR):

```ts
const { $amancay } = useNuxtApp();
onMounted(async () => {
  const pagina = await $amancay.listarTiendas();
  const tiendas = pagina.docs.map(d => ({ id: d.id, ...d.data() }));
  // Para la siguiente página: await $amancay.listarTiendas(pagina.docs.at(-1));
});
```

### 1a. Web comprador (primer incremento)

Meta: catálogo público **sin sesión** (tiendas, productos, variantes, imágenes, paginación).

- No requiere iniciar sesión.
- Métodos: `listarTiendas()`, `listarProductos(tiendaId)`, `listarVariantes(tiendaId, productoId)`, `leerImagen(ruta)`.
- Paginación: guardar el **DocumentSnapshot** como cursor (no solo el id). Una página vacía = fin.
- Las imágenes del fixture están vacías: mostrar un **placeholder**, no asumir una URL.
- Si `mostrarPrecios` es `false`, la lectura del catálogo de productos se bloquea: leer la tienda primero y manejar esa condición.

### 1b. Web administrador (primer incremento)

Meta: sesión + lectura de accesos + invitación/cancelación de emprendedoras + desactivación/reactivación. El flujo de pantallas usa el [contrato 10](../contratos/10-ciclo-cuentas.md).

- Credenciales de prueba: las genera `cuentas:demo` en `backend/.local/cuentas-<uuid>.json` (incluye la administradora y una candidata con `solicitudAlta`).
- Iniciar sesión con `iniciarSesion(correo, clave)`; luego `consultarAcceso()`.
  - `consultarAcceso()` = `null` significa identidad **sin permisos**; no tratarla como administradora.
- `invitarEmprendedora` recibe correo y datos de tienda según contrato 10; conservar su `invitacionId`. En local se entrega manualmente a la destinataria de prueba, que verifica su correo y llama a `aceptarInvitacion`. No se envían invitaciones reales.
- Comprobar acceso `estado === 'activo'` y que `roles` incluya `administrador` antes de mostrar gestión. La API y las reglas siguen verificando los permisos; ocultar un botón no los sustituye.
- Generar **un** `operacionId` por intención y **conservar el cuerpo completo** ante error o conexión perdida.
- `altaEmprendedora` y `solicitudAlta` quedan para la prueba técnica rápida con la candidata existente; no usarlos como formulario final de alta ni reemplazo de invitaciones.
- El rol administrador **no** puede leer fichas privadas ni ventas de emprendedoras.
- Al cerrar sesión, limpiar de la interfaz cualquier estado de gestión privada.

```ts
await $amancay.iniciarSesion(correo, clave);
const acceso = await $amancay.consultarAcceso();
// Al confirmar una nueva invitación; formulario sigue exactamente contrato 10.
const solicitud = { ...formularioInvitacion, operacionId: crypto.randomUUID() };
const resultado = await $amancay.llamar('invitarEmprendedora', solicitud);
// Conservar solicitud para reintentar; resultado.invitacionId para la aceptación.
```

## 2. Equipo app Android (modo comprador, primer incremento)

Meta: proyecto **Kotlin + Jetpack Compose** con la pantalla inicial que separa **modo comprador** y **modo emprendedora** (RF-ACC-05); se empieza por comprador.

1. Crear el proyecto Gradle/Compose en `android/` con su `applicationId`.
2. Agregar dependencias (BoM de Firebase + coroutines):

```kotlin
implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-storage")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
```

3. Copiar `ConexionLocal.kt` y `ClienteLocal.kt` de `android/ejemplos/src/debug/java/ejemplo/amancay/` al `src/debug/java/<paquete>/` del módulo app. Copiar `ErrorApi.kt` desde `android/ejemplos/src/main/java/ejemplo/amancay/` a `src/main/java/<paquete>/`: lo usan ambos entornos. Cambiar el `package` de los tres archivos al namespace del equipo. Por ahora no copiar los adaptadores compartidos.
4. Red: el emulador usa `10.0.2.2`; teléfono físico por USB usa `adb reverse` para 9099/8080/9199/8787.

**La app sí necesita su `applicationId` de Gradle para compilar** (ejemplo de formato: `cl.equipo.amancay`). No necesita registrarse todavía en Firebase ni tener `google-services.json`: `ConexionLocal` usa un Firebase App ID ficticio. Para la nube se usará otro dato, `firebaseAppId` (`mobilesdk_app_id`, formato `1:…:android:…`), obtenido al registrar la app.

Integrar el permiso INTERNET en el manifest principal para todas las variantes. La excepción de HTTP y `network_security_config.xml` van solo en debug según [el ejemplo Android](../android/ejemplos/README.md). El primer control del equipo es sincronizar Gradle, compilar debug y consultar las tiendas sin sesión. Los ejemplos Kotlin todavía no se han compilado en este repositorio.

Uso desde una coroutine (comprador, sin sesión):

```kotlin
val cliente = ClienteLocal(ConexionLocal.obtener(context.applicationContext))
val tiendas = cliente.listarTiendas()          // sin sesión
val productos = cliente.listarProductos(tiendaId)
// Para otra página: pasar el último DocumentSnapshot como cursor.
```

Ver `android/ejemplos/README.md` para manifiesto debug, red y manejo de errores.

## 3. Verificación conjunta

Con los tres clientes conectados a los emuladores de una misma máquina, seguir el **escenario común** de [integración de equipos](integracion-equipos.md#escenario-común-para-revisar-juntos). No hace falta terminar las pantallas: basta una vista de catálogo y una acción de prueba. Las operaciones de emprendedora pueden comprobarse mediante los adaptadores mientras su interfaz no exista.

## 4. Alcance provisional y mantenimiento

Está disponible el catálogo por unidad entera en CLP, con campos específicos de texto y hasta 20 variantes por producto. `mostrarPrecios=false` bloquea productos; ajustes de stock no registran ventas. Cambiar tipo/modalidad de productos existentes requiere una migración futura. Ver [contrato 07](../contratos/07-edicion-productos.md) y [contrato 11](../contratos/11-disponibilidad-e-imagenes.md). Tras terreno se revisarán estos límites de forma coordinada, no con campos diferentes en cada cliente.

Mapa, QR, voz, asistente, ventas y nuevas funciones necesitan sus propios acuerdos e implementación; no forman parte de este arranque. No asumir que un campo del modelo implica una operación terminada.

Si una operación de cuentas queda reservada tras detenerse el servidor, avisar al responsable del backend. Solo él sigue [recuperación técnica](recuperacion-cuentas.md); las pantallas conservan la solicitud y muestran su estado pendiente.

## Errores comunes

- Abrir la web en un puerto distinto de 3000 sin coordinar → CORS rechaza las llamadas.
- Reiniciar emuladores y olvidar `seed`/`cuentas:demo` → datos o credenciales obsoletos.
- Reintentar una escritura con un `operacionId` nuevo → duplica la intención. Conservar el cuerpo y el id.
- Copiar URLs de descarga con token a los documentos → no hacerlo; usar `leerImagen`/`getBytes`.
- Usar Firebase Admin o credenciales de servidor en la web → nunca.
- Reutilizar el adaptador `local` en compilación de producción → el plugin lo bloquea por diseño.

Códigos de error de la API: `401` sesión, `403` permiso, `409` conflicto (releer), `400/422` datos, `resultadoIncierto=true` = no se sabe si hubo efectos. Detalle en [integración de equipos](integracion-equipos.md#errores-y-reintentos).
