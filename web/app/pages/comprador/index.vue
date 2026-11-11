<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import '~/assets/amancay-tokens.css';
import type { FichaComprador, ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico';
useHead({
    title: 'Comprar local — Rincón Amancay',
    link: [{ rel: 'icon', href: '/favicon.png' },
        { rel: 'stylesheet',
            href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' }]
});

// Cliente y estado de la página
const { $catalogoPublico } = useNuxtApp();
const route = useRoute();
const router = useRouter();
const tienda = ref<FichaComprador | null>(null);
const producto = ref<FichaComprador | null>(null);
const filas = ref<FichaComprador[]>([]);
const variantes = ref<FichaComprador[]>([]);
const destacados = ref<{ tienda: FichaComprador; producto: FichaComprador }[]>([]);
const seleccion = ref('');
const busqueda = ref('');
const error = ref('');
const cargando = ref(false);
const siguiente = ref(false);
const pagina = ref(1);
const aviso = ref('');
const sectores = ref<Record<string, string>>({});
const tiposEmprendimiento = ref<Record<string, string>>({});
const categorias = ref<Record<string, string>>({});
const categoriaSeleccionada = ref('');
const filtroVisible = ref(true);
let cursores: (string | null)[] = [null];
let cursorSiguiente: string | null = null;
let revision = 0;
let avisoTimer: ReturnType<typeof setTimeout> | undefined;
let pausaSincronizacionRuta = 0;
let temporizadorRuta: ReturnType<typeof setTimeout> | undefined;
let rutaInicializada = false;

function valorRuta(valor: unknown): string {
    if (Array.isArray(valor)) return typeof valor[0] === 'string' ? valor[0] : '';
    return typeof valor === 'string' ? valor : '';
}

function paginaRuta(): number {
    const valor = Number(valorRuta(route.query.pagina));
    return Number.isSafeInteger(valor) && valor > 0 ? Math.min(valor, 100) : 1;
}

function queryVista(tiendaId = tienda.value?.id, productoId = producto.value?.id) {
    const query = { ...route.query };
    if (tiendaId) query.tienda = tiendaId;
    else delete query.tienda;
    if (productoId && tiendaId) query.producto = productoId;
    else delete query.producto;
    if (categoriaSeleccionada.value) query.categoria = categoriaSeleccionada.value;
    else delete query.categoria;
    if (busqueda.value.trim()) query.buscar = busqueda.value.trim();
    else delete query.buscar;
    if (pagina.value > 1) query.pagina = String(pagina.value);
    else delete query.pagina;
    return query;
}

async function guardarVistaEnUrl(metodo: 'push' | 'replace' = 'replace') {
    if (!import.meta.client) return;
    const destino = { path: '/comprador', query: queryVista(), hash: route.hash };
    if (metodo === 'push') await router.push(destino);
    else await router.replace(destino);
}

async function cargarPaginaRestaurada(destinoSolicitado: number) {
    const destino = Math.min(Math.max(destinoSolicitado, 1), 100);
    let destinoDisponible = 1;
    for (let paginaCursor = 1; paginaCursor < destino; paginaCursor++) {
        if (cursores[paginaCursor] === undefined) {
            const paginaAnterior = tienda.value
                ? await $catalogoPublico.listarProductos(tienda.value.id, cursores[paginaCursor - 1] ?? null)
                : await $catalogoPublico.listarTiendas(cursores[paginaCursor - 1] ?? null);
            cursores[paginaCursor] = paginaAnterior.cursorSiguiente;
        }
        if (!cursores[paginaCursor]) break;
        destinoDisponible = paginaCursor + 1;
    }
    await cargar(destinoDisponible);
}

async function restaurarVistaDesdeUrl() {
    pausaSincronizacionRuta++;
    try {
        const tiendaId = valorRuta(route.query.tienda);
        const productoId = valorRuta(route.query.producto);
        const busquedaGuardada = valorRuta(route.query.buscar);
        const categoriaGuardada = valorRuta(route.query.categoria);
        const paginaGuardada = paginaRuta();

        tienda.value = null;
        producto.value = null;
        variantes.value = [];
        seleccion.value = '';
        cursores = [null];
        cursorSiguiente = null;
        pagina.value = 1;
        filas.value = [];
        siguiente.value = false;
        busqueda.value = '';
        categoriaSeleccionada.value = '';
        error.value = '';

        if (tiendaId) {
            const encontrada = await $catalogoPublico.leerTienda(tiendaId);
            tienda.value = fichaTienda(encontrada);
        }

        await cargarPaginaRestaurada(paginaGuardada);
        busqueda.value = busquedaGuardada;
        categoriaSeleccionada.value = categoriaGuardada;

        if (productoId && tienda.value) {
            const encontrado = await $catalogoPublico.leerProducto(tienda.value.id, productoId);
            await abrir(fichaProducto(encontrado), false);
        }

        const ancla = valorRuta(route.hash).slice(1);
        if (ancla) {
            await nextTick();
            document.getElementById(ancla)?.scrollIntoView({ behavior: 'smooth' });
        }
    } catch (causa) {
        error.value = fallo(causa);
    } finally {
        pausaSincronizacionRuta--;
        if (route.query.tienda !== tienda.value?.id || route.query.producto !== producto.value?.id) {
            await guardarVistaEnUrl('replace');
        }
    }
}

function fichaTienda(elemento: TiendaPublica): FichaComprador {
    return {
        id: elemento.id,
        datos: {
            nombre: elemento.nombre,
            descripcion: elemento.descripcion,
            tipoEmprendimientoId: elemento.tipoEmprendimientoId,
            sectorId: elemento.sectorId,
            mostrarPrecios: elemento.mostrarPrecios,
            contacto: elemento.contacto,
            logo: elemento.logo,
        },
    };
}

function fichaProducto(elemento: ProductoPublico): FichaComprador {
    return {
        id: elemento.id,
        datos: {
            nombre: elemento.nombre,
            descripcion: elemento.descripcion,
            categoriaId: elemento.categoriaId,
            imagenes: elemento.imagenes,
            tieneVariantes: elemento.tieneVariantes,
            manejaStock: elemento.manejaStock,
            unidadVenta: elemento.unidadVenta,
            modalidad: elemento.modalidad,
            estadoDisponibilidad: elemento.estadoDisponibilidad,
            precioBase: elemento.precioBase,
            stock: elemento.stock,
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
            imagen: elemento.imagen,
            estadoDisponibilidad: elemento.estadoDisponibilidad,
            precio: elemento.precio,
            stock: elemento.stock,
        },
    };
}

// Datos derivados para la vista
const visibles = computed(() => filas.value.filter((f) => {
    const coincideTexto = `${f.datos.nombre} ${f.datos.descripcion ?? ''}`.toLowerCase().includes(busqueda.value.trim().toLowerCase());
    // En Explorar los chips afectan destacados; en tienda filtran productos por categoría.
    const coincideCategoria = !tienda.value || !categoriaSeleccionada.value || f.datos.categoriaId === categoriaSeleccionada.value;
    return coincideTexto && coincideCategoria;
}));
const destacadosVisibles = computed(() => destacados.value.filter((d) =>
    !categoriaSeleccionada.value || d.producto.datos.categoriaId === categoriaSeleccionada.value));
const variante = computed(() => variantes.value.find(v => v.id === seleccion.value));
const mensajeVacio = computed(() => busqueda.value
    ? 'No hay coincidencias en esta página.'
    : tienda.value
        ? 'Esta tienda todavía no tiene productos publicados.'
        : 'Todavía no hay tiendas publicadas.');
const imagenes = computed<string[]>(() => {
    const imagenVariante = variante.value?.datos.imagen;
    return imagenVariante ? [imagenVariante] : (producto.value?.datos.imagenes ?? []);
});
const etiquetaSector = computed(() => ubicacionTienda(tienda.value?.datos));
// La ubicación mostrada es la dirección escrita por la emprendedora; si no tiene,
// se muestra la zona del catálogo (comportamiento anterior).
const ubicacionTienda = (datos?: { direccion?: string; sectorId?: string }) => {
    if (!datos) return '';
    return (datos.direccion ?? '').trim() || (datos.sectorId ? sectores.value[datos.sectorId] ?? '' : '');
};
const etiquetaTipo = computed(() => {
    const id = tienda.value?.datos.tipoEmprendimientoId;
    return id ? tiposEmprendimiento.value[id] ?? '' : '';
});
const conteoProductos = computed(() => `${filas.value.length}${siguiente.value ? '+' : ''}`);
const enlaceContacto = computed(() => {
    const contacto = tienda.value?.datos.contacto;
    if (!contacto) return null;
    if (contacto.tipo === 'whatsapp') {
        const telefono = contacto.valor.replace(/\D/g, '');
        return telefono ? { href: `https://wa.me/${telefono}?text=${encodeURIComponent('Hola, vi tus productos en Rincón Amancay.')}`, texto: 'Contactar por WhatsApp' } : null;
    }
    if (contacto.tipo === 'correo') return { href: `mailto:${contacto.valor}`, texto: 'Contactar por correo' };
    if (contacto.tipo === 'web' && contacto.valor.startsWith('https://')) return { href: contacto.valor, texto: 'Visitar sitio de la tienda' };
    return null;
});

// Vista rápida de producto (modal tipo ficha)
const productoRapido = ref<FichaComprador | null>(null);
const tiendaRapida = ref<FichaComprador | null>(null);
const variantesRapidas = ref<FichaComprador[]>([]);
const seleccionRapida = ref('');
const variantesCargando = ref(false);
let revisionRapida = 0;
let overflowRapido = '';

const varianteRapida = computed(() => variantesRapidas.value.find(v => v.id === seleccionRapida.value));
const stockProducto = computed(() => textoStockProducto(producto.value, variantes.value, cargando.value));
const stockProductoRapido = computed(() => textoStockProducto(productoRapido.value, variantesRapidas.value, variantesCargando.value));
const categoriaRapida = computed(() => {
    const id = productoRapido.value?.datos.categoriaId;
    return id ? categorias.value[id] ?? id : '';
});
const etiquetaModalidad = computed(() => {
    const valor = productoRapido.value?.datos.modalidad;
    if (valor === 'regular') return 'Regular';
    if (valor === 'por_encargo' || valor === 'a_pedido') return 'Por encargo';
    return valor ? valor.charAt(0).toUpperCase() + valor.slice(1) : '';
});

async function abrirRapido(producto: FichaComprador, tiendaFicha: FichaComprador) {
    const actual = ++revisionRapida;
    productoRapido.value = producto;
    tiendaRapida.value = tiendaFicha;
    variantesRapidas.value = [];
    seleccionRapida.value = '';
    variantesCargando.value = producto.datos.tieneVariantes === true;
    if (import.meta.client) {
        overflowRapido = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
    }
    if (producto.datos.tieneVariantes) {
        try {
            const r = await $catalogoPublico.listarVariantes(tiendaFicha.id, producto.id);
            if (actual !== revisionRapida) return;
            variantesRapidas.value = r.map(fichaVariante);
            seleccionRapida.value = variantesRapidas.value[0]?.id ?? '';
        } catch {
            if (actual === revisionRapida) variantesRapidas.value = [];
        } finally {
            if (actual === revisionRapida) variantesCargando.value = false;
        }
    }
}

function cerrarRapido() {
    revisionRapida++;
    productoRapido.value = null;
    tiendaRapida.value = null;
    variantesRapidas.value = [];
    variantesCargando.value = false;
    if (import.meta.client) document.body.style.overflow = overflowRapido;
}

// Ventana de contacto: solo muestra datos públicos de la tienda.
const tiendaContacto = ref<FichaComprador | null>(null);
const estadoCopia = ref<'' | 'ok' | 'error'>('');
let overflowContacto = '';
const tiposContacto: Record<string, string> = { correo: 'Correo', whatsapp: 'WhatsApp', web: 'Sitio web' };
const sectorContacto = computed(() => {
    const id = tiendaContacto.value?.datos.sectorId;
    return id ? sectores.value[id] ?? '' : '';
});

function abrirContacto(ficha: FichaComprador | null) {
    if (!ficha) return;
    tiendaContacto.value = ficha;
    estadoCopia.value = '';
    if (import.meta.client) {
        overflowContacto = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
    }
}

function cerrarContacto() {
    tiendaContacto.value = null;
    if (import.meta.client) document.body.style.overflow = overflowContacto;
}

async function copiarContacto() {
    const valor = tiendaContacto.value?.datos.contacto?.valor;
    if (!valor) return;
    try {
        await navigator.clipboard.writeText(valor);
        estadoCopia.value = 'ok';
    } catch {
        estadoCopia.value = 'error';
    }
}

function nombreRapidoVariante(v: FichaComprador) {
    const partes = Object.entries(v.datos.opciones ?? {}).map(([k, val]) => `${k}: ${val}`);
    return partes.length ? partes.join(' · ') : v.id;
}

function textoStock(stock: number | null | undefined, manejaStock?: boolean): string {
    if (manejaStock === false) return 'Sin control de stock';
    if (manejaStock !== true) return 'Stock no informado';
    if (typeof stock !== 'number' || !Number.isSafeInteger(stock) || stock < 0) return 'Stock no informado';
    return `${stock} ${stock === 1 ? 'unidad disponible' : 'unidades disponibles'}`;
}

function textoStockProducto(ficha: FichaComprador | null, listaVariantes: FichaComprador[], cargandoVariantes = false): string {
    if (!ficha) return '';
    if (ficha.datos.tieneVariantes !== true) return textoStock(ficha.datos.stock, ficha.datos.manejaStock);
    if (ficha.datos.manejaStock === false) return 'Sin control de stock';
    if (ficha.datos.manejaStock !== true) return 'Stock no informado';
    if (!listaVariantes.length) return cargandoVariantes ? 'Cargando…' : 'Sin variantes disponibles';
    const saldos = listaVariantes.map(variante => variante.datos.stock);
    if (saldos.some(stock => typeof stock !== 'number' || !Number.isSafeInteger(stock) || stock < 0)) return 'Stock no informado';
    const total = saldos.reduce((suma, stock) => suma + (stock as number), 0);
    if (!Number.isSafeInteger(total)) return 'Stock no informado';
    return `${total} ${total === 1 ? 'unidad disponible' : 'unidades disponibles'} en total`;
}

function alTeclearRapido(evento: KeyboardEvent) {
    if (evento.key !== 'Escape') return;
    if (tiendaContacto.value) cerrarContacto();
    else if (productoRapido.value) cerrarRapido();
}

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
        a_pedido: 'Por encargo',
        vendido: 'Vendido',
    };

    return estados[String(valor)] ?? 'Consultar disponibilidad';
}

function claseDisponibilidad(valor: unknown) {
    const estado = String(valor);
    if (estado === 'disponible') return 'ok';
    if (estado === 'agotado' || estado === 'vendido') return 'off';
    return 'pend';
}

function fallo(errorConsulta: unknown) {
    const codigo = (errorConsulta as { code?: string }).code;

    return codigo === 'no-encontrado'
        ? 'La tienda o el producto ya no está disponible.'
        : codigo === 'sin-permiso'
        ? 'No se pudo leer el catálogo público. Revisa los permisos de lectura.'
        : codigo === 'sin-conexion'
            ? 'No pudimos conectar con el servicio. Revisa la conexión y vuelve a intentar.'
            : 'No pudimos cargar el catálogo. Revisa la conexión y vuelve a intentar.';
}

function mostrarAviso(texto: string) {
    aviso.value = texto;
    clearTimeout(avisoTimer);
    avisoTimer = setTimeout(() => {
        aviso.value = '';
    }, 2600);
}

// Consulta pública, catálogos y paginación

async function cargarCatalogos() {
    try {
        const catalogos = await $catalogoPublico.listarCatalogosPublicos();
        sectores.value = Object.fromEntries(catalogos.sectores.map(e => [e.id, e.nombre]));
        tiposEmprendimiento.value = Object.fromEntries(catalogos.tiposEmprendimiento.map(e => [e.id, e.nombre]));
        categorias.value = Object.fromEntries((catalogos.categorias ?? []).map(e => [e.id, e.nombre]));
    } catch {
        sectores.value = {};
        tiposEmprendimiento.value = {};
        categorias.value = {};
    }
}

async function cargarDestacados(tiendas: FichaComprador[], revisionDestacados: number) {
    const candidatas = tiendas.filter(t => t.datos.mostrarPrecios === true).slice(0, 4);
    if (!candidatas.length) {
        if (revisionDestacados === revision) destacados.value = [];
        return;
    }
    const resultados = await Promise.all(candidatas.map(async (t) => {
        try {
            const r = await $catalogoPublico.listarProductos(t.id, null);
            const primero = r.elementos[0];
            return primero ? { tienda: t, producto: fichaProducto(primero) } : null;
        } catch {
            return null;
        }
    }));
    if (revisionDestacados !== revision || tienda.value) return;
    destacados.value = resultados.filter((d): d is { tienda: FichaComprador; producto: FichaComprador } => d !== null);
}

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
        if (!tienda.value && destino === 1)
            void cargarDestacados(filas.value, actual);
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

function letraInicial(nombre: string) {
    return (nombre.trim()[0] ?? 'A').toUpperCase();
}

async function irSeccion(id: string) {
    if (tienda.value) {
        await navegar(null);
        await nextTick();
    }
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
}

async function navegar(ficha: FichaComprador | null) {
    pausaSincronizacionRuta++;
    try {
        revision++;
        tienda.value = ficha;
        producto.value = null;
        variantes.value = [];
        seleccion.value = '';
        cursores = [null];
        cursorSiguiente = null;
        pagina.value = 1;
        filas.value = [];
        siguiente.value = false;
        busqueda.value = '';
        categoriaSeleccionada.value = '';

        await cargar(1);
        await guardarVistaEnUrl('push');
    } finally {
        pausaSincronizacionRuta--;
    }
}

async function abrir(f: FichaComprador, guardarEnUrl = true) {
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
        if (actual === revision) {
            cargando.value = false;
            if (guardarEnUrl) await guardarVistaEnUrl('push');
        }
    }
}

