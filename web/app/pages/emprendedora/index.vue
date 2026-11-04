<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { sendPasswordResetEmail } from 'firebase/auth'
import { cargarCuentaEmprendedora, type ProductoEmprendedoraWeb } from '~/repositories/firebase/emprendedora'

type Seccion = 'inicio' | 'productos' | 'agregar' | 'mas'
type Filtro = 'todos' | 'publicado' | 'borrador'

useHead({
  title: 'Mi espacio emprendedora — Rincón Amancay',
  link: [
    { rel: 'icon', href: '/favicon.png' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' },
  ],
})

const secciones: { id: Seccion; nombre: string; icono: string }[] = [
  { id: 'inicio', nombre: 'Inicio', icono: '⌂' },
  { id: 'productos', nombre: 'Productos', icono: '▦' },
  { id: 'agregar', nombre: 'Agregar', icono: '+' },
  { id: 'mas', nombre: 'Más', icono: '•••' },
]
const cuenta = ref<Awaited<ReturnType<typeof cargarCuentaEmprendedora>> | null>(null)
const revisando = ref(true)
const enviando = ref(false)
const enviandoRecuperacion = ref(false)
const correo = ref('')
const contrasena = ref('')
const error = ref('')
const aviso = ref('')
const seccion = ref<Seccion>('inicio')
const filtro = ref<Filtro>('todos')
const fecha = ref('')

const publicados = computed(() => cuenta.value?.productos.filter((producto) => producto.estado === 'publicado').length ?? 0)
const borradores = computed(() => cuenta.value?.productos.filter((producto) => producto.estado === 'borrador').length ?? 0)
const productosFiltrados = computed(() => {
  const productos = cuenta.value?.productos ?? []
  return filtro.value === 'todos' ? productos : productos.filter((producto) => producto.estado === filtro.value)
})

function cliente() {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  return $amancay
}

function nombrePersona() {
  const nombre = cuenta.value?.nombreMostrar.trim()
  if (nombre) return nombre.split(/\s+/)[0]
  return cuenta.value?.correo.split('@')[0] || 'Artesana'
}

function mensajeDeError(valor: unknown) {
  const codigo = typeof valor === 'object' && valor !== null && 'code' in valor ? String((valor as { code?: unknown }).code ?? '') : ''
  const mensaje = valor instanceof Error ? valor.message : ''
  if (codigo.includes('invalid-credential') || codigo.includes('wrong-password') || codigo.includes('user-not-found')) return 'No se pudo iniciar sesión. Revisa el correo y la contraseña.'
  if (codigo.includes('network-request-failed')) return 'No hay conexión. Revisa internet e inténtalo nuevamente.'
  if (codigo.includes('too-many-requests')) return 'Hubo muchos intentos. Espera un momento y vuelve a probar.'
  return mensaje || 'No se pudo completar el acceso. Inténtalo nuevamente.'
}

async function revisarSesion() {
  const servicio = cliente()
  await servicio.firebase.auth.authStateReady()
  const usuario = servicio.firebase.auth.currentUser
  if (!usuario) return
  try {
    cuenta.value = await cargarCuentaEmprendedora(servicio.firebase, usuario.uid)
  } catch (fallo) {
    await servicio.cerrarSesion()
    error.value = mensajeDeError(fallo)
  }
}

async function ingresar() {
  error.value = ''
  aviso.value = ''
  enviando.value = true
  let servicio: ReturnType<typeof cliente> | null = null
  try {
    servicio = cliente()
    const resultado = await servicio.iniciarSesion(correo.value.trim(), contrasena.value)
    cuenta.value = await cargarCuentaEmprendedora(servicio.firebase, resultado.user.uid)
    contrasena.value = ''
    seccion.value = 'inicio'
  } catch (fallo) {
    if (servicio) {
      try { await servicio.cerrarSesion() } catch {}
    }
    error.value = mensajeDeError(fallo)
  } finally {
    enviando.value = false
  }
}

async function recuperarContrasena() {
  error.value = ''
  aviso.value = ''
  if (!correo.value.trim()) {
    error.value = 'Escribe tu correo para enviarte un enlace de recuperación.'
    return
  }
  enviandoRecuperacion.value = true
  try {
    const servicio = cliente()
    servicio.firebase.auth.languageCode = 'es'
    await sendPasswordResetEmail(servicio.firebase.auth, correo.value.trim())
    aviso.value = 'Si existe una cuenta con ese correo, recibirás un enlace para restablecer tu contraseña.'
  } catch (fallo) {
    const codigo = typeof fallo === 'object' && fallo !== null && 'code' in fallo ? String((fallo as { code?: unknown }).code ?? '') : ''
    error.value = codigo.includes('invalid-email') ? 'Ingresa un correo electrónico válido.' : 'No se pudo enviar el enlace. Revisa el correo e inténtalo nuevamente.'
  } finally {
    enviandoRecuperacion.value = false
  }
}

async function cerrarSesion() {
  await cliente().cerrarSesion()
  cuenta.value = null
  contrasena.value = ''
  seccion.value = 'inicio'
  aviso.value = ''
  error.value = ''
}

function irA(destino: Seccion) {
  seccion.value = destino
  filtro.value = 'todos'
  aviso.value = ''
  error.value = ''
}

function precioBonito(precio: number | null) {
  if (precio === null) return 'Precio por definir'
  return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(precio)
}

function stockBonito(producto: ProductoEmprendedoraWeb) {
  if (!producto.manejaStock) return producto.modalidad === 'a_pedido' ? 'Por encargo' : 'Sin control de stock'
  if (producto.stock === null) return 'Stock por variante'
  return producto.stock + (producto.stock === 1 ? ' unidad' : ' unidades')
}

function estadoBonito(estado: ProductoEmprendedoraWeb['estado']) {
  if (estado === 'publicado') return 'Publicado'
  if (estado === 'archivado') return 'Retirado'
  return 'Borrador'
}

onMounted(async () => {
  fecha.value = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
  try {
    await revisarSesion()
  } catch (fallo) {
    error.value = mensajeDeError(fallo)
  } finally {
    revisando.value = false
  }
})
</script>

<template>
  <div class="envoltura">
    <aside class="barra-lateral">
      <NuxtLink class="marca" to="/comprador" aria-label="Ir al catálogo de Rincón Amancay">
        <img src="/logo-amancay.png" alt="" />
        <span><small>Rincón</small><strong>Amancay</strong></span>
      </NuxtLink>
      <div class="etiqueta-lateral">ESPACIO EMPRENDEDORA</div>
      <nav class="menu-lateral" aria-label="Navegación emprendedora">
        <template v-if="cuenta">
          <button v-for="item in secciones" :key="item.id" class="opcion-menu" :class="{ seleccionada: seccion === item.id }" :aria-current="seccion === item.id ? 'page' : undefined" type="button" @click="irA(item.id)">
            <span class="icono-menu" aria-hidden="true">{{ item.icono }}</span>{{ item.nombre }}
            <span v-if="item.id === 'productos' && borradores" class="contador-menu">{{ borradores }}</span>
          </button>
        </template>
        <button v-else class="opcion-menu seleccionada" type="button" aria-current="page"><span class="icono-menu" aria-hidden="true">↪</span>Iniciar sesión</button>
      </nav>
      <div v-if="cuenta" class="perfil-lateral">
        <span class="avatar">{{ nombrePersona().slice(0, 1).toUpperCase() }}</span>
        <span class="datos-perfil"><strong>{{ cuenta.tienda.nombre || 'Mi emprendimiento' }}</strong><small>Sesión activa</small></span>
        <button class="salir-lateral" type="button" aria-label="Cerrar sesión" @click="cerrarSesion">↗</button>
      </div>
      <div v-else class="ayuda-lateral"><span aria-hidden="true">✿</span><p>Un espacio para que tu emprendimiento siga creciendo.</p></div>
    </aside>

    <main class="principal">
      <header class="barra-superior">
        <div><span class="punto-marca"></span> RINCÓN AMANCAY <span class="separador">/</span> EMPRENDEDORA</div>
        <NuxtLink to="/comprador" class="volver-catalogo">Volver al catálogo <span aria-hidden="true">↗</span></NuxtLink>
      </header>

      <div v-if="revisando" class="estado-carga" role="status"><span class="rueda"></span><p>Preparando tu espacio…</p></div>

      <section v-else-if="!cuenta" class="vista-ingreso">
        <div class="presentacion-ingreso">
          <span class="sobre-titulo">HECHO CON CARIÑO, CERCA DE TI</span>
          <h1>Tu tienda,<br /><em>paso a paso.</em></h1>
          <p>Un lugar para cuidar tus productos, revisar tu tienda y mostrar el trabajo que haces con tus manos.</p>
          <div class="detalle-ingreso"><span class="detalle-icono" aria-hidden="true">✿</span><span>Tu historia emprendedora comienza aquí.</span></div>
          <span class="flor-decorativa" aria-hidden="true">✿</span>
        </div>
        <div class="tarjeta-ingreso">
          <img class="logo-ingreso" src="/logo-amancay.png" alt="" />
          <span class="sobre-titulo">ACCESO EMPRENDEDORA</span>
          <h2>Qué bueno tenerte aquí</h2>
          <p>Ingresa con las credenciales que te entregó la administración.</p>
          <form class="formulario-ingreso" @submit.prevent="ingresar">
            <label for="correo-emprendedora">Correo electrónico</label>
            <input id="correo-emprendedora" v-model="correo" type="email" autocomplete="username" placeholder="tu@correo.cl" required :disabled="enviando || enviandoRecuperacion" />
            <label for="clave-emprendedora">Contraseña</label>
            <input id="clave-emprendedora" v-model="contrasena" type="password" autocomplete="current-password" placeholder="Tu contraseña" required :disabled="enviando || enviandoRecuperacion" />
            <p v-if="error" class="mensaje mensaje-error" role="alert">{{ error }}</p>
            <p v-if="aviso" class="mensaje mensaje-aviso" role="status">{{ aviso }}</p>
            <button class="boton-recuperacion" type="button" :disabled="enviando || enviandoRecuperacion" @click="recuperarContrasena">{{ enviandoRecuperacion ? 'Enviando enlace…' : '¿Olvidaste tu contraseña?' }}</button>
            <button class="boton-principal" type="submit" :disabled="enviando || enviandoRecuperacion">
              <span v-if="enviando" class="rueda rueda-clara"></span>{{ enviando ? 'Ingresando…' : 'Ingresar a mi espacio' }}
            </button>
          </form>
          <NuxtLink class="enlace-invitacion" to="/invitacion-emprendedora">¿Tienes una invitación para unirte?</NuxtLink>
        </div>
      </section>

      <section v-else class="contenido-panel">
        <div class="cabecera-panel">
          <div>
            <span class="sobre-titulo">HOLA, {{ nombrePersona().toUpperCase() }}</span>
            <h1>{{ seccion === 'inicio' ? 'Tu tienda, paso a paso' : seccion === 'productos' ? 'Mis productos' : seccion === 'agregar' ? 'Un nuevo producto' : 'Tu espacio' }}</h1>
            <p v-if="seccion === 'inicio'">Administra tus productos y revisa cómo va tu tienda.</p>
            <p v-else-if="seccion === 'productos'">Revisa el estado de los productos de tu tienda.</p>
            <p v-else-if="seccion === 'agregar'">Prepara una nueva pieza para compartir con tus clientes.</p>
            <p v-else>Tu cuenta y los datos principales de tu emprendimiento.</p>
          </div>
          <span v-if="fecha" class="fecha-hoy">{{ fecha }}</span>
        </div>

        <template v-if="seccion === 'inicio'">
          <div class="tarjeta-accion">
            <div><span class="sobre-titulo claro">UN PASO A LA VEZ</span><h2>¿Qué quieres hacer hoy?</h2><p>Elige una acción. Te acompañamos en cada paso.</p></div>
            <button class="boton-blanco" type="button" @click="irA('agregar')"><span aria-hidden="true">＋</span> Agregar producto</button>
            <span class="flor-tarjeta" aria-hidden="true">✿</span>
          </div>
          <button class="tarjeta-resumen" type="button" @click="irA('productos')">
            <span class="icono-resumen" aria-hidden="true">▦</span>
            <span class="texto-resumen"><strong>Mis productos</strong><small>{{ publicados }} publicados · {{ borradores }} borradores</small></span>
            <span class="flecha-resumen" aria-hidden="true">›</span>
          </button>
          <section class="tarjeta-tienda">
            <div class="cabecera-tienda"><div><span class="sobre-titulo">MI TIENDA</span><h2>{{ cuenta.tienda.nombre || 'Mi emprendimiento' }}</h2></div>
              <span class="estado-tienda" :class="{ publicada: cuenta.tienda.estadoPublicacion === 'publicado' }"><span></span>{{ cuenta.tienda.estadoPublicacion === 'publicado' ? 'Publicada' : 'En borrador' }}</span>
            </div>
            <p>{{ cuenta.tienda.descripcion || 'Agrega una descripción a tu tienda para contar qué haces.' }}</p>
            <div class="separador-tienda"></div><div class="progreso-cabecera"><strong>Tu progreso</strong><span>{{ publicados > 0 ? '¡Vas muy bien!' : 'Comienza cuando quieras' }}</span></div>
            <div class="pasos-progreso">
              <div class="paso-progreso"><span class="check-progreso" :class="{ completado: cuenta.tienda.nombre.trim().length > 0 }">{{ cuenta.tienda.nombre.trim().length > 0 ? '✓' : '1' }}</span><span><strong>Prepara tu tienda</strong><small>{{ cuenta.tienda.nombre.trim() ? 'Nombre listo' : 'Completa el nombre de tu tienda' }}</small></span></div>
              <div class="paso-progreso"><span class="check-progreso" :class="{ completado: cuenta.productos.length > 0 }">{{ cuenta.productos.length > 0 ? '✓' : '2' }}</span><span><strong>Agrega tu primer producto</strong><small>{{ cuenta.productos.length > 0 ? cuenta.productos.length + ' productos en tu lista' : 'Cada creación comienza con una idea' }}</small></span></div>
              <div class="paso-progreso"><span class="check-progreso" :class="{ completado: publicados > 0 }">{{ publicados > 0 ? '✓' : '3' }}</span><span><strong>Publica tu catálogo</strong><small>{{ publicados > 0 ? 'Ya tienes productos visibles' : 'Publica productos para mostrarlos' }}</small></span></div>
            </div>
          </section>
        </template>

        <template v-else-if="seccion === 'productos'">
          <div class="encabezado-seccion"><div><span class="sobre-titulo">MI CATÁLOGO</span><h2>Productos de {{ cuenta.tienda.nombre || 'mi tienda' }}</h2></div><button class="boton-vino compacto" type="button" @click="irA('agregar')">＋ Agregar</button></div>
          <div class="filtros-productos" role="group" aria-label="Filtrar productos">
            <button type="button" :class="{ activo: filtro === 'todos' }" @click="filtro = 'todos'">Todos · {{ cuenta.productos.length }}</button>
            <button type="button" :class="{ activo: filtro === 'publicado' }" @click="filtro = 'publicado'">Publicados · {{ publicados }}</button>
            <button type="button" :class="{ activo: filtro === 'borrador' }" @click="filtro = 'borrador'">Borradores · {{ borradores }}</button>
          </div>
          <div v-if="productosFiltrados.length" class="lista-productos">
            <article v-for="producto in productosFiltrados" :key="producto.id" class="tarjeta-producto">
              <div class="imagen-producto" aria-hidden="true">{{ producto.nombre.trim().charAt(0).toUpperCase() || '✿' }}</div>
              <div class="datos-producto"><strong>{{ producto.nombre || 'Producto sin nombre' }}</strong><p>{{ producto.descripcion || 'Sin descripción' }}</p>
                <div class="metadatos-producto"><span>{{ precioBonito(producto.precioBase) }}</span><span>·</span><span>{{ stockBonito(producto) }}</span></div>
              </div>
              <span class="estado-producto" :class="producto.estado">{{ estadoBonito(producto.estado) }}</span>
            </article>
          </div>
          <div v-else class="vacio-productos"><span aria-hidden="true">✿</span><h3>{{ filtro === 'todos' ? 'Todavía no hay productos' : 'No hay productos en esta lista' }}</h3>
            <p>{{ filtro === 'todos' ? 'Cuando agregues tus productos, aparecerán aquí para que puedas revisar su estado.' : 'Prueba con otro filtro para ver tus productos.' }}</p>
            <button v-if="filtro === 'todos'" class="boton-vino" type="button" @click="irA('agregar')">＋ Agregar producto</button>
          </div>
        </template>

        <template v-else-if="seccion === 'agregar'">
          <section class="tarjeta-agregar"><div class="icono-agregar" aria-hidden="true">✿</div><span class="sobre-titulo">NUEVA CREACIÓN</span>
            <h2>Tu próximo producto empieza con una idea.</h2>
            <p>En la app emprendedora puedes agregar fotos, completar los detalles y preparar variantes antes de publicar.</p>
            <div class="pasos-agregar">
              <div><span>01</span><strong>Cuenta qué haces</strong><small>Nombre, categoría y detalles</small></div>
              <div><span>02</span><strong>Agrega una imagen</strong><small>Una foto ayuda a mostrar tu trabajo</small></div>
              <div><span>03</span><strong>Revisa y publica</strong><small>Precios, stock y visibilidad</small></div>
            </div>
            <div class="nota-web"><span aria-hidden="true">i</span><p>Por ahora, la creación y edición de productos se realiza desde la app Android. Esta página muestra tu cuenta y catálogo.</p></div>
            <button class="boton-vino" type="button" @click="irA('productos')">Volver a mis productos</button>
          </section>
        </template>

        <template v-else>
          <section class="tarjeta-tienda tarjeta-cuenta"><span class="sobre-titulo">MI CUENTA</span><h2>{{ cuenta.nombreMostrar || cuenta.correo }}</h2><p>{{ cuenta.correo }}</p>
            <div class="separador-tienda"></div><span class="sobre-titulo">MI TIENDA</span><h2>{{ cuenta.tienda.nombre || 'Mi emprendimiento' }}</h2><p>{{ cuenta.tienda.descripcion || 'Aún no hay una descripción para mostrar.' }}</p>
            <div class="estado-cuenta"><span class="estado-tienda" :class="{ publicada: cuenta.tienda.estadoPublicacion === 'publicado' }"><span></span>{{ cuenta.tienda.estadoPublicacion === 'publicado' ? 'Visible para compradores' : 'Aún no aparece en el catálogo comprador' }}</span></div>
            <button class="boton-secundario" type="button" @click="cerrarSesion">Cerrar sesión</button>
          </section>
        </template>
      </section>

      <nav v-if="cuenta" class="navegacion-movil" aria-label="Navegación emprendedora">
        <button v-for="item in secciones" :key="item.id" type="button" :class="{ seleccionada: seccion === item.id }" :aria-current="seccion === item.id ? 'page' : undefined" @click="irA(item.id)"><span aria-hidden="true">{{ item.icono }}</span><small>{{ item.nombre }}</small></button>
      </nav>
    </main>
  </div>
</template>

<style scoped>
.envoltura{--vino:#75264f;--oscuro:#4a0e2e;--texto:#3e1f31;--suave:#816976;--crema:#fff8ef;--rosa:#f7e9e8;--borde:#e8d9d0;--verde:#2f6b3f;min-height:100vh;display:grid;grid-template-columns:250px minmax(0,1fr);background:var(--crema);color:var(--texto);font-family:'Plus Jakarta Sans',sans-serif}
.envoltura :global(*){box-sizing:border-box}
.barra-lateral{position:sticky;top:0;height:100vh;display:flex;flex-direction:column;padding:28px 18px 18px;background:#fff;border-right:1px solid var(--borde);z-index:4}
.marca{display:flex;align-items:center;gap:10px;color:inherit;text-decoration:none}.marca img{width:46px;height:46px;object-fit:contain;padding:5px;border-radius:50%;background:var(--crema);border:1px solid var(--borde)}.marca span{display:grid;line-height:1.12}.marca small{color:var(--suave);font-size:11px}.marca strong{color:var(--vino);font:400 22px 'DM Serif Display',Georgia,serif}
.etiqueta-lateral{margin:38px 10px 12px;color:#a08d98;font-size:10px;letter-spacing:.12em;font-weight:700}.menu-lateral{display:grid;gap:7px}
.opcion-menu{display:flex;align-items:center;gap:10px;width:100%;min-height:45px;padding:7px 11px;border:0;border-radius:14px;background:transparent;color:var(--suave);text-align:left;font:600 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.opcion-menu:hover,.opcion-menu:focus-visible{background:var(--crema);color:var(--vino);outline-color:var(--vino)}.opcion-menu.seleccionada{background:var(--rosa);color:var(--vino)}
.icono-menu{display:grid;place-items:center;width:30px;height:30px;border-radius:10px;background:var(--crema);color:var(--vino);font-size:18px;line-height:1}.seleccionada .icono-menu{background:#fff}.contador-menu{display:grid;place-items:center;width:21px;height:21px;margin-left:auto;border-radius:50%;background:var(--vino);color:#fff;font-size:10px}
.perfil-lateral{display:flex;align-items:center;gap:9px;margin-top:auto;padding:12px 8px;border-top:1px solid var(--borde)}.avatar{display:grid;place-items:center;flex:0 0 36px;height:36px;border-radius:50%;background:var(--rosa);color:var(--vino);font-weight:700}.datos-perfil{display:grid;gap:3px;min-width:0;flex:1}.datos-perfil strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.datos-perfil small{color:var(--suave);font-size:9px}.salir-lateral{border:0;background:transparent;color:var(--suave);font-size:18px;cursor:pointer}
.ayuda-lateral{margin-top:auto;padding:15px;border-radius:17px;background:var(--crema);color:var(--suave);text-align:center}.ayuda-lateral span{color:var(--vino);font-size:22px}.ayuda-lateral p{margin:5px 0 0;font-size:10px;line-height:1.6}
.principal{min-width:0;min-height:100vh}.barra-superior{height:68px;display:flex;align-items:center;justify-content:space-between;gap:14px;padding:0 clamp(20px,5vw,64px);background:rgba(255,255,255,.78);border-bottom:1px solid var(--borde);color:var(--suave);font-size:9px;font-weight:700;letter-spacing:.08em}.punto-marca{display:inline-block;width:8px;height:8px;margin-right:6px;border-radius:50%;background:var(--vino)}.separador{padding:0 8px;color:#c0aeb8}.volver-catalogo{color:var(--vino);font-size:10px;text-decoration:none;letter-spacing:0}.volver-catalogo:hover{text-decoration:underline}
.vista-ingreso{display:grid;grid-template-columns:minmax(0,1fr) minmax(320px,420px);align-items:center;gap:clamp(28px,6vw,80px);width:min(1080px,calc(100% - 56px));min-height:calc(100vh - 68px);margin:auto;padding:40px 0}
.presentacion-ingreso{position:relative;max-width:500px}.sobre-titulo{display:inline-block;color:var(--vino);font-size:9px;line-height:1.5;letter-spacing:.13em;font-weight:700}.presentacion-ingreso h1{margin:13px 0;color:var(--texto);font:400 clamp(48px,6vw,72px)/.98 'DM Serif Display',Georgia,serif;letter-spacing:-.03em}.presentacion-ingreso h1 em{color:var(--vino);font-style:normal}.presentacion-ingreso>p{max-width:420px;margin:0;color:var(--suave);font-size:14px;line-height:1.8}
.detalle-ingreso{display:flex;align-items:center;gap:10px;margin-top:26px;color:var(--vino);font-size:11px;font-weight:600}.detalle-icono{display:grid;place-items:center;width:32px;height:32px;border-radius:11px;background:var(--rosa);font-size:17px}.flor-decorativa{position:absolute;right:12px;bottom:-65px;color:#f1dcd9;font-size:145px;opacity:.55;pointer-events:none}
.tarjeta-ingreso{position:relative;z-index:1;padding:30px;border:1px solid var(--borde);border-radius:25px;background:rgba(255,255,255,.95);box-shadow:0 18px 60px rgba(62,31,49,.07)}.logo-ingreso{display:block;width:49px;height:49px;object-fit:contain;margin-bottom:17px;padding:5px;border:1px solid var(--borde);border-radius:50%;background:var(--crema)}.tarjeta-ingreso h2{margin:7px 0;color:var(--texto);font:400 29px/1.15 'DM Serif Display',Georgia,serif}.tarjeta-ingreso>p{margin:0;color:var(--suave);font-size:11px;line-height:1.7}
.formulario-ingreso{display:grid;gap:9px;margin-top:20px}.formulario-ingreso label{margin-top:4px;color:var(--texto);font-size:10px;font-weight:700}.formulario-ingreso input{width:100%;height:45px;padding:0 13px;border:1px solid var(--borde);border-radius:12px;outline:none;background:#fff;color:var(--texto);font:11px 'Plus Jakarta Sans',sans-serif}.formulario-ingreso input:focus{border-color:var(--vino);box-shadow:0 0 0 3px rgba(117,38,79,.1)}.formulario-ingreso input::placeholder{color:#b4a5ad}
.mensaje{margin:3px 0;padding:10px 11px;border-radius:11px;font-size:10px;line-height:1.5}.mensaje-error{background:#fff0ee;color:#9d3429}.mensaje-aviso{background:#eaf3e8;color:#356243}.boton-recuperacion{justify-self:end;padding:5px 0;border:0;background:transparent;color:var(--vino);font:600 10px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-principal,.boton-vino,.boton-secundario{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:0 18px;border:0;border-radius:13px;background:var(--vino);color:#fff;font:700 11px 'Plus Jakarta Sans',sans-serif;text-decoration:none;cursor:pointer}.boton-principal{width:100%;margin-top:4px}.boton-principal:hover,.boton-vino:hover{background:var(--oscuro)}.boton-principal:disabled{opacity:.65;cursor:wait}.enlace-invitacion{display:block;margin-top:15px;color:var(--suave);font-size:9px;text-align:center;text-decoration-color:#cbbbc3}
.estado-carga{display:grid;justify-items:center;align-content:center;min-height:calc(100vh - 68px);color:var(--suave);font-size:12px}.rueda{width:18px;height:18px;border:2px solid rgba(117,38,79,.2);border-top-color:var(--vino);border-radius:50%;animation:girar .7s linear infinite}.rueda-clara{border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes girar{to{transform:rotate(360deg)}}
.contenido-panel{width:min(1010px,calc(100% - 56px));margin:auto;padding:38px 0 55px}.cabecera-panel{display:flex;align-items:flex-end;justify-content:space-between;gap:18px;margin-bottom:22px}.cabecera-panel h1{margin:6px 0 5px;color:var(--texto);font:400 clamp(32px,4vw,44px)/1.1 'DM Serif Display',Georgia,serif}.cabecera-panel p{margin:0;color:var(--suave);font-size:11px;line-height:1.6}.fecha-hoy{padding-bottom:4px;color:#e96869;font-size:9px;font-weight:700;text-transform:uppercase}
.tarjeta-accion{position:relative;overflow:hidden;display:flex;align-items:center;justify-content:space-between;gap:20px;min-height:185px;margin-bottom:14px;padding:27px 30px;border-radius:23px;background:var(--vino);color:#fff}.tarjeta-accion>div{position:relative;z-index:1}.sobre-titulo.claro{color:#f4d7e5}.tarjeta-accion h2{margin:8px 0 5px;font:400 clamp(25px,3vw,32px)/1.1 'DM Serif Display',Georgia,serif}.tarjeta-accion p{margin:0;color:rgba(255,255,255,.8);font-size:11px}.boton-blanco{position:relative;z-index:1;display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:0 18px;border:0;border-radius:13px;background:#fff;color:var(--texto);font:700 11px 'Plus Jakarta Sans',sans-serif;white-space:nowrap;cursor:pointer}.boton-blanco span{color:var(--vino);font-size:18px}.flor-tarjeta{position:absolute;right:20%;top:-50px;color:rgba(255,255,255,.08);font-size:230px;pointer-events:none}
.tarjeta-resumen,.tarjeta-tienda,.tarjeta-agregar,.vacio-productos{width:100%;border:1px solid var(--borde);border-radius:20px;background:#fff}.tarjeta-resumen{display:flex;align-items:center;gap:14px;min-height:80px;margin-bottom:14px;padding:14px 18px;text-align:left;cursor:pointer}.icono-resumen{display:grid;place-items:center;flex:0 0 48px;height:48px;border-radius:14px;background:var(--crema);color:var(--vino);font-size:24px}.texto-resumen{display:grid;flex:1;gap:5px}.texto-resumen strong{color:var(--texto);font-size:12px}.texto-resumen small{color:var(--suave);font-size:10px}.flecha-resumen{color:var(--vino);font:30px 'DM Serif Display',Georgia,serif}
.tarjeta-tienda{padding:21px}.cabecera-tienda{display:flex;align-items:center;justify-content:space-between;gap:16px}.cabecera-tienda h2,.encabezado-seccion h2{margin:5px 0 0;color:var(--texto);font:400 24px/1.2 'DM Serif Display',Georgia,serif}.estado-tienda{display:inline-flex;align-items:center;gap:7px;padding:7px 10px;border-radius:999px;background:#fbefdf;color:#765921;font-size:9px;font-weight:700;white-space:nowrap}.estado-tienda>span{width:7px;height:7px;border-radius:50%;background:#c08c30}.estado-tienda.publicada{background:#e4f0e6;color:var(--verde)}.estado-tienda.publicada>span{background:#50a266}
.tarjeta-tienda>p{margin:10px 0 0;color:var(--suave);font-size:10px;line-height:1.7}.separador-tienda{height:1px;margin:18px 0 14px;background:var(--borde)}.progreso-cabecera{display:flex;justify-content:space-between;gap:12px}.progreso-cabecera strong{color:var(--texto);font-size:11px}.progreso-cabecera>span{color:var(--suave);font-size:9px}.pasos-progreso{display:grid;gap:11px;margin-top:13px}.paso-progreso{display:flex;align-items:center;gap:10px}.check-progreso{display:grid;place-items:center;flex:0 0 26px;height:26px;border:1px solid var(--borde);border-radius:50%;color:#a58f9b;font-size:10px;font-weight:700}.check-progreso.completado{border-color:var(--vino);background:var(--vino);color:#fff}.paso-progreso>span:last-child{display:grid;gap:3px}.paso-progreso strong{color:var(--texto);font-size:9px}.paso-progreso small{color:var(--suave);font-size:9px}
.encabezado-seccion{display:flex;align-items:end;justify-content:space-between;gap:14px;margin-bottom:16px}.boton-vino.compacto{min-height:38px;padding:0 14px}.filtros-productos{display:flex;gap:8px;overflow-x:auto;margin-bottom:14px}.filtros-productos button{min-height:35px;padding:0 12px;border:1px solid var(--borde);border-radius:999px;background:white;color:var(--suave);font:600 9px 'Plus Jakarta Sans',sans-serif;white-space:nowrap;cursor:pointer}.filtros-productos button.activo{border-color:var(--vino);background:var(--vino);color:white}
.lista-productos{display:grid;gap:10px}.tarjeta-producto{display:flex;align-items:center;gap:13px;min-height:100px;padding:13px;border:1px solid var(--borde);border-radius:18px;background:white}.imagen-producto{display:grid;place-items:center;flex:0 0 67px;height:67px;border-radius:16px;background:linear-gradient(140deg,#f8e8e6,#fff4df);color:var(--vino);font:400 30px 'DM Serif Display',Georgia,serif}.datos-producto{display:grid;gap:5px;flex:1;min-width:0}.datos-producto>strong{overflow:hidden;color:var(--texto);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.datos-producto p{overflow:hidden;margin:0;color:var(--suave);font-size:9px;text-overflow:ellipsis;white-space:nowrap}.metadatos-producto{display:flex;flex-wrap:wrap;gap:6px;color:var(--suave);font-size:9px}.metadatos-producto span:first-child{color:var(--vino);font-weight:700}.estado-producto{padding:6px 8px;border-radius:999px;background:#fbefdf;color:#765921;font-size:8px;font-weight:700;white-space:nowrap}.estado-producto.publicado{background:#e4f0e6;color:var(--verde)}.estado-producto.archivado{background:#f0ebee;color:var(--suave)}
.vacio-productos{display:grid;justify-items:center;padding:36px 20px;text-align:center}.vacio-productos>span{color:var(--vino);font-size:30px}.vacio-productos h3{margin:9px 0 5px;color:var(--texto);font:400 22px 'DM Serif Display',Georgia,serif}.vacio-productos p{max-width:390px;margin:0 0 15px;color:var(--suave);font-size:10px;line-height:1.7}
.tarjeta-agregar{display:grid;justify-items:center;padding:32px;text-align:center}.icono-agregar{display:grid;place-items:center;width:55px;height:55px;margin-bottom:15px;border-radius:17px;background:var(--rosa);color:var(--vino);font-size:27px}.tarjeta-agregar h2{max-width:570px;margin:8px 0;color:var(--texto);font:400 32px/1.15 'DM Serif Display',Georgia,serif}.tarjeta-agregar>p{max-width:550px;margin:0;color:var(--suave);font-size:11px;line-height:1.7}.pasos-agregar{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;width:100%;margin:23px 0;text-align:left}.pasos-agregar>div{display:grid;gap:6px;padding:14px;border:1px solid var(--borde);border-radius:14px;background:#fffdfa}.pasos-agregar span{color:var(--vino);font-size:9px;font-weight:700}.pasos-agregar strong{color:var(--texto);font-size:9px}.pasos-agregar small{color:var(--suave);font-size:8px;line-height:1.5}.nota-web{display:flex;align-items:center;gap:9px;width:100%;max-width:630px;margin-bottom:17px;padding:11px 13px;border-radius:12px;background:var(--crema);text-align:left}.nota-web>span{display:grid;place-items:center;flex:0 0 20px;height:20px;border:1px solid #cdb9c5;border-radius:50%;color:var(--vino);font-size:10px;font-weight:700}.nota-web p{margin:0;color:var(--suave);font-size:9px;line-height:1.6}
.tarjeta-cuenta{max-width:720px}.tarjeta-cuenta>h2{margin:6px 0;color:var(--texto);font:400 25px 'DM Serif Display',Georgia,serif}.estado-cuenta{margin:16px 0}.boton-secundario{border:1px solid var(--borde);background:white;color:var(--vino)}.navegacion-movil{display:none}button:focus-visible,a:focus-visible{outline:2px solid var(--vino);outline-offset:3px}
@media(max-width:940px){.envoltura{grid-template-columns:215px minmax(0,1fr)}.barra-lateral{padding-right:13px;padding-left:13px}.vista-ingreso{grid-template-columns:minmax(0,1fr) minmax(300px,380px);gap:24px;width:min(100% - 40px,800px)}.presentacion-ingreso h1{font-size:55px}.contenido-panel{width:calc(100% - 40px)}}
@media(max-width:740px){.envoltura{display:block}.barra-lateral{position:sticky;height:auto;padding:11px 14px 7px;border-right:0;border-bottom:1px solid var(--borde);box-shadow:0 5px 18px rgba(62,31,49,.04)}.marca img{width:38px;height:38px}.marca strong{font-size:19px}.etiqueta-lateral,.perfil-lateral,.ayuda-lateral{display:none}.menu-lateral{display:flex;gap:6px;margin-top:8px;overflow-x:auto}.opcion-menu{width:auto;min-height:37px;flex:0 0 auto;gap:7px;padding:5px 9px;font-size:10px;white-space:nowrap}.icono-menu{width:24px;height:24px;font-size:15px}.principal{min-height:calc(100vh - 94px)}.barra-superior{height:48px;padding:0 15px;font-size:8px}.volver-catalogo{font-size:9px}.vista-ingreso{grid-template-columns:1fr;gap:14px;width:min(100% - 30px,500px);min-height:auto;padding:24px 0 45px}.presentacion-ingreso{padding:6px 2px 0}.presentacion-ingreso h1{margin:8px 0;font-size:44px}.presentacion-ingreso>p{max-width:420px;font-size:11px;line-height:1.65}.detalle-ingreso{margin-top:11px;font-size:9px}.detalle-icono{width:27px;height:27px}.flor-decorativa{right:8px;bottom:-30px;font-size:85px}.tarjeta-ingreso{padding:21px;border-radius:20px}.logo-ingreso{display:none}.tarjeta-ingreso h2{font-size:26px}.formulario-ingreso{margin-top:15px}.contenido-panel{width:calc(100% - 30px);padding:23px 0 98px}.cabecera-panel{align-items:flex-start;flex-direction:column;gap:6px;margin-bottom:16px}.cabecera-panel h1{font-size:34px}.fecha-hoy{order:-1}.tarjeta-accion{align-items:flex-start;flex-direction:column;min-height:195px;gap:15px;padding:21px}.tarjeta-accion h2{font-size:27px}.boton-blanco{min-height:41px}.flor-tarjeta{right:-12px;top:5px;font-size:145px}.tarjeta-tienda{padding:17px}.cabecera-tienda{align-items:flex-start;flex-direction:column;gap:9px}.cabecera-tienda h2,.encabezado-seccion h2{font-size:21px}.progreso-cabecera{align-items:flex-start;flex-direction:column;gap:4px}.encabezado-seccion{align-items:flex-start}.tarjeta-producto{align-items:flex-start;flex-wrap:wrap;gap:10px}.imagen-producto{flex-basis:56px;height:56px;font-size:24px}.datos-producto{flex-basis:calc(100% - 70px);align-self:center}.estado-producto{margin-left:66px;margin-top:-2px}.tarjeta-agregar{padding:23px 16px}.tarjeta-agregar h2{font-size:27px}.pasos-agregar{grid-template-columns:1fr;margin:17px 0}.pasos-agregar>div{grid-template-columns:28px 1fr;align-items:center}.pasos-agregar small{grid-column:2;margin-top:-4px}.navegacion-movil{position:fixed;z-index:5;right:0;bottom:0;left:0;display:grid;grid-template-columns:repeat(4,1fr);padding:7px 6px calc(7px + env(safe-area-inset-bottom));border-top:1px solid var(--borde);background:rgba(255,255,255,.97);box-shadow:0 -5px 25px rgba(62,31,49,.06)}.navegacion-movil button{display:grid;justify-items:center;gap:3px;padding:2px;border:0;background:transparent;color:var(--suave);font:inherit;cursor:pointer}.navegacion-movil button>span{display:grid;place-items:center;width:30px;height:24px;border-radius:8px;font-size:18px}.navegacion-movil button.seleccionada{color:var(--vino);font-weight:700}.navegacion-movil button.seleccionada>span{background:var(--rosa)}.navegacion-movil small{font-size:8px}}
@media(max-width:400px){.barra-superior{padding:0 10px;letter-spacing:.03em}.volver-catalogo{font-size:8px}.vista-ingreso,.contenido-panel{width:calc(100% - 22px)}.presentacion-ingreso h1{font-size:40px}.tarjeta-ingreso{padding:18px}}
@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;animation-iteration-count:1!important;scroll-behavior:auto!important;transition-duration:.01ms!important}}
</style>