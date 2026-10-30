<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import type { FichaComprador, ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico';
useHead({
    title: 'Comprar local — Rincón Amancay',
    link: [{ rel: 'icon', href: '/favicon.png' },
        { rel: 'stylesheet',
            href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' }]
});

// Cliente y estado de la página
const { $catalogoPublico } = useNuxtApp();
const tienda = ref<FichaComprador | null>(null);
const producto = ref<FichaComprador | null>(null);
const filas = ref<FichaComprador[]>([]);
const variantes = ref<FichaComprador[]>([]);
const seleccion = ref('');
const busqueda = ref('');
const error = ref('');
const cargando = ref(false);
const siguiente = ref(false);
const pagina = ref(1);
let cursores: (string | null)[] = [null];
let cursorSiguiente: string | null = null;
let revision = 0;

function fichaTienda(elemento: TiendaPublica): FichaComprador {
    return {
        id: elemento.id,
        datos: {
            nombre: elemento.nombre,
            descripcion: elemento.descripcion,
            tipoEmprendimientoId: elemento.tipoEmprendimientoId,
            sectorId: elemento.sectorId,
            mostrarPrecios: elemento.mostrarPrecios,
        },
    };
}

function fichaProducto(elemento: ProductoPublico): FichaComprador {
    return {
        id: elemento.id,
        datos: {
            nombre: elemento.nombre,
            descripcion: elemento.descripcion,
            imagenes: elemento.imagenes,
            tieneVariantes: elemento.tieneVariantes,
            estadoDisponibilidad: elemento.estadoDisponibilidad,
            precioBase: elemento.precioBase,
            atributosEspecificos: elemento.atributosEspecificos,
        },
    };
}

function fichaVariante(elemento: VariantePublica): FichaComprador {
    const nombre = Object.values(elemento.opciones)[0] || elemento.id;
    return {
        id: elemento.id,
        datos: {
            nombre,
            descripcion: '',
            opciones: elemento.opciones,
            estadoDisponibilidad: elemento.estadoDisponibilidad,
            precio: elemento.precio,
        },
    };
}

// Datos derivados para la vista
const visibles = computed(() => filas.value.filter(f => `${f.datos.nombre} ${f.datos.descripcion ?? ''}`.toLowerCase().includes(busqueda.value.trim().toLowerCase())));
const variante = computed(() => variantes.value.find(v => v.id === seleccion.value));
const subtitulo = computed(() => tienda.value?.datos.descripcion ?? 'Conoce nuestras emprendedoras y encuentra productos con identidad local.');
const mensajeVacio = computed(() => busqueda.value
    ? 'No hay coincidencias en esta página.'
    : tienda.value
        ? 'Esta tienda todavía no tiene productos publicados.'
        : 'Todavía no hay tiendas publicadas.');
const imagenes = computed<string[]>(() => (producto.value?.datos.imagenes ?? []).filter((r: unknown) => typeof r === 'string'));

// Formato de precios, disponibilidad y errores

function precio(valor: unknown) {
    if (typeof valor !== 'number' || !Number.isFinite(valor)) {
        return 'Consultar precio';
    }

    return new Intl.NumberFormat('es-CL', {
        style: 'currency',
        currency: 'CLP',
        maximumFractionDigits: 0,
    }).format(valor);
}

function disponibilidad(valor: unknown) {
    const estados: Record<string, string> = {
        disponible: 'Disponible',
        agotado: 'Agotado',
        a_pedido: 'A pedido',
        vendido: 'Vendido',
    };

    return estados[String(valor)] ?? 'Consultar disponibilidad';
}

function fallo(errorConsulta: unknown) {
    const codigo = (errorConsulta as { code?: string }).code;

    return codigo === 'sin-permiso'
        ? 'No se pudo leer el catálogo público. Revisa los permisos de lectura.'
        : codigo === 'sin-conexion'
            ? 'No pudimos conectar con el servicio. Revisa la conexión y vuelve a intentar.'
        : 'No pudimos cargar el catálogo. Revisa la conexión y vuelve a intentar.';
}

// Consulta pública y paginación

async function cargar(destino = pagina.value) {
    const actual = ++revision;
    cargando.value = true;
    error.value = '';
    try {
        const r = tienda.value
            ? await $catalogoPublico.listarProductos(tienda.value.id, cursores[destino - 1])
            : await $catalogoPublico.listarTiendas(cursores[destino - 1]);
        if (actual !== revision)
            return;
        if (!r.elementos.length && destino > 1) {
            siguiente.value = false;
            return;
        }
        filas.value = tienda.value
            ? (r.elementos as ProductoPublico[]).map(fichaProducto)
            : (r.elementos as TiendaPublica[]).map(fichaTienda);
        cursorSiguiente = r.cursorSiguiente;
        cursores[destino] = r.cursorSiguiente;
        siguiente.value = r.cursorSiguiente !== null;
        pagina.value = destino;
        busqueda.value = '';
    }
    catch (e) {
        if (actual === revision)
            error.value = fallo(e);
    }
    finally {
        if (actual === revision)
            cargando.value = false;
    }
}

// Navegación entre tiendas y productos

async function navegar(ficha: FichaComprador | null) {
    revision++;
    tienda.value = ficha;
    producto.value = null;
    cursores = [null];
    cursorSiguiente = null;
    pagina.value = 1;
    filas.value = [];
    siguiente.value = false;
    busqueda.value = '';

    await cargar(1);
}

async function abrir(f: FichaComprador) {
    if (!tienda.value)
        return;
    const actual = ++revision;
    producto.value = f;
    variantes.value = [];
    seleccion.value = '';
    error.value = '';
    cargando.value = true;
    try {
        if (f.datos.tieneVariantes) {
            const r = await $catalogoPublico.listarVariantes(tienda.value.id, f.id);
            if (actual !== revision)
                return;
            variantes.value = r.map(fichaVariante);
            seleccion.value = variantes.value[0]?.id ?? '';
        }
    }
    catch (e) {
        if (actual === revision)
            error.value = fallo(e);
    }
    finally {
        if (actual === revision)
            cargando.value = false;
    }
}

function volver() {
    revision++;
    producto.value = null;
    error.value = '';
    cargando.value = false;
}

async function avanzar() {
    if (!cursorSiguiente) return;
    await cargar(pagina.value + 1);
}

function reintentar() {
    if (producto.value) {
        void abrir(producto.value);
    } else {
        void cargar();
    }
}

// Ciclo de vida
onMounted(() => {
    void cargar(1);
});
onBeforeUnmount(() => {
    revision++;
});
</script>

<template>
  <div class="admin">
    <aside class="lateral">
      <div class="marca">
        <img class="marca-icono" src="/logo-amancay.png" alt="Rincón Amancay" />
        <span class="marca-texto">
          <small>Rincón</small>
          <strong>Amancay</strong>
        </span>
        <span class="modo">Comprador</span>
      </div>
      <nav class="menu" aria-label="Catálogo">
        <button :class="{ on: !tienda }" @click="navegar(null)">
          <span class="mi" aria-hidden="true">🏠</span>
          Explorar tiendas
        </button>
        <button v-if="tienda" class="on" @click="volver">
          <span class="mi" aria-hidden="true">🛍️</span>
          {{ tienda.datos.nombre }}
        </button>
        <NuxtLink class="menu-enlace" to="/admin">
          <span class="mi" aria-hidden="true">🛡️</span>
          Administración
        </NuxtLink>
      </nav>
      <div class="pie">
        <img class="pie-avatar" src="/logo-amancay.png" alt="" />
        <div>
          <b>Compra local</b>
          <br />
          <small>Explora sin iniciar sesión</small>
        </div>
      </div>
    </aside>
    <main class="contenido">
      <header class="encabezado">
        <p class="sobretitulo">Hecho con cariño, cerca de ti</p>
        <h1>{{ producto ? producto.datos.nombre : tienda ? tienda.datos.nombre : 'Descubre Rincón Amancay' }}</h1>
        <p class="sub">{{ subtitulo }}</p>
      </header>
      <nav v-if="tienda" class="migas" aria-label="Ruta">
        <button @click="navegar(null)">Tiendas</button>
        <span>/</span>
        <button v-if="producto" @click="volver">{{ tienda.datos.nombre }}</button>
        <span v-else>{{ tienda.datos.nombre }}</span>
        <template v-if="producto">
          <span>/</span>
          <span>{{ producto.datos.nombre }}</span>
        </template>
      </nav>
      <section v-if="!tienda" class="destacada">
        <div class="destacada-texto">
          <p class="fecha">EMPRENDIMIENTOS LOCALES</p>
          <h2>Pequeñas tiendas, grandes historias</h2>
          <p>Explora sus productos, colores y variedades.</p>
          <a class="boton boton-ambar" href="#catalogo">Conocer las tiendas ↓</a>
        </div>
        <div class="destacada-arte">
          <img src="/logo-amancay.png" alt="" />
        </div>
      </section>
      <div v-if="error" class="aviso-error" role="alert">
        {{ error }}
        <button class="editar" :disabled="cargando" @click="reintentar">Reintentar</button>
      </div>
      <p v-if="cargando" class="aviso-carga" role="status">
        Cargando
        {{ producto ? 'variantes' : tienda ?
                'productos' :
                'tiendas' }}
        …
      </p>
      <section v-if="producto" class="detalle">
        <div class="galeria">
          <template v-if="imagenes.length">
            <ImagenPublica v-for="ruta in imagenes" :key="ruta" :ruta="ruta" :alt="producto.datos.nombre" />
          </template>
          <ImagenPublica v-else :alt="producto.datos.nombre" />
        </div>
        <div class="ficha">
          <span class="etiqueta">
            {{ disponibilidad(variante?.datos.estadoDisponibilidad ??
                    producto.datos.estadoDisponibilidad) }}
          </span>
          <h2 class="titulo-bloque">{{ producto.datos.nombre }}</h2>
          <p>{{ producto.datos.descripcion }}</p>
          <template v-if="producto.datos.tieneVariantes">
            <label for="variante">Elige una variante</label>
            <select id="variante" v-model="seleccion" :disabled="cargando || !variantes.length">
              <option v-for="v in variantes" :key="v.id" :value="v.id">
                {{ Object.entries(v.datos.opciones
                                ?? {}).map(([k, v]) => `${k}: ${v}`).join(' · ') || v.id }}
              </option>
            </select>
            <p v-if="!cargando && !error && !variantes.length">No hay variantes disponibles.</p>
            <small v-if="variantes.length === 100">Se muestran hasta 100 variantes activas.</small>
          </template>
          <p class="precio">
            {{ tienda?.datos.mostrarPrecios === true ? precio(producto.datos.tieneVariantes ?
                        variante?.datos.precio : producto.datos.precioBase) : 'Consultar precio en la tienda' }}
          </p>
          <dl>
            <template v-for="(valor, campo) in producto.datos.atributosEspecificos" :key="campo">
              <dt>{{ campo }}</dt>
              <dd>{{ valor }}</dd>
            </template>
          </dl>
          <button class="boton boton-primario" @click="volver">Volver a los productos</button>
        </div>
      </section>
      <section v-else id="catalogo" :aria-busy="cargando">
        <h2 class="titulo-bloque">{{ tienda ? 'Productos de la tienda' : 'Nuestras tiendas' }}</h2>
        <div class="herramientas">
          <label class="buscar">
            <span aria-hidden="true">⌕</span>
            <input v-model="busqueda" type="search" placeholder="Buscar en esta página" aria-label="Buscar en la página actual" />
          </label>
          <span class="etiqueta">
            Página
            {{ pagina }}
            ·
            {{ visibles.length }}
            resultados
          </span>
        </div>
        <div v-if="!cargando && !error && !visibles.length" class="vacio">
          <span aria-hidden="true">🌼</span>
          <p>{{ mensajeVacio }}</p>
          <button v-if="busqueda" class="editar" @click="busqueda = ''">Limpiar búsqueda</button>
        </div>
        <div class="rejilla">
          <button v-for="f in visibles" :key="f.id" class="tarjeta" :disabled="cargando" @click="tienda ? abrir(f) : navegar(f)">
            <ImagenPublica v-if="tienda" :ruta="f.datos.imagenes?.[0]" :alt="f.datos.nombre" />
            <span v-else class="icono" aria-hidden="true">🛍️</span>
            <h3>{{ f.datos.nombre }}</h3>
            <p>{{ f.datos.descripcion }}</p>
            <span v-if="tienda" class="etiqueta">{{ disponibilidad(f.datos.estadoDisponibilidad) }}</span>
            <b v-if="tienda?.datos.mostrarPrecios === true && !f.datos.tieneVariantes">{{ precio(f.datos.precioBase) }}</b>
            <small v-if="tienda && f.datos.tieneVariantes">Ver variantes y precios</small>
            <span class="ver">
              {{ tienda ? 'Ver producto' : 'Visitar tienda' }}
              →
            </span>
          </button>
        </div>
        <nav class="paginacion" aria-label="Paginación">
          <button class="editar" :disabled="cargando || pagina === 1" @click="cargar(pagina - 1)">← Anterior</button>
          <span aria-live="polite">
            Página
            {{ pagina }}
          </span>
          <button class="editar" :disabled="cargando || !siguiente" @click="avanzar">Siguiente →</button>
        </nav>
      </section>
    </main>
  </div>
</template>

<style scoped>
.acceso-admin {
    max-width: 420px;
    margin: 3rem auto;
    padding: 1.5rem;
    font-family: system-ui;
    color: #3a1228;
}

.acceso-admin form {
    display: grid;
    gap: .8rem;
}

.acceso-admin input {
    font: inherit;
    padding: .8rem;
    border: 1px solid #89727d;
    border-radius: 8px;
}

.estado-conexion {
    display: flex;
    flex-wrap: wrap;
    gap: 1rem;
    align-items: center;
    margin-bottom: 1rem;
}

button:disabled {
    opacity: .55;
    cursor: not-allowed;
}

.admin {
    --vino: #75264f;
    --vino-oscuro: #5a1c3c;
    --crema: #fff8ef;
    --crema-2: #fdf1d4;
    --durazno: #f5b183;
    --ambar: #f6c15b;
    --coral: #e96869;
    --rosado: #f7e9e8;
    --tinta: #3a1228;
    --suave: #89727d;
    --borde: #ead9cc;
    --sombra: 0 6px 20px rgba(117, 38, 79, 0.08);

    display: grid;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto 1fr;
    width: 100%;
    min-height: 100vh;
    min-height: 100dvh;
    background: radial-gradient(900px 400px at 100% 0, var(--crema-2), transparent 70%), var(--crema);
    color: var(--tinta);
    font-family: 'Plus Jakarta Sans', system-ui, -apple-system, 'Segoe UI', sans-serif;
}

:global(html),
:global(body) {
    margin: 0;
    min-height: 100%;
}

.admin::before {
    content: '';
    position: fixed;
    inset: 0;
    z-index: 0;
    pointer-events: none;
    background: url('/logo-amancay.png') no-repeat right -4% bottom -6% / min(62vw, 620px);
    opacity: 0.09;
}

.lateral,
.contenido {
    position: relative;
    z-index: 1;
}

button {
    font-family: inherit;
}

button:focus-visible,
input:focus-visible {
    outline: 2px solid var(--vino);
    outline-offset: 2px;
}

/* Lateral */
.lateral {
    position: relative;
    align-self: start;
    height: auto;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 1rem 2rem;
    padding: 1rem clamp(1.25rem, 3vw, 3.5rem);
    box-sizing: border-box;
    background: rgba(255, 255, 255, 0.82);
    backdrop-filter: blur(6px);
    border-bottom: 1.5px solid var(--borde);
}

.marca {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    margin-bottom: 0;
}

.marca-icono {
    width: 44px;
    height: 44px;
    padding: 6px;
    box-sizing: border-box;
    border-radius: 50%;
    object-fit: contain;
    background: var(--crema-2);
    border: 1.5px solid var(--borde);
}

.marca-texto {
    display: flex;
    flex-direction: column;
    line-height: 1.1;
}

.marca-texto small {
    font-size: 0.7rem;
    color: var(--suave);
}

.marca-texto strong {
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.15rem;
    color: var(--vino);
}

.modo {
    margin-left: auto;
    font-size: 0.66rem;
    font-weight: 700;
    color: var(--vino);
    background: var(--rosado);
    border-radius: 999px;
    padding: 0.2rem 0.6rem;
}

.menu {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 0.5rem;
}

.menu-titulo {
    margin: 1rem 0.9rem 0.3rem;
    font-size: 0.68rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--suave);
}

.menu button {
    position: relative;
    display: flex;
    align-items: center;
    gap: 0.65rem;
    padding: 0.65rem 0.9rem;
    border: 0;
    border-radius: 14px;
    background: none;
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--suave);
    text-align: left;
    cursor: pointer;
    transition: background 0.15s, color 0.15s;
}

.menu-enlace {
    position: relative;
    display: flex;
    align-items: center;
    gap: 0.65rem;
    padding: 0.65rem 0.9rem;
    border-radius: 14px;
    color: var(--suave);
    font-size: 0.92rem;
    font-weight: 600;
    text-decoration: none;
    transition: background 0.15s, color 0.15s;
}

.menu-enlace:hover {
    background: var(--crema);
    color: var(--vino);
}

.menu-enlace:focus-visible {
    outline: 2px solid var(--vino);
    outline-offset: 2px;
}

.mi {
    width: 30px;
    height: 30px;
    display: grid;
    place-items: center;
    border-radius: 10px;
    background: var(--crema);
    font-size: 0.95rem;
}

.menu button:hover {
    background: var(--crema);
    color: var(--vino);
}

.menu button.on {
    background: var(--rosado);
    color: var(--vino);
}

.menu button.on .mi {
    background: var(--durazno);
}

.menu button.on::before {
    display: none;
}

.pie {
    display: flex;
    align-items: center;
    gap: 0.7rem;
    margin-top: 0;
    padding: 0.7rem;
    background: var(--crema);
    border-radius: 16px;
}

.pie span:last-child {
    display: flex;
    flex-direction: column;
    font-size: 0.85rem;
    line-height: 1.2;
}

.pie small {
    color: var(--suave);
    font-size: 0.72rem;
}

.pie-avatar,
.avatar {
    flex: none;
    display: grid;
    place-items: center;
    font-weight: 700;
    color: var(--vino);
    background: var(--durazno);
}

.pie-avatar {
    width: 42px;
    height: 42px;
    padding: 5px;
    box-sizing: border-box;
    border-radius: 50%;
    object-fit: contain;
    background: #fff;
    border: 1.5px solid var(--borde);
}

/* Contenido */
.contenido {
    width: 100%;
    min-width: 0;
    padding: 2.2rem clamp(1.25rem, 3vw, 3.5rem) 4rem;
    box-sizing: border-box;
}

.encabezado {
    margin-bottom: 1.4rem;
}

.fila-encabezado {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 1rem;
    flex-wrap: wrap;
}

.fila-encabezado .sub {
    margin-bottom: 0;
}

.formulario {
    margin: 0 0 1.4rem;
    padding: 1.4rem;
    background: rgba(255, 255, 255, 0.94);
    border: 1.5px solid var(--borde);
    border-radius: 22px;
    box-shadow: var(--sombra);
}

.formulario-encabezado {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1rem;
}

.formulario-encabezado h2 {
    margin: 0;
    color: var(--vino);
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 1.35rem;
    font-weight: 400;
}

.formulario-encabezado p {
    margin: 0.35rem 0 0;
    color: var(--suave);
    font-size: 0.86rem;
}

.campos-formulario {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
}

.campos-formulario label {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
    color: var(--tinta);
    font-size: 0.84rem;
    font-weight: 600;
}

.campos-formulario input,
.campos-formulario select,
.campos-formulario textarea {
    width: 100%;
    min-height: 44px;
    padding: 0.65rem 0.8rem;
    box-sizing: border-box;
    color: var(--tinta);
    background: #fff;
    border: 1.5px solid var(--borde);
    border-radius: 12px;
    font: inherit;
}

.campos-formulario textarea {
    resize: vertical;
}

.campo-completo {
    grid-column: 1 / -1;
}

.acciones-formulario {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.75rem;
    grid-column: 1 / -1;
    padding-top: 0.25rem;
}

.aviso-error,
.aviso-carga {
    margin: 0 0 1rem;
    padding: 0.7rem 1rem;
    border-radius: 14px;
    font-size: 0.88rem;
    font-weight: 600;
}

.aviso-error {
    background: var(--rosado, #f7e9e8);
    color: #b23b3c;
}

.aviso-carga {
    background: var(--crema-2, #fdf1d4);
    color: #8a5a00;
}

.sobretitulo {
    margin: 0 0 0.35rem;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--vino);
}

h1 {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 2.2rem;
    line-height: 1.12;
    color: var(--vino);
}

.sub {
    margin: 0.45rem 0 0;
    color: var(--suave);
    font-size: 0.96rem;
    max-width: 54ch;
}

.titulo-bloque {
    margin: 1.8rem 0 0.9rem;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.4rem;
    color: var(--vino);
}

.migas {
    display: flex;
    gap: 0.5rem;
    margin-bottom: 1rem;
    font-size: 0.85rem;
    color: var(--suave);
}

.migas button {
    padding: 0;
    background: none;
    border: 0;
    font-size: inherit;
    font-weight: 600;
    color: var(--vino);
    cursor: pointer;
}

.migas span:last-child {
    color: var(--tinta);
    font-weight: 600;
}

/* Botones */
.boton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 48px;
    padding: 0.7rem 1.5rem;
    font-size: 0.95rem;
    font-weight: 700;
    border-radius: 999px;
    border: 1.5px solid var(--vino);
    cursor: pointer;
    transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
}

.boton:active {
    transform: scale(0.98);
}

.boton-primario {
    color: #fff;
    background: var(--vino);
    box-shadow: 0 6px 16px rgba(117, 38, 79, 0.25);
}

.boton-primario:hover {
    background: var(--vino-oscuro);
}

.boton-ambar {
    color: var(--tinta);
    background: var(--ambar);
    border-color: var(--ambar);
}

.boton-ambar:hover {
    background: #f9cd75;
}

/* Hero */
.destacada {
    position: relative;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 1.8rem;
    border-radius: 26px;
    background: linear-gradient(135deg, var(--vino) 0%, var(--vino-oscuro) 100%);
    color: var(--crema);
    box-shadow: 0 14px 30px rgba(117, 38, 79, 0.28);
}

.destacada::before,
.destacada::after {
    content: '';
    position: absolute;
    border-radius: 50%;
    pointer-events: none;
}

.destacada::before {
    width: 260px;
    height: 260px;
    right: -60px;
    top: -90px;
    background: rgba(245, 177, 131, 0.16);
}

.destacada::after {
    width: 120px;
    height: 120px;
    right: 150px;
    bottom: -60px;
    background: rgba(246, 193, 91, 0.14);
}

.destacada-texto {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.4rem;
}

.fecha {
    margin: 0;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: var(--durazno);
}

.destacada h2 {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.9rem;
    line-height: 1.12;
}

.destacada-texto p:not(.fecha) {
    margin: 0 0 0.9rem;
    opacity: 0.85;
}

.destacada-arte {
    position: relative;
    z-index: 1;
    flex: none;
    width: 130px;
    height: 130px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--crema);
    box-shadow: 0 0 0 10px rgba(245, 177, 131, 0.25);
}