async function abrirDestacado(d: { tienda: FichaComprador; producto: FichaComprador }) {
    await navegar(d.tienda);
    await abrir(d.producto);
}

function volver() {
    pausaSincronizacionRuta++;
    revision++;
    producto.value = null;
    error.value = '';
    cargando.value = false;
    void guardarVistaEnUrl('replace').finally(() => { pausaSincronizacionRuta--; });
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

watch(
    () => [valorRuta(route.query.tienda), valorRuta(route.query.producto)],
    ([tiendaRuta, productoRuta]) => {
        if (!rutaInicializada) return;
        if (tiendaRuta === (tienda.value?.id ?? '') && productoRuta === (producto.value?.id ?? '')) return;
        void restaurarVistaDesdeUrl();
    },
);

watch([busqueda, categoriaSeleccionada, pagina], () => {
    if (!import.meta.client || pausaSincronizacionRuta > 0) return;
    clearTimeout(temporizadorRuta);
    temporizadorRuta = setTimeout(() => { void guardarVistaEnUrl('replace'); }, 250);
});

async function compartirTienda() {
    if (!tienda.value) return;
    const url = window.location.href;
    const datos = {
        title: tienda.value.datos.nombre,
        text: `${tienda.value.datos.nombre} en Rincón Amancay`,
        url,
    };
    try {
        if (navigator.share) {
            await navigator.share(datos);
            return;
        }
        await navigator.clipboard.writeText(url);
        mostrarAviso('Enlace copiado');
    } catch (causa) {
        if ((causa as { name?: string }).name === 'AbortError') return;
        try {
            await navigator.clipboard.writeText(url);
            mostrarAviso('Enlace copiado');
        } catch {
            mostrarAviso('No se pudo compartir el enlace.');
        }
    }
}

// Ciclo de vida
onMounted(() => {
    void cargarCatalogos();
    window.addEventListener('keydown', alTeclearRapido);
});
onNuxtReady(() => {
    // En Hosting, Nuxt restaura la consulta de la URL tras hidratar la página estática.
    rutaInicializada = true;
    void restaurarVistaDesdeUrl();
});
onBeforeUnmount(() => {
    revision++;
    clearTimeout(avisoTimer);
    clearTimeout(temporizadorRuta);
    window.removeEventListener('keydown', alTeclearRapido);
    if (productoRapido.value && import.meta.client) document.body.style.overflow = overflowRapido;
});
</script>

<template>
  <div class="comprador" :class="tienda ? 'modo-tienda' : 'modo-explorar'">
    <!-- Explorar: barra lateral -->
    <CompradorLateral v-if="!tienda" activo="explorar" />

    <!-- Barra superior: tienda y producto -->
    <header v-else class="barra-superior">
      <button class="marca marca-boton" type="button" @click="navegar(null)">
        <img class="marca-icono" src="/logo-amancay.png" alt="" />
        <span class="marca-texto">
          <small>Rincón</small>
          <strong>Amancay</strong>
        </span>
      </button>
      <nav class="barra-nav" aria-label="Principal">
        <button type="button" @click="navegar(null)">Explorar</button>
        <button type="button" @click="irSeccion('catalogo')">Tiendas</button>
        <NuxtLink class="barra-enlace" to="/comprador/ferias">Ferias</NuxtLink>
        <span class="barra-actual">{{ tienda.datos.nombre }}</span>
      </nav>
      <label v-if="!producto" class="buscador buscador-barra">
        <span aria-hidden="true">⌕</span>
        <input v-model="busqueda" type="search" placeholder="Buscar productos en esta tienda" aria-label="Buscar productos en la tienda" />
        <button
          class="buscador-filtro"
          type="button"
          :aria-expanded="filtroVisible"
          aria-label="Filtrar por categoría"
          @click="filtroVisible = !filtroVisible"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M4 6h16M7 12h10M10 18h4" stroke-linecap="round" />
          </svg>
        </button>
      </label>
      <div class="barra-sesion">
        <AccesoComprador />
      </div>
    </header>

    <main class="contenido">
      <!-- Vista explorar -->
      <template v-if="!tienda">
        <header class="encabezado-fila">
          <div>
            <h1>Descubre productos hechos cerca</h1>
            <p class="sub">Conoce a nuestras emprendedoras y encuentra algo especial.</p>
          </div>
          <div class="encabezado-sesion">
            <AccesoComprador />
          </div>
        </header>

        <section class="hero">
          <div class="hero-texto">
            <p class="sobretitulo">EMPRENDIMIENTOS LOCALES</p>
            <h2>Hecho cerca, elegido con cariño</h2>
            <p>Productos con historia, manos locales y un impacto real.</p>
            <a class="boton boton-mostaza" href="#destacados">Explorar productos →</a>
          </div>
          <p class="hero-frase">Lo local<br />también florece</p>
          <div class="hero-arte" aria-hidden="true">
            <img src="/logo-amancay.png" alt="" />
          </div>
          <ul class="hero-lista" aria-hidden="true">
            <li>Personas</li>
            <li>Productos</li>
            <li>Territorios</li>
            <li>Comunidad</li>
          </ul>
        </section>

        <label class="buscador buscador-ancho">
          <span aria-hidden="true">⌕</span>
          <input v-model="busqueda" type="search" placeholder="Busca una tienda por nombre o descripción" aria-label="Buscar tiendas" />
          <button
            class="buscador-filtro"
            type="button"
            :aria-expanded="filtroVisible"
            aria-label="Filtrar por categoría"
            @click="filtroVisible = !filtroVisible"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M4 6h16M7 12h10M10 18h4" stroke-linecap="round" />
            </svg>
          </button>
        </label>

        <div v-if="filtroVisible && Object.keys(categorias).length" class="filtros" role="group" aria-label="Filtrar por categoría">
          <span class="filtros-etiqueta">Filtrar por categoría:</span>
          <button
            class="chip-filtro"
            :class="{ on: !categoriaSeleccionada }"
            type="button"
            @click="categoriaSeleccionada = ''"
          >Todos</button>
          <button
            v-for="(nombre, id) in categorias"
            :key="id"
            class="chip-filtro"
            :class="{ on: categoriaSeleccionada === id }"
            type="button"
            @click="categoriaSeleccionada = categoriaSeleccionada === id ? '' : id"
          >{{ nombre }}</button>
        </div>

        <div v-if="error" class="aviso-error" role="alert">
          {{ error }}
          <button class="editar" :disabled="cargando" @click="reintentar">Reintentar</button>
        </div>
        <p v-if="cargando && !filas.length" class="aviso-carga" role="status">
          Cargando tiendas …
        </p>

        <section v-if="destacadosVisibles.length" id="destacados" aria-labelledby="tit-destacados">
          <div class="fila-titulo">
            <h2 id="tit-destacados" class="titulo-bloque">Productos destacados</h2>
            <a class="enlace" href="#catalogo">Ver todos →</a>
          </div>
          <div class="rejilla-productos">
            <button
              v-for="d in destacadosVisibles"
              :key="`${d.tienda.id}-${d.producto.id}`"
              class="tarjeta-producto"
              type="button"
              @click="abrirDestacado(d)"
            >
              <span class="foto" @click.stop="abrirRapido(d.producto, d.tienda)">
                <ImagenPublica :ruta="d.producto.datos.imagenes?.[0]" :alt="d.producto.datos.nombre" ajuste="contain" :zoom="false" />
                <span class="badge" :class="claseDisponibilidad(d.producto.datos.estadoDisponibilidad)">
                  {{ disponibilidad(d.producto.datos.estadoDisponibilidad) }}
                </span>
              </span>
              <h3>{{ d.producto.datos.nombre }}</h3>
              <span class="tarjeta-tienda">
                <span class="tarjeta-tienda-logo">
                  <ImagenPublica v-if="d.tienda.datos.logo" :ruta="d.tienda.datos.logo" :alt="d.tienda.datos.nombre" ajuste="contain" />
                  <span v-else class="logo-inicial" aria-hidden="true">{{ letraInicial(d.tienda.datos.nombre) }}</span>
                </span>
                {{ d.tienda.datos.nombre }}
              </span>
              <b class="precio">{{ precio(d.producto.datos.precioBase) }}</b>
            </button>
          </div>
        </section>

        <section aria-labelledby="tit-emprendimientos">
          <div class="fila-titulo">
            <h2 id="tit-emprendimientos" class="titulo-bloque">Emprendimientos destacados</h2>
            <a class="enlace" href="#catalogo">Ver todas las tiendas →</a>
          </div>
          <div class="rejilla-tiendas">
            <article v-for="f in filas.slice(0, 4)" :key="f.id" class="tarjeta-tienda">
              <span class="tarjeta-tienda-logo grande">
                <ImagenPublica v-if="f.datos.logo" :ruta="f.datos.logo" :alt="f.datos.nombre" ajuste="contain" />
                <span v-else class="logo-inicial grande" aria-hidden="true">{{ letraInicial(f.datos.nombre) }}</span>
              </span>
              <div class="tarjeta-tienda-cuerpo">
                <h3>{{ f.datos.nombre }}</h3>
                <p>{{ f.datos.descripcion }}</p>
                <span v-if="ubicacionTienda(f.datos)" class="chip">
                  <span aria-hidden="true">📍</span>
                  {{ ubicacionTienda(f.datos) }}
                </span>
              </div>
              <button class="boton boton-secundario" type="button" @click="navegar(f)">Visitar tienda →</button>
            </article>
          </div>
        </section>

        <section id="catalogo" :aria-busy="cargando">
          <div class="fila-titulo">
            <h2 class="titulo-bloque">Nuestras tiendas</h2>
            <span class="cuenta">Página {{ pagina }} · {{ visibles.length }} resultados</span>
          </div>
          <div v-if="!cargando && !error && !visibles.length" class="vacio">
            <span aria-hidden="true">🌼</span>
            <p>{{ mensajeVacio }}</p>
            <button v-if="busqueda || categoriaSeleccionada" class="editar" @click="busqueda = ''; categoriaSeleccionada = ''">Limpiar filtros</button>
          </div>
          <div class="rejilla-tiendas">
            <article v-for="f in visibles" :key="f.id" class="tarjeta-tienda">
              <span class="tarjeta-tienda-logo grande">
                <ImagenPublica v-if="f.datos.logo" :ruta="f.datos.logo" :alt="f.datos.nombre" ajuste="contain" />
                <span v-else class="logo-inicial grande" aria-hidden="true">{{ letraInicial(f.datos.nombre) }}</span>
              </span>
              <div class="tarjeta-tienda-cuerpo">
                <h3>{{ f.datos.nombre }}</h3>
                <p>{{ f.datos.descripcion }}</p>
                <span v-if="ubicacionTienda(f.datos)" class="chip">
                  <span aria-hidden="true">📍</span>
                  {{ ubicacionTienda(f.datos) }}
                </span>
              </div>
              <button class="boton boton-secundario" type="button" :disabled="cargando" @click="navegar(f)">Visitar tienda →</button>
            </article>
          </div>
          <nav class="paginacion" aria-label="Paginación">
            <button class="editar" :disabled="cargando || pagina === 1" @click="cargar(pagina - 1)">← Anterior</button>
            <span aria-live="polite">Página {{ pagina }}</span>
            <button class="editar" :disabled="cargando || !siguiente" @click="avanzar">Siguiente →</button>
          </nav>
        </section>
      </template>

      <!-- Vista tienda y producto -->
      <template v-else>
        <nav class="migas" aria-label="Ruta">
          <button type="button" @click="navegar(null)">Explorar</button>
          <span>/</span>
          <span>Tiendas</span>
          <span>/</span>
          <button v-if="producto" type="button" @click="volver">{{ tienda.datos.nombre }}</button>
          <span v-else class="actual">{{ tienda.datos.nombre }}</span>
          <template v-if="producto">
            <span>/</span>
            <span class="actual">{{ producto.datos.nombre }}</span>
          </template>
        </nav>

        <section v-if="!producto" class="tienda-hero">
          <div class="tienda-hero-info">
            <span class="tienda-logo">
              <ImagenPublica v-if="tienda.datos.logo" :ruta="tienda.datos.logo" :alt="tienda.datos.nombre" ajuste="contain" />
              <span v-else class="logo-inicial hero" aria-hidden="true">{{ letraInicial(tienda.datos.nombre) }}</span>
            </span>
            <div class="tienda-hero-texto">
              <h1>{{ tienda.datos.nombre }}</h1>
              <div class="chips">
                <span v-if="etiquetaTipo" class="chip">
                  <span aria-hidden="true">🧺</span>
                  {{ etiquetaTipo }}
                </span>
                <span v-if="etiquetaSector" class="chip">
                  <span aria-hidden="true">📍</span>
                  {{ etiquetaSector }}
                </span>
              </div>
              <p class="sub clamp">{{ tienda.datos.descripcion }}</p>
              <div class="chips">
                <span class="chip">
                  <span aria-hidden="true">📦</span>
                  {{ conteoProductos }} productos publicados
                </span>
              </div>
              <div class="acciones-hero">
                <a v-if="enlaceContacto" class="boton boton-primario" :href="enlaceContacto.href" target="_blank" rel="noopener noreferrer">
                  {{ enlaceContacto.texto }} →
                </a>
                <button class="boton boton-secundario" type="button" @click="compartirTienda">
                  <span aria-hidden="true">↗</span>
                  Compartir tienda
                </button>
              </div>
            </div>
          </div>
          <div class="tienda-hero-arte" aria-hidden="true">
            <img src="/logo-amancay.png" alt="" />
            <p class="arte-tag">Hecho a mano en Chile</p>
          </div>
        </section>

        <div v-if="error" class="aviso-error" role="alert">
          {{ error }}
          <button class="editar" :disabled="cargando" @click="reintentar">Reintentar</button>
        </div>
        <p v-if="cargando && !producto && !filas.length" class="aviso-carga" role="status">
          Cargando productos …
        </p>
        <p v-if="cargando && producto" class="aviso-carga" role="status">
          Cargando variantes …
        </p>

        <section v-if="producto" class="detalle">
          <div class="galeria">
            <div class="galeria-marco">
              <template v-if="imagenes.length">
                <ImagenPublica v-for="ruta in imagenes" :key="ruta" :ruta="ruta" :alt="producto.datos.nombre" ajuste="contain" />
              </template>
              <ImagenPublica v-else :alt="producto.datos.nombre" ajuste="contain" />
            </div>
            <span class="badge grande" :class="claseDisponibilidad(variante?.datos.estadoDisponibilidad ?? producto.datos.estadoDisponibilidad)">
              {{ disponibilidad(variante?.datos.estadoDisponibilidad ?? producto.datos.estadoDisponibilidad) }}
            </span>
          </div>
          <div class="ficha">
            <p class="ficha-miga" v-if="tienda">
              <button type="button" @click="volver">{{ tienda.datos.nombre }}</button>
              <span aria-hidden="true">·</span>
              <span>Producto</span>
            </p>
            <h2 class="titulo-ficha">{{ producto.datos.nombre }}</h2>
            <p class="ficha-desc">{{ producto.datos.descripcion }}</p>
            <div class="ficha-datos">
              <div v-if="producto.datos.unidadVenta" class="ficha-dato">
                <span class="ficha-dato-etiqueta">Venta</span>
                <span>{{ producto.datos.unidadVenta }}</span>
              </div>
              <div v-if="etiquetaModalidad" class="ficha-dato">
                <span class="ficha-dato-etiqueta">Modalidad</span>
                <span>{{ etiquetaModalidad }}</span>
              </div>
              <div v-if="producto.datos.categoriaId && categorias[producto.datos.categoriaId]" class="ficha-dato">
                <span class="ficha-dato-etiqueta">Categoría</span>
                <span>{{ categorias[producto.datos.categoriaId] }}</span>
              </div>
              <div class="ficha-dato">
                <span class="ficha-dato-etiqueta">{{ producto.datos.tieneVariantes ? 'Stock total' : 'Stock' }}</span>
                <span>{{ stockProducto }}</span>
              </div>
            </div>
            <template v-if="producto.datos.tieneVariantes">
              <label class="ficha-label" for="variante">Elige una variante</label>
              <select id="variante" class="ficha-select" v-model="seleccion" :disabled="cargando || !variantes.length">
                <option v-for="v in variantes" :key="v.id" :value="v.id">
                  {{ nombreRapidoVariante(v) }} · {{ textoStock(v.datos.stock, producto.datos.manejaStock) }}
                </option>
              </select>
              <section v-if="variantes.length" class="ficha-stock-variantes" aria-label="Stock por variante">
                <h3>Stock por variante</h3>
                <ul>
                  <li v-for="v in variantes" :key="v.id">
                    <span>{{ nombreRapidoVariante(v) }}</span>
                    <b>{{ textoStock(v.datos.stock, producto.datos.manejaStock) }}</b>
                  </li>
                </ul>
              </section>
              <p v-if="!cargando && !error && !variantes.length" class="ficha-nota">No hay variantes disponibles.</p>
              <small v-if="variantes.length === 100" class="ficha-nota">Se muestran hasta 100 variantes activas.</small>
            </template>
            <div class="ficha-precio-caja">
              <span class="ficha-precio-etiqueta">Precio</span>
              <p class="precio-ficha">
                {{ tienda?.datos.mostrarPrecios === true ? precio(producto.datos.tieneVariantes ?
                            variante?.datos.precio : producto.datos.precioBase) : 'Consultar precio en la tienda' }}
              </p>
            </div>
            <dl v-if="Object.keys(producto.datos.atributosEspecificos ?? {}).length" class="ficha-atributos">
              <template v-for="(valor, campo) in producto.datos.atributosEspecificos" :key="campo">
                <dt>{{ campo }}</dt>
                <dd>{{ valor }}</dd>
              </template>
            </dl>
            <div class="ficha-acciones">
              <button class="boton boton-mostaza" type="button" @click="abrirContacto(tienda)">
                Contactar emprendedora
              </button>
              <a v-if="enlaceContacto" class="boton boton-primario" :href="enlaceContacto.href" target="_blank" rel="noopener noreferrer">{{ enlaceContacto.texto }}</a>
              <button class="boton boton-secundario" type="button" @click="volver">Volver a los productos</button>
            </div>
          </div>
        </section>

        <div v-else class="tienda-cuerpo">
          <section id="catalogo" :aria-busy="cargando">
            <div class="fila-titulo">
              <h2 class="titulo-bloque">Productos de {{ tienda.datos.nombre }}</h2>
              <span class="cuenta">Página {{ pagina }} · {{ visibles.length }} resultados</span>
            </div>
            <div v-if="filtroVisible && Object.keys(categorias).length" class="filtros" role="group" aria-label="Filtrar por categoría">
              <span class="filtros-etiqueta">Filtrar por categoría:</span>
              <button
                class="chip-filtro"
                :class="{ on: !categoriaSeleccionada }"
                type="button"
                @click="categoriaSeleccionada = ''"
              >Todos</button>
              <button
                v-for="(nombre, id) in categorias"
                :key="id"
                class="chip-filtro"
                :class="{ on: categoriaSeleccionada === id }"
                type="button"
                @click="categoriaSeleccionada = categoriaSeleccionada === id ? '' : id"
              >{{ nombre }}</button>
            </div>
            <div v-if="!cargando && !error && !visibles.length" class="vacio">
              <span aria-hidden="true">🌼</span>
              <p>{{ mensajeVacio }}</p>
              <button v-if="busqueda || categoriaSeleccionada" class="editar" @click="busqueda = ''; categoriaSeleccionada = ''">Limpiar filtros</button>
            </div>
            <div class="rejilla-productos">
              <button
                v-for="f in visibles"
                :key="f.id"
                class="tarjeta-producto tarjeta-producto-tienda"
                type="button"
                :disabled="cargando"
                @click="abrir(f)"
              >
                <span class="foto" @click.stop="abrirRapido(f, tienda)">
                  <ImagenPublica :ruta="f.datos.imagenes?.[0]" :alt="f.datos.nombre" ajuste="contain" :zoom="false" />
                </span>
                <h3>{{ f.datos.nombre }}</h3>
                <span class="tarjeta-meta">
                  <b v-if="tienda.datos.mostrarPrecios === true && !f.datos.tieneVariantes" class="precio">
                    {{ precio(f.datos.precioBase) }}
                  </b>
                  <small v-if="f.datos.tieneVariantes" class="precio nota">Ver variantes y precios</small>
                  <span class="badge" :class="claseDisponibilidad(f.datos.estadoDisponibilidad)">
                    {{ disponibilidad(f.datos.estadoDisponibilidad) }}
                  </span>
                </span>
              </button>
            </div>
            <nav class="paginacion" aria-label="Paginación">
              <button class="editar" :disabled="cargando || pagina === 1" @click="cargar(pagina - 1)">← Anterior</button>
              <span aria-live="polite">Página {{ pagina }}</span>
              <button class="editar" :disabled="cargando || !siguiente" @click="avanzar">Siguiente →</button>
            </nav>
          </section>

          <aside class="sobre-tienda" aria-label="Sobre la tienda">
            <h2 class="titulo-sobre">
              <span aria-hidden="true">🍂</span>
              Sobre la tienda
            </h2>
            <div v-if="etiquetaTipo" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">🏪</span>
              <div>
                <b>Tipo de emprendimiento</b>
                <p>{{ etiquetaTipo }}</p>
              </div>
            </div>
            <div v-if="etiquetaSector" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">📍</span>
              <div>
                <b>Sector</b>
                <p>{{ etiquetaSector }}</p>
              </div>
            </div>
            <div v-if="tienda.datos.contacto" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">☎</span>
              <div>
                <b>Contacto</b>
                <a
                  v-if="enlaceContacto"
                  :href="enlaceContacto.href"
                  target="_blank"
                  rel="noopener noreferrer"
                >{{ tienda.datos.contacto.valor }}</a>
                <p v-else>{{ tienda.datos.contacto.valor }}</p>
              </div>
            </div>
            <div class="sobre-nosotras">
              <h3><span aria-hidden="true">👥</span> Sobre nosotras</h3>
              <p>{{ tienda.datos.descripcion }}</p>
            </div>
          </aside>
        </div>
      </template>
    </main>

    <!-- Vista rápida de producto -->
    <Teleport to="body">
      <div
        v-if="productoRapido && tiendaRapida"
        class="rapido-fondo"
        role="dialog"
        aria-modal="true"
        :aria-label="productoRapido.datos.nombre"
        @click.self="cerrarRapido"
      >
        <div class="rapido">
          <button class="rapido-cerrar" type="button" aria-label="Cerrar" @click="cerrarRapido">✕</button>
          <div class="rapido-scroll">
            <h2 class="rapido-titulo">{{ productoRapido.datos.nombre }}</h2>
            <div class="rapido-foto">
              <ImagenPublica
                :ruta="varianteRapida?.datos.imagen ?? productoRapido.datos.imagenes?.[0]"
                :alt="productoRapido.datos.nombre"
                ajuste="contain"
                :zoom="false"
              />
            </div>
            <p class="rapido-tienda">{{ tiendaRapida.datos.nombre }}</p>
            <p v-if="categoriaRapida" class="rapido-linea">{{ categoriaRapida }}</p>
            <p class="rapido-linea">{{ productoRapido.datos.descripcion }}</p>
            <p v-if="productoRapido.datos.unidadVenta" class="rapido-linea">Venta: {{ productoRapido.datos.unidadVenta }}</p>
            <p v-if="etiquetaModalidad" class="rapido-linea">Modalidad: {{ etiquetaModalidad }}</p>
            <p class="rapido-linea"><b>{{ productoRapido.datos.tieneVariantes ? 'Stock total:' : 'Stock:' }}</b> {{ stockProductoRapido }}</p>
            <span class="badge" :class="claseDisponibilidad(varianteRapida?.datos.estadoDisponibilidad ?? productoRapido.datos.estadoDisponibilidad)">
              {{ disponibilidad(varianteRapida?.datos.estadoDisponibilidad ?? productoRapido.datos.estadoDisponibilidad) }}
            </span>
            <p v-if="variantesCargando" class="rapido-linea">Cargando variantes…</p>
            <label v-else-if="variantesRapidas.length" for="variante-rapida">Variante</label>
            <select v-if="variantesRapidas.length" id="variante-rapida" v-model="seleccionRapida" class="ficha-select">
              <option v-for="v in variantesRapidas" :key="v.id" :value="v.id">{{ nombreRapidoVariante(v) }}</option>
            </select>
            <ul v-if="variantesRapidas.length" class="rapido-variantes">
              <li v-for="v in variantesRapidas" :key="v.id">
                <span>{{ nombreRapidoVariante(v) }}<small>{{ textoStock(v.datos.stock, productoRapido.datos.manejaStock) }}</small></span>
                <b v-if="tiendaRapida.datos.mostrarPrecios !== false">{{ precio(v.datos.precio) }}</b>
              </li>
            </ul>
            <p
              v-else-if="productoRapido.datos.tieneVariantes === false && tiendaRapida.datos.mostrarPrecios !== false"
              class="rapido-precio-unico"
            >{{ precio(productoRapido.datos.precioBase) }}</p>
            <div class="rapido-acciones">
              <button class="boton boton-mostaza" type="button" @click="abrirContacto(tiendaRapida)">Contactar emprendedora</button>
              <button class="boton boton-secundario" type="button" @click="cerrarRapido">Cerrar</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Ventana de contacto con la emprendedora -->
    <Teleport to="body">
      <div
        v-if="tiendaContacto"
        class="rapido-fondo contacto-fondo"
        role="dialog"
        aria-modal="true"
        aria-labelledby="contacto-titulo"
        @click.self="cerrarContacto"
      >
        <div class="rapido contacto">
          <button class="rapido-cerrar" type="button" aria-label="Cerrar" @click="cerrarContacto">✕</button>
          <div class="rapido-scroll">
            <h2 id="contacto-titulo" class="rapido-titulo">Contactar emprendedora</h2>
            <div class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">🏪</span>
              <div>
                <b>Tienda</b>
                <p>{{ tiendaContacto.datos.nombre }}</p>
              </div>
            </div>
            <div v-if="sectorContacto" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">📍</span>
              <div>
                <b>Sector</b>
                <p>{{ sectorContacto }}</p>
              </div>
            </div>
            <div v-if="tiendaContacto.datos.contacto" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">☎</span>
              <div>
                <b>{{ tiposContacto[tiendaContacto.datos.contacto.tipo] ?? 'Contacto' }}</b>
                <p class="contacto-valor">{{ tiendaContacto.datos.contacto.valor }}</p>
              </div>
            </div>
            <p v-else class="contacto-aviso" role="status">
              Esta emprendedora aún no publicó un medio de contacto.
            </p>
            <p v-if="estadoCopia" class="contacto-copia" role="status">
              {{ estadoCopia === 'ok' ? 'Contacto copiado.' : 'No se pudo copiar. Selecciona el texto y cópialo manualmente.' }}
            </p>
            <div class="rapido-acciones">
              <button
                v-if="tiendaContacto.datos.contacto"
                class="boton boton-mostaza"
                type="button"
                @click="copiarContacto"
              >Copiar contacto</button>
              <button class="boton boton-secundario" type="button" @click="cerrarContacto">Cerrar</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <div class="aviso" :class="{ visible: aviso }" role="status">{{ aviso }}</div>
  </div>
</template>

<style scoped>
button:disabled {
    opacity: .55;
    cursor: not-allowed;
}

a.boton {
    text-decoration: none;
}

button,
a,
input,
select {
    font-family: inherit;
}

button:focus-visible,
a:focus-visible,
input:focus-visible,
select:focus-visible {
    outline: 2px solid var(--amancay-bordo);
    outline-offset: 2px;
}

.comprador {
    min-height: 100vh;
    min-height: 100dvh;
    background: var(--amancay-fondo);
    color: var(--amancay-tinta);
    font-family: var(--amancay-fuente-cuerpo);
}

.comprador.modo-explorar {
    display: grid;
    grid-template-columns: 280px minmax(0, 1fr);
}

@media (max-width: 900px) {
    .comprador.modo-explorar {
        grid-template-columns: 1fr;
    }
}

.comprador.modo-tienda {
    display: grid;
    grid-template-rows: auto 1fr;
}

:global(html),
:global(body) {
    margin: 0;
    min-height: 100%;
}

/* Marca compartida */
.marca {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    text-align: left;
}

.marca-icono {
    width: 46px;
    height: 46px;
    padding: 6px;
    box-sizing: border-box;
    border-radius: 50%;
    object-fit: contain;
    background: var(--amancay-naranja-fondo);
    border: 1.5px solid var(--amancay-borde);
}

.marca-texto {
    display: flex;
    flex-direction: column;
    line-height: 1.1;
}

.marca-texto small {
    font-size: 0.7rem;
    color: var(--amancay-tinta-suave);
}

.marca-texto strong {
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.2rem;
    color: var(--amancay-bordo);
}

.marca-boton {
    background: none;
    border: 0;
    padding: 0;
    cursor: pointer;
}

/* Barra superior de tienda */
.barra-superior {
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.8rem 1.2rem;
    padding: 0.8rem clamp(1rem, 3vw, 2.5rem);
    background: rgba(255, 251, 242, 0.94);
    backdrop-filter: blur(6px);
    border-bottom: 1.5px solid var(--amancay-borde);
}

.barra-nav {
    display: flex;
    align-items: center;
    gap: 0.9rem;
    min-width: 0;
}

.barra-nav button {
    border: 0;
    background: none;
    padding: 0.4rem 0.2rem;
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
    cursor: pointer;
}

.barra-nav button:hover {
    color: var(--amancay-bordo);
}

.barra-enlace {
    border: 0;
    background: none;
    padding: 0.4rem 0.2rem;
    font-size: 0.92rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
    text-decoration: none;
    cursor: pointer;
    font-family: inherit;
}

.barra-enlace:hover {
    color: var(--amancay-bordo);
}

.barra-actual {
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 1rem;
    color: var(--amancay-bordo);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 30vw;
}

.barra-sesion {
    margin-left: auto;
}

.barra-sesion :deep(.acceso) {
    margin-top: 0;
}

.barra-sesion :deep(.acceso-abrir) {
    width: auto;
    white-space: nowrap;
    padding: 0.6rem 1.3rem;
}

.barra-sesion :deep(.acceso-sesion) {
    min-width: 200px;
    max-width: 260px;
}

/* Contenido */
.contenido {
    width: 100%;
    min-width: 0;
    padding: 1.8rem clamp(1rem, 3vw, 2.5rem) 3.5rem;
    box-sizing: border-box;
}

.encabezado-fila {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 1rem;
    flex-wrap: wrap;
    margin-bottom: 1.4rem;
}

.encabezado-sesion :deep(.acceso) {
    margin-top: 0.4rem;
}

.encabezado-sesion :deep(.acceso-abrir) {
    width: auto;
    white-space: nowrap;
    padding: 0.7rem 1.5rem;
}

.encabezado-sesion :deep(.acceso-sesion) {
    min-width: 220px;
    max-width: 280px;
}

h1 {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: clamp(1.7rem, 3vw, 2.4rem);
    line-height: 1.12;
    color: var(--amancay-bordo);
}

.sub {
    margin: 0.45rem 0 0;
    color: var(--amancay-tinta-suave);
    font-size: 0.98rem;
    max-width: 56ch;
}

.sub.clamp {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.sobretitulo {
    margin: 0 0 0.4rem;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.1em;
    color: var(--amancay-mostaza);
}

.titulo-bloque {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.5rem;
    color: var(--amancay-bordo);
}

.fila-titulo {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 1rem;
    flex-wrap: wrap;
    margin: 2rem 0 1rem;
}

.fila-titulo .titulo-bloque {
    margin: 0;
}

.cuenta {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
}

.enlace {
    font-size: 0.9rem;
    font-weight: 700;
    color: var(--amancay-bordo);
    text-decoration: none;
}

.enlace:hover {
    text-decoration: underline;
}

/* Hero de explorar */
.hero {
    position: relative;
    overflow: hidden;
    display: flex;
    align-items: center;
    gap: 1.5rem;
    padding: clamp(1.5rem, 3vw, 2.4rem);
    border-radius: 26px;
    background: linear-gradient(135deg, var(--amancay-bordo) 0%, var(--amancay-bordo-oscuro) 100%);
    color: var(--amancay-superficie);
    box-shadow: 0 14px 30px rgba(107, 30, 61, 0.25);
    margin-bottom: 1.4rem;
}

.hero::before {
    content: '';
    position: absolute;
    width: 300px;
    height: 300px;
    right: 0;
    top: -110px;
    border-radius: 50%;
    background: rgba(246, 193, 119, 0.14);
    pointer-events: none;
}

.hero-texto {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.45rem;
    flex: 1;
    min-width: 0;
}

.hero-texto h2 {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: clamp(1.5rem, 2.6vw, 2.1rem);
    line-height: 1.15;
}

.hero-texto p:not(.sobretitulo) {
    margin: 0 0 0.7rem;
    opacity: 0.88;
}

.hero-frase {
    position: relative;
    z-index: 1;
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-style: italic;
    font-size: 1.05rem;
    line-height: 1.35;
    color: var(--amancay-naranja-fondo);
    text-align: center;
    flex: none;
}

.hero-arte {
    position: relative;
    z-index: 1;
    flex: none;
    width: 132px;
    height: 132px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--amancay-superficie);
    box-shadow: 0 0 0 12px rgba(246, 193, 119, 0.22);
}

.hero-arte img {
    width: 104px;
    height: 104px;
    object-fit: contain;
}

.hero-lista {
    position: relative;
    z-index: 1;
    margin: 0;
    padding: 0 0 0 0.4rem;
    list-style: none;
    font-size: 0.85rem;
    line-height: 1.9;
    color: rgba(255, 251, 242, 0.82);
    flex: none;
}

/* Buscadores */
.buscador {
    display: flex;
    align-items: center;
    gap: 0.55rem;
    padding: 0 1.1rem;
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 999px;
    color: var(--amancay-tinta-suave);
}

.buscador:focus-within {
    border-color: var(--amancay-bordo);
}

.buscador input {
    flex: 1;
    min-width: 0;
    padding: 0.8rem 0;
    border: 0;
    outline: 0;
    background: none;
    font-size: 0.93rem;
    color: var(--amancay-tinta);
}

.buscador-ancho {
    margin-bottom: 0.6rem;
}

.buscador-barra {
    flex: 1;
    min-width: 200px;
    max-width: 460px;
}

.buscador-filtro {
    flex: none;
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    margin-right: -0.4rem;
    padding: 0;
    border: 0;
    border-radius: 50%;
    background: var(--amancay-naranja-fondo);
    color: var(--amancay-bordo);
    cursor: pointer;
}

.buscador-filtro:hover,
.buscador-filtro[aria-expanded='true'] {
    background: var(--amancay-bordo);
    color: #fff;
}

/* Filtros de categoría (catálogo activo de la app) */
.filtros {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem;
    margin: 0.2rem 0 1.1rem;
}

.filtros-etiqueta {
    font-size: 0.86rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
    margin-right: 0.15rem;
}

.chip-filtro {
    padding: 0.42rem 0.95rem;
    border: 1.5px solid var(--amancay-borde);
    border-radius: 999px;
    background: #fff;
    font-size: 0.84rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
    cursor: pointer;
    transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.chip-filtro:hover {
    border-color: var(--amancay-bordo);
    color: var(--amancay-bordo);
}

.chip-filtro.on {
    background: var(--amancay-bordo);
    border-color: var(--amancay-bordo);
    color: #fff;
}

/* Migas */
.migas {
    display: flex;
    flex-wrap: wrap;
    gap: 0.45rem;
    margin-bottom: 1.1rem;
    font-size: 0.86rem;
    color: var(--amancay-tinta-suave);
}

.migas button {
    padding: 0;
    background: none;
    border: 0;
    font-size: inherit;
    font-weight: 600;
    color: var(--amancay-bordo);
    cursor: pointer;
}

.migas .actual {
    color: var(--amancay-tinta);
    font-weight: 600;
}

/* Hero de tienda */
.tienda-hero {
    display: flex;
    gap: 1.5rem;
    padding: clamp(1.2rem, 2.5vw, 1.8rem);
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 26px;
    box-shadow: 0 8px 24px rgba(107, 30, 61, 0.07);
    margin-bottom: 1.6rem;
}

.tienda-hero-info {
    display: flex;
    gap: 1.4rem;
    flex: 1;
    min-width: 0;
}

.tienda-logo {
    flex: none;
    width: 150px;
    height: 150px;
    border-radius: 50%;
    overflow: hidden;
    background: var(--amancay-naranja-fondo);
    border: 4px solid #fff;
    box-shadow: 0 6px 18px rgba(107, 30, 61, 0.12);
}

.tienda-logo :deep(.imagen-publica) {
    aspect-ratio: 1;
    height: 100%;
    border-radius: 0;
    background: transparent;
}

.tienda-hero-texto {
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.55rem;
}

.tienda-hero-texto h1 {
    font-size: clamp(1.5rem, 2.6vw, 2.1rem);
}

.tienda-hero-texto .sub {
    margin: 0;
}

.acciones-hero {
    display: flex;
    flex-wrap: wrap;
    gap: 0.7rem;
    margin-top: 0.4rem;
}

.tienda-hero-arte {
    position: relative;
    flex: none;
    width: min(32%, 300px);
    border-radius: 20px;
    overflow: hidden;
    display: grid;
    place-items: center;
    background:
        radial-gradient(120px 80px at 80% 20%, rgba(246, 193, 119, 0.45), transparent 70%),
        linear-gradient(160deg, var(--amancay-superficie-variante), var(--amancay-naranja-fondo));
}

.tienda-hero-arte img {
    width: 58%;
    max-width: 150px;
    opacity: 0.9;
}

.arte-tag {
    position: absolute;
    right: 12px;
    bottom: 12px;
    margin: 0;
    padding: 0.35rem 0.7rem;
    border-radius: 999px;
    background: rgba(255, 251, 242, 0.9);
    border: 1px dashed var(--amancay-bordo);
    font-family: 'DM Serif Display', Georgia, serif;
    font-style: italic;
    font-size: 0.78rem;
    color: var(--amancay-bordo);
}

/* Cuerpo de tienda: productos + lateral */
.tienda-cuerpo {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    gap: 1.5rem;
    align-items: start;
}

.tienda-cuerpo .fila-titulo {
    margin-top: 0.4rem;
}

/* Botones */
.boton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.4rem;
    min-height: 46px;
    padding: 0.65rem 1.4rem;
    font-size: 0.93rem;
    font-weight: 700;
    border-radius: 999px;
    border: 1.5px solid transparent;
    cursor: pointer;
    transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
}

.boton:active {
    transform: scale(0.98);
}

.boton-primario {
    color: #fff;
    background: var(--amancay-bordo);
    border-color: var(--amancay-bordo);
    box-shadow: 0 6px 16px rgba(107, 30, 61, 0.25);
}

.boton-primario:hover {
    background: var(--amancay-bordo-oscuro);
}

.boton-mostaza {
    color: var(--amancay-tinta);
    background: var(--amancay-mostaza);
    border-color: var(--amancay-mostaza);
    box-shadow: 0 6px 16px rgba(242, 183, 59, 0.3);
}

.boton-mostaza:hover {
    background: #f6c65f;
}

.boton-secundario {
    color: var(--amancay-bordo);
    background: #fff;
    border-color: var(--amancay-borde);
}

.boton-secundario:hover {
    background: var(--amancay-bordo-suave);
    border-color: var(--amancay-bordo);
}

/* Tarjetas de producto */
.rejilla-productos {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
    gap: 1.1rem;
}

.tarjeta-producto {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 0.45rem;
    padding: 0.8rem 0.8rem 1rem;
    background: #fff;
    border: 1.5px solid var(--amancay-borde);
    border-radius: 20px;
    text-align: left;
    color: inherit;
    cursor: pointer;
    transition: border-color 0.15s, transform 0.15s, box-shadow 0.15s;
}

.tarjeta-producto:hover {
    border-color: var(--amancay-bordo);
    transform: translateY(-3px);
    box-shadow: 0 10px 24px rgba(107, 30, 61, 0.1);
}

.tarjeta-producto .foto {
    position: relative;
    display: block;
    border-radius: 14px;
    overflow: hidden;
}

.tarjeta-producto h3 {
    margin: 0.35rem 0 0;
    font-size: 1rem;
    line-height: 1.3;
    color: var(--amancay-tinta);
}

.tarjeta-tienda {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
}

.tarjeta-tienda-logo {
    flex: none;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    overflow: hidden;
    background: var(--amancay-naranja-fondo);
    border: 1px solid var(--amancay-borde);
}

.tarjeta-tienda-logo :deep(.imagen-publica) {
    aspect-ratio: 1;
    height: 100%;
    border-radius: 0;
    background: transparent;
    font-size: 0.7rem;
}

.tarjeta-tienda-logo :deep(.imagen-publica span) {
    font-size: 0.7rem;
    gap: 0;
}

.tarjeta-tienda-logo :deep(.imagen-publica small) {
    display: none;
}

/* Placeholder de logo sin imagen */
.logo-inicial {
    display: grid;
    place-items: center;
    width: 100%;
    height: 100%;
    aspect-ratio: 1;
    border-radius: 50%;
    background:
        radial-gradient(circle at 30% 25%, rgba(246, 193, 119, 0.55), transparent 55%),
        var(--amancay-naranja-fondo);
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 0.95rem;
    color: var(--amancay-bordo);
    user-select: none;
}

.logo-inicial.grande {
    font-size: 1.75rem;
}

.logo-inicial.hero {
    width: 100%;
    height: 100%;
    font-size: 3rem;
}

.precio {
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 1.1rem;
    color: var(--amancay-bordo);
}

.precio.nota {
    font-family: var(--amancay-fuente-cuerpo);
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
}

/* Badges de disponibilidad */
.badge {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    border-radius: 999px;
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0.28rem 0.7rem;
    background: var(--amancay-verde-fondo);
    color: var(--amancay-verde-texto);
    white-space: nowrap;
}

.badge::before {
    content: '';
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: currentColor;
}

.badge.pend {
    background: var(--amancay-mostaza-suave);
    color: #8a5a00;
}

.badge.off {
    background: var(--amancay-bordo-suave);
    color: var(--amancay-bordo-oscuro);
}

.foto .badge {
    position: absolute;
    top: 10px;
    right: 10px;
    box-shadow: 0 4px 10px rgba(46, 27, 32, 0.12);
}

.tarjeta-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 0.75rem;
    margin-top: 0.15rem;
}

