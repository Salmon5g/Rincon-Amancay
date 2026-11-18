<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import '~/assets/amancay-tokens.css';
import type { FeriaPublica } from '~/types/comprador-publico';
import { compartirEnlacePublico } from '~/utils/compartir-enlace-publico';

const { $catalogoPublico } = useNuxtApp();
const route = useRoute();
const ferias = ref<FeriaPublica[]>([]);
const feria = ref<FeriaPublica | null>(null);
const feriaSeleccionadaId = ref('');
const mapaAbierto = computed(() => route.query.mapa === '1');
const ubicacionUsuario = ref<{ latitud: number; longitud: number } | null>(null);
const estadoUbicacion = ref<'inactiva' | 'buscando' | 'activa' | 'rechazada' | 'fallo'>('inactiva');
const mensajeUbicacion = ref('');
const mensajeCompartir = ref('');
const cargando = ref(true);
const error = ref('');

const feriaId = computed(() => {
    const valor = route.query.feria;
    return typeof valor === 'string' ? valor : Array.isArray(valor) && typeof valor[0] === 'string' ? valor[0] : '';
});

const formatoFecha = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
function fechaTexto(valor: Date | null) {
    if (!valor) return '';
    const texto = formatoFecha.format(valor);
    return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function coordenadasValidas(latitud: number | null, longitud: number | null) {
    return latitud !== null && Number.isFinite(latitud) && latitud >= -90 && latitud <= 90
        && longitud !== null && Number.isFinite(longitud) && longitud >= -180 && longitud <= 180;
}

function ubicacionValida(f: FeriaPublica) {
    return coordenadasValidas(f.latitud, f.longitud);
}

function distanciaMetros(f: FeriaPublica) {
    const origen = ubicacionUsuario.value;
    if (!origen || !ubicacionValida(f)) return null;
    const radianes = (grados: number) => grados * Math.PI / 180;
    const diferenciaLatitud = radianes(f.latitud! - origen.latitud);
    const diferenciaLongitud = radianes(f.longitud! - origen.longitud);
    const a = Math.sin(diferenciaLatitud / 2) ** 2
        + Math.cos(radianes(origen.latitud)) * Math.cos(radianes(f.latitud!))
        * Math.sin(diferenciaLongitud / 2) ** 2;
    const proporcion = Math.min(1, Math.max(0, a));
    return 6_371_000 * 2 * Math.atan2(Math.sqrt(proporcion), Math.sqrt(1 - proporcion));
}

function distanciaTexto(f: FeriaPublica) {
    const distancia = distanciaMetros(f);
    if (distancia === null) return '';
    return distancia < 1000 ? `${Math.round(distancia)} m` : `${(distancia / 1000).toFixed(1)} km`;
}

const feriasOrdenadas = computed(() => [...ferias.value].sort((primera, segunda) => {
    const distanciaPrimera = distanciaMetros(primera);
    const distanciaSegunda = distanciaMetros(segunda);
    if (distanciaPrimera !== null && distanciaSegunda !== null) return distanciaPrimera - distanciaSegunda;
    if (distanciaPrimera !== null) return -1;
    if (distanciaSegunda !== null) return 1;
    const fechaPrimera = primera.fechaInicio?.getTime() ?? Number.POSITIVE_INFINITY;
    const fechaSegunda = segunda.fechaInicio?.getTime() ?? Number.POSITIVE_INFINITY;
    return fechaPrimera - fechaSegunda;
}));

function seleccionarPrimeraFeria() {
    if (!feriaSeleccionadaId.value) {
        feriaSeleccionadaId.value = ferias.value.find(ubicacionValida)?.id ?? '';
    }
}

function localizarUsuario() {
    if (!import.meta.client || !navigator.geolocation) {
        ubicacionUsuario.value = null;
        estadoUbicacion.value = 'fallo';
        mensajeUbicacion.value = 'Este navegador no permite obtener tu ubicación. Las ferias se muestran por fecha.';
        seleccionarPrimeraFeria();
        return;
    }

    estadoUbicacion.value = 'buscando';
    ubicacionUsuario.value = null;
    feriaSeleccionadaId.value = '';
    mensajeUbicacion.value = '';
    navigator.geolocation.getCurrentPosition(
        posicion => {
            const latitud = posicion.coords.latitude;
            const longitud = posicion.coords.longitude;
            if (!coordenadasValidas(latitud, longitud)) {
                ubicacionUsuario.value = null;
                estadoUbicacion.value = 'fallo';
                mensajeUbicacion.value = 'No pudimos determinar tu ubicación. Las ferias se muestran por fecha.';
                seleccionarPrimeraFeria();
                return;
            }
            ubicacionUsuario.value = { latitud, longitud };
            feriaSeleccionadaId.value = '';
            estadoUbicacion.value = 'activa';
            mensajeUbicacion.value = '';
        },
        errorUbicacion => {
            ubicacionUsuario.value = null;
            estadoUbicacion.value = errorUbicacion.code === errorUbicacion.PERMISSION_DENIED ? 'rechazada' : 'fallo';
            mensajeUbicacion.value = errorUbicacion.code === errorUbicacion.PERMISSION_DENIED
                ? 'No autorizaste la ubicación. Las ferias se muestran por fecha.'
                : 'No pudimos obtener tu ubicación. Las ferias se muestran por fecha.';
            seleccionarPrimeraFeria();
        },
        { enableHighAccuracy: true, timeout: 30_000, maximumAge: 0 },
    );
}

function enlaceComoLlegar(f: FeriaPublica) {
    const destino = ubicacionValida(f) ? `${f.latitud},${f.longitud}` : f.direccion.trim();
    return destino ? `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destino)}` : '';
}

async function compartirFeria() {
    if (!feria.value) return;
    const url = new URL('/comprador/ferias', window.location.origin);
    url.searchParams.set('feria', feria.value.id);
    const resultado = await compartirEnlacePublico({
        titulo: feria.value.nombre,
        texto: `${feria.value.nombre} en Rincón Amancay`,
        url: url.toString(),
    });
    mensajeCompartir.value = resultado === 'copiado'
        ? 'Enlace copiado.'
        : resultado === 'compartido'
            ? 'Enlace compartido.'
            : resultado === 'fallo'
                ? 'No se pudo compartir el enlace.'
                : '';
}

function seleccionarFeria(id: string) {
    feriaSeleccionadaId.value = id;
}

async function abrirMapa() {
    await navigateTo({ path: '/comprador/ferias', query: { mapa: '1' } });
}

async function cargar() {
    cargando.value = true;
    error.value = '';
    feria.value = null;
    try {
        if (feriaId.value) feria.value = await $catalogoPublico.leerFeria(feriaId.value);
        else {
            ferias.value = await $catalogoPublico.listarFeriasPublicas();
            if (mapaAbierto.value && estadoUbicacion.value !== 'buscando' && !ubicacionUsuario.value) {
                seleccionarPrimeraFeria();
            }
        }
    } catch (e) {
        const codigo = (e as { code?: string })?.code;
        error.value = feriaId.value && (codigo === 'no-encontrado' || codigo === 'sin-permiso')
            ? 'Esta feria ya no está disponible o aún no se publica.'
            : 'No pudimos cargar las ferias. Revisa tu conexión e inténtalo nuevamente.';
    } finally {
        cargando.value = false;
    }
}

watch(feriaId, () => void cargar());
watch(mapaAbierto, abierto => {
    if (abierto) localizarUsuario();
});
onMounted(() => {
    void cargar();
    if (mapaAbierto.value) localizarUsuario();
});

useHead(() => ({
    title: feria.value ? `${feria.value.nombre} — Rincón Amancay` : 'Ferias — Rincón Amancay',
}));

useHead({
    link: [
        { rel: 'icon', href: '/favicon.png' },
        {
            rel: 'stylesheet',
            href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap',
        },
    ],
});
</script>

<template>
  <div class="comprador modo-explorar">
    <CompradorLateral activo="ferias" />
    <main class="contenido">
      <nav class="migas" aria-label="Ruta">
        <NuxtLink to="/comprador">Explorar</NuxtLink>
        <span>/</span>
        <NuxtLink v-if="feriaId" to="/comprador/ferias">Ferias</NuxtLink>
        <span v-else class="actual">Ferias</span>
        <template v-if="feria">
          <span>/</span>
          <span class="actual">{{ feria.nombre }}</span>
        </template>
      </nav>

      <p v-if="cargando" class="estado" role="status">Cargando ferias…</p>

      <section v-else-if="error" class="proximo" role="alert">
        <span class="proximo-arte" aria-hidden="true">🎪</span>
        <div>
          <h2>No disponible</h2>
          <p>{{ error }}</p>
          <NuxtLink class="boton" to="/comprador/ferias">Ver todas las ferias →</NuxtLink>
        </div>
      </section>

      <article v-else-if="feria" class="detalle">
        <img v-if="feria.imagenUrl" :src="feria.imagenUrl" :alt="`Imagen de ${feria.nombre}`" class="detalle-imagen" />
        <header class="encabezado">
          <p class="sobretitulo">FERIA</p>
          <h1>{{ feria.nombre }}</h1>
          <p v-if="feria.organizador" class="sub">Organiza: {{ feria.organizador }}</p>
        </header>
        <dl class="datos">
          <div v-if="feria.fechaInicio">
            <dt>Inicio</dt>
            <dd>{{ fechaTexto(feria.fechaInicio) }}</dd>
          </div>
          <div v-if="feria.fechaFin">
            <dt>Término</dt>
            <dd>{{ fechaTexto(feria.fechaFin) }}</dd>
          </div>
          <div v-if="feria.direccion">
            <dt>Lugar</dt>
            <dd>{{ feria.direccion }}</dd>
          </div>
        </dl>
        <p v-if="feria.descripcion" class="descripcion">{{ feria.descripcion }}</p>
        <section class="detalle-ubicacion" aria-labelledby="titulo-mapa-feria">
          <div class="detalle-ubicacion-cabecera">
            <div>
              <h2 id="titulo-mapa-feria">Ubicación de la feria</h2>
              <p v-if="feria.direccion">{{ feria.direccion }}</p>
            </div>
            <span class="nota-mapa">OpenStreetMap</span>
          </div>
          <MapaFeriasComprador
            v-if="ubicacionValida(feria)"
            :ferias="[feria]"
            :seleccionada-id="feria.id"
            modo="detalle"
          />
          <p v-else class="ubicacion-pendiente" role="status">Esta feria aún no tiene un punto guardado en el mapa.</p>
        </section>
        <div class="detalle-acciones">
          <button class="boton boton-secundario" type="button" @click="compartirFeria">Compartir feria</button>
          <a v-if="enlaceComoLlegar(feria)" class="boton" :href="enlaceComoLlegar(feria)" target="_blank" rel="noopener">Cómo llegar ↗</a>
          <NuxtLink class="boton boton-secundario" to="/comprador/ferias">Volver a ferias</NuxtLink>
          <NuxtLink class="boton boton-secundario" to="/comprador">Explorar tiendas →</NuxtLink>
        </div>
        <p v-if="mensajeCompartir" class="aviso-compartir" role="status" aria-live="polite">{{ mensajeCompartir }}</p>
      </article>

      <template v-else>
        <NuxtLink v-if="mapaAbierto" class="volver-ferias" to="/comprador/ferias">← Volver a ferias</NuxtLink>
        <header class="encabezado">
          <p class="sobretitulo">{{ mapaAbierto ? 'ENCUENTROS LOCALES' : 'ACTIVIDADES DE NUESTRA COMUNIDAD' }}</p>
          <h1>{{ mapaAbierto ? 'Mapa de ferias' : 'Ferias' }}</h1>
          <p class="sub">{{ mapaAbierto ? 'Selecciona una feria para centrarla en el mapa.' : 'Ferias publicadas de los emprendimientos Amancay.' }}</p>
        </header>
        <section v-if="ferias.length === 0" class="proximo" aria-label="Sin ferias">
          <span class="proximo-arte" aria-hidden="true">🎪</span>
          <div>
            <h2>Aún no hay ferias publicadas</h2>
            <p>Vuelve pronto para ver las próximas ferias y su información.</p>
            <NuxtLink class="boton" to="/comprador">Explorar tiendas →</NuxtLink>
          </div>
        </section>
        <button v-else-if="!mapaAbierto" class="boton boton-mapa" type="button" @click="abrirMapa">Ver ferias en el mapa</button>
        <section v-if="mapaAbierto && ferias.length" class="explorador-ferias" aria-labelledby="titulo-mapa-directorio">
          <div class="directorio-mapa-cabecera">
            <div>
              <h2 id="titulo-mapa-directorio">Ferias publicadas</h2>
              <p>{{ ubicacionUsuario ? 'Ordenadas de la más cercana a la más lejana.' : 'Selecciona una feria para mover el mapa a su ubicación.' }}</p>
            </div>
            <span class="nota-mapa">OpenStreetMap</span>
          </div>
          <div class="estado-ubicacion-mapa" role="status" aria-live="polite">
            <p v-if="estadoUbicacion === 'buscando'">Buscando tu ubicación…</p>
            <p v-else-if="ubicacionUsuario">Tu ubicación está marcada en el mapa.</p>
            <p v-else>{{ mensajeUbicacion || 'Activa tu ubicación para ordenar las ferias por cercanía.' }}</p>
            <button type="button" :disabled="estadoUbicacion === 'buscando'" @click="localizarUsuario">
              {{ estadoUbicacion === 'buscando' ? 'Buscando…' : ubicacionUsuario ? 'Actualizar ubicación' : 'Usar mi ubicación' }}
            </button>
          </div>
          <MapaFeriasComprador
            :ferias="feriasOrdenadas"
            :seleccionada-id="feriaSeleccionadaId"
            :ubicacion-usuario="ubicacionUsuario"
            modo="directorio"
            @seleccionar="seleccionarFeria"
          />
          <p v-if="!ferias.some(ubicacionValida)" class="ubicacion-pendiente" role="status">Las ferias publicadas todavía no tienen puntos guardados para mostrar.</p>
        </section>
        <ul v-if="!mapaAbierto && ferias.length" class="lista">
          <li v-for="f in ferias" :key="f.id">
            <article class="tarjeta">
              <h2>{{ f.nombre }}</h2>
              <p v-if="f.fechaInicio">{{ fechaTexto(f.fechaInicio) }}</p>
              <p v-if="f.direccion">{{ f.direccion }}</p>
              <p v-if="f.organizador">Organiza: {{ f.organizador }}</p>
              <div class="tarjeta-acciones">
                <NuxtLink class="accion-detalle" :to="{ path: '/comprador/ferias', query: { feria: f.id } }">Ver detalles →</NuxtLink>
              </div>
            </article>
          </li>
        </ul>
        <ul v-if="mapaAbierto && ferias.length" class="lista">
          <li v-for="f in feriasOrdenadas" :key="f.id">
            <article class="tarjeta" :class="{ seleccionada: feriaSeleccionadaId === f.id }">
              <div class="tarjeta-titulo">
                <h2>{{ f.nombre }}</h2>
                <span v-if="distanciaTexto(f)" class="distancia-feria">{{ distanciaTexto(f) }}</span>
              </div>
              <p v-if="f.fechaInicio">{{ fechaTexto(f.fechaInicio) }}</p>
              <p v-if="f.direccion">{{ f.direccion }}</p>
              <p v-if="f.organizador">Organiza: {{ f.organizador }}</p>
              <div class="tarjeta-acciones">
                <button v-if="ubicacionValida(f)" class="accion-secundaria" type="button" @click="seleccionarFeria(f.id)">Ubicar en el mapa</button>
                <span v-else class="sin-ubicacion">Ubicación no disponible</span>
                <a v-if="enlaceComoLlegar(f)" class="accion-detalle" :href="enlaceComoLlegar(f)" target="_blank" rel="noopener">Cómo llegar ↗</a>
              </div>
            </article>
          </li>
        </ul>
      </template>
    </main>
  </div>
</template>

<style scoped>
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

.contenido {
    width: 100%;
    min-width: 0;
    padding: 1.8rem clamp(1rem, 3vw, 2.5rem) 3.5rem;
    box-sizing: border-box;
}

.migas {
    display: flex;
    flex-wrap: wrap;
    gap: 0.45rem;
    margin-bottom: 1.1rem;
    font-size: 0.86rem;
    color: var(--amancay-tinta-suave);
}

.migas a {
    font-weight: 600;
    color: var(--amancay-bordo);
    text-decoration: none;
}

.migas a:hover {
    text-decoration: underline;
}

.migas .actual {
    color: var(--amancay-tinta);
    font-weight: 600;
}

.encabezado {
    margin-bottom: 1.6rem;
}

.sobretitulo {
    margin: 0 0 0.4rem;
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.1em;
    color: var(--amancay-mostaza);
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

.proximo {
    display: flex;
    align-items: center;
    gap: 1.4rem;
    padding: clamp(1.4rem, 3vw, 2.2rem);
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 26px;
    box-shadow: 0 8px 24px rgba(107, 30, 61, 0.07);
    max-width: 720px;
}

.proximo-arte {
    flex: none;
    width: 88px;
    height: 88px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--amancay-naranja-fondo);
    font-size: 2.2rem;
}

.proximo h2 {
    margin: 0 0 0.4rem;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.5rem;
    color: var(--amancay-bordo);
}

.proximo p {
    margin: 0 0 1rem;
    color: var(--amancay-tinta-suave);
    line-height: 1.5;
}

.boton {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    min-height: 46px;
    padding: 0.65rem 1.4rem;
    font-size: 0.93rem;
    font-weight: 700;
    border-radius: 999px;
    border: 1.5px solid var(--amancay-bordo);
    background: var(--amancay-bordo);
    color: #fff;
    text-decoration: none;
    cursor: pointer;
}

.boton:hover {
    background: var(--amancay-bordo-oscuro);
}

.estado {
    color: var(--amancay-tinta-suave);
}

.detalle {
    max-width: 760px;
    padding: clamp(1.2rem, 3vw, 2rem);
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 26px;
    box-shadow: 0 8px 24px rgba(107, 30, 61, 0.07);
}

.explorador-ferias {
    margin: 0 0 1.4rem;
    padding: clamp(1rem, 2vw, 1.4rem);
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 24px;
    box-shadow: 0 8px 24px rgba(107, 30, 61, 0.07);
}

.boton-mapa {
    display: flex;
    width: 100%;
    min-height: 58px;
    justify-content: center;
    margin: 0 0 1.2rem;
}

.volver-ferias {
    display: inline-flex;
    align-items: center;
    min-height: 40px;
    margin-bottom: 0.9rem;
    color: var(--amancay-bordo);
    font-weight: 700;
    text-decoration: none;
}

.volver-ferias:hover {
    text-decoration: underline;
}

.directorio-mapa-cabecera,
.detalle-ubicacion-cabecera {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 0.9rem;
}

.directorio-mapa-cabecera h2,
.detalle-ubicacion-cabecera h2 {
    margin: 0;
    font-family: 'DM Serif Display', Georgia, serif;
    font-size: 1.3rem;
    font-weight: 400;
    color: var(--amancay-bordo);
}

.directorio-mapa-cabecera p,
.detalle-ubicacion-cabecera p {
    margin: 0.3rem 0 0;
    color: var(--amancay-tinta-suave);
    line-height: 1.45;
}

.estado-ubicacion-mapa {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.8rem;
    margin: 0 0 0.85rem;
    padding: 0.75rem 0.9rem;
    border-radius: 14px;
    background: var(--amancay-fondo);
}

.estado-ubicacion-mapa p {
    margin: 0;
    color: var(--amancay-tinta-suave);
    font-size: 0.88rem;
}

.estado-ubicacion-mapa button {
    flex: none;
    min-height: 38px;
    padding: 0.45rem 0.8rem;
    border: 1px solid var(--amancay-borde);
    border-radius: 999px;
    background: var(--amancay-superficie);
    color: var(--amancay-bordo);
    font: inherit;
    font-size: 0.82rem;
    font-weight: 700;
    cursor: pointer;
}

.estado-ubicacion-mapa button:disabled {
    cursor: wait;
    opacity: 0.65;
}

.nota-mapa {
    flex: none;
    padding: 0.35rem 0.65rem;
    border-radius: 999px;
    background: var(--amancay-fondo);
    color: var(--amancay-tinta-suave);
    font-size: 0.76rem;
    font-weight: 600;
}

.detalle-ubicacion {
    margin: 0 0 1.4rem;
    padding-top: 1rem;
    border-top: 1px solid var(--amancay-borde);
}

.ubicacion-pendiente {
    margin: 0;
    padding: 0.9rem 1rem;
    border-radius: 14px;
    background: var(--amancay-fondo);
    color: var(--amancay-tinta-suave);
    line-height: 1.5;
}

.detalle-acciones {
    display: flex;
    flex-wrap: wrap;
    gap: 0.7rem;
}

.aviso-compartir {
    margin: 0.7rem 0 0;
    color: var(--amancay-tinta-suave);
    font-size: 0.88rem;
}

.boton-secundario {
    border-color: var(--amancay-borde);
    background: transparent;
    color: var(--amancay-bordo);
}

.boton-secundario:hover {
    border-color: var(--amancay-bordo);
    background: var(--amancay-fondo);
}

.detalle-imagen {
    display: block;
    width: 100%;
    max-height: 340px;
    object-fit: cover;
    border-radius: 18px;
    margin-bottom: 1.2rem;
}

.datos {
    display: grid;
    gap: 0.8rem;
    margin: 0 0 1.2rem;
}

.datos dt {
    font-size: 0.75rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--amancay-mostaza);
}

