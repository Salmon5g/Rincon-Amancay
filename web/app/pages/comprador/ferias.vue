<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import '~/assets/amancay-tokens.css';
import type { FeriaPublica } from '~/types/comprador-publico';

const { $catalogoPublico } = useNuxtApp();
const route = useRoute();
const ferias = ref<FeriaPublica[]>([]);
const feria = ref<FeriaPublica | null>(null);
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

function enlaceMapa(f: FeriaPublica) {
    const destino = f.latitud !== null && f.longitud !== null ? `${f.latitud},${f.longitud}` : f.direccion;
    return destino ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(destino)}` : '';
}

async function cargar() {
    cargando.value = true;
    error.value = '';
    feria.value = null;
    try {
        if (feriaId.value) feria.value = await $catalogoPublico.leerFeria(feriaId.value);
        else ferias.value = await $catalogoPublico.listarFeriasPublicas();
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
onMounted(() => void cargar());

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
            <dd>
              {{ feria.direccion }}
              <a v-if="enlaceMapa(feria)" :href="enlaceMapa(feria)" target="_blank" rel="noopener">Ver en el mapa ↗</a>
            </dd>
          </div>
        </dl>
        <p v-if="feria.descripcion" class="descripcion">{{ feria.descripcion }}</p>
        <NuxtLink class="boton" to="/comprador">Explorar tiendas →</NuxtLink>
      </article>

      <template v-else>
        <header class="encabezado">
          <p class="sobretitulo">ENCUENTROS LOCALES</p>
          <h1>Ferias de Rincón Amancay</h1>
          <p class="sub">Descubre ferias, encuentros y actividades de nuestras emprendedoras.</p>
        </header>
        <section v-if="ferias.length === 0" class="proximo" aria-label="Sin ferias">
          <span class="proximo-arte" aria-hidden="true">🎪</span>
          <div>
            <h2>Aún no hay ferias publicadas</h2>
            <p>Vuelve pronto para ver las próximas ferias y su información.</p>
            <NuxtLink class="boton" to="/comprador">Explorar tiendas →</NuxtLink>
          </div>
        </section>
        <ul v-else class="lista">
          <li v-for="f in ferias" :key="f.id">
            <NuxtLink class="tarjeta" :to="{ path: '/comprador/ferias', query: { feria: f.id } }">
              <h2>{{ f.nombre }}</h2>
              <p v-if="f.fechaInicio">{{ fechaTexto(f.fechaInicio) }}</p>
              <p v-if="f.direccion">{{ f.direccion }}</p>
            </NuxtLink>
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

.tarjeta p {
    margin: 0.2rem 0 0;
    font-size: 0.9rem;
    color: var(--amancay-tinta-suave);
}

@media (max-width: 560px) {
    .proximo {
        flex-direction: column;
        align-items: flex-start;
    }
}
</style>
