# Base compartida y conexión con los emuladores (notas)

> **Aviso:** este documento describe un **avance teórico, no definitivo**. Se irá **modificando
> después de la feria** y **actualizando a medida que se agreguen otras funciones**.
> Complementa a [arranque por equipo](arranque-equipos.md) e [integración de equipos](integracion-equipos.md);
> para el modo comprador ver [modo comprador](modo-comprador.md).
>
> El **diseño visual de las pantallas (la interfaz) todavía no está implementado**. Las pantallas
> actuales son **funcionales/provisionales** y el diseño se realizará **después de la feria**.

Explica **qué provee la base del repositorio** y **cómo la app Android (modo comprador) se conecta
a los emuladores locales**. Sirve de mapa para entender de dónde salen los datos que la app muestra.

---

## 1. Qué es la base compartida

La raíz del repositorio es una **base común** para los tres clientes (web administrador, web
comprador y app comprador). Todavía **no hay despliegue en la nube**: todo corre en **emuladores
locales** en el mismo computador.

Áreas del repositorio:

| Carpeta | Para qué sirve |
|---------|----------------|
| `android/` | Proyecto Gradle de la app Kotlin (los dos modos). Aquí vive el modo comprador. |
| `backend/` | API HTTP autenticada (Node/TypeScript) con las operaciones de escritura. |
| `firebase/` | Reglas, índices y **emuladores** de Firestore, Auth y Storage. |
| `contratos/` | Acuerdos de campos, rutas y permisos, independientes del lenguaje. |
| `datos-prueba/` | Datos ficticios para cargar en los emuladores. |
| `docs/` | Documentación de arquitectura y del equipo. |
| `web/` | Web Nuxt (aún sin `npm run dev`). |

La idea central: los **clientes leen** el catálogo público directo de **Firestore** (según reglas),
y las **escrituras** pasan por la **API del backend** (que revisa el token y los permisos).

---

## 2. Puertos y emuladores

Configurados en `firebase.json` (todos en `127.0.0.1`):

| Servicio | Puerto | Uso |
|----------|--------|-----|
| Firestore | `8080` | Catálogo público (lectura del comprador). |
| Authentication | `9099` | Cuentas (login, tokens). |
| Storage | `9199` | Imágenes. |
| Emulator UI | `4000` | Panel web para inspeccionar los emuladores. |

El proyecto local es siempre **`demo-rincon-amancay`** (modo demo, no toca la nube).

---

## 3. Cómo se levanta la base

Desde la **raíz del repositorio** (una vez por computador, o si cambian los lockfiles):

```powershell
npm --prefix firebase ci
npm --prefix backend ci
```

Emuladores de Firebase (dejar la terminal abierta hasta ver `All emulators ready!`):

```powershell
npm --prefix firebase run emulators
```

Cargar los datos ficticios (en otra terminal):

```powershell
npm --prefix firebase run seed
```

API del backend (solo si se van a probar operaciones de escritura):

```powershell
npm --prefix backend run dev
```

> El `seed` carga 29 documentos ficticios en `demo-rincon-amancay`: usuarios/accesos de prueba,
> una tienda pública (`tiendasPublicas/t_demo_01`) con tres productos (un gorro con variantes,
> un alfajor y una planta), más sectores, categorías y tipos.

---

## 4. Cómo se conecta la app Android

La app **no se conecta a la nube**. Usa `android/app/src/debug/java/cl/equipo/amancay/ConexionLocal.kt`:

- Inicializa Firebase con un **ID ficticio** y `projectId = demo-rincon-amancay`.
- Apunta a `HOST = "127.0.0.1"` y a los puertos `9099` (Auth), `8080` (Firestore) y `9199` (Storage).
- Configura Firestore con host y `ssl = false` explícitos (emulador).