.destacada-arte img {
    width: 100px;
    height: 100px;
    object-fit: contain;
}

/* Resumen */
.resumen {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0.8rem;
    margin-top: 1rem;
}

.dato {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.1rem;
    padding: 0.9rem 1rem;
    background: #fff;
    border: 1.5px solid var(--borde);
    border-radius: 18px;
    text-align: left;
    cursor: pointer;
    transition: border-color 0.15s;
}

.dato:hover {
    border-color: var(--vino);
}

.dato-num {
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 1.9rem;
    line-height: 1;
    color: var(--vino);
}

.dato-txt {
    font-size: 0.8rem;
    color: var(--suave);
    font-weight: 600;
}

/* Tarjetas */
.rejilla {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 1rem;
}

.tarjeta {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.35rem;
    padding: 1.2rem;
    background: #fff;
    border: 1.5px solid var(--borde);
    border-radius: 22px;
    text-align: left;
    color: inherit;
    cursor: pointer;
    transition: border-color 0.15s, transform 0.15s, box-shadow 0.15s;
}

.tarjeta:hover {
    border-color: var(--vino);
    transform: translateY(-3px);
    box-shadow: var(--sombra);
}

.icono {
    width: 46px;
    height: 46px;
    display: grid;
    place-items: center;
    border-radius: 15px;
    background: var(--durazno);
    font-size: 1.35rem;
}

