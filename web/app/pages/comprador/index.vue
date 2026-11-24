<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import '~/assets/amancay-tokens.css';
import type { FichaComprador, ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico';
import { categoriasProductoVigentes } from '~/repositories/catalogo-publico';
import { compartirEnlacePublico } from '~/utils/compartir-enlace-publico';
useHead({
    title: 'Comprar local — Rincón Amancay',
    link: [{ rel: 'icon', href: '/favicon.png' },
        { rel: 'stylesheet',
            href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' }]
});

// Cliente y estado de la página
const { $catalogoPublico } = useNuxtApp();
const apiUrlPublica = useRuntimeConfig().public.apiUrl;
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
const categoriaTipoProducto = ref<Record<string, string>>({});
const categoriasFiltro = computed(() => Object.fromEntries(
    categoriasProductoVigentes(Object.entries(categorias.value).map(([id, nombre]) => ({
        id,
        nombre,
        tipoProductoId: categoriaTipoProducto.value[id] ?? '',
    }))).map((categoria) => [categoria.id, categoria.nombre]),
));

function abrirAyudaComprador() {
    window.dispatchEvent(new Event('amancay:ayuda-comprador'));
}
const categoriaSeleccionada = ref('');
const filtroVisible = ref(true);
let cursores: (string | null)[] = [null];
let cursorSiguiente: string | null = null;
let revision = 0;
let avisoTimer: ReturnType<typeof setTimeout> | undefined;
let pausaSincronizacionRuta = 0;
let temporizadorRuta: ReturnType<typeof setTimeout> | undefined;
let rutaInicializada = false;
let detenerObservadoresProductos: (() => void)[] = [];

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
            direccion: elemento.direccion,
            puntoRetiro: elemento.puntoRetiro,
            mostrarPrecios: elemento.mostrarPrecios,
            contacto: elemento.contacto,
            canalesContacto: elemento.canalesContacto,
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

function valorContactoVisible(ficha: FichaComprador | null): string {
    const contacto = ficha?.datos.contacto;
    if (!contacto) return '';
    const whatsapp = ficha.datos.canalesContacto?.whatsapp;
    return contacto.tipo === 'whatsapp' && whatsapp?.habilitado && whatsapp.numero.trim()
        ? whatsapp.numero
        : contacto.valor;
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
const ubicacionTienda = (datos?: Pick<FichaComprador['datos'], 'direccion' | 'sectorId'>) => {
    if (!datos) return '';
    const sector = datos.sectorId ? sectores.value[datos.sectorId] : '';
    if (sector) return sector;
    return datos.direccion?.trim() ?? '';
};
const etiquetaUbicacion = computed(() => ubicacionTienda(tienda.value?.datos));
const etiquetaTipo = computed(() => {
    const id = tienda.value?.datos.tipoEmprendimientoId;
    return id ? tiposEmprendimiento.value[id] ?? '' : '';
});
const enlaceComoLlegarPunto = computed(() => {
    const punto = tienda.value?.datos.puntoRetiro;
    if (!punto) return '';
    return `https://www.google.com/maps/dir/?api=1&destination=${punto.latitud},${punto.longitud}`;
});
const conteoProductos = computed(() => `${filas.value.length}${siguiente.value ? '+' : ''}`);

function numeroWhatsapp(ficha: FichaComprador | null | undefined): string | null {
    if (!ficha) return null;
    const canales = ficha.datos.canalesContacto;
    if (canales) {
        if (!canales.whatsapp?.habilitado) return null;
        const numero = (canales.whatsapp.numero || '').replace(/\D/g, '');
        return /^[1-9]\d{7,14}$/.test(numero) ? numero : null;
    }
    const contacto = ficha.datos.contacto;
    if (contacto?.tipo !== 'whatsapp') return null;
    const numero = contacto.valor.replace(/\D/g, '');
    return /^[1-9]\d{7,14}$/.test(numero) ? numero : null;
}

function enlaceWhatsapp(numero: string, mensaje: string): string {
    return `https://wa.me/${numero}?text=${encodeURIComponent(mensaje)}`;
}

const whatsappPublico = computed(() => {
    const numero = numeroWhatsapp(tienda.value);
    return numero ? enlaceWhatsapp(numero, 'Hola, vi tus productos en Rincón Amancay.') : null;
});

function correoContactoPublico(ficha: FichaComprador | null | undefined): string | null {
    if (!ficha) return null;
    const canales = ficha.datos.canalesContacto;
    const correo = canales
        ? canales.correo?.habilitado === true ? canales.correo.direccion : ''
        : ficha.datos.contacto?.tipo === 'correo' ? ficha.datos.contacto.valor : '';
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim()) ? correo.trim() : null;
}

const correoPublico = computed(() => correoContactoPublico(tienda.value));

const formularioPublico = computed(() => {
    const canales = tienda.value?.datos.canalesContacto;
    // Las fichas antiguas se normalizan desde formaContacto en el repositorio.
    // Si un adaptador no entrega canales, se conserva el comportamiento legado de formulario.
    return canales ? canales.formulario?.habilitado === true : Boolean(tienda.value);
});

const tieneContactoPublico = computed(() => Boolean(whatsappPublico.value || formularioPublico.value || correoPublico.value));

const formularioContactoAbierto = ref(false);
const formularioContactoDirecto = ref(false);
const nombreConsulta = ref('');
const correoConsulta = ref('');
const mensajeConsulta = ref('');
const enviandoConsulta = ref(false);
const consultaEnviada = ref(false);
const errorConsulta = ref('');
let consultaPendiente: { firma: string; operacionId: string } | null = null;

const urlConsultasPublicas = computed(() => {
    try {
        const url = new URL(apiUrlPublica);
        const httpLocal = url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname);
        if ((url.protocol !== 'https:' && !httpLocal) || url.username || url.password || url.search || url.hash || url.pathname !== '/') return null;
        return `${url.origin}/api/v1/consultasPublicas`;
    } catch {
        return null;
    }
});

const productoContacto = ref<FichaComprador | null>(null);
const varianteContacto = ref<FichaComprador | null>(null);
const imagenProductoContacto = computed(() => varianteContacto.value?.datos.imagen || productoContacto.value?.datos.imagenes?.[0] || '');
const varianteProductoContacto = computed(() => varianteContacto.value ? nombreVariante(varianteContacto.value) : '');
const precioProductoContacto = computed(() => {
    if (tiendaContacto.value?.datos.mostrarPrecios !== true || !productoContacto.value) return 'Consultar precio';
    const monto = productoContacto.value.datos.tieneVariantes
        ? varianteContacto.value?.datos.precio
        : productoContacto.value.datos.precioBase;
    return precio(monto);
});

const enlaceContacto = computed(() => {
    const ficha = tienda.value;
    const numero = numeroWhatsapp(ficha);
    if (ficha?.datos.canalesContacto) {
        if (numero) return { href: enlaceWhatsapp(numero, 'Hola, vi tus productos en Rincón Amancay.'), texto: 'Contactar por WhatsApp' };
        const correo = correoContactoPublico(ficha);
        return correo ? { href: `mailto:${correo}`, texto: 'Contactar por correo' } : null;
    }
    const contacto = ficha?.datos.contacto;
    if (!contacto) return null;
    if (contacto.tipo === 'whatsapp') return numero ? { href: enlaceWhatsapp(numero, 'Hola, vi tus productos en Rincón Amancay.'), texto: 'Contactar por WhatsApp' } : null;
    if (contacto.tipo === 'correo') return { href: `mailto:${contacto.valor}`, texto: 'Contactar por correo' };
    if (contacto.tipo === 'web' && contacto.valor.startsWith('https://')) return { href: contacto.valor, texto: 'Visitar sitio de la tienda' };
    return null;
});

const contactoTiendaVisible = computed(() => {
    const ficha = tienda.value;
    if (!ficha) return null;
    if (ficha.datos.canalesContacto) {
        const numero = numeroWhatsapp(ficha);
        if (numero) return { tipo: 'whatsapp' as const, valor: numero };
        const correo = correoContactoPublico(ficha);
        return correo ? { tipo: 'correo' as const, valor: correo } : null;
    }
    return ficha.datos.contacto ?? null;
});

const stockProducto = computed(() => textoStockProducto(producto.value, variantes.value, cargando.value));
const etiquetaModalidad = computed(() => {
    const valor = producto.value?.datos.modalidad;
    if (valor === 'regular') return 'Regular';
    if (valor === 'por_encargo' || valor === 'a_pedido') return 'Por encargo';
    return valor ? valor.charAt(0).toUpperCase() + valor.slice(1) : '';
});

// Ventana de contacto: solo muestra datos públicos de la tienda.
const tiendaContacto = ref<FichaComprador | null>(null);
const correoContacto = computed(() => correoContactoPublico(tiendaContacto.value));
const estadoCopia = ref<'' | 'ok' | 'error'>('');
let overflowContacto = '';
const tiposContacto: Record<string, string> = { correo: 'Correo', whatsapp: 'WhatsApp', web: 'Sitio web' };
const contactoContacto = computed(() => {
    const ficha = tiendaContacto.value;
    if (!ficha) return null;
    if (ficha.datos.canalesContacto) {
        const numero = numeroWhatsapp(ficha);
        if (numero) return { tipo: 'whatsapp' as const, valor: numero };
        const correo = correoContactoPublico(ficha);
        return correo ? { tipo: 'correo' as const, valor: correo } : null;
    }
    return ficha.datos.contacto ?? null;
});
const whatsappContacto = computed(() => {
    const numero = numeroWhatsapp(tiendaContacto.value);
    if (!numero) return null;
    const nombre = productoContacto.value?.datos.nombre;
    const variante = varianteProductoContacto.value;
    const mensaje = nombre
        ? `Hola, vi el producto ${nombre}${variante ? ` (${variante})` : ''} en Rincón Amancay y quisiera hacer una consulta.`
        : `Hola, vi la tienda ${tiendaContacto.value?.datos.nombre ?? ''} en Rincón Amancay y quisiera hacer una consulta.`;
    return enlaceWhatsapp(numero, mensaje);
});
const ubicacionContacto = computed(() => ubicacionTienda(tiendaContacto.value?.datos));

function inicializarContacto(ficha: FichaComprador, productoFicha: FichaComprador | null, varianteFicha: FichaComprador | null, mostrarFormulario: boolean, formularioDirecto: boolean) {
    if (!tieneContactoPublico.value && ficha.id === tienda.value?.id) return;
    tiendaContacto.value = ficha;
    productoContacto.value = productoFicha;
    varianteContacto.value = varianteFicha;
    formularioContactoAbierto.value = mostrarFormulario;
    formularioContactoDirecto.value = formularioDirecto;
    nombreConsulta.value = '';
    correoConsulta.value = '';
    mensajeConsulta.value = '';
    enviandoConsulta.value = false;
    consultaEnviada.value = false;
    errorConsulta.value = '';
    estadoCopia.value = '';
    consultaPendiente = null;
    if (import.meta.client) {
        overflowContacto = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
    }
}

function abrirFormularioContacto() {
    if (!formularioPublico.value || !tienda.value) return;
    inicializarContacto(tienda.value, null, null, true, true);
}

function mostrarFormularioContacto() {
    if (!formularioPublico.value) return;
    formularioContactoAbierto.value = true;
    errorConsulta.value = '';
}

function volverCanalesContacto() {
    if (formularioContactoDirecto.value) {
        cerrarContacto();
        return;
    }
    formularioContactoAbierto.value = false;
    errorConsulta.value = '';
    consultaEnviada.value = false;
}

function abrirContacto(ficha: FichaComprador | null, productoFicha: FichaComprador | null = null) {
    if (!ficha) return;
    inicializarContacto(ficha, productoFicha, productoFicha ? variante.value ?? null : null, false, false);
}

function cerrarContacto() {
    tiendaContacto.value = null;
    productoContacto.value = null;
    varianteContacto.value = null;
    formularioContactoAbierto.value = false;
    formularioContactoDirecto.value = false;
    consultaEnviada.value = false;
    errorConsulta.value = '';
    if (import.meta.client) document.body.style.overflow = overflowContacto;
}

async function copiarContacto() {
    const valor = contactoContacto.value?.valor || valorContactoVisible(tiendaContacto.value);
    if (!valor) return;
    try {
        await navigator.clipboard.writeText(valor);
        estadoCopia.value = 'ok';
    } catch {
        estadoCopia.value = 'error';
    }
}

function crearOperacionConsulta(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    if (typeof crypto === 'undefined' || typeof crypto.getRandomValues !== 'function') throw new Error('Este navegador no puede proteger el reintento del formulario.');
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

async function enviarConsulta() {
    const ficha = tiendaContacto.value;
    if (!ficha || !formularioPublico.value || enviandoConsulta.value || consultaEnviada.value) return;
    if (!urlConsultasPublicas.value) {
        errorConsulta.value = 'El envío del formulario no está configurado en este entorno. Inténtalo más tarde.';
        return;
    }
    const nombre = nombreConsulta.value.trim();
    const correo = correoConsulta.value.trim();
    const mensaje = mensajeConsulta.value.trim();
    if (nombre.length < 2 || nombre.length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo) || correo.length > 254 || mensaje.length < 5 || mensaje.length > 2000) {
        errorConsulta.value = 'Revisa tu nombre, correo y mensaje antes de enviar.';
        return;
    }
    const productoNombre = productoContacto.value
        ? `${productoContacto.value.datos.nombre}${varianteProductoContacto.value ? ` · ${varianteProductoContacto.value}` : ''}`.slice(0, 160)
        : undefined;
    const solicitud = {
        tiendaId: ficha.id,
        nombre,
        correo,
        mensaje,
        ...(productoNombre ? { producto: productoNombre } : {}),
    };
    const firma = JSON.stringify(solicitud);
    if (!consultaPendiente || consultaPendiente.firma !== firma) {
        try {
            consultaPendiente = { firma, operacionId: crearOperacionConsulta() };
        } catch (fallo) {
            errorConsulta.value = fallo instanceof Error ? fallo.message : 'No se pudo preparar el envío.';
            return;
        }
    }
    enviandoConsulta.value = true;
    errorConsulta.value = '';
    try {
        const respuesta = await fetch(urlConsultasPublicas.value, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'omit',
            body: JSON.stringify({ ...solicitud, operacionId: consultaPendiente.operacionId }),
        });
        const resultado = await respuesta.json().catch(() => null) as { datos?: { recibida?: boolean }; error?: { mensaje?: string } } | null;
        if (!respuesta.ok) throw new Error(resultado?.error?.mensaje || 'No se pudo enviar tu consulta. Inténtalo nuevamente.');
        if (resultado?.datos?.recibida !== true) throw new Error('No se confirmó la recepción de tu consulta. Puedes reintentar el envío.');
        consultaEnviada.value = true;
        consultaPendiente = null;
    } catch (fallo) {
        errorConsulta.value = fallo instanceof Error ? fallo.message : 'No se pudo enviar tu consulta. Inténtalo nuevamente.';
    } finally {
        enviandoConsulta.value = false;
    }
}

function nombreVariante(v: FichaComprador) {
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

function alTeclearEscape(evento: KeyboardEvent) {
    if (evento.key !== 'Escape') return;
    if (tiendaContacto.value) cerrarContacto();
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
        categoriaTipoProducto.value = Object.fromEntries((catalogos.categorias ?? []).map(e => [e.id, e.tipoProductoId ?? '']));
    } catch {
        sectores.value = {};
        tiposEmprendimiento.value = {};
        categorias.value = {};
        categoriaTipoProducto.value = {};
    }
}

async function cargarDestacados(tiendas: FichaComprador[], revisionDestacados: number) {
    const candidatas = tiendas.slice(0, 5);
    if (!candidatas.length) {
        if (revisionDestacados === revision) {
            destacados.value = [];
            fotosCarrusel.value = {};
        }
        return;
    }
    type ResultadoCarrusel = { tienda: FichaComprador; producto: FichaComprador | null; fotos: string[] };
    const resultados: (ResultadoCarrusel | null)[] = await Promise.all(candidatas.map(async (t) => {
        try {
            const r = await $catalogoPublico.listarProductos(t.id, null);
            const primero = r.elementos[0];
            const fotos = r.elementos
                .map((producto) => producto.imagenes?.[0])
                .filter((foto): foto is string => typeof foto === 'string')
                .slice(0, 8);
            return { tienda: t, producto: primero ? fichaProducto(primero) : null, fotos };
        } catch {
            return null;
        }
    }));
    if (revisionDestacados !== revision || tienda.value) return;
    const cargados = resultados.filter((resultado): resultado is ResultadoCarrusel => resultado !== null);
    destacados.value = cargados
        .flatMap(({ tienda, producto }) => producto ? [{ tienda, producto }] : [])
        .slice(0, 4);
    fotosCarrusel.value = Object.fromEntries(cargados.map(({ tienda, fotos }) => [tienda.id, fotos]));
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
        if (!tienda.value && destino === 1) {
            tiendasCarrusel.value = filas.value.slice(0, 5);
            indiceCarrusel.value = 0;
            void cargarDestacados(filas.value, actual);
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

// Navegación entre tiendas y productos

// Carrusel: la diapositiva 0 es la portada y las siguientes, hasta 5 tiendas publicadas.
const tiendasCarrusel = ref<FichaComprador[]>([]);
const indiceCarrusel = ref(0);
const totalCarrusel = computed(() => tiendasCarrusel.value.length + 1);
const tiendaCarrusel = computed(() => tiendasCarrusel.value[indiceCarrusel.value - 1] ?? null);
const ubicacionCarrusel = computed(() => ubicacionTienda(tiendaCarrusel.value?.datos));

// Fotos de productos por tienda para la cinta animada de fondo.
const fotosCarrusel = ref<Record<string, string[]>>({});
const cintaCarrusel = computed(() => {
    const fotos = tiendaCarrusel.value ? fotosCarrusel.value[tiendaCarrusel.value.id] ?? [] : [];
    if (!fotos.length) return [];
    const base: string[] = [];
    while (base.length < 8) base.push(...fotos);
    return [...base, ...base];
});

function moverCarrusel(paso: number) {
    indiceCarrusel.value = (indiceCarrusel.value + paso + totalCarrusel.value) % totalCarrusel.value;
}

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

watch(() => tienda.value?.id, (tiendaId, _, alLimpiar) => {
    if (!import.meta.client || !tiendaId) return;
    alLimpiar($catalogoPublico.observarTienda(tiendaId, actualizada => {
        if (tienda.value?.id !== tiendaId) return;
        const ficha = fichaTienda(actualizada);
        tienda.value = ficha;
        if (tiendaContacto.value?.id === tiendaId) tiendaContacto.value = ficha;
    }));
});
watch(() => JSON.stringify(tienda.value
    ? [tienda.value.id, filas.value.map((ficha) => ficha.id)]
    : destacados.value.map(({ tienda: fichaTienda, producto: fichaProducto }) => [fichaTienda.id, fichaProducto.id])), () => {
    detenerObservadoresProductos.forEach((detener) => detener());
    detenerObservadoresProductos = [];
    if (!import.meta.client) return;
    const observados = tienda.value
        ? filas.value.map((ficha) => ({ tiendaId: tienda.value!.id, productoId: ficha.id }))
        : destacados.value.map(({ tienda: fichaTienda, producto: fichaProducto }) => ({ tiendaId: fichaTienda.id, productoId: fichaProducto.id }));
    for (const { tiendaId, productoId } of observados) {
        detenerObservadoresProductos.push($catalogoPublico.observarProducto(tiendaId, productoId, (actualizado) => {
            if (!actualizado) return;
            const ficha = fichaProducto(actualizado);
            if (tienda.value?.id === tiendaId) {
                filas.value = filas.value.map((fila) => fila.id === productoId ? ficha : fila);
                if (producto.value?.id === productoId) producto.value = ficha;
                if (tiendaContacto.value?.id === tiendaId && productoContacto.value?.id === productoId) productoContacto.value = ficha;
            }
            destacados.value = destacados.value.map((destacado) => destacado.tienda.id === tiendaId && destacado.producto.id === productoId
                ? { ...destacado, producto: ficha }
                : destacado);
        }));
    }
}, { immediate: true });

watch([busqueda, categoriaSeleccionada, pagina], () => {
    if (!import.meta.client || pausaSincronizacionRuta > 0) return;
    clearTimeout(temporizadorRuta);
    temporizadorRuta = setTimeout(() => { void guardarVistaEnUrl('replace'); }, 250);
});

function urlPublicaTienda(productoId?: string) {
    if (!tienda.value) return '';
    const url = new URL('/comprador', window.location.origin);
    url.searchParams.set('tienda', tienda.value.id);
    if (productoId) url.searchParams.set('producto', productoId);
    return url.toString();
}

async function compartirTienda() {
    if (!tienda.value) return;
    const nombre = tienda.value.datos.nombre;
    const resultado = await compartirEnlacePublico({
        titulo: nombre,
        texto: `${nombre} en Rincón Amancay`,
        url: urlPublicaTienda(),
    });
    if (resultado === 'copiado') mostrarAviso('Enlace copiado');
    else if (resultado === 'compartido') mostrarAviso('Enlace compartido');
    else if (resultado === 'fallo') mostrarAviso('No se pudo compartir el enlace.');
}

async function compartirProducto() {
    if (!tienda.value || !producto.value) return;
    const nombre = producto.value.datos.nombre;
    const resultado = await compartirEnlacePublico({
        titulo: nombre,
        texto: `${nombre} en ${tienda.value.datos.nombre} · Rincón Amancay`,
        url: urlPublicaTienda(producto.value.id),
    });
    if (resultado === 'copiado') mostrarAviso('Enlace copiado');
    else if (resultado === 'compartido') mostrarAviso('Enlace compartido');
    else if (resultado === 'fallo') mostrarAviso('No se pudo compartir el enlace.');
}

// Ciclo de vida
onMounted(() => {
    void cargarCatalogos();
    window.addEventListener('keydown', alTeclearEscape);
});
onNuxtReady(() => {
    // En Hosting, Nuxt restaura la consulta de la URL tras hidratar la página estática.
    rutaInicializada = true;
    void restaurarVistaDesdeUrl();
});
onBeforeUnmount(() => {
    revision++;
    detenerObservadoresProductos.forEach((detener) => detener());
    detenerObservadoresProductos = [];
    clearTimeout(avisoTimer);
    clearTimeout(temporizadorRuta);
    window.removeEventListener('keydown', alTeclearEscape);
    if (tiendaContacto.value && import.meta.client) document.body.style.overflow = overflowContacto;
});
</script>

<template>
  <div class="comprador" :class="[tienda ? 'modo-tienda' : 'modo-explorar', producto ? 'modo-detalle' : '']">
    <!-- Explorar: barra lateral -->
    <CompradorLateral v-if="!tienda" activo="explorar" />
    <Teleport to="body">
      <div class="botanica-pie" :class="{ 'botanica-pie-detalle': !!producto, 'botanica-pie-tienda': !!tienda && !producto }" aria-hidden="true">
        <svg class="botanica-olas" viewBox="0 0 1600 250" preserveAspectRatio="none">
          <path fill="#f9ded0" d="M0 106C190 48 354 62 529 112s304 87 489 35 386-116 582-61v164H0z" />
          <path fill="#f6c4b3" fill-opacity=".84" d="M0 150C187 94 365 118 542 163s309 57 479 7 390-103 579-46v126H0z" />
          <path fill="#f2ad9e" fill-opacity=".64" d="M0 192C196 146 353 154 531 194s319 37 486-2 389-75 583-27v85H0z" />
          <image :href="'/amancay-botanica-inferior.png'" x="1090" y="60" width="460" height="211" preserveAspectRatio="none" />
          <image v-if="producto" :href="'/amancay-botanica-inferior.png'" x="50" y="180" width="300" height="70" preserveAspectRatio="none" transform="translate(400 0) scale(-1 1)" />
          <image v-else-if="tienda" :href="'/amancay-botanica-inferior.png'" x="25" y="130" width="360" height="120" preserveAspectRatio="none" transform="translate(410 0) scale(-1 1)" />
        </svg>
      </div>
      <div v-if="producto" class="detalle-ondas-superiores" aria-hidden="true">
        <svg viewBox="0 0 1600 300" preserveAspectRatio="none">
          <path fill="#f9ded0" fill-opacity=".58" d="M0 95C190 148 335 36 548 68s321 132 535 84 344-112 517-57v92c-183-36-331 78-523 81s-310-92-500-87S205 252 0 190z" />
          <path fill="#f4c0b0" fill-opacity=".28" d="M0 145c212 41 327-54 529-34s323 96 517 68 364-93 554-48v89c-171-25-336 51-531 59s-355-60-540-52S171 277 0 236z" />
        </svg>
      </div>
    </Teleport>

    <!-- Barra superior: tienda y producto -->
    <header v-if="tienda" class="barra-superior">
      <button class="marca marca-boton" type="button" @click="navegar(null)">
        <img class="marca-icono" src="/logo-amancay.png" alt="" />
        <span class="marca-texto">
          <small>Rincón</small>
          <strong>Amancay</strong>
        </span>
      </button>
      <nav class="barra-nav" aria-label="Principal">
        <button type="button" @click="navegar(null)">Explorar</button>
        <NuxtLink class="barra-enlace" to="/comprador/tiendas">Tiendas</NuxtLink>
        <NuxtLink class="barra-enlace" to="/comprador/productos">Productos</NuxtLink>
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
        <button class="ayuda-cabecera" type="button" data-guia="comprador-ayuda" aria-label="Ayuda" title="Ayuda" @click="abrirAyudaComprador">?</button>
        <AccesoComprador />
      </div>
    </header>

    <main class="contenido">
      <!-- Vista explorar -->
      <template v-if="!tienda">
        <div class="escena-explorar">
        <header class="encabezado-fila" data-guia="comprador-inicio">
          <div class="encabezado-titulo">
            <h1>Descubre productos hechos cerca</h1>
            <p class="sub">Conoce a nuestras emprendedoras y encuentra algo especial.</p>
          </div>
          <div class="encabezado-cuenta">
            <AccesoComprador />
          </div>
        </header>

        <div class="carrusel" :class="{ claro: tiendaCarrusel }" aria-roledescription="carrusel" aria-label="Destacados">
          <Transition name="carrusel" mode="out-in">
            <section v-if="!tiendaCarrusel" key="banner" class="hero" aria-roledescription="diapositiva" :aria-label="`1 de ${totalCarrusel}`">
              <img class="hero-flor" src="/amancay-flor-principal-v3.png" alt="" aria-hidden="true" />
              <img class="hero-tallo" src="../../assets/amancay-tallo-extension.svg" alt="" aria-hidden="true" />
              <div class="hero-texto">
                <p class="sobretitulo">EMPRENDIMIENTOS LOCALES</p>
                <h2>Hecho cerca, elegido con cariño</h2>
                <p>Productos con historia, manos locales y un impacto real.</p>
                <NuxtLink class="boton boton-mostaza" to="/comprador/productos">Explorar productos →</NuxtLink>
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
            <button
              v-else
              :key="tiendaCarrusel.id"
              class="hero hero-tienda"
              type="button"
              aria-roledescription="diapositiva"
              :aria-label="`${indiceCarrusel + 1} de ${totalCarrusel}: abrir la tienda ${tiendaCarrusel.datos.nombre}`"
              @click="navegar(tiendaCarrusel)"
            >
              <span v-if="cintaCarrusel.length" class="carrusel-productos" aria-hidden="true">
                <span class="carrusel-cinta">
                  <span v-for="(foto, i) in cintaCarrusel" :key="i" class="carrusel-foto">
                    <ImagenPublica :ruta="foto" alt="" ajuste="cover" :zoom="false" />
                  </span>
                </span>
              </span>
              <span class="hero-texto">
                <span class="sobretitulo">EMPRENDIMIENTOS DESTACADOS</span>
                <span v-if="ubicacionCarrusel" class="carrusel-sector">
                  <span aria-hidden="true">📍</span>
                  {{ ubicacionCarrusel }}
                </span>
                <span class="carrusel-nombre">{{ tiendaCarrusel.datos.nombre }}</span>
              </span>
              <span class="hero-arte carrusel-logo">
                <ImagenPublica v-if="tiendaCarrusel.datos.logo" :ruta="tiendaCarrusel.datos.logo" :alt="tiendaCarrusel.datos.nombre" ajuste="contain" :zoom="false" />
                <span v-else class="logo-inicial hero" aria-hidden="true">{{ letraInicial(tiendaCarrusel.datos.nombre) }}</span>
              </span>
            </button>
          </Transition>
          <template v-if="totalCarrusel > 1">
            <button class="carrusel-flecha" type="button" aria-label="Diapositiva anterior" @click="moverCarrusel(-1)">←</button>
            <button class="carrusel-flecha siguiente" type="button" aria-label="Diapositiva siguiente" @click="moverCarrusel(1)">→</button>
            <div class="carrusel-puntos">
              <button
                v-for="i in totalCarrusel"
                :key="i"
                type="button"
                :class="{ activo: i - 1 === indiceCarrusel }"
                :aria-label="i === 1 ? 'Ver portada' : `Ver ${tiendasCarrusel[i - 2]?.datos.nombre}`"
                :aria-current="i - 1 === indiceCarrusel ? 'true' : undefined"
                @click="indiceCarrusel = i - 1"
              ></button>
            </div>
          </template>
        </div>

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

        <div v-if="filtroVisible && Object.keys(categoriasFiltro).length" class="filtros" role="group" aria-label="Filtrar por categoría">
          <span class="filtros-etiqueta">Filtrar por categoría:</span>
          <button
            class="chip-filtro"
            :class="{ on: !categoriaSeleccionada }"
            type="button"
            @click="categoriaSeleccionada = ''"
          >Todos</button>
          <button
            v-for="(nombre, id) in categoriasFiltro"
            :key="id"
            class="chip-filtro"
            :class="{ on: categoriaSeleccionada === id }"
            type="button"
            @click="categoriaSeleccionada = categoriaSeleccionada === id ? '' : id"
          >{{ nombre }}</button>
        </div>

        <AvisosFerias />

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
            <NuxtLink class="enlace" to="/comprador/productos">Ver todos →</NuxtLink>
          </div>
          <div class="rejilla-productos">
            <button
              v-for="d in destacadosVisibles"
              :key="`${d.tienda.id}-${d.producto.id}`"
              class="tarjeta-producto"
              type="button"
              @click="abrirDestacado(d)"
            >
              <span class="foto">
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
              <b class="precio">{{ d.tienda.datos.mostrarPrecios ? precio(d.producto.datos.precioBase) : 'Consultar precio' }}</b>
            </button>
          </div>
        </section>

        <section id="catalogo" :aria-busy="cargando">
          <div class="fila-titulo">
            <h2 class="titulo-bloque">Nuestras tiendas</h2>
            <NuxtLink class="enlace" to="/comprador/tiendas">Ver todas →</NuxtLink>
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
        </div>
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
          <div class="tienda-hero-halo" aria-hidden="true"></div>
          <img class="tienda-flor-fondo" src="/amancay-flor-principal-v3.png" alt="" aria-hidden="true" />
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
                <span v-if="etiquetaUbicacion" class="chip">
                  <span aria-hidden="true">📍</span>
                  {{ etiquetaUbicacion }}
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
                <a v-if="whatsappPublico" class="boton boton-primario" :href="whatsappPublico" target="_blank" rel="noopener noreferrer">
                    Contactar por WhatsApp
                </a>
                <button v-if="formularioPublico" class="boton boton-secundario" type="button" @click="abrirFormularioContacto">
                  Enviar mensaje
                </button>
                <button class="boton boton-secundario" type="button" @click="compartirTienda">Compartir tienda</button>
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
                {{ nombreVariante(v) }} · {{ textoStock(v.datos.stock, producto.datos.manejaStock) }}
                </option>
              </select>
              <section v-if="variantes.length" class="ficha-stock-variantes" aria-label="Stock por variante">
                <h3>Stock por variante</h3>
                <ul>
                  <li v-for="v in variantes" :key="v.id">
                    <span>{{ nombreVariante(v) }}</span>
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
              <button class="boton boton-secundario" type="button" @click="compartirProducto">Compartir producto</button>
              <button v-if="tieneContactoPublico" class="boton boton-mostaza" type="button" @click="abrirContacto(tienda, producto)">
                Contactar emprendedora
              </button>
              <p v-else class="ficha-nota">Esta tienda aún no habilita un canal de contacto.</p>
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
            <div v-if="filtroVisible && Object.keys(categoriasFiltro).length" class="filtros" role="group" aria-label="Filtrar por categoría">
              <span class="filtros-etiqueta">Filtrar por categoría:</span>
              <button
                class="chip-filtro"
                :class="{ on: !categoriaSeleccionada }"
                type="button"
                @click="categoriaSeleccionada = ''"
              >Todos</button>
              <button
                v-for="(nombre, id) in categoriasFiltro"
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
                <span class="foto">
                  <ImagenPublica :ruta="f.datos.imagenes?.[0]" :alt="f.datos.nombre" ajuste="contain" :zoom="false" />
                </span>
                <h3>{{ f.datos.nombre }}</h3>
                <span class="tarjeta-meta">
                  <b class="precio">
                    {{ tienda.datos.mostrarPrecios === true
                      ? (f.datos.tieneVariantes ? 'Ver variantes y precios' : precio(f.datos.precioBase))
                      : 'Consultar precio' }}
                  </b>
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
            <div class="sobre-seguir">
              <BotonSeguir tipo="tienda" :entidad-id="tienda.id" :nombre="tienda.datos.nombre" />
            </div>
            <AvisosFerias />
            <div v-if="etiquetaTipo" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">🏪</span>
              <div>
                <b>Tipo de emprendimiento</b>
                <p>{{ etiquetaTipo }}</p>
              </div>
            </div>
            <div v-if="etiquetaUbicacion && !tienda.datos.puntoRetiro" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">📍</span>
              <div>
                <b>Ubicación</b>
                <p>{{ etiquetaUbicacion }}</p>
              </div>
            </div>
            <div v-if="tienda.datos.puntoRetiro" class="dato-tienda dato-punto">
              <span class="dato-icono" aria-hidden="true">📌</span>
              <div>
                <b>Punto de venta o retiro</b>
                <p v-if="tienda.datos.puntoRetiro.descripcion">{{ tienda.datos.puntoRetiro.descripcion }}</p>
              </div>
              <div class="visor-punto">
                <MapaPuntoRetiro
                  :latitud="tienda.datos.puntoRetiro.latitud"
                  :longitud="tienda.datos.puntoRetiro.longitud"
                  :nombre-tienda="tienda.datos.nombre"
                />
                <a
                  class="enlace-punto"
                  :href="enlaceComoLlegarPunto"
                  target="_blank"
                  rel="noopener noreferrer"
                >Cómo llegar al punto →</a>
              </div>
            </div>
            <div v-if="contactoTiendaVisible" class="dato-tienda">
              <span class="dato-icono" aria-hidden="true">☎</span>
              <div>
                <b>{{ tiposContacto[contactoTiendaVisible.tipo] ?? 'Contacto' }}</b>
                <a
                  v-if="enlaceContacto"
                  :href="enlaceContacto.href"
                  target="_blank"
                  rel="noopener noreferrer"
                >{{ contactoTiendaVisible.valor }}</a>
                <p v-else>{{ contactoTiendaVisible.valor }}</p>
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

    <!-- Ventana de contacto con la emprendedora -->
    <Teleport to="body">
      <div
        v-if="tiendaContacto"
        class="modal-fondo contacto-fondo"
        role="dialog"
        aria-modal="true"
        aria-labelledby="contacto-titulo"
        @click.self="cerrarContacto"
      >
        <div class="modal contacto">
          <button class="modal-cerrar" type="button" aria-label="Cerrar" @click="cerrarContacto">✕</button>
          <div class="modal-scroll">
            <h2 id="contacto-titulo" class="modal-titulo">
              {{ formularioContactoAbierto ? `Contactar a ${tiendaContacto.datos.nombre}` : 'Contactar emprendedora' }}
            </h2>
            <p v-if="formularioContactoAbierto" class="contacto-subtitulo">Haz tu consulta directamente a la emprendedora.</p>
            <div v-if="productoContacto" class="contacto-producto">
              <div class="contacto-producto-imagen">
                <ImagenPublica v-if="imagenProductoContacto" :ruta="imagenProductoContacto" :alt="productoContacto.datos.nombre" ajuste="contain" :zoom="false" />
                <span v-else aria-hidden="true">✿</span>
              </div>
              <div class="contacto-producto-texto">
                <b>{{ productoContacto.datos.nombre }}</b>
                <span>{{ varianteProductoContacto || tiendaContacto.datos.nombre }}</span>
                <strong>{{ precioProductoContacto }}</strong>
              </div>
            </div>
            <template v-if="!formularioContactoAbierto">
              <div class="dato-tienda">
                <span class="dato-icono" aria-hidden="true">🏪</span>
                <div>
                  <b>Tienda</b>
                  <p>{{ tiendaContacto.datos.nombre }}</p>
                </div>
              </div>
              <div v-if="ubicacionContacto" class="dato-tienda">
                <span class="dato-icono" aria-hidden="true">📍</span>
                <div>
                  <b>Ubicación</b>
                  <p>{{ ubicacionContacto }}</p>
                </div>
              </div>
              <div v-if="contactoContacto" class="dato-tienda">
                <span class="dato-icono" aria-hidden="true">☎</span>
                <div>
                  <b>{{ tiposContacto[contactoContacto.tipo] ?? 'Contacto' }}</b>
                  <p class="contacto-valor">{{ contactoContacto.valor }}</p>
                </div>
              </div>
              <p v-if="!whatsappContacto && !formularioPublico && !correoContacto" class="contacto-aviso" role="status">
                Esta emprendedora aún no habilitó un medio de contacto.
              </p>
              <p v-if="estadoCopia" class="contacto-copia" role="status">
                {{ estadoCopia === 'ok' ? 'Contacto copiado.' : 'No se pudo copiar. Selecciona el texto y cópialo manualmente.' }}
              </p>
              <section v-if="whatsappContacto || formularioPublico || correoContacto" class="canales-contacto" aria-labelledby="canales-titulo">
                <h3 id="canales-titulo">¿Cómo quieres contactar?</h3>
                <a v-if="whatsappContacto" class="canal-contacto canal-whatsapp" :href="whatsappContacto" target="_blank" rel="noopener noreferrer">
                  <span class="canal-icono" aria-hidden="true">◉</span>
                  <span><b>Contactar por WhatsApp</b><small>Conversación directa</small></span>
                  <span aria-hidden="true">↗</span>
                </a>
                <button v-if="formularioPublico" class="canal-contacto canal-formulario" type="button" @click="mostrarFormularioContacto">
                  <span class="canal-icono" aria-hidden="true">▤</span>
                  <span><b>Contactar con formulario</b><small>Deja tu consulta</small></span>
                  <span aria-hidden="true">→</span>
                </button>
                <a v-if="correoContacto" class="canal-contacto canal-correo" :href="`mailto:${correoContacto}`">
                  <span class="canal-icono" aria-hidden="true">✉</span>
                  <span><b>Contactar por correo</b><small>{{ correoContacto }}</small></span>
                  <span aria-hidden="true">↗</span>
                </a>
              </section>
              <div class="modal-acciones">
                <button
                  v-if="contactoContacto && !tiendaContacto.datos.canalesContacto"
                  class="boton boton-mostaza"
                  type="button"
                  @click="copiarContacto"
                >Copiar contacto</button>
                <button class="boton boton-secundario" type="button" @click="cerrarContacto">Cerrar</button>
              </div>
            </template>
            <template v-else>
              <div v-if="consultaEnviada" class="consulta-estado consulta-exito" role="status">
                <b>Consulta enviada</b>
                <span>La emprendedora recibió tu mensaje y podrá responderte al correo que indicaste.</span>
              </div>
              <form v-else class="formulario-consulta" @submit.prevent="enviarConsulta">
                <label for="consulta-mensaje">Tu mensaje</label>
                <textarea id="consulta-mensaje" v-model="mensajeConsulta" rows="4" maxlength="2000" required :placeholder="productoContacto ? 'Hola, me interesa este producto…' : 'Hola, quiero hacer una consulta…'" />
                <small class="consulta-contador">{{ mensajeConsulta.length }}/2000</small>
                <label for="consulta-nombre">Tu nombre</label>
                <input id="consulta-nombre" v-model="nombreConsulta" type="text" maxlength="100" autocomplete="name" required placeholder="Tu nombre" />
                <label for="consulta-correo">Tu correo</label>
                <input id="consulta-correo" v-model="correoConsulta" type="email" maxlength="254" autocomplete="email" required placeholder="Tu correo" />
                <p v-if="errorConsulta" class="consulta-estado consulta-error" role="alert">{{ errorConsulta }}</p>
                <button class="boton boton-primario consulta-enviar" type="submit" :disabled="enviandoConsulta || nombreConsulta.trim().length < 2 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correoConsulta.trim()) || mensajeConsulta.trim().length < 5">
                  {{ enviandoConsulta ? 'Enviando consulta…' : 'Enviar consulta →' }}
                </button>
              </form>
              <div class="modal-acciones">
                <button v-if="!consultaEnviada" class="boton boton-secundario" type="button" :disabled="enviandoConsulta" @click="volverCanalesContacto">
                  {{ formularioContactoDirecto ? 'Cerrar' : 'Volver a los canales' }}
                </button>
                <button v-if="consultaEnviada" class="boton boton-mostaza" type="button" @click="cerrarContacto">Listo</button>
              </div>
            </template>
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
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
    min-height: 100vh;
    min-height: 100dvh;
    background: var(--amancay-fondo);
    color: var(--amancay-tinta);
    font-family: var(--amancay-fuente-cuerpo);
}

.comprador.modo-explorar {
    display: grid;
    grid-template-columns: 280px minmax(0, 1fr);
    min-width: 0;
}

@media (max-width: 900px) {
    .comprador.modo-explorar {
        grid-template-columns: 1fr;
    }
}

.comprador.modo-tienda {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto 1fr;
    min-width: 0;
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
    min-width: 0;
    box-sizing: border-box;
    background: rgba(255, 251, 242, 0.94);
    backdrop-filter: blur(6px);
    border-bottom: 1.5px solid var(--amancay-borde);
}

.comprador.modo-detalle .barra-superior {
    background: #fffbf2;
    backdrop-filter: none;
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
    display: flex;
    align-items: center;
    gap: .5rem;
    margin-left: auto;
}

.ayuda-cabecera{flex:0 0 44px;width:44px;height:44px;border:1px solid var(--amancay-borde);border-radius:50%;background:#f8e9ea;color:var(--amancay-bordo);font:700 1.3rem var(--amancay-fuente-cuerpo);cursor:pointer}
.ayuda-cabecera:hover,.ayuda-cabecera:focus-visible{outline:3px solid rgba(107,30,61,.24);outline-offset:2px}

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
    position: relative;
    isolation: isolate;
    z-index: 1;
    width: 100%;
    min-width: 0;
    box-sizing: border-box;
    padding: 1.8rem clamp(1rem, 3vw, 2.5rem) 3.5rem;
}

:global(.botanica-pie) {
    position: fixed;
    z-index: 0;
    left: 280px;
    right: 0;
    bottom: 0;
    height: 300px;
    pointer-events: none;
}

:global(.botanica-pie-detalle) {
    left: 0;
    height: 270px;
}

:global(.botanica-pie-tienda) {
    left: 0;
    height: 300px;
}

:global(.botanica-olas) {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
}

:global(.detalle-ondas-superiores) {
    position: fixed;
    z-index: 0;
    top: 0;
    left: 0;
    right: 0;
    height: 270px;
    pointer-events: none;
}

:global(.detalle-ondas-superiores svg) {
    display: block;
    width: 100%;
    height: 100%;
}

.escena-explorar {
    position: relative;
    width: 100%;
    min-width: 0;
}

.encabezado-fila {
    position: relative;
    z-index: 2;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: flex-start;
    column-gap: 1rem;
    margin-bottom: 1.4rem;
}

.encabezado-titulo {
    grid-column: 1;
    grid-row: 1;
    min-width: 0;
}

.encabezado-cuenta {
    grid-column: 2;
    grid-row: 1;
    justify-self: end;
    min-width: 0;
}

.encabezado-cuenta :deep(.acceso) {
    margin-top: 0;
}

.encabezado-cuenta :deep(.acceso-abrir) {
    width: auto;
    white-space: nowrap;
    padding: 0.7rem 1.5rem;
}

.encabezado-cuenta :deep(.acceso-sesion) {
    width: min(330px, 100%);
    min-width: 0;
    max-width: 330px;
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
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0.45rem 0.85rem;
    border: 1.5px solid var(--amancay-bordo);
    border-radius: 999px;
    background: rgba(255, 251, 242, 0.82);
    font-size: 0.9rem;
    font-weight: 700;
    color: var(--amancay-bordo);
    text-decoration: none;
    transition: background-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.enlace:hover {
    background: var(--amancay-bordo);
    color: #fff;
    box-shadow: 0 4px 12px rgba(107, 30, 61, 0.18);
}

.enlace:focus-visible {
    outline: 3px solid var(--amancay-mostaza);
    outline-offset: 3px;
}

/* Hero de explorar */
.hero {
    position: relative;
    z-index: 1;
    isolation: isolate;
    overflow: visible;
    box-sizing: border-box;
    clip-path: inset(-190px 0 0 round 26px);
    display: flex;
    align-items: center;
    gap: 1.5rem;
    min-height: 224px;
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
    z-index: 0;
    width: 470px;
    height: 370px;
    right: 20px;
    top: -175px;
    border-radius: 50%;
    background: radial-gradient(ellipse, rgba(248, 227, 218, 0.46), rgba(246, 193, 119, 0.12) 58%, transparent 76%);
    pointer-events: none;
}

.hero-flor {
    position: absolute;
    z-index: 0;
    top: clamp(-140px, -5vw, -70px);
    right: clamp(150px, 13vw, 260px);
    width: clamp(360px, 26vw, 500px);
    height: auto;
    object-fit: contain;
    filter: drop-shadow(0 12px 10px rgba(81, 17, 47, 0.14));
    pointer-events: none;
}

.hero-tallo {
    position: absolute;
    z-index: 0;
    top: clamp(-140px, -5vw, -70px);
    right: clamp(150px, 13vw, 260px);
    width: clamp(360px, 26vw, 500px);
    height: auto;
    pointer-events: none;
}

.hero-texto {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.45rem;
    flex: 1;
    min-width: 0;
    max-width: 56%;
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
    z-index: 2;
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-style: italic;
    font-size: 1.05rem;
    line-height: 1.35;
    color: var(--amancay-naranja-fondo);
    text-align: center;
    flex: none;
    margin-left: auto;
}

.hero-lista {
    position: relative;
    z-index: 2;
    margin: 0;
    padding: 0 0 0 0.4rem;
    list-style: none;
    font-size: 0.85rem;
    line-height: 1.9;
    color: rgba(255, 251, 242, 0.82);
    flex: none;
}

.carrusel {
    position: relative;
    width: 100%;
    min-width: 0;
    margin-bottom: 1.4rem;
}

.carrusel .hero {
    width: 100%;
    box-sizing: border-box;
    min-height: 250px;
    margin-bottom: 0;
    padding-inline: clamp(3.9rem, 6vw, 4.9rem);
    padding-bottom: 2.6rem;
}

.hero-tienda {
    --tam-nombre: clamp(2.2rem, 4.4vw, 3.4rem);
    justify-content: space-between;
    border: 0;
    background: #f9ded0;
    color: var(--amancay-tinta);
    box-shadow: 0 14px 30px rgba(107, 30, 61, 0.14);
    font: inherit;
    text-align: left;
    cursor: pointer;
}

.hero-tienda .hero-texto {
    max-width: none;
    gap: 0.35rem;
}

.hero-tienda::before {
    display: none;
}

.carrusel.claro .carrusel-puntos button::before {
    background: rgba(107, 30, 61, 0.3);
}

.carrusel.claro .carrusel-puntos button.activo::before {
    background: var(--amancay-bordo);
}

.carrusel-productos {
    position: absolute;
    inset: 0;
    z-index: 0;
    display: flex;
    align-items: center;
    overflow: hidden;
    border-radius: inherit;
    opacity: 0.22;
    pointer-events: none;
    -webkit-mask-image: linear-gradient(90deg, transparent, #000 18%, #000 82%, transparent);
    mask-image: linear-gradient(90deg, transparent, #000 18%, #000 82%, transparent);
}

.carrusel-cinta {
    display: flex;
    width: max-content;
    animation: carrusel-cinta 45s linear infinite;
}

.carrusel-foto {
    flex: none;
    width: 120px;
    height: 120px;
    margin-right: 1rem;
    overflow: hidden;
    border-radius: 18px;
}

.carrusel-foto :deep(.imagen-publica) {
    width: 100%;
    height: 100%;
    aspect-ratio: 1;
    border-radius: 0;
    background: transparent;
}

@keyframes carrusel-cinta {
    to {
        transform: translateX(-50%);
    }
}

@media (prefers-reduced-motion: reduce) {
    .carrusel-cinta {
        animation: none;
    }
}

.hero-tienda .sobretitulo {
    color: var(--amancay-bordo);
}

.hero-tienda:focus-visible {
    outline: 3px solid var(--amancay-mostaza);
    outline-offset: 4px;
}

.carrusel-sector {
    font-size: calc(var(--tam-nombre) * 0.5);
    line-height: 1.2;
    opacity: 0.9;
}

.carrusel-nombre {
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: var(--tam-nombre);
    line-height: 1.05;
    color: var(--amancay-bordo);
    overflow-wrap: anywhere;
}

.carrusel-flecha {
    position: absolute;
    z-index: 3;
    top: 50%;
    left: 0.85rem;
    width: 2.75rem;
    height: 2.75rem;
    border: 1.5px solid rgba(255, 251, 242, 0.5);
    border-radius: 50%;
    background: var(--amancay-superficie);
    color: var(--amancay-bordo);
    font-size: 1.15rem;
    cursor: pointer;
    box-shadow: 0 6px 14px rgba(46, 27, 32, 0.25);
    transform: translateY(-50%);
}

.carrusel-flecha.siguiente {
    left: auto;
    right: 0.85rem;
}

.carrusel-flecha:hover {
    background: var(--amancay-naranja-fondo);
}

.carrusel-puntos {
    position: absolute;
    z-index: 3;
    left: 50%;
    bottom: 0.9rem;
    display: flex;
    gap: 0.25rem;
    transform: translateX(-50%);
}

.carrusel-puntos button {
    width: 24px;
    height: 24px;
    padding: 0;
    border: 0;
    background: none;
    cursor: pointer;
}

.carrusel-puntos button::before {
    content: '';
    display: block;
    width: 9px;
    height: 9px;
    margin: auto;
    border-radius: 999px;
    background: rgba(255, 251, 242, 0.45);
    transition: width 0.2s ease, background 0.2s ease;
}

.carrusel-puntos button.activo::before {
    width: 22px;
    background: var(--amancay-mostaza);
}

.carrusel-enter-active,
.carrusel-leave-active {
    transition: opacity 0.2s ease, transform 0.2s ease;
}

.carrusel-enter-from {
    opacity: 0;
    transform: translateX(12px);
}

.carrusel-leave-to {
    opacity: 0;
    transform: translateX(-12px);
}

.carrusel-logo {
    overflow: hidden;
}

.carrusel-logo :deep(.imagen-publica) {
    width: 104px;
    height: 104px;
    aspect-ratio: 1;
    border-radius: 50%;
    background: transparent;
}

.carrusel-logo .logo-inicial.hero {
    width: 104px;
    height: 104px;
}

.hero-arte {
    position: relative;
    z-index: 2;
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
    position: relative;
    isolation: isolate;
    overflow: hidden;
    display: flex;
    gap: 1.5rem;
    padding: clamp(1.2rem, 2.5vw, 1.8rem);
    background: linear-gradient(105deg, rgba(255, 251, 242, 0.94), rgba(255, 246, 231, 0.82) 55%, rgba(255, 251, 242, 0.94));
    border: 1.5px solid #f4c6b4;
    border-radius: 26px;
    box-shadow: 0 8px 24px rgba(107, 30, 61, 0.07);
    margin-bottom: 1.6rem;
}

.tienda-hero-halo {
    position: absolute;
    z-index: 0;
    inset: 0;
    background: radial-gradient(ellipse 36% 112% at 64% 50%, rgba(246, 193, 119, 0.2) 0%, rgba(249, 222, 208, 0.46) 34%, rgba(249, 222, 208, 0.18) 58%, transparent 80%);
    pointer-events: none;
}

.tienda-flor-fondo {
    position: absolute;
    z-index: 1;
    top: 50%;
    right: 19%;
    height: min(440px, 135%);
    width: auto;
    transform: translateY(-50%);
    object-fit: contain;
    filter: drop-shadow(0 12px 20px rgba(107, 30, 61, 0.18)) drop-shadow(0 0 24px rgba(246, 193, 119, 0.22));
    pointer-events: none;
}

.tienda-hero-info {
    position: relative;
    z-index: 2;
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
    z-index: 2;
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
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 230px), 1fr));
    gap: 1.1rem;
    min-width: 0;
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
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr));
    gap: 1.1rem;
    min-width: 0;
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

.sobre-seguir {
    margin: 0 0 1rem;
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


.dato-punto {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    row-gap: 0.55rem;
}

.visor-punto {
    grid-column: 1 / -1;
}

.dato-tienda .enlace-punto {
    display: inline-block;
    margin-top: 0.45rem;
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
    position: relative;
    isolation: isolate;
    display: grid;
    grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
    gap: 1.75rem;
    align-items: start;
}

.galeria {
    position: relative;
    z-index: 1;
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
    aspect-ratio: auto;
    height: min(68vh, 760px);
    border-radius: 18px;
    background: linear-gradient(160deg, var(--amancay-superficie-variante), var(--amancay-naranja-fondo));
}

.galeria-marco :deep(.imagen-publica img) {
    width: auto;
    height: auto;
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    object-position: center;
}

.ficha {
    z-index: 1;
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

/* Ventana de contacto */
.modal-fondo {
    position: fixed;
    z-index: 10000;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 1.25rem;
    background: rgba(46, 27, 32, 0.55);
    backdrop-filter: blur(3px);
}

.modal {
    position: relative;
    width: min(440px, 100%);
    max-height: min(90vh, 820px);
    overflow: auto;
    background: var(--amancay-superficie);
    border-radius: 28px;
    border: 1.5px solid var(--amancay-borde);
    box-shadow: 0 24px 60px rgba(46, 27, 32, 0.35);
}

.modal-cerrar {
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

.modal-cerrar:hover {
    background: var(--amancay-bordo-oscuro);
}

.modal-scroll {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.45rem;
    padding: 1.5rem 1.4rem 1.4rem;
}

.modal-titulo {
    margin: 0 2rem 0.35rem 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.65rem;
    line-height: 1.15;
    color: var(--amancay-tinta);
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

.contacto-subtitulo {
    margin: -0.1rem 0 0.45rem;
    color: var(--amancay-tinta-suave);
    font-size: 0.91rem;
    line-height: 1.5;
}

.contacto-producto {
    display: flex;
    align-items: center;
    gap: 0.8rem;
    width: 100%;
    padding: 0.65rem;
    border: 1px solid var(--amancay-borde);
    border-radius: 16px;
    background: var(--amancay-superficie);
}

.contacto-producto-imagen {
    display: grid;
    flex: 0 0 76px;
    width: 76px;
    height: 76px;
    overflow: hidden;
    place-items: center;
    border-radius: 12px;
    background: var(--amancay-mostaza-suave);
    color: var(--amancay-bordo);
    font-size: 2rem;
}

.contacto-producto-imagen :deep(img) {
    width: 100%;
    height: 100%;
    object-fit: contain;
}

.contacto-producto-texto {
    display: grid;
    min-width: 0;
    gap: 0.2rem;
}

.contacto-producto-texto b,
.contacto-producto-texto span,
.contacto-producto-texto strong {
    overflow-wrap: anywhere;
}

.contacto-producto-texto b,
.contacto-producto-texto strong {
    color: var(--amancay-bordo);
}

.contacto-producto-texto span {
    color: var(--amancay-tinta-suave);
    font-size: 0.82rem;
}

.contacto-producto-texto strong {
    font-size: 0.91rem;
}

.canales-contacto {
    display: grid;
    gap: 0.55rem;
    width: 100%;
    margin-top: 0.35rem;
}

.canales-contacto h3 {
    margin: 0.25rem 0 0.1rem;
    color: var(--amancay-bordo);
    font-size: 1rem;
}

.canal-contacto {
    display: flex;
    align-items: center;
    gap: 0.7rem;
    width: 100%;
    min-height: 66px;
    padding: 0.7rem 0.8rem;
    border: 1px solid var(--amancay-borde);
    border-radius: 16px;
    color: var(--amancay-bordo);
    text-align: left;
    text-decoration: none;
    cursor: pointer;
}

.canal-contacto > span:nth-child(2) {
    display: grid;
    flex: 1;
    gap: 0.1rem;
}

.canal-contacto small {
    color: var(--amancay-tinta-suave);
    font-size: 0.76rem;
}

.canal-icono {
    display: grid;
    flex: 0 0 38px;
    width: 38px;
    height: 38px;
    place-items: center;
    border-radius: 50%;
    background: var(--amancay-bordo);
    color: white;
    font-size: 1.1rem;
}

.canal-whatsapp {
    background: #edf5e7;
    border-color: #cddfc3;
    color: #315e37;
}

.canal-whatsapp .canal-icono {
    background: #315e37;
}

.canal-formulario {
    background: #f8eaf0;
}

.canal-correo {
    background: #fff4dc;
    border-color: #efddb4;
}

.canal-contacto:hover {
    filter: brightness(0.98);
}

.formulario-consulta {
    display: grid;
    gap: 0.48rem;
    width: 100%;
    margin-top: 0.35rem;
}

.formulario-consulta label {
    margin-top: 0.25rem;
    color: var(--amancay-bordo);
    font-size: 0.9rem;
    font-weight: 700;
}

.formulario-consulta input,
.formulario-consulta textarea {
    width: 100%;
    min-width: 0;
    padding: 0.78rem 0.85rem;
    border: 1px solid #aa9da2;
    border-radius: 11px;
    outline: none;
    background: var(--amancay-superficie);
    color: var(--amancay-tinta);
    font: inherit;
    font-size: 0.9rem;
}

.formulario-consulta textarea {
    min-height: 112px;
    resize: vertical;
    line-height: 1.45;
}

.formulario-consulta input:focus,
.formulario-consulta textarea:focus {
    border-color: var(--amancay-bordo);
    box-shadow: 0 0 0 3px rgba(117, 38, 79, 0.12);
}

.consulta-contador {
    margin: -0.25rem 0 0;
    color: var(--amancay-tinta-suave);
    text-align: right;
    font-size: 0.75rem;
}

.consulta-enviar {
    width: 100%;
    margin-top: 0.45rem;
}

.consulta-estado {
    display: grid;
    gap: 0.3rem;
    width: 100%;
    margin: 0.3rem 0;
    padding: 0.8rem 0.9rem;
    border-radius: 13px;
    font-size: 0.85rem;
    line-height: 1.45;
}

.consulta-error {
    border: 1px solid #e8b9b4;
    background: #fff0ee;
    color: #922f2b;
}

.consulta-exito {
    border: 1px solid #cddfc3;
    background: #edf5e7;
    color: #315e37;
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

.modal-acciones {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
    width: 100%;
    margin-top: 0.85rem;
}

.modal-acciones .boton {
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

    .hero-flor,
    .hero-tallo {
        top: -90px;
        right: 70px;
        width: 360px;
    }
}

@media (max-width: 900px) {
    .comprador.modo-explorar {
        grid-template-columns: minmax(0, 1fr);
        width: 100vw;
        max-width: 100vw;
    }

    .comprador.modo-tienda {
        width: 100vw;
        max-width: 100vw;
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

    :global(.botanica-pie) {
        left: 0;
        height: 220px;
    }

    .hero-frase,
    .hero:not(.hero-tienda) .hero-arte {
        display: none;
    }

    .hero-texto {
        max-width: 64%;
    }

    .hero-flor,
    .hero-tallo {
        top: -42px;
        right: 0;
        width: min(44vw, 340px);
        opacity: 0.28;
    }

    .hero::before {
        width: 360px;
        height: 320px;
        top: -100px;
        right: -55px;
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

    .tienda-flor-fondo {
        right: -42px;
        height: 330px;
        opacity: 0.58;
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

@media (max-width: 700px) {
    .comprador.modo-tienda > * {
        min-width: 0;
    }

    .chip {
        max-width: 100%;
        white-space: normal;
        overflow-wrap: break-word;
        word-break: normal;
    }

    article.tarjeta-tienda {
        display: grid;
        grid-template-columns: 68px minmax(0, 1fr);
        align-items: center;
        gap: 0.7rem 0.9rem;
        min-width: 0;
    }

    .tarjeta-tienda-logo.grande {
        grid-column: 1;
        grid-row: 1;
        width: 64px;
        height: 64px;
    }

    .tarjeta-tienda-cuerpo {
        grid-column: 2;
        min-width: 0;
        width: 100%;
    }

    .tarjeta-tienda-cuerpo .chip {
        align-self: flex-start;
        max-width: 100%;
        white-space: normal;
        overflow-wrap: break-word;
    }

    .tarjeta-tienda .boton {
        grid-column: 1 / -1;
        width: 100%;
        box-sizing: border-box;
    }
}

@media (max-width: 560px) {
    .encabezado-fila {
        grid-template-columns: minmax(0, 1fr);
        row-gap: 0.7rem;
        margin-bottom: 1.2rem;
    }

    .encabezado-cuenta {
        grid-column: 1;
        grid-row: 1;
        justify-self: end;
    }

    .encabezado-titulo {
        grid-column: 1;
        grid-row: 2;
    }

    .comprador.modo-tienda > * {
        min-width: 0;
    }

    .hero {
        padding: 1.25rem;
        clip-path: inset(-50px 0 0 round 26px);
    }

    .hero-texto {
        max-width: 70%;
    }

    .hero-flor,
    .hero-tallo {
        top: -36px;
        right: 0;
        width: min(58vw, 280px);
        opacity: 0.24;
    }

    .hero::before {
        width: 280px;
        height: 260px;
        top: -62px;
        right: -80px;
    }

    .hero-texto h2 {
        font-size: clamp(1.45rem, 7vw, 1.8rem);
    }

    .hero-texto > p:not(.sobretitulo) {
        max-width: 34ch;
    }

    .carrusel .hero {
        padding-inline: 3.4rem;
    }

    .hero-tienda {
        --tam-nombre: clamp(1.9rem, 9vw, 2.4rem);
        flex-direction: column-reverse;
        align-items: flex-start;
        gap: 0.9rem;
    }

    .carrusel-flecha {
        left: 0.5rem;
        width: 2.4rem;
        height: 2.4rem;
    }

    .carrusel-flecha.siguiente {
        right: 0.5rem;
    }

    .carrusel-logo.hero-arte {
        width: 84px;
        height: 84px;
        box-shadow: 0 0 0 8px rgba(246, 193, 119, 0.22);
    }

    .carrusel-logo :deep(.imagen-publica),
    .carrusel-logo .logo-inicial.hero {
        width: 66px;
        height: 66px;
        font-size: 2rem;
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

    .tienda-flor-fondo {
        right: -66px;
        height: 290px;
        opacity: 0.44;
    }

    .rejilla-productos {
        grid-template-columns: repeat(auto-fill, minmax(min(160px, 100%), 1fr));
    }

    .detalle {
        min-width: 0;
    }

    :global(.botanica-pie) {
        height: 160px;
    }

    :global(.detalle-ondas-superiores) {
        height: 190px;
    }

    .tienda-hero-texto h1 {
        overflow-wrap: anywhere;
    }
}
</style>
