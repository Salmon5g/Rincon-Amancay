<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

useHead({ title: 'Administración — Rincón Amancay' })

type Acceso = { roles?: string[]; estado?: string; tiendaId?: string } | null

const { $amancay } = useNuxtApp()

const cargando = ref(true)
const enviando = ref(false)
const correo = ref('')
const clave = ref('')
const error = ref('')
const acceso = ref<Acceso>(null)

const esAdmin = computed(
  () =>
    !!acceso.value &&
    acceso.value.estado === 'activo' &&
    Array.isArray(acceso.value.roles) &&
    acceso.value.roles.includes('administrador'),
)

const correoUsuario = computed(() => {
  const usuario = $amancay?.firebase?.auth?.currentUser
  return usuario?.email ?? correo.value
})

const secciones = [
  {
    titulo: 'Emprendedoras',
    descripcion: 'Registrar, invitar, desactivar y reactivar cuentas de emprendedoras.',
  },
  {
    titulo: 'Asociaciones',
    descripcion: 'Crear asociaciones y asignar o quitar emprendedoras integrantes.',
  },
  {
    titulo: 'Ferias y eventos',
    descripcion: 'Crear ferias, invitar participantes y seguir su estado en el checklist.',
  },
  {
    titulo: 'Catálogos',
    descripcion: 'Mantener los tipos de emprendimiento, tipos de producto y categorías.',
  },
  {
    titulo: 'Configuración general',
    descripcion: 'Navegación anónima de la app y habilitación de módulos opcionales.',
  },
  {
    titulo: 'Administradores',
    descripcion: 'Gestionar otras cuentas de administrador de la plataforma.',
  },
]

onMounted(async () => {
  try {
    acceso.value = $amancay ? await $amancay.consultarAcceso() : null
  } catch {
    acceso.value = null
  } finally {
    cargando.value = false
  }
})

async function ingresar() {
  error.value = ''
  if (!$amancay) {
    error.value = 'El servicio local no está disponible.'
    return
  }
  if (!correo.value.trim() || !clave.value) {
    error.value = 'Escribe tu correo y tu contraseña.'
    return
  }
  enviando.value = true
  try {
    await $amancay.iniciarSesion(correo.value.trim(), clave.value)
    acceso.value = await $amancay.consultarAcceso()
    clave.value = ''
  } catch {
    error.value = 'No se pudo iniciar sesión. Revisa tus datos e inténtalo de nuevo.'
  } finally {
    enviando.value = false
  }
}

async function salir() {
  if ($amancay) await $amancay.cerrarSesion()
  acceso.value = null
  correo.value = ''
  clave.value = ''
  error.value = ''
}
</script>

<template>
  <main class="admin">
    <section v-if="cargando" class="panel mensaje">
      <p>Cargando…</p>
    </section>

    <section v-else-if="!esAdmin" class="panel estrecho">
      <header class="marca">
        <h1>Rincón Amancay</h1>
        <p class="sub">Panel de administración</p>
      </header>

      <template v-if="acceso">
        <p class="aviso">Esta cuenta no tiene permisos de administración.</p>
        <button class="boton" type="button" @click="salir">Cerrar sesión</button>
      </template>

      <form v-else class="formulario" @submit.prevent="ingresar">
        <label for="correo">Correo</label>
        <input
          id="correo"
          v-model="correo"
          type="email"
          autocomplete="username"
          placeholder="nombre@ejemplo.cl"
        />

        <label for="clave">Contraseña</label>
        <input
          id="clave"
          v-model="clave"
          type="password"
          autocomplete="current-password"
          placeholder="Tu contraseña"
        />

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button class="boton boton-primario" type="submit" :disabled="enviando">
          {{ enviando ? 'Ingresando…' : 'Ingresar' }}
        </button>
      </form>
    </section>

    <section v-else class="panel">
      <header class="cabecera">
        <div>
          <h1>Panel de administración</h1>
          <p class="sub">{{ correoUsuario }}</p>
        </div>
        <button class="boton" type="button" @click="salir">Cerrar sesión</button>
      </header>

      <nav class="rejilla" aria-label="Secciones de administración">
        <article v-for="seccion in secciones" :key="seccion.titulo" class="tarjeta">
          <h2>{{ seccion.titulo }}</h2>
          <p>{{ seccion.descripcion }}</p>
          <span class="etiqueta">Próximamente</span>
        </article>
      </nav>
    </section>
  </main>
</template>

<style scoped>
.admin {
  --verde: #1f6f4a;
  --verde-oscuro: #17563a;
  --tinta: #1c2b24;
  --suave: #5c6b63;
  --borde: #d7e0da;
  --fondo: #f2f6f3;

  min-height: 100vh;
  margin: 0;
  padding: 2rem 1rem;
  box-sizing: border-box;
  background: var(--fondo);
  color: var(--tinta);
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
}

.panel {
  max-width: 960px;
  margin: 0 auto;
}

.panel.estrecho {
  max-width: 380px;
  margin-top: 8vh;
  background: #fff;
  border: 1px solid var(--borde);
  border-radius: 14px;
  padding: 2rem;
  box-shadow: 0 8px 24px rgba(28, 43, 36, 0.08);
}

.mensaje {
  text-align: center;
  color: var(--suave);
  padding-top: 8vh;
}

.marca h1,
.cabecera h1 {
  margin: 0;
  font-size: 1.6rem;
  color: var(--verde-oscuro);
}

.sub {
  margin: 0.25rem 0 1.5rem;
  color: var(--suave);
  font-size: 0.95rem;
}

.formulario {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.formulario label {
  font-weight: 600;
  font-size: 0.9rem;
}

.formulario input {
  padding: 0.75rem;
  font-size: 1rem;
  border: 1px solid var(--borde);
  border-radius: 9px;
  margin-bottom: 0.75rem;
}

.formulario input:focus {
  outline: 2px solid var(--verde);
  outline-offset: 1px;
  border-color: var(--verde);
}

.boton {
  min-height: 48px;
  padding: 0.75rem 1.25rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--verde-oscuro);
  background: #fff;
  border: 1px solid var(--borde);
  border-radius: 9px;
  cursor: pointer;
}

.boton:hover {
  background: var(--fondo);
}

.boton-primario {
  color: #fff;
  background: var(--verde);
  border-color: var(--verde);
}

.boton-primario:hover {
  background: var(--verde-oscuro);
}

.boton:disabled {
  opacity: 0.6;
  cursor: default;
}

.error {
  margin: 0 0 0.75rem;
  color: #a3231b;
  font-size: 0.9rem;
}

.aviso {
  margin: 0 0 1rem;
  color: var(--suave);
}

.cabecera {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
}

.rejilla {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
}

.tarjeta {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: #fff;
  border: 1px solid var(--borde);
  border-radius: 12px;
  padding: 1.25rem;
}

.tarjeta h2 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--verde-oscuro);
}

.tarjeta p {
  margin: 0;
  flex: 1;
  color: var(--suave);
  font-size: 0.92rem;
  line-height: 1.4;
}

.etiqueta {
  align-self: flex-start;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--verde);
  background: rgba(31, 111, 74, 0.1);
  border-radius: 999px;
  padding: 0.2rem 0.6rem;
}
</style>