.tarjeta h3 {
    margin: 0.3rem 0 0;
    font-size: 1.02rem;
    color: var(--vino);
}

.tarjeta p {
    margin: 0;
    padding-right: 1.5rem;
    font-size: 0.88rem;
    line-height: 1.5;
    color: var(--suave);
}

.flecha {
    position: absolute;
    right: 1.1rem;
    bottom: 1rem;
    font-weight: 700;
    color: var(--vino);
    opacity: 0;
    transform: translateX(-4px);
    transition: opacity 0.15s, transform 0.15s;
}

.tarjeta:hover .flecha {
    opacity: 1;
    transform: none;
}

/* Herramientas y lista */
.herramientas {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.8rem;
    flex-wrap: wrap;
    margin-bottom: 1rem;
}

.buscar {
    flex: 1;
    min-width: 220px;
    max-width: 420px;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0 1rem;
    background: #fff;
    border: 1.5px solid var(--borde);
    border-radius: 999px;
}

.buscar:focus-within {
    border-color: var(--vino);
}

.buscar input {
    flex: 1;
    min-width: 0;
    padding: 0.7rem 0;
    border: 0;
    outline: 0;
    background: none;
    font: inherit;
    font-size: 0.92rem;
    color: var(--tinta);
}

.chips {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
}