.datos dd {
    margin: 0.15rem 0 0;
    color: var(--amancay-tinta);
}

.datos a {
    display: inline-block;
    margin-left: 0.4rem;
    font-weight: 600;
    color: var(--amancay-bordo);
}

.descripcion {
    margin: 0 0 1.4rem;
    line-height: 1.6;
    white-space: pre-line;
    color: var(--amancay-tinta);
}

.lista {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
}

.tarjeta {
    display: block;
    height: 100%;
    padding: 1.2rem 1.3rem;
    background: var(--amancay-superficie);
    border: 1.5px solid var(--amancay-borde);
    border-radius: 20px;
    color: var(--amancay-tinta);
    text-decoration: none;
    box-sizing: border-box;
}

.tarjeta.seleccionada {
    border-color: var(--amancay-bordo);
    background: linear-gradient(145deg, #fff5f6, var(--amancay-superficie));
    box-shadow: 0 8px 22px rgba(107, 30, 61, 0.1);
}

.tarjeta:hover,
.tarjeta:focus-visible {
    border-color: var(--amancay-bordo);
}

.tarjeta h2 {
    margin: 0 0 0.5rem;
    font-family: 'DM Serif Display', Georgia, serif;
    font-weight: 400;
    font-size: 1.3rem;
    color: var(--amancay-bordo);
}

.tarjeta-titulo {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 0.7rem;
}

.tarjeta-titulo h2 {
    min-width: 0;
}

.distancia-feria {
    flex: none;
    padding: 0.25rem 0.55rem;
    border-radius: 999px;
    background: var(--amancay-fondo);
    color: var(--amancay-bordo);
    font-size: 0.82rem;
    font-weight: 700;
}

.tarjeta p {
    margin: 0.2rem 0 0;
    font-size: 0.9rem;
    color: var(--amancay-tinta-suave);
}

.tarjeta-acciones {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.7rem;
    flex-wrap: wrap;
    margin-top: 1rem;
    padding-top: 0.8rem;
    border-top: 1px solid var(--amancay-borde);
}

.accion-secundaria,
.accion-detalle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0.55rem 0.9rem;
    border: 1px solid var(--amancay-borde);
    border-radius: 999px;
    background: transparent;
    color: var(--amancay-bordo);
    font: inherit;
    font-size: 0.84rem;
    font-weight: 700;
    text-decoration: none;
    cursor: pointer;
}

.accion-detalle {
    border-color: var(--amancay-bordo);
    background: var(--amancay-bordo);
    color: #fff;
}

.sin-ubicacion {
    color: var(--amancay-tinta-suave);
    font-size: 0.82rem;
}

@media (max-width: 560px) {
    .proximo {
        flex-direction: column;
        align-items: flex-start;
    }

    .directorio-mapa-cabecera,
    .detalle-ubicacion-cabecera {
        flex-direction: column;
    }

    .estado-ubicacion-mapa {
        align-items: flex-start;
        flex-direction: column;
    }

    .estado-ubicacion-mapa button {
        width: 100%;
    }

    .detalle-acciones > * {
        flex: 1 1 100%;
        justify-content: center;
    }

    .tarjeta-acciones {
        align-items: stretch;
    }

    .accion-secundaria,
    .accion-detalle {
        flex: 1 1 100%;
    }
}
</style>
