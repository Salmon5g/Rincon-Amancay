<script setup lang="ts">
import '~/assets/amancay-tokens.css';

useHead({
    title: 'Ferias — Rincón Amancay',
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
        <span class="actual">Ferias</span>
      </nav>
      <header class="encabezado">
        <p class="sobretitulo">ENCUENTROS LOCALES</p>
        <h1>Ferias de Rincón Amancay</h1>
        <p class="sub">Próximamente podrás descubrir ferias, encuentros y actividades de nuestras emprendedoras.</p>
      </header>
      <section class="proximo" aria-label="Próximamente">
        <span class="proximo-arte" aria-hidden="true">🎪</span>
        <div>
          <h2>Próximamente</h2>
          <p>Estamos preparando esta sección. Vuelve pronto para ver ferias publicadas y su información.</p>
          <NuxtLink class="boton" to="/comprador">Explorar tiendas →</NuxtLink>
        </div>
      </section>
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
</style>