El teléfono/emulador **no ve** el `127.0.0.1` del PC, por lo que se crea un túnel con **`adb reverse`**:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb reverse tcp:8080 tcp:8080
& $adb reverse tcp:9099 tcp:9099
& $adb reverse tcp:9199 tcp:9199
```

Hay que **repetir `adb reverse`** cada vez que se reinicia el emulador Android.

> **Nota técnica:** en esta computadora el alias `10.0.2.2` establecía la conexión TCP pero la sesión
> de Firestore no arrancaba (la app quedaba sin datos). Por eso se usa `127.0.0.1` + `adb reverse`.

---

## 5. Flujo de los datos del catálogo

```
datos-prueba/*.json ──(npm run seed)──► Emulador Firestore (demo-rincon-amancay)
                                              ▲
                                     reglas de firestore.rules
                                              ▲
   app Android (RepositorioCatalogo) ──lecturas──┘
```

1. `npm --prefix firebase run seed` inserta los documentos ficticios en el emulador.
2. La app lee colecciones públicas a través de `RepositorioCatalogo` (ver modo comprador).
3. Las **reglas** (`firebase/firestore.rules`) deciden qué documentos puede leer el comprador.

Colecciones que lee el comprador:

| Colección | Condición de lectura |
|-----------|----------------------|
| `tiendasPublicas` | `estadoPublicacion = 'publicado'` y `habilitada = true`. |
| `tiendasPublicas/{id}/productos` | `estadoPublicacion = 'publicado'` **y** la tienda muestra precios. |
| `tiendasPublicas/{id}/productos/{id}/variantes` | `activa = true` **y** la tienda muestra precios. |
| `sectores`, `tiposEmprendimiento`, `categorias` | `activo = true`. |

> Si una tienda tiene `mostrarPrecios = false`, las reglas **bloquean** la lectura de sus productos
> (falla de forma cerrada). Por eso la app solo pide productos cuando la tienda sí muestra precios.

---

## 6. El backend (API de escritura)

`backend/` es una API HTTP (Node/TypeScript) que corre en el **puerto 8787** (`npm run dev`) y en
local trabaja **solo contra los emuladores**. El comprador casi no la usa: **lee de Firestore** y
solo las operaciones administrativas/emprendedora pasan por aquí.

Estructura:

| Ruta | Contenido |
|------|-----------|
| `src/server.ts` | Arranque local (emuladores). |
| `src/http/api.ts` | API autenticada (revisa el ID token de Firebase). |
| `src/modules/` | Operaciones por función del negocio (publicación, cuentas, productos, stock, imágenes, desactivación, ciclo de cuentas). |
| `src/config/` | Conexiones y configuración HTTP. |
| `src/montar-api.ts` | Conecta módulos y servidor. |

En Android, el cliente de esta API es
`android/app/src/debug/java/cl/equipo/amancay/ClienteLocal.kt` (manda el token y usa
`ConexionLocal.API = http://127.0.0.1:8787/api/v1/`). El directorio del comprador **no lo necesita**:
solo se incluiría cuando se implementen acciones que escriben (seguir tienda, enviar contacto, etc.).

---

## 7. Contratos (acuerdo de datos)

En `contratos/` están los acuerdos de campos, rutas y permisos, pensados para leerse desde Kotlin
y TypeScript. Los más relevantes para el comprador:

| Contrato | Tema |
|----------|------|
| `01-cuentas-tiendas.md` | Cuentas y tiendas. |
| `02-catalogo-local.md` | Catálogo (tiendas, productos, variantes). |
| `03-publicacion.md` | Publicar/retirar tienda y producto. |
| `04-api-local.md` | API local del backend. |
| `05-imagenes-storage.md` | Imágenes y Storage. |

---

## 8. Pendientes y limitaciones

- La **nube no está desplegada**: reglas, web y servidor corren solo en local.
- El registro en Firebase real (coordinado con Juan Pablo) es **preparación**, no cambia el trabajo local.
- Ventas y la vista pública de ferias/asociaciones no están implementadas en la app. La API web ahora administra ferias, asociaciones e integrantes; consulta `contratos/13-administracion-asociaciones.md` y `contratos/16-administracion-ferias.md`. Esto todavía no significa que Android consuma esas operaciones ni que exista una ficha o filtro público.
- Las **imágenes** de productos/variantes aún no se usan en la app (RF-CMP-03 completo queda pendiente).