.chip {
    padding: 0.5rem 1rem;
    border-radius: 999px;
    border: 1.5px solid var(--borde);
    background: #fff;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--tinta);
    cursor: pointer;
}

.chip.on {
    background: var(--vino);
    border-color: var(--vino);
    color: #fff;
}

.lista {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.7rem;
}

.fila {
    display: flex;
    align-items: center;
    gap: 0.9rem;
    padding: 0.8rem 1rem 0.8rem 0.8rem;
    background: #fff;
    border: 1.5px solid var(--borde);
    border-radius: 20px;
    transition: box-shadow 0.15s, border-color 0.15s;
}

.fila:hover {
    border-color: var(--durazno);
    box-shadow: var(--sombra);
}

.avatar {
    width: 48px;
    height: 48px;
    border-radius: 15px;
    font-size: 0.95rem;
}

.fila-texto {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
}

.fila-texto b {
    color: var(--vino);
}

.fila-texto small {
    color: var(--suave);
}

.etiqueta {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.74rem;
    font-weight: 700;
    color: var(--vino);
    background: var(--rosado);
    border-radius: 999px;
    padding: 0.3rem 0.8rem;
    white-space: nowrap;
}

.etiqueta::before {
    content: '';
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--suave);
}

.etiqueta.ok {
    background: #e8f1e4;
    color: #2f5d2b;
}

