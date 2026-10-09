# Arranque por equipo (desarrollo local)

Guía corta para que los tres equipos empiecen **en local** sin depender de la nube. Primero todos hacen la **preparación común**; después cada equipo sigue su sección. Para el detalle, ver [integración de equipos](integracion-equipos.md) y los [contratos](../contratos/).

> La conexión a la nube todavía **no** está activa. No usar los adaptadores `*-compartido` (`cliente-compartido.ts`, `ConexionCompartida.kt`), ni el `admin:compartido`, ni pedir Blaze. Todo el trabajo inicial es contra los emuladores.

## 0. Preparación común (una vez por computador)

Requisitos: **Node.js 24.15.0 o superior**, npm y **Java en PATH**. En PowerShell, usar `npm.cmd` si `npm.ps1` está bloqueado.

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

1. Copiar `web/ejemplos/nuxt/firebase-local.client.ts.example` a `web/app/plugins/firebase-local.client.ts`.
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

Meta: sesión + lectura de accesos + `altaEmprendedora` y `desactivarEmprendedora`.

- Credenciales de prueba: las genera `cuentas:demo` en `backend/.local/cuentas-<uuid>.json` (incluye la administradora y una candidata con `solicitudAlta`).
- Iniciar sesión con `iniciarSesion(correo, clave)`; luego `consultarAcceso()`.
  - `consultarAcceso()` = `null` significa identidad **sin permisos**; no tratarla como administradora.
- `altaEmprendedora` usa el objeto `solicitudAlta` del archivo de cuentas. Generar **un** `operacionId` por intención y **conservarlo** ante error o conexión perdida (no reintentar con uno nuevo).
- El rol administrador **no** puede leer fichas privadas ni ventas de emprendedoras.
- Al cerrar sesión, limpiar de la interfaz cualquier estado de gestión privada.

```ts
await $amancay.iniciarSesion(correo, clave);
const acceso = await $amancay.consultarAcceso();
await $amancay.llamar('altaEmprendedora', { ...solicitudAlta, operacionId: crypto.randomUUID() });
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

3. Copiar `android/ejemplos/src/debug/java/ejemplo/amancay/` (`ConexionLocal.kt`, `ClienteLocal.kt`, `ErrorApi.kt`) a `src/debug` del módulo app del equipo.
4. Red: el emulador usa `10.0.2.2`; teléfono físico por USB usa `adb reverse` para 9099/8080/9199/8787.

**No necesitan `applicationId` ni `google-services.json` para trabajar en local:** `ConexionLocal` usa opciones ficticias contra el emulador. Esos datos solo harán falta para la app real (nube, aún bloqueada).

Uso desde una coroutine (comprador, sin sesión):

```kotlin
val cliente = ClienteLocal(ConexionLocal.obtener(context.applicationContext))
val tiendas = cliente.listarTiendas()          // sin sesión
val productos = cliente.listarProductos(tiendaId)
// Para otra página: pasar el último DocumentSnapshot como cursor.
```

Ver `android/ejemplos/README.md` para manifiesto debug, red y manejo de errores.

## 3. Verificación conjunta

Con los tres equipos en una misma máquina, seguir el **escenario común** de [integración de equipos](integracion-equipos.md#escenario-común-para-revisar-juntos): comprador consulta el fixture, administradora da de alta a la emprendedora, se publica tienda/producto/foto, y comprador ve la coincidencia de nombre, precio e imagen.

## Errores comunes

- Abrir la web en un puerto distinto de 3000 sin coordinar → CORS rechaza las llamadas.
- Reiniciar emuladores y olvidar `seed`/`cuentas:demo` → datos o credenciales obsoletos.
- Reintentar una escritura con un `operacionId` nuevo → duplica la intención. Conservar el cuerpo y el id.
- Copiar URLs de descarga con token a los documentos → no hacerlo; usar `leerImagen`/`getBytes`.
- Usar Firebase Admin o credenciales de servidor en la web → nunca.
- Reutilizar el adaptador `local` en compilación de producción → el plugin lo bloquea por diseño.

Códigos de error de la API: `401` sesión, `403` permiso, `409` conflicto (releer), `400/422` datos, `resultadoIncierto=true` = no se sabe si hubo efectos. Detalle en [integración de equipos](integracion-equipos.md#errores-y-reintentos).