.tarjeta-producto-tienda .foto .badge {
    display: none;
}

.badge.grande {
    font-size: 0.78rem;
    padding: 0.35rem 0.85rem;
    align-self: flex-start;
}

/* Tarjetas de tienda */
.rejilla-tiendas {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 1.1rem;
}

article.tarjeta-tienda {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 1.1rem 1.2rem;
    background: #fff;
    border: 1.5px solid var(--amancay-borde);
    border-radius: 20px;
    transition: border-color 0.15s, transform 0.15s, box-shadow 0.15s;
}

article.tarjeta-tienda:hover {
    border-color: var(--amancay-bordo);
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(107, 30, 61, 0.08);
}

.tarjeta-tienda-logo.grande {
    width: 76px;
    height: 76px;
}

.tarjeta-tienda-logo.grande :deep(.imagen-publica span) {
    font-size: 1.4rem;
}

.tarjeta-tienda-cuerpo {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.35rem;
}

.tarjeta-tienda-cuerpo h3 {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.15rem;
    color: var(--amancay-bordo);
}

.tarjeta-tienda-cuerpo p {
    margin: 0;
    font-size: 0.85rem;
    line-height: 1.5;
    color: var(--amancay-tinta-suave);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.tarjeta-tienda .boton {
    flex: none;
    min-height: 40px;
    padding: 0.5rem 1.1rem;
    font-size: 0.84rem;
}

/* Chips */
.chips {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
}

.chip {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.4rem 0.85rem;
    border-radius: 999px;
    border: 1.5px solid var(--amancay-borde);
    background: var(--amancay-fondo);
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--amancay-tinta);
    white-space: nowrap;
}

