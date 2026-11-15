<script setup lang="ts">
withDefaults(defineProps<{ activo?: 'explorar' | 'ferias' }>(), { activo: 'explorar' })

const ruta = useRoute()

async function irExplorar() {
  if (ruta.path === '/comprador') window.scrollTo({ top: 0, behavior: 'smooth' })
  else await navigateTo('/comprador')
}

async function irSeccion(id: string) {
  if (ruta.path !== '/comprador') {
    await navigateTo({ path: '/comprador', hash: `#${id}` })
    return
  }
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <aside class="lateral">
    <button class="marca marca-boton" type="button" @click="irExplorar">
      <img class="marca-icono" src="/logo-amancay.png" alt="Rincón Amancay" />
      <span class="marca-texto">
        <small>Rincón</small>
        <strong>Amancay</strong>
      </span>
    </button>
    <nav class="menu" aria-label="Catálogo">
      <button :class="{ on: activo === 'explorar' && ruta.hash === '' }" type="button" @click="irExplorar">
        <span class="mi" aria-hidden="true">🏠</span>
        Explorar
      </button>
      <button type="button" @click="irSeccion('destacados')">
        <span class="mi" aria-hidden="true">🛍️</span>
        Productos
      </button>
      <button type="button" @click="irSeccion('catalogo')">
        <span class="mi" aria-hidden="true">🏪</span>
        Tiendas
      </button>
      <NuxtLink class="menu-enlace" :class="{ on: activo === 'ferias' }" to="/comprador/ferias">
        <span class="mi" aria-hidden="true">🎪</span>
        Ferias
      </NuxtLink>
      <NuxtLink class="menu-enlace" to="/admin">
        <span class="mi" aria-hidden="true">🛡️</span>
        Administración
      </NuxtLink>
      <NuxtLink class="menu-enlace" to="/emprendedora"><span class="mi" aria-hidden="true">&#x1F9D1;</span> Iniciar sesi&#xF3;n emprendedora</NuxtLink>
    </nav>
    <div class="lateral-frase">
      <svg class="lateral-montanas" viewBox="0 0 240 70" aria-hidden="true" preserveAspectRatio="none">
        <path d="M0 70 L40 28 L72 52 L110 12 L150 48 L180 30 L240 70 Z" fill="var(--amancay-bordo)" opacity="0.18" />
        <path d="M0 70 L50 40 L90 58 L130 32 L175 55 L210 42 L240 70 Z" fill="var(--amancay-bordo)" opacity="0.32" />
        <circle cx="196" cy="18" r="10" fill="var(--amancay-mostaza)" opacity="0.85" />
      </svg>
      <p>Apoyando lo local,<br />hacemos florecer<br />nuestras comunidades.</p>
    </div>
  </aside>
</template>

<style scoped>
.lateral {
  position: sticky;
  z-index: 2;
  top: 0;
  align-self: start;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  gap: 1.4rem;
  padding: 1.4rem 1.2rem;
  box-sizing: border-box;
  background: var(--amancay-superficie);
  border-right: 1.5px solid var(--amancay-borde);
  font-family: var(--amancay-fuente-cuerpo);
}

.marca {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  text-align: left;
}

.marca-boton {
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  font-family: inherit;
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

.menu {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.menu button,
.menu-enlace {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.75rem 0.9rem;
  border: 0;
  border-radius: 14px;
  background: none;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--amancay-tinta-suave);
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  font-family: inherit;
}

.menu button:hover,
.menu-enlace:hover {
  background: var(--amancay-fondo);
  color: var(--amancay-bordo);
}

.menu button.on,
.menu-enlace.on {
  background: var(--amancay-bordo-suave);
  color: var(--amancay-bordo);
}

.mi {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--amancay-naranja-fondo);
  font-size: 0.95rem;
}

.lateral-frase {
  position: relative;
  margin-top: auto;
  padding: 1.2rem 1rem 1rem;
  border-radius: 20px;
  background: linear-gradient(160deg, var(--amancay-superficie-variante), var(--amancay-naranja-fondo));
  overflow: hidden;
}

.lateral-frase p {
  position: relative;
  z-index: 1;
  margin: 0;
  font-family: 'DM Serif Display', Georgia, serif;
  font-style: italic;
  font-size: 1rem;
  line-height: 1.45;
  color: var(--amancay-bordo);
}

.lateral-montanas {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 64px;
  pointer-events: none;
}

@media (max-width: 900px) {
  .lateral {
    position: static;
    height: auto;
    min-width: 0;
    flex-direction: row;
    align-items: center;
    gap: 0.8rem 1.2rem;
    padding: 0.7rem 1rem;
    border-right: 0;
    border-bottom: 1.5px solid var(--amancay-borde);
  }

  .menu {
    flex: 1;
    min-width: 0;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.2rem 0.4rem;
  }

  .menu button,
  .menu-enlace {
    min-width: 0;
    padding: 0.45rem 0.55rem;
    font-size: 0.83rem;
  }

  .mi {
    flex: none;
  }

  .lateral-frase {
    display: none;
  }
}

@media (max-width: 560px) {
  .lateral {
    flex-direction: column;
    align-items: stretch;
    gap: 0.45rem;
    padding: 0.55rem 0.8rem 0.65rem;
  }

  .lateral > .marca {
    align-self: flex-start;
  }

  .lateral .menu {
    width: 100%;
    display: flex;
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 0.25rem;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scrollbar-width: none;
  }

  .lateral .menu::-webkit-scrollbar {
    display: none;
  }

  .menu button,
  .menu-enlace {
    flex: none;
    white-space: nowrap;
    justify-content: flex-start;
    gap: 0.35rem;
    padding: 0.35rem 0.4rem;
    font-size: 0.72rem;
    line-height: 1.25;
  }

  .mi {
    width: 27px;
    height: 27px;
    border-radius: 8px;
    font-size: 0.8rem;
  }
}
</style>