.etiqueta.ok::before {
    background: #4c9a45;
}

.etiqueta.pend {
    background: var(--crema-2);
    color: #8a5a00;
}

.etiqueta.pend::before {
    background: var(--ambar);
}

.etiqueta.off::before {
    background: #b9a5ad;
}

.editar {
    padding: 0.5rem 1.1rem;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--vino);
    background: transparent;
    border: 1.5px solid var(--vino);
    border-radius: 999px;
    cursor: pointer;
}

.editar:hover {
    background: var(--rosado);
}

.vacio {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.6rem;
    padding: 2.5rem 1rem;
    background: #fff;
    border: 1.5px dashed var(--borde);
    border-radius: 22px;
    color: var(--suave);
}

.vacio span {
    font-size: 2.2rem;
}

.vacio p {
    margin: 0;
}

.aviso {
    position: fixed;
    left: 50%;
    bottom: 1.5rem;
    transform: translate(-50%, 8px);
    padding: 0.8rem 1.2rem;
    background: var(--vino);
    color: #fff;
    border-radius: 14px;
    font-size: 0.9rem;
    box-shadow: 0 10px 24px rgba(58, 18, 40, 0.3);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s, transform 0.2s;
}

.aviso.visible {
    opacity: 1;
    transform: translate(-50%, 0);
}