/* Sobre la tienda */
.sobre-tienda {
    position: sticky;
    top: 90px;
    padding: 1.3rem;
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 22px;
    box-shadow: 0 8px 24px rgba(107, 30, 61, 0.06);
}

.titulo-sobre {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin: 0 0 1rem;
    padding-bottom: 0.8rem;
    border-bottom: 1.5px solid var(--amancay-borde);
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.3rem;
    color: var(--amancay-bordo);
}

.dato-tienda {
    display: flex;
    gap: 0.75rem;
    padding: 0.7rem 0;
    border-bottom: 1px solid var(--amancay-borde);
}

.dato-icono {
    flex: none;
    width: 38px;
    height: 38px;
    display: grid;
    place-items: center;
    border-radius: 12px;
    background: var(--amancay-naranja-fondo);
    font-size: 1rem;
}

.dato-tienda b {
    display: block;
    font-size: 0.85rem;
    color: var(--amancay-tinta);
}

.dato-tienda p,
.dato-tienda a {
    margin: 0.15rem 0 0;
    font-size: 0.88rem;
    color: var(--amancay-tinta-suave);
    text-decoration: none;
    overflow-wrap: anywhere;
}

.dato-tienda a {
    color: var(--amancay-bordo);
    font-weight: 600;
}

.dato-tienda a:hover {
    text-decoration: underline;
}

