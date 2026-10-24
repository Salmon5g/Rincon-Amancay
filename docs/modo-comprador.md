# Módulo Comprador (app Android) — avance

> **Aviso:** este documento describe un **avance teórico, no definitivo**. Se irá **modificando después de la feria** y **actualizando a medida que se agreguen otras funciones**. No es una versión cerrada.
>
> El **diseño visual de las pantallas usa el sistema Amancay del Figma
> de la emprendedora (fondo crema, bordó + mostaza, barra superior con logo,
> tarjetas y chips)**: ver `android/.../comprador/AmancayDiseno.kt`.
> La parte compradora reutiliza la "vista pública" (Figma 09).
> La foto real sigue pendiente (RF-CMP-03): hay placeholder hasta que los
> datos de prueba traigan imágenes.

Este documento resume **las partes más importantes del código** del modo comprador de
la app Android de Rincón Amancay: qué hace cada pieza, cómo se conectan entre sí y cómo
probarlas en local. El trabajo vive en la **variante `debug`** (ver abajo cómo elegirla
en Android Studio). Todavía no se subió a GitHub; el `.gitignore` ya excluye
`local.properties`, `build/`, `.gradle/` y `.idea/`, así que cuando se quiera subir
basta con `git add android web docs` y revisar `git status` antes del commit.

---

## 1. Alcance actual

Implementado hasta ahora:

| Requerimiento | Estado | Qué incluye |
|---------------|--------|-------------|
| **RF-CMP-01** | Hecho | Directorio de tiendas con búsqueda y filtros (tipo de emprendimiento y sector). |
| **RF-CMP-02** | Hecho | Detalle de tienda y detalle de producto. |
| **RF-CMP-03** | Parcial | Al seleccionar una variante se muestra su precio. **Falta la foto** (los datos de prueba no traen imágenes). |

Pendiente/omitido por ahora:

- **Ficha de asociación** (parte de RF-CMP-02): no existe la colección de asociaciones en la base.
- **Filtro por asociación** (RF-CMP-01): igual motivo.
- Fotos de productos/variantes (RF-CMP-03): hay placeholder cálido; reemplazar por Coil/AsyncImage cuando el seed traiga imágenes.

---

## 2. Dónde vive el código

Todo el código del comprador está en la variante `debug`, bajo:

`android/app/src/debug/java/cl/equipo/amancay/`

| Archivo | Rol | Capa |
|---------|-----|------|
| `data/Modelos.kt` | Modelos de datos públicos (`TiendaPublica`, `ProductoPublico`, `VariantePublica`, etc.). | Datos |
| `data/RepositorioCatalogo.kt` | Acceso a Firestore: consultas de tiendas, productos y variantes. | Datos |
| `ConexionLocal.kt` | Conexión a los emuladores de Firebase (host, puertos). | Datos |
| `ClienteLocal.kt` | Cliente de la API del backend para escrituras (no usado por el directorio). | Datos |
| `comprador/DirectorioViewModel.kt` | Estado y lógica del directorio (búsqueda, filtros, paginación). | Controlador |
| `comprador/DirectorioScreen.kt` | UI del directorio (Compose). | Vista |
| `comprador/DetalleTiendaViewModel.kt` | Estado/lógica del detalle de tienda. | Controlador |
| `comprador/DetalleTiendaScreen.kt` | UI del detalle de tienda. | Vista |
| `comprador/DetalleProductoViewModel.kt` | Estado/lógica del detalle de producto. | Controlador |
| `comprador/DetalleProductoScreen.kt` | UI del detalle de producto (variantes). | Vista |
| `comprador/Formato.kt` | Utilidades: formato de precio y etiquetas en español. | Vista |
| `comprador/EstadoPantalla.kt` | Componente reutilizable de error con botón "Reintentar". | Vista |
| `ModoCompradorActivity.kt` | Punto de entrada y navegación entre pantallas. | Entrada |

> La actividad de `src/main/MainActivity.kt` queda intacta como marcador; en `debug` el
> launcher apunta a `ModoCompradorActivity` (ver `src/debug/AndroidManifest.xml`).

---

## 3. Arquitectura en capas

El módulo sigue un patrón simple, en el mismo orden en que fluyen los datos:

```
Pantalla (Compose)  ──observa──►  ViewModel  ──pide──►  Repositorio  ──lee──►  Firestore (emulador)
      UI                          Estado          Datos
```

1. **Datos** — `Modelos.kt` define las estructuras; `RepositorioCatalogo.kt` las obtiene de Firestore.
2. **Controlador** — cada `ViewModel` guarda el estado (con `StateFlow`) y expone funciones
   (buscar, filtrar, cargar más, reintentar). No conoce la UI.
3. **Vista** — cada `Screen` es una función `@Composable` que observa el estado del ViewModel
   y dibuja la interfaz. No accede a Firestore directamente.