@media (prefers-reduced-motion: reduce) {
    * {
        transition: none !important;
    }
}

@media (max-width: 860px) {
    .campos-formulario {
        grid-template-columns: minmax(0, 1fr);
    }

    .admin {
        grid-template-columns: minmax(0, 1fr);
    }

    .admin::before {
        background-size: 90vw;
        background-position: center bottom -8%;
    }

    .lateral {
        position: static;
        height: auto;
        border-right: 0;
        border-bottom: 1.5px solid var(--borde);
        padding: 0.9rem 1rem 0.6rem;
        flex-direction: column;
        align-items: stretch;
        gap: 0.7rem;
    }

    .marca {
        margin-bottom: 0;
    }

    .menu {
        flex-direction: row;
        flex-wrap: nowrap;
        overflow-x: auto;
        padding-bottom: 0.3rem;
    }

    .menu-titulo,
    .pie {
        display: none;
    }

    .menu button {
        white-space: nowrap;
    }

    .menu-enlace {
        white-space: nowrap;
    }

    .menu button.on::before {
        display: none;
    }

    .contenido {
        padding: 1.25rem 1rem 3rem;
    }

    h1 {
        font-size: 1.7rem;
    }

    .destacada-arte {
        display: none;
    }

    .resumen {
        grid-template-columns: repeat(2, 1fr);
    }

    .fila {
        flex-wrap: wrap;
    }
}
</style>