.sobre-nosotras {
    margin-top: 1rem;
    padding: 1rem;
    border-radius: 16px;
    background: linear-gradient(160deg, var(--amancay-superficie-variante), var(--amancay-mostaza-suave));
}

.sobre-nosotras h3 {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    margin: 0 0 0.5rem;
    font-size: 0.95rem;
    color: var(--amancay-bordo);
}

.sobre-nosotras p {
    margin: 0;
    font-size: 0.87rem;
    line-height: 1.6;
    color: var(--amancay-tinta);
}

/* Detalle de producto */
.detalle {
    display: grid;
    grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
    gap: 1.75rem;
    align-items: start;
}

.galeria {
    display: grid;
    gap: 0.9rem;
    align-content: start;
    justify-items: start;
}

.galeria-marco {
    width: 100%;
    display: grid;
    gap: 0.85rem;
    padding: 1rem;
    box-sizing: border-box;
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 26px;
    box-shadow: 0 12px 28px rgba(107, 30, 61, 0.08);
}

.galeria-marco :deep(.imagen-publica) {
    border-radius: 18px;
    background: linear-gradient(160deg, var(--amancay-superficie-variante), var(--amancay-naranja-fondo));
}

.ficha {
    position: sticky;
    top: 90px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
    padding: clamp(1.4rem, 2.5vw, 2rem);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 26px;
    background:
        radial-gradient(220px 120px at 100% 0%, rgba(246, 193, 119, 0.22), transparent 70%),
        #fff;
    box-shadow: 0 14px 32px rgba(107, 30, 61, 0.1);
}