La **navegación** no usa una librería externa: `ModoCompradorActivity.kt` guarda la pantalla
actual en una variable de estado y decide qué mostrar.

### 3.1 Flujo de navegación

```
Directorio ──tocar tienda──► Detalle de tienda ──tocar producto──► Detalle de producto
     ◄──── "Volver" / botón atrás ────┘        ◄── "Volver" / botón atrás ──┘
```

Cada cambio de pantalla está envuelto en `BackHandler` para que el botón atrás del sistema
funcione igual que el botón "Volver".

---

## 4. Capa de datos

### 4.1 Modelos (`data/Modelos.kt`)

Son clases de datos inmutables (`data class`) que representan lo que el comprador puede ver:

- `TiendaPublica`: `id`, `nombre`, `descripcion`, `tipoEmprendimientoId`, `sectorId`, `mostrarPrecios`.
- `ProductoPublico`: `id`, `nombre`, `descripcion`, `categoriaId`, `tipoProductoId`, `tieneVariantes`,
  `moneda`, `unidadVenta`, `modalidad`, `estadoDisponibilidad`, `precioBase`, `atributosEspecificos`.
- `VariantePublica`: `id`, `opciones`, `precio`, `estadoDisponibilidad`, `activa`.
- `SectorPublico`, `TipoEmprendimientoPublico`, `CategoriaPublica`: catálogos para filtros y nombres.

### 4.2 Repositorio (`data/RepositorioCatalogo.kt`)

Es la **única puerta de entrada a Firestore**. Funciones principales:

| Función | Qué hace |
|---------|----------|
| `listarSectores()`, `listarTiposEmprendimiento()`, `listarCategorias()` | Cargan catálogos activos. |
| `listarTiendas(tipo, sector, ultimoVisor)` | Directorio paginado (20 por página) de tiendas publicadas. |
| `obtenerTienda(id)` | Una tienda por su id. |
| `listarProductos(tiendaId)` | Productos publicados de una tienda. |
| `obtenerProducto(tiendaId, productoId)` | Un producto por su id. |
| `listarVariantes(tiendaId, productoId)` | Variantes activas de un producto. |

Todas las lecturas ocurren fuera del hilo principal (`Dispatchers.IO`).

### 4.3 Colecciones en Firestore

| Colección | Filtro de lectura |
|-----------|-------------------|
| `tiendasPublicas` | `estadoPublicacion = 'publicado'` y `habilitada = true`. |
| `tiendasPublicas/{id}/productos` | `estadoPublicacion = 'publicado'` **y** la tienda muestra precios. |
| `tiendasPublicas/{id}/productos/{id}/variantes` | `activa = true` **y** la tienda muestra precios. |
| `sectores`, `tiposEmprendimiento`, `categorias` | `activo = true`. |

> Detalle importante: si una tienda tiene `mostrarPrecios = false`, las reglas de Firestore
> **bloquean** la lectura de sus productos. Por eso `DetalleTiendaViewModel` solo pide los
> productos cuando la tienda sí muestra precios.

---

## 5. Conexión local (`ConexionLocal.kt`)

La app **no se conecta a la nube** todavía: usa los **emuladores de Firebase** que corren en el PC.

- `HOST = "127.0.0.1"` y puertos: Firestore `8080`, Auth `9099`, Storage `9199`.
- El celular/emulador Android no ve al PC por `127.0.0.1` directamente; se usa un túnel
  `adb reverse` que redirige esos puertos del teléfono hacia el PC.
- **Nota técnica:** en esta computadora `10.0.2.2` no completaba la sesión de Firestore; por eso
  se usa `adb reverse` + `127.0.0.1`. Recordar re-ejecutar `adb reverse` tras reiniciar el emulador.

---

## 6. Cómo compilar y probar

Compilar (Windows PowerShell, usando el Java de Android Studio):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain
```

> En Android Studio, abrir la carpeta `android/` y en **Build Variants**
> elegir **debug** para el módulo `app`. La variante `release` muestra el
> `MainActivity` de marcador ("Hello Android") sin diseño ni tiendas:
> si ves esa pantalla, estás en la variante equivocada.

Requisitos para ver datos:

```powershell
# Terminal 1: emuladores de Firebase
npm --prefix firebase run emulators
# Terminal 2: datos de prueba
npm --prefix firebase run seed
```

Y en otra terminal, el túnel hacia el dispositivo:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb reverse tcp:8080 tcp:8080
& $adb reverse tcp:9099 tcp:9099
& $adb reverse tcp:9199 tcp:9199
```

Luego, en Android Studio: elegir el dispositivo y presionar **Run** sobre `app`.

---

## 7. Ideas para seguir

- Agregar imágenes a los productos/variantes (RF-CMP-03 completo).
- Ficha de asociación y filtro por asociación cuando exista la colección.
- Agregar más tiendas de ejemplo al `seed` para que el directorio se vea más lleno.
- Resolver la discrepancia de login (la pauta pide Google; la base usa correo/contraseña).