<style scoped>
button:disabled {
    opacity: .55;
    cursor: not-allowed;
}

a.boton {
    text-decoration: none;
}

.destacada {
    margin-bottom: 1.5rem;
}

.ver {
    margin-top: auto;
    padding-top: .8rem;
    color: var(--vino);
    font-weight: 700;
}

.tarjeta .imagen-publica {
    margin-bottom: .5rem;
}

.tarjeta p {
    padding-right: 0;
}

.paginacion {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 1rem;
    margin-top: 1.5rem;
}

.detalle {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 1.5rem;
}

.ficha {
    padding: 1.5rem;
    border: 1.5px solid var(--borde);
    border-radius: 22px;
    background: white;
}

.ficha .titulo-bloque {
    margin-top: 1rem;
}

.ficha p {
    line-height: 1.6;
}

.ficha label {
    display: block;
    margin-bottom: .5rem;
    font-weight: 600;
}

.ficha select {
    width: 100%;
    padding: .8rem;
    border: 1.5px solid var(--borde);
    border-radius: 12px;
    font: inherit;
    background: white;
}

.precio {
    font-size: 1.6rem;
    font-weight: 700;
    color: var(--vino);
}

.galeria {
    display: grid;
    gap: 1rem;
}

dt {
    font-weight: 700;
    color: var(--vino);
}

dd {
    margin: .3rem 0 1rem;
}

@media(max-width: 860px) {
    .detalle {
        grid-template-columns: 1fr;
    }

    .paginacion {
        gap: .5rem;
    }
}
</style>