.ficha-miga {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem;
    margin: 0;
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--amancay-tinta-suave);
}

.ficha-miga button {
    padding: 0;
    border: 0;
    background: none;
    font: inherit;
    font-weight: 700;
    color: var(--amancay-bordo);
    cursor: pointer;
}

.ficha-miga button:hover {
    text-decoration: underline;
}

.titulo-ficha {
    margin: 0.15rem 0 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: clamp(1.6rem, 2.5vw, 2rem);
    line-height: 1.15;
    color: var(--amancay-bordo);
}

.ficha-desc {
    margin: 0;
    line-height: 1.65;
    color: var(--amancay-tinta-suave);
    font-size: 0.97rem;
}

.ficha-datos {
    display: flex;
    flex-wrap: wrap;
    gap: 0.55rem;
    width: 100%;
    margin-top: 0.15rem;
}

.ficha-dato {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
    padding: 0.55rem 0.85rem;
    border-radius: 14px;
    background: var(--amancay-fondo);
    border: 1px solid var(--amancay-borde);
    font-size: 0.86rem;
    font-weight: 600;
    color: var(--amancay-tinta);
}

.ficha-dato-etiqueta {
    font-size: 0.7rem;
    font-weight: 700;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--amancay-tinta-suave);
}

.ficha-label {
    font-weight: 700;
    font-size: 0.88rem;
    color: var(--amancay-tinta);
    margin-top: 0.2rem;
}

.ficha-select {
    width: 100%;
    box-sizing: border-box;
    padding: 0.85rem 1rem;
    border: 1.5px solid var(--amancay-borde);
    border-radius: 14px;
    font: inherit;
    font-size: 0.95rem;
    background: #fff;
    color: var(--amancay-tinta);
    cursor: pointer;
}

.ficha-select:hover,
.ficha-select:focus {
    border-color: var(--amancay-bordo);
}

.ficha-nota {
    margin: 0;
    font-size: 0.84rem;
    color: var(--amancay-tinta-suave);
}

.ficha-stock-variantes {
    width: 100%;
    padding: 0.85rem 1rem;
    border: 1px solid var(--amancay-borde);
    border-radius: 14px;
    background: var(--amancay-fondo);
    box-sizing: border-box;
}

.ficha-stock-variantes h3 {
    margin: 0 0 0.55rem;
    color: var(--amancay-bordo);
    font-size: 0.9rem;
}

.ficha-stock-variantes ul {
    display: grid;
    gap: 0.45rem;
    margin: 0;
    padding: 0;
    list-style: none;
}

.ficha-stock-variantes li {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 0.75rem;
    color: var(--amancay-tinta);
    font-size: 0.86rem;
}

.ficha-stock-variantes b {
    text-align: right;
}

.ficha-precio-caja {
    width: 100%;
    margin-top: 0.25rem;
    padding: 0.9rem 1.1rem;
    border-radius: 16px;
    background: linear-gradient(135deg, var(--amancay-bordo-suave), var(--amancay-mostaza-suave));
    border: 1px solid var(--amancay-borde);
}

.ficha-precio-etiqueta {
    display: block;
    font-size: 0.7rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--amancay-tinta-suave);
    margin-bottom: 0.15rem;
}

.precio-ficha {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: clamp(1.7rem, 3vw, 2.2rem);
    line-height: 1.1;
    color: var(--amancay-bordo);
}

.ficha-atributos {
    margin: 0.35rem 0 0;
    width: 100%;
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 0.35rem 0.9rem;
    padding-top: 0.75rem;
    border-top: 1.5px dashed var(--amancay-borde);
}

.ficha-atributos dt {
    font-weight: 700;
    color: var(--amancay-bordo);
    font-size: 0.86rem;
}

.ficha-atributos dd {
    margin: 0;
    color: var(--amancay-tinta);
    font-size: 0.9rem;
}

.ficha-acciones {
    display: flex;
    flex-wrap: wrap;
    gap: 0.7rem;
    width: 100%;
    margin-top: 0.55rem;
}

.ficha-acciones .boton {
    flex: 1 1 auto;
    min-width: min(100%, 160px);
}

/* Vista rápida modal */
.rapido-fondo {
    position: fixed;
    z-index: 10000;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 1.25rem;
    background: rgba(46, 27, 32, 0.55);
    backdrop-filter: blur(3px);
}

.rapido {
    position: relative;
    width: min(440px, 100%);
    max-height: min(90vh, 820px);
    overflow: auto;
    background: var(--amancay-superficie);
    border-radius: 28px;
    border: 1.5px solid var(--amancay-borde);
    box-shadow: 0 24px 60px rgba(46, 27, 32, 0.35);
}

.rapido-cerrar {
    position: absolute;
    top: 0.85rem;
    right: 0.85rem;
    z-index: 1;
    width: 2.3rem;
    height: 2.3rem;
    border: none;
    border-radius: 50%;
    background: var(--amancay-bordo);
    color: #fff;
    font-size: 1.05rem;
    line-height: 1;
    cursor: pointer;
}

.rapido-cerrar:hover {
    background: var(--amancay-bordo-oscuro);
}

.rapido-scroll {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.45rem;
    padding: 1.5rem 1.4rem 1.4rem;
}

.rapido-titulo {
    margin: 0 2rem 0.35rem 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.65rem;
    line-height: 1.15;
    color: var(--amancay-tinta);
}

.rapido-foto {
    width: 100%;
    margin-bottom: 0.35rem;
}

.rapido-foto :deep(.imagen-publica) {
    border-radius: 18px;
    background: linear-gradient(160deg, var(--amancay-superficie-variante), var(--amancay-naranja-fondo));
    max-width: calc(260px * 4 / 3);
}

.rapido-tienda {
    margin: 0;
    font-weight: 700;
    font-size: 0.95rem;
    color: var(--amancay-bordo);
}

.rapido-linea {
    margin: 0;
    font-size: 0.92rem;
    line-height: 1.5;
    color: var(--amancay-tinta);
}

.rapido-variantes {
    list-style: none;
    margin: 0.35rem 0 0;
    padding: 0;
    width: 100%;
    display: grid;
    gap: 0.4rem;
}

.rapido-variantes li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 0.75rem;
    padding: 0.65rem 0.85rem;
    border-radius: 12px;
    background: var(--amancay-fondo);
    border: 1px solid var(--amancay-borde);
    font-size: 0.9rem;
    color: var(--amancay-tinta);
}

.rapido-variantes b {
    color: var(--amancay-bordo);
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1rem;
    white-space: nowrap;
}

.rapido-variantes li > span {
    display: grid;
    gap: 0.2rem;
}

.rapido-variantes small {
    color: var(--amancay-tinta-suave);
    font-size: 0.78rem;
}

.rapido-precio-unico {
    margin: 0.35rem 0 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 1.35rem;
    color: var(--amancay-bordo);
}

.contacto-fondo {
    z-index: 10001;
}

.contacto {
    width: min(400px, 100%);
}

.contacto .dato-tienda {
    width: 100%;
}

.contacto-valor {
    overflow-wrap: anywhere;
}

.contacto-aviso,
.contacto-copia {
    margin: 0.35rem 0 0;
    padding: 0.7rem 0.9rem;
    border-radius: 12px;
    background: var(--amancay-mostaza-suave);
    border: 1px solid var(--amancay-mostaza);
    font-size: 0.88rem;
    line-height: 1.45;
    color: #6a4a00;
    font-weight: 600;
}

.contacto-copia {
    background: var(--amancay-fondo);
    border-color: var(--amancay-borde);
    color: var(--amancay-tinta);
}

.rapido-acciones {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
    width: 100%;
    margin-top: 0.85rem;
}

.rapido-acciones .boton {
    flex: 1 1 auto;
}

/* Estados */
.aviso-error,
.aviso-carga {
    margin: 0 0 1rem;
    padding: 0.75rem 1.1rem;
    border-radius: 14px;
    font-size: 0.88rem;
    font-weight: 600;
}

.aviso-error {
    background: var(--amancay-bordo-suave);
    color: var(--amancay-bordo-oscuro);
}

.aviso-carga {
    background: var(--amancay-mostaza-suave);
    color: #8a5a00;
}

.vacio {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.6rem;
    padding: 2.5rem 1rem;
    background: var(--amancay-superficie);
    border: 1.5px dashed var(--amancay-borde);
    border-radius: 22px;
    color: var(--amancay-tinta-suave);
}

.vacio span {
    font-size: 2.2rem;
}

.vacio p {
    margin: 0;
}

.editar {
    padding: 0.5rem 1.1rem;
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--amancay-bordo);
    background: transparent;
    border: 1.5px solid var(--amancay-bordo);
    border-radius: 999px;
    cursor: pointer;
}

.editar:hover {
    background: var(--amancay-bordo-suave);
}

.paginacion {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 1rem;
    margin-top: 1.5rem;
}

.aviso {
    position: fixed;
    left: 50%;
    bottom: 1.5rem;
    transform: translate(-50%, 8px);
    padding: 0.8rem 1.2rem;
    background: var(--amancay-bordo);
    color: #fff;
    border-radius: 999px;
    font-size: 0.9rem;
    font-weight: 600;
    box-shadow: 0 10px 24px rgba(46, 27, 32, 0.3);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s, transform 0.2s;
    z-index: 50;
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

@media (max-width: 1100px) {
    .tienda-cuerpo {
        grid-template-columns: minmax(0, 1fr);
    }

    .sobre-tienda {
        position: static;
    }

    .hero-lista {
        display: none;
    }

    .hero {
        min-width: 0;
    }
}

@media (max-width: 900px) {
    .comprador.modo-explorar {
        grid-template-columns: minmax(0, 1fr);
    }

    .lateral {
        position: static;
        height: auto;
        flex-direction: row;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.8rem;
        padding: 0.9rem 1rem;
        border-right: 0;
        border-bottom: 1.5px solid var(--amancay-borde);
    }

    .menu {
        flex-direction: row;
        flex-wrap: wrap;
    }

    .lateral-frase {
        display: none;
    }

    .contenido {
        padding: 1.25rem 1rem 3rem;
    }

    .hero-frase,
    .hero-arte {
        display: none;
    }

    .tienda-hero {
        flex-direction: column;
    }

    .tienda-hero-info {
        align-items: flex-start;
    }

    .tienda-hero-arte {
        width: 100%;
        min-height: 150px;
    }

    .detalle {
        grid-template-columns: 1fr;
    }

    .ficha {
        position: static;
    }

    .barra-sesion {
        margin-left: 0;
    }

    .buscador-barra {
        order: 3;
        max-width: none;
        flex-basis: 100%;
    }

    .rejilla-tiendas {
        grid-template-columns: minmax(0, 1fr);
    }
}

@media (max-width: 560px) {
    .hero {
        padding: 1.25rem;
    }

    .hero-texto h2 {
        font-size: clamp(1.45rem, 7vw, 1.8rem);
    }

    .hero-texto > p:not(.sobretitulo) {
        max-width: 34ch;
    }

    .encabezado-fila h1 {
        font-size: clamp(1.55rem, 7vw, 2rem);
    }

    .tienda-hero-info {
        flex-direction: row;
        gap: 0.9rem;
    }

    .tienda-logo {
        width: 84px;
        height: 84px;
    }

    .tarjeta-tienda {
        flex-wrap: wrap;
    }

    .rejilla-productos {
        grid-template-columns: repeat(auto-fill, minmax(min(160px, 100%), 1fr));
    }

    .detalle {
        min-width: 0;
    }

    .tienda-hero-texto h1 {
        overflow-wrap: anywhere;
    }
}
</style>
