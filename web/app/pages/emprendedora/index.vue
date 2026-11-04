<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { sendPasswordResetEmail } from 'firebase/auth'
import '~/assets/amancay-tokens.css'
import { cargarCuentaEmprendedora, type ProductoEmprendedoraWeb } from '~/repositories/firebase/emprendedora'

type Seccion = 'inicio' | 'productos' | 'agregar' | 'mas'
type Filtro = 'todos' | 'publicado' | 'borrador' | 'archivado'
type TipoContacto = 'whatsapp' | 'correo' | 'web'
type ElementoCatalogo = { id: string; nombre: string }

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
const mostrarContrasena = ref(false)
const enviandoCambioContrasena = ref(false)
const error = ref('')
const aviso = ref('')
const seccion = ref<Seccion>('inicio')
const filtro = ref<Filtro>('todos')
const fecha = ref('')
const editandoTienda = ref(false)
const cargandoEdicionTienda = ref(false)
const guardandoTienda = ref(false)
const catalogosTienda = ref<{ sectores: ElementoCatalogo[]; tiposEmprendimiento: ElementoCatalogo[] } | null>(null)
const formularioTienda = ref({
  nombre: '',
  descripcion: '',
  tipoEmprendimientoId: '',
  sectorId: '',
  contactoTipo: 'whatsapp' as TipoContacto,
  contactoValor: '',
  mostrarContacto: false,
})
const edicionTiendaPendiente = ref<{ firma: string; operacionId: string } | null>(null)
const logoSeleccionado = ref<File | null>(null)
const logoArchivoId = ref('')
const logoVistaPrevia = ref('')
const logoInput = ref<HTMLInputElement | null>(null)

const { $catalogoPublico } = useNuxtApp()

const publicados = computed(() => cuenta.value?.productos.filter((producto) => producto.estado === 'publicado').length ?? 0)
const borradores = computed(() => cuenta.value?.productos.filter((producto) => producto.estado === 'borrador').length ?? 0)
const retirados = computed(() => cuenta.value?.productos.filter((producto) => producto.estado === 'archivado').length ?? 0)
const progresoTienda = computed(() => {
  const tienda = cuenta.value?.tienda
  return [
    { titulo: 'Nombre listo', detalle: 'Ponle un nombre a tu emprendimiento.', completo: Boolean(tienda?.nombre.trim()) },
    { titulo: 'Descripción agregada', detalle: 'Cuenta qué haces y qué te inspira.', completo: Boolean(tienda?.descripcion.trim()) },
    { titulo: 'Contacto visible', detalle: 'Activa un medio para que te encuentren.', completo: tienda?.contactoVisible === true },
    { titulo: 'Logo cargado', detalle: 'Agrega una imagen para destacar tu tienda.', completo: tienda?.tieneLogo === true },
  ]
})
const productosFiltrados = computed(() => {
  const productos = cuenta.value?.productos ?? []
  return filtro.value === 'todos' ? productos : productos.filter((producto) => producto.estado === filtro.value)
})
const sectoresTienda = computed(() => {
  const opciones = catalogosTienda.value?.sectores ?? []
  const actual = cuenta.value?.tienda.sectorId
  return actual && !opciones.some((opcion) => opcion.id === actual) ? [{ id: actual, nombre: actual }, ...opciones] : opciones
})
const tiposTienda = computed(() => {
  const opciones = catalogosTienda.value?.tiposEmprendimiento ?? []
  const actual = cuenta.value?.tienda.tipoEmprendimientoId
  return actual && !opciones.some((opcion) => opcion.id === actual) ? [{ id: actual, nombre: actual }, ...opciones] : opciones
})
const logoVisible = computed(() => {
  if (logoVistaPrevia.value) return logoVistaPrevia.value
  const ruta = cuenta.value?.tienda.logoPublico
  return ruta ? $catalogoPublico.resolverUrlImagen(ruta) ?? '/logo-amancay.png' : '/logo-amancay.png'
})
const tipoTiendaVisible = computed(() => tiposTienda.value.find((opcion) => opcion.id === formularioTienda.value.tipoEmprendimientoId)?.nombre || formularioTienda.value.tipoEmprendimientoId || 'Tu emprendimiento')
const sectorTiendaVisible = computed(() => sectoresTienda.value.find((opcion) => opcion.id === formularioTienda.value.sectorId)?.nombre || formularioTienda.value.sectorId || 'Tu sector')

function limpiarVistaPreviaLogo() {
  if (logoVistaPrevia.value.startsWith('blob:')) URL.revokeObjectURL(logoVistaPrevia.value)
  logoVistaPrevia.value = ''
}

onBeforeUnmount(limpiarVistaPreviaLogo)

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

async function cambiarContrasena() {
  if (!cuenta.value) return
  error.value = ''
  aviso.value = ''
  enviandoCambioContrasena.value = true
  try {
    const servicio = cliente()
    servicio.firebase.auth.languageCode = 'es'
    await sendPasswordResetEmail(servicio.firebase.auth, cuenta.value.correo)
    aviso.value = 'Si existe una cuenta con ese correo, recibirás un enlace para restablecer tu contraseña.'
  } catch {
    error.value = 'No se pudo enviar el enlace. Revisa el correo e inténtalo nuevamente.'
  } finally {
    enviandoCambioContrasena.value = false
  }
}

async function abrirEdicionTienda() {
  if (!cuenta.value) return
  error.value = ''
  aviso.value = ''
  editandoTienda.value = true
  cargandoEdicionTienda.value = true
  edicionTiendaPendiente.value = null
  logoSeleccionado.value = null
  logoArchivoId.value = ''
  limpiarVistaPreviaLogo()
  try {
    const servicio = cliente()
    const actualizada = await cargarCuentaEmprendedora(servicio.firebase, cuenta.value.uid)
    cuenta.value = actualizada
    formularioTienda.value = {
      nombre: actualizada.tienda.nombre,
      descripcion: actualizada.tienda.descripcion,
      tipoEmprendimientoId: actualizada.tienda.tipoEmprendimientoId,
      sectorId: actualizada.tienda.sectorId,
      contactoTipo: actualizada.tienda.contacto?.tipo ?? 'whatsapp',
      contactoValor: actualizada.tienda.contacto?.valor ?? '',
      mostrarContacto: actualizada.tienda.mostrarContacto,
    }
    catalogosTienda.value = await $catalogoPublico.listarCatalogosPublicos()
  } catch (fallo) {
    error.value = fallo instanceof Error ? fallo.message : 'No se pudo cargar la ficha de tu tienda.'
  } finally {
    cargandoEdicionTienda.value = false
  }
}

function cancelarEdicionTienda() {
  editandoTienda.value = false
  edicionTiendaPendiente.value = null
  logoSeleccionado.value = null
  logoArchivoId.value = ''
  limpiarVistaPreviaLogo()
  error.value = ''
  aviso.value = ''
}

function abrirSelectorLogo() {
  logoInput.value?.click()
}

function seleccionarLogo(evento: Event) {
  const entrada = evento.target as HTMLInputElement
  const archivo = entrada.files?.[0]
  if (!archivo) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(archivo.type)) {
    error.value = 'El logo debe ser una imagen PNG, JPEG o WebP.'
    entrada.value = ''
    return
  }
  if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) {
    error.value = 'El logo debe pesar hasta 5 MiB.'
    entrada.value = ''
    return
  }
  error.value = ''
  limpiarVistaPreviaLogo()
  logoSeleccionado.value = archivo
  logoArchivoId.value = crypto.randomUUID()
  logoVistaPrevia.value = URL.createObjectURL(archivo)
  entrada.value = ''
}

async function guardarTienda() {
  if (!cuenta.value) return
  error.value = ''
  aviso.value = ''
  const campos = {
    nombre: formularioTienda.value.nombre.trim(),
    descripcion: formularioTienda.value.descripcion.trim(),
    tipoEmprendimientoId: formularioTienda.value.tipoEmprendimientoId,
    sectorId: formularioTienda.value.sectorId,
    contacto: formularioTienda.value.contactoValor.trim()
      ? { tipo: formularioTienda.value.contactoTipo, valor: formularioTienda.value.contactoValor.trim() }
      : null,
    mostrarContacto: formularioTienda.value.mostrarContacto,
  }
  if (!campos.nombre || campos.nombre.length > 120) {
    error.value = 'Escribe un nombre para tu tienda de hasta 120 caracteres.'
    return
  }
  if (campos.descripcion.length > 2000) {
    error.value = 'La descripción puede tener hasta 2.000 caracteres.'
    return
  }
  if (!campos.tipoEmprendimientoId || !campos.sectorId) {
    error.value = 'Selecciona el tipo de emprendimiento y el sector.'
    return
  }
  if (campos.mostrarContacto && !campos.contacto) {
    error.value = 'Agrega un dato de contacto antes de mostrarlo a quienes visiten tu tienda.'
    return
  }

  const servicio = cliente() as ReturnType<typeof cliente> & {
    llamar?: (operacion: 'editarTiendaEmprendedora', solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>
    subirLogoTienda?: (tiendaId: string, archivo: Blob, archivoId: string) => Promise<string>
  }
  if (typeof servicio.llamar !== 'function') {
    error.value = 'La edición requiere conectar la web a la API autenticada (modo real-api).'
    return
  }
  if (logoSeleccionado.value && typeof servicio.subirLogoTienda !== 'function') {
    error.value = 'La carga del logo requiere conectar la web a la API autenticada (modo real-api).'
    return
  }

  const uid = cuenta.value.uid
  const tiendaId = cuenta.value.tienda.id
  guardandoTienda.value = true
  try {
    if (logoSeleccionado.value) {
      await servicio.subirLogoTienda!(tiendaId, logoSeleccionado.value, logoArchivoId.value)
      cuenta.value = await cargarCuentaEmprendedora(servicio.firebase, uid)
      logoSeleccionado.value = null
      logoArchivoId.value = ''
      if (logoInput.value) logoInput.value.value = ''
      if (cuenta.value.tienda.logoPublico) limpiarVistaPreviaLogo()
    }

    const datos = {
      tiendaId,
      versionEsperada: cuenta.value?.tienda.versionEsperada ?? '',
      ...campos,
    }
    if (!datos.versionEsperada) throw new Error('Esta ficha todavía no tiene una versión para guardar. Actualiza la página y vuelve a intentarlo.')
    const firma = JSON.stringify(datos)
    if (!edicionTiendaPendiente.value || edicionTiendaPendiente.value.firma !== firma) {
      edicionTiendaPendiente.value = { firma, operacionId: crypto.randomUUID() }
    }
    const pendiente = edicionTiendaPendiente.value
    const solicitud = { ...datos, operacionId: pendiente.operacionId }
    await servicio.llamar('editarTiendaEmprendedora', solicitud)
    cuenta.value = await cargarCuentaEmprendedora(servicio.firebase, uid)
    edicionTiendaPendiente.value = null
    editandoTienda.value = false
    logoSeleccionado.value = null
    logoArchivoId.value = ''
    if (logoInput.value) logoInput.value.value = ''
    aviso.value = 'Los datos de tu tienda se guardaron correctamente.'
  } catch (fallo) {
    const codigo = typeof fallo === 'object' && fallo !== null && 'code' in fallo ? String((fallo as { code?: unknown }).code ?? '') : ''
    if (codigo === 'conflicto') {
      try { cuenta.value = await cargarCuentaEmprendedora(servicio.firebase, uid) } catch {}
      edicionTiendaPendiente.value = null
      error.value = 'La ficha cambió desde otro dispositivo. Actualizamos la versión; revisa los datos antes de guardar otra vez.'
    } else {
      error.value = fallo instanceof Error ? fallo.message : 'No se pudieron guardar los datos de la tienda.'
    }
  } finally {
    guardandoTienda.value = false
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
        <div v-if="cuenta && editandoTienda" class="perfil-cabecera-editor"><span>{{ nombrePersona().slice(0, 2).toUpperCase() }}</span><div><strong>{{ nombrePersona() }}</strong><small>Mi tienda</small></div><b aria-hidden="true">⌄</b></div>
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
            <div class="campo-clave">
              <input id="clave-emprendedora" v-model="contrasena" :type="mostrarContrasena ? 'text' : 'password'" autocomplete="current-password" placeholder="Tu contraseña" required :disabled="enviando || enviandoRecuperacion" />
              <button class="mostrar-clave" type="button" :aria-label="mostrarContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="mostrarContrasena" :disabled="enviando || enviandoRecuperacion" @click="mostrarContrasena = !mostrarContrasena">{{ mostrarContrasena ? '◉' : '◎' }}</button>
            </div>
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

      <section v-else class="contenido-panel" :class="{ 'modo-edicion-tienda': seccion === 'mas' && editandoTienda }">
        <div v-if="!(seccion === 'mas' && editandoTienda)" class="cabecera-panel">
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
            <button type="button" :class="{ activo: filtro === 'archivado' }" @click="filtro = 'archivado'">Retirados · {{ retirados }}</button>
          </div>
          <div v-if="productosFiltrados.length" class="lista-productos">
            <article v-for="producto in productosFiltrados" :key="producto.id" class="tarjeta-producto">
              <div class="imagen-producto">
                <ImagenPublica v-if="producto.imagenPublica" :ruta="producto.imagenPublica" :alt="producto.nombre" ajuste="contain" :zoom="false" />
                <span v-else aria-hidden="true">{{ producto.nombre.trim().charAt(0).toUpperCase() || '✿' }}</span>
              </div>
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
          <section class="tarjeta-agregar"><div class="icono-agregar"><img src="/logo-amancay.png" alt="" /></div><span class="sobre-titulo">NUEVA CREACIÓN</span>
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

        <template v-else-if="editandoTienda">
          <section class="editor-ficha">
            <header class="titulo-editor-ficha">
              <span class="sobre-titulo">MI TIENDA</span>
              <h1>Editar ficha</h1>
              <p>Completa la información de tu tienda para que más personas te encuentren.</p>
            </header>
            <div class="rejilla-editor-ficha">
              <form id="formulario-edicion-tienda" class="formulario-ficha-tienda" @submit.prevent="guardarTienda">
                <section class="paso-edicion-tienda">
                  <header class="cabecera-paso-tienda"><span class="numero-paso-tienda">1</span><div><h2>Información de la tienda</h2><p>Cuéntanos sobre tu emprendimiento.</p></div></header>
                  <div class="campos-ficha-tienda">
                    <label class="campo-ficha-tienda">Nombre de la tienda
                      <input v-model="formularioTienda.nombre" type="text" maxlength="120" autocomplete="organization" required :disabled="cargandoEdicionTienda || guardandoTienda" />
                    </label>
                    <label class="campo-ficha-tienda campo-descripcion-tienda">Descripción
                      <textarea v-model="formularioTienda.descripcion" maxlength="2000" rows="3" placeholder="Cuenta qué haces y qué te inspira." :disabled="cargandoEdicionTienda || guardandoTienda"></textarea>
                      <small>{{ formularioTienda.descripcion.length }} / 2.000 caracteres</small>
                    </label>
                    <label class="campo-ficha-tienda">Tipo de emprendimiento
                      <select v-model="formularioTienda.tipoEmprendimientoId" required :disabled="cargandoEdicionTienda || guardandoTienda">
                        <option value="" disabled>Selecciona una opción</option>
                        <option v-for="opcion in tiposTienda" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
                      </select>
                    </label>
                    <label class="campo-ficha-tienda">Sector
                      <select v-model="formularioTienda.sectorId" required :disabled="cargandoEdicionTienda || guardandoTienda">
                        <option value="" disabled>Selecciona una opción</option>
                        <option v-for="opcion in sectoresTienda" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
                      </select>
                    </label>
                  </div>
                </section>

                <section class="paso-edicion-tienda">
                  <header class="cabecera-paso-tienda"><span class="numero-paso-tienda">2</span><div><h2>Contacto público</h2><p>Elige cómo pueden contactarte tus clientes.</p></div></header>
                  <div class="campos-ficha-tienda">
                    <label class="campo-ficha-tienda">Medio de contacto
                      <select v-model="formularioTienda.contactoTipo" :disabled="cargandoEdicionTienda || guardandoTienda">
                        <option value="whatsapp">WhatsApp</option>
                        <option value="correo">Correo electrónico</option>
                        <option value="web">Sitio web (HTTPS)</option>
                      </select>
                    </label>
                    <label class="campo-ficha-tienda">Dato de contacto
                      <input v-model="formularioTienda.contactoValor" :type="formularioTienda.contactoTipo === 'correo' ? 'email' : formularioTienda.contactoTipo === 'web' ? 'url' : 'tel'" :placeholder="formularioTienda.contactoTipo === 'correo' ? 'hola@mitienda.cl' : formularioTienda.contactoTipo === 'web' ? 'https://mitienda.cl' : '+56 9 1234 5678'" :disabled="cargandoEdicionTienda || guardandoTienda" />
                    </label>
                    <label class="interruptor-contacto-tienda">
                      <input v-model="formularioTienda.mostrarContacto" type="checkbox" :disabled="cargandoEdicionTienda || guardandoTienda" />
                      <span class="pista-interruptor"></span>
                      <span><strong>Mostrar este contacto a quienes visiten mi tienda</strong><small>Tu contacto será visible en la tienda pública.</small></span>
                    </label>
                  </div>
                </section>

                <section class="paso-edicion-tienda paso-identidad-tienda">
                  <header class="cabecera-paso-tienda"><span class="numero-paso-tienda">3</span><div><h2>Identidad visual</h2><p>Agrega un logo para que tu tienda destaque.</p></div></header>
                  <div class="selector-logo-tienda">
                    <div class="marco-logo-tienda"><img :src="logoVisible" :alt="'Logo de ' + (formularioTienda.nombre || 'mi tienda')" /></div>
                    <div class="datos-logo-tienda">
                      <input ref="logoInput" class="entrada-logo-oculta" type="file" accept="image/png,image/jpeg,image/webp" @change="seleccionarLogo" />
                      <button class="boton-secundario boton-cambiar-logo" type="button" :disabled="cargandoEdicionTienda || guardandoTienda" @click="abrirSelectorLogo"><span aria-hidden="true">▧</span>{{ logoSeleccionado ? 'Cambiar selección' : 'Cambiar logo' }}</button>
                      <small>Usa una imagen cuadrada y clara. PNG, JPEG o WebP de hasta 5 MiB.</small>
                      <span v-if="logoSeleccionado" class="nombre-logo-seleccionado">{{ logoSeleccionado.name }}</span>
                    </div>
                  </div>
                </section>
                <p v-if="cargandoEdicionTienda" class="mensaje mensaje-aviso" role="status">Cargando los datos actuales de tu tienda…</p>
                <p v-if="error" class="mensaje mensaje-error" role="alert">{{ error }}</p>
                <p v-if="aviso" class="mensaje mensaje-aviso" role="status">{{ aviso }}</p>
              </form>

              <aside class="panel-vista-previa-tienda" aria-label="Vista previa de la tienda">
                <header class="cabecera-vista-previa"><span class="icono-vista-previa" aria-hidden="true">◉</span><div><h2>Así la verán tus clientes</h2><p>Vista previa de tu tienda en el catálogo público.</p></div></header>
                <article class="tarjeta-vista-previa-tienda">
                  <div class="portada-vista-previa"><span>Emprendimientos<br />que hacen florecer<br />lo nuestro</span><i aria-hidden="true"></i></div>
                  <div class="cuerpo-vista-previa">
                    <div class="logo-vista-previa"><img :src="logoVisible" :alt="'Logo de ' + (formularioTienda.nombre || 'mi tienda')" /></div>
                    <div class="texto-vista-previa"><h3>{{ formularioTienda.nombre || 'Nombre de tu tienda' }}</h3><p>{{ tipoTiendaVisible }}</p><span class="sector-vista-previa"><span aria-hidden="true">◇</span>{{ sectorTiendaVisible }}</span></div>
                  </div>
                  <button v-if="formularioTienda.mostrarContacto && formularioTienda.contactoValor.trim()" class="contacto-vista-previa" type="button" disabled><span aria-hidden="true">{{ formularioTienda.contactoTipo === 'whatsapp' ? '◉' : formularioTienda.contactoTipo === 'correo' ? '✉' : '↗' }}</span>{{ formularioTienda.contactoTipo === 'whatsapp' ? 'Contactar por WhatsApp' : formularioTienda.contactoTipo === 'correo' ? 'Contactar por correo' : 'Visitar sitio web' }}</button>
                  <div v-else class="contacto-vista-previa contacto-inactivo"><span aria-hidden="true">◎</span>El contacto está oculto</div>
                </article>
                <div class="nota-vista-previa"><span aria-hidden="true">i</span><p>Esta es una vista referencial. Los cambios se reflejarán en tu tienda pública.</p></div>
              </aside>
            </div>
            <div class="barra-acciones-editor">
              <button class="boton-cancelar-editor" type="button" :disabled="guardandoTienda" @click="cancelarEdicionTienda">Cancelar</button>
              <button class="boton-vino boton-guardar-editor" type="submit" form="formulario-edicion-tienda" :disabled="cargandoEdicionTienda || guardandoTienda">
                <span v-if="guardandoTienda" class="rueda rueda-clara"></span>{{ guardandoTienda ? 'Guardando…' : 'Guardar cambios' }}
              </button>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="tarjeta-tienda tarjeta-cuenta">
            <div class="cuenta-cabecera">
              <div><span class="sobre-titulo">MI CUENTA</span><h2>{{ cuenta.nombreMostrar || cuenta.correo }}</h2><p>{{ cuenta.correo }}</p></div>
              <button class="boton-secundario boton-restablecer" type="button" :disabled="enviandoCambioContrasena" @click="cambiarContrasena">
                <span aria-hidden="true">⌑</span>{{ enviandoCambioContrasena ? 'Enviando enlace…' : 'Cambiar contraseña' }}
              </button>
            </div>
            <p v-if="error" class="mensaje mensaje-error" role="alert">{{ error }}</p>
            <p v-if="aviso" class="mensaje mensaje-aviso" role="status">{{ aviso }}</p>
            <div class="separador-tienda"></div>
            <div class="cuenta-panel">
              <div class="cuenta-ficha">
                <span class="sobre-titulo">MI TIENDA</span>
                <h2>{{ cuenta.tienda.nombre || 'Mi emprendimiento' }}</h2>
                <p>{{ cuenta.tienda.descripcion || 'Aún no hay una descripción para mostrar.' }}</p>
                <span class="estado-tienda" :class="{ publicada: cuenta.tienda.estadoPublicacion === 'publicado' }"><span></span>{{ cuenta.tienda.estadoPublicacion === 'publicado' ? 'Visible para compradores' : 'Aún no aparece en el catálogo comprador' }}</span>
                <button class="boton-secundario boton-editar-tienda" type="button" :disabled="cargandoEdicionTienda || guardandoTienda" @click="abrirEdicionTienda">Editar datos de mi tienda</button>
              </div>
              <section class="cuenta-completitud" aria-label="Completa tu tienda">
                <span class="sobre-titulo">COMPLETA TU TIENDA</span>
                <p>Sigue estos pasos para que quienes visiten tu tienda conozcan tu trabajo.</p>
                <ul class="lista-completitud">
                  <li v-for="paso in progresoTienda" :key="paso.titulo" :class="{ completado: paso.completo }">
                    <span class="check-completitud" aria-hidden="true">{{ paso.completo ? '✓' : '' }}</span>
                    <span><strong>{{ paso.titulo }}</strong><small v-if="!paso.completo">{{ paso.detalle }}</small></span>
                  </li>
                </ul>
              </section>
            </div>
            <div class="cuenta-acciones"><button class="boton-secundario" type="button" @click="cerrarSesion">Cerrar sesión</button></div>
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
.envoltura{--vino:var(--amancay-bordo);--oscuro:var(--amancay-bordo-oscuro);--texto:var(--amancay-tinta);--suave:var(--amancay-tinta-suave);--crema:var(--amancay-fondo);--rosa:var(--amancay-naranja-fondo);--naranja:var(--amancay-naranja-icono);--naranja-fondo:var(--amancay-naranja-fondo);--borde:var(--amancay-borde);--verde:var(--amancay-verde-texto);min-height:100vh;display:grid;grid-template-columns:250px minmax(0,1fr);background:var(--amancay-fondo);color:var(--amancay-tinta);font-family:'Plus Jakarta Sans',sans-serif}
.envoltura :global(*){box-sizing:border-box}
.barra-lateral{position:sticky;top:0;height:100vh;display:flex;flex-direction:column;padding:28px 18px 18px;background:var(--amancay-superficie);border-right:1px solid var(--borde);z-index:4}
.marca{display:flex;align-items:center;gap:10px;color:inherit;text-decoration:none}.marca img{width:46px;height:46px;object-fit:contain;padding:5px;border-radius:50%;background:var(--crema);border:1px solid var(--borde)}.marca span{display:grid;line-height:1.12}.marca small{color:var(--suave);font-size:11px}.marca strong{color:var(--vino);font:400 22px 'DM Serif Display',Georgia,serif}
.etiqueta-lateral{margin:38px 10px 12px;color:var(--amancay-tinta-suave);font-size:10px;letter-spacing:.12em;font-weight:700}.menu-lateral{display:grid;gap:7px}
.opcion-menu{display:flex;align-items:center;gap:10px;width:100%;min-height:45px;padding:7px 11px;border:0;border-radius:14px;background:transparent;color:var(--suave);text-align:left;font:600 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.opcion-menu:hover,.opcion-menu:focus-visible{background:var(--crema);color:var(--vino);outline-color:var(--vino)}.opcion-menu.seleccionada{background:var(--rosa);color:var(--vino)}
.icono-menu{display:grid;place-items:center;width:30px;height:30px;border-radius:10px;background:var(--crema);color:var(--vino);font-size:18px;line-height:1}.seleccionada .icono-menu{background:var(--amancay-superficie)}.contador-menu{display:grid;place-items:center;width:21px;height:21px;margin-left:auto;border-radius:50%;background:var(--vino);color:#fff;font-size:10px}
.perfil-lateral{display:flex;align-items:center;gap:9px;margin-top:auto;padding:12px 8px;border-top:1px solid var(--borde)}.avatar{display:grid;place-items:center;flex:0 0 36px;height:36px;border-radius:50%;background:var(--rosa);color:var(--vino);font-weight:700}.datos-perfil{display:grid;gap:3px;min-width:0;flex:1}.datos-perfil strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.datos-perfil small{color:var(--suave);font-size:9px}.salir-lateral{border:0;background:transparent;color:var(--suave);font-size:18px;cursor:pointer}
.ayuda-lateral{margin-top:auto;padding:15px;border-radius:17px;background:var(--crema);color:var(--suave);text-align:center}.ayuda-lateral span{color:var(--vino);font-size:22px}.ayuda-lateral p{margin:5px 0 0;font-size:10px;line-height:1.6}
.principal{min-width:0;min-height:100vh}.barra-superior{height:68px;display:flex;align-items:center;justify-content:space-between;gap:14px;padding:0 clamp(20px,5vw,64px);background:rgba(255,251,242,.94);border-bottom:1px solid var(--borde);color:var(--suave);font-size:9px;font-weight:700;letter-spacing:.08em}.punto-marca{display:inline-block;width:8px;height:8px;margin-right:6px;border-radius:50%;background:var(--vino)}.separador{padding:0 8px;color:var(--amancay-borde)}.volver-catalogo{display:inline-flex;align-items:center;gap:8px;padding:10px 15px;border:1.5px solid var(--vino);border-radius:12px;background:var(--rosa);box-shadow:0 2px 8px rgba(78,20,44,.1);color:var(--vino);font-size:13px;font-weight:700;text-decoration:none;letter-spacing:0;white-space:nowrap;transition:background .15s,color .15s,box-shadow .15s}.volver-catalogo span{font-size:16px;line-height:1}.volver-catalogo:hover{background:var(--vino);color:#fff;box-shadow:0 4px 12px rgba(78,20,44,.18)}
.vista-ingreso{display:grid;grid-template-columns:minmax(0,1fr) minmax(320px,420px);align-items:center;gap:clamp(28px,6vw,80px);width:min(1080px,calc(100% - 56px));min-height:calc(100vh - 68px);margin:auto;padding:40px 0}
.presentacion-ingreso{position:relative;max-width:500px}.sobre-titulo{display:inline-block;color:var(--vino);font-size:9px;line-height:1.5;letter-spacing:.13em;font-weight:700}.presentacion-ingreso h1{margin:13px 0;color:var(--texto);font:400 clamp(48px,6vw,72px)/.98 'DM Serif Display',Georgia,serif;letter-spacing:-.03em}.presentacion-ingreso h1 em{color:var(--vino);font-style:normal}.presentacion-ingreso>p{max-width:420px;margin:0;color:var(--suave);font-size:14px;line-height:1.8}
.detalle-ingreso{display:flex;align-items:center;gap:10px;margin-top:26px;color:var(--vino);font-size:11px;font-weight:600}.detalle-icono{display:grid;place-items:center;width:32px;height:32px;border-radius:11px;background:var(--rosa);font-size:17px}.flor-decorativa{position:absolute;right:12px;bottom:-65px;color:var(--amancay-naranja-fondo);font-size:145px;opacity:.55;pointer-events:none}
.tarjeta-ingreso{position:relative;z-index:1;padding:30px;border:1px solid var(--borde);border-radius:25px;background:var(--amancay-superficie);box-shadow:0 18px 60px rgba(46,27,32,.07)}.logo-ingreso{display:block;width:49px;height:49px;object-fit:contain;margin-bottom:17px;padding:5px;border:1px solid var(--borde);border-radius:50%;background:var(--crema)}.tarjeta-ingreso h2{margin:7px 0;color:var(--texto);font:400 29px/1.15 'DM Serif Display',Georgia,serif}.tarjeta-ingreso>p{margin:0;color:var(--suave);font-size:11px;line-height:1.7}
.formulario-ingreso{display:grid;gap:9px;margin-top:20px}.formulario-ingreso label{margin-top:4px;color:var(--texto);font-size:10px;font-weight:700}.formulario-ingreso input{width:100%;height:45px;padding:0 13px;border:1px solid var(--borde);border-radius:12px;outline:none;background:var(--amancay-superficie);color:var(--texto);font:11px 'Plus Jakarta Sans',sans-serif}.formulario-ingreso input:focus{border-color:var(--vino);box-shadow:0 0 0 3px rgba(107,30,61,.1)}.formulario-ingreso input::placeholder{color:var(--amancay-tinta-suave)}
.mensaje{margin:3px 0;padding:10px 11px;border-radius:11px;font-size:10px;line-height:1.5}.mensaje-error{background:#fff0ee;color:#9d3429}.mensaje-aviso{background:var(--amancay-verde-fondo);color:var(--amancay-verde-texto)}.boton-recuperacion{justify-self:end;padding:5px 0;border:0;background:transparent;color:var(--vino);font:600 10px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-principal,.boton-vino,.boton-secundario{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:0 18px;border:0;border-radius:13px;background:var(--vino);color:#fff;font:700 11px 'Plus Jakarta Sans',sans-serif;text-decoration:none;cursor:pointer}.boton-principal{width:100%;margin-top:4px}.boton-principal:hover,.boton-vino:hover{background:var(--oscuro)}.boton-principal:disabled{opacity:.65;cursor:wait}.enlace-invitacion{display:block;margin-top:15px;color:var(--suave);font-size:9px;text-align:center;text-decoration-color:var(--amancay-borde)}
.estado-carga{display:grid;justify-items:center;align-content:center;min-height:calc(100vh - 68px);color:var(--suave);font-size:12px}.rueda{width:18px;height:18px;border:2px solid rgba(107,30,61,.2);border-top-color:var(--vino);border-radius:50%;animation:girar .7s linear infinite}.rueda-clara{border-color:rgba(255,255,255,.35);border-top-color:#fff}@keyframes girar{to{transform:rotate(360deg)}}
.contenido-panel{width:min(1010px,calc(100% - 56px));margin:auto;padding:38px 0 55px}.cabecera-panel{display:flex;align-items:flex-end;justify-content:space-between;gap:18px;margin-bottom:22px}.cabecera-panel h1{margin:6px 0 5px;color:var(--texto);font:400 clamp(32px,4vw,44px)/1.1 'DM Serif Display',Georgia,serif}.cabecera-panel p{margin:0;color:var(--suave);font-size:11px;line-height:1.6}.fecha-hoy{padding-bottom:4px;color:var(--amancay-bordo);font-size:9px;font-weight:700;text-transform:uppercase}
.tarjeta-accion{position:relative;overflow:hidden;display:flex;align-items:center;justify-content:space-between;gap:20px;min-height:185px;margin-bottom:14px;padding:27px 30px;border-radius:23px;background:var(--vino);color:#fff}.tarjeta-accion>div{position:relative;z-index:1}.sobre-titulo.claro{color:var(--amancay-naranja-fondo)}.tarjeta-accion h2{margin:8px 0 5px;font:400 clamp(25px,3vw,32px)/1.1 'DM Serif Display',Georgia,serif}.tarjeta-accion p{margin:0;color:rgba(255,255,255,.8);font-size:11px}.boton-blanco{position:relative;z-index:1;display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:0 18px;border:0;border-radius:13px;background:var(--amancay-superficie);color:var(--texto);font:700 11px 'Plus Jakarta Sans',sans-serif;white-space:nowrap;cursor:pointer}.boton-blanco span{color:var(--vino);font-size:18px}.flor-tarjeta{position:absolute;right:20%;top:-50px;color:rgba(255,255,255,.08);font-size:230px;pointer-events:none}
.tarjeta-resumen,.tarjeta-tienda,.tarjeta-agregar,.vacio-productos{width:100%;border:1px solid var(--borde);border-radius:20px;background:var(--amancay-superficie)}.tarjeta-resumen{display:flex;align-items:center;gap:14px;min-height:80px;margin-bottom:14px;padding:14px 18px;text-align:left;cursor:pointer}.icono-resumen{display:grid;place-items:center;flex:0 0 48px;height:48px;border-radius:14px;background:var(--crema);color:var(--vino);font-size:24px}.texto-resumen{display:grid;flex:1;gap:5px}.texto-resumen strong{color:var(--texto);font-size:12px}.texto-resumen small{color:var(--suave);font-size:10px}.flecha-resumen{color:var(--vino);font:30px 'DM Serif Display',Georgia,serif}
.tarjeta-tienda{padding:21px}.cabecera-tienda{display:flex;align-items:center;justify-content:space-between;gap:16px}.cabecera-tienda h2,.encabezado-seccion h2{margin:5px 0 0;color:var(--texto);font:400 24px/1.2 'DM Serif Display',Georgia,serif}.estado-tienda{display:inline-flex;align-items:center;gap:7px;padding:7px 10px;border-radius:999px;background:var(--amancay-mostaza-suave);color:var(--amancay-bordo-oscuro);font-size:9px;font-weight:700;white-space:nowrap}.estado-tienda>span{width:7px;height:7px;border-radius:50%;background:var(--amancay-mostaza)}.estado-tienda.publicada{background:var(--amancay-verde-fondo);color:var(--verde)}.estado-tienda.publicada>span{background:var(--amancay-verde-texto)}
.tarjeta-tienda>p{margin:10px 0 0;color:var(--suave);font-size:10px;line-height:1.7}.separador-tienda{height:1px;margin:18px 0 14px;background:var(--borde)}.progreso-cabecera{display:flex;justify-content:space-between;gap:12px}.progreso-cabecera strong{color:var(--texto);font-size:11px}.progreso-cabecera>span{color:var(--suave);font-size:9px}.pasos-progreso{display:grid;gap:11px;margin-top:13px}.paso-progreso{display:flex;align-items:center;gap:10px}.check-progreso{display:grid;place-items:center;flex:0 0 26px;height:26px;border:1px solid var(--borde);border-radius:50%;color:var(--amancay-tinta-suave);font-size:10px;font-weight:700}.check-progreso.completado{border-color:var(--vino);background:var(--vino);color:#fff}.paso-progreso>span:last-child{display:grid;gap:3px}.paso-progreso strong{color:var(--texto);font-size:9px}.paso-progreso small{color:var(--suave);font-size:9px}
.encabezado-seccion{display:flex;align-items:end;justify-content:space-between;gap:14px;margin-bottom:16px}.boton-vino.compacto{min-height:38px;padding:0 14px}.filtros-productos{display:flex;gap:8px;overflow-x:auto;margin-bottom:14px}.filtros-productos button{min-height:35px;padding:0 12px;border:1px solid var(--borde);border-radius:999px;background:var(--amancay-superficie);color:var(--suave);font:600 9px 'Plus Jakarta Sans',sans-serif;white-space:nowrap;cursor:pointer}.filtros-productos button.activo{border-color:var(--vino);background:var(--vino);color:white}
.lista-productos{display:grid;gap:10px}.tarjeta-producto{display:flex;align-items:center;gap:13px;min-height:100px;padding:13px;border:1px solid var(--borde);border-radius:18px;background:var(--amancay-superficie)}.imagen-producto{display:grid;place-items:center;flex:0 0 67px;height:67px;border-radius:16px;background:linear-gradient(140deg,var(--amancay-naranja-fondo),var(--amancay-mostaza-suave));color:var(--vino);font:400 30px 'DM Serif Display',Georgia,serif}.datos-producto{display:grid;gap:5px;flex:1;min-width:0}.datos-producto>strong{overflow:hidden;color:var(--texto);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.datos-producto p{overflow:hidden;margin:0;color:var(--suave);font-size:9px;text-overflow:ellipsis;white-space:nowrap}.metadatos-producto{display:flex;flex-wrap:wrap;gap:6px;color:var(--suave);font-size:9px}.metadatos-producto span:first-child{color:var(--vino);font-weight:700}.estado-producto{padding:6px 8px;border-radius:999px;background:var(--amancay-mostaza-suave);color:var(--amancay-bordo-oscuro);font-size:8px;font-weight:700;white-space:nowrap}.estado-producto.publicado{background:var(--amancay-verde-fondo);color:var(--verde)}.estado-producto.archivado{background:var(--amancay-superficie-variante);color:var(--suave)}
.vacio-productos{display:grid;justify-items:center;padding:36px 20px;text-align:center}.vacio-productos>span{color:var(--vino);font-size:30px}.vacio-productos h3{margin:9px 0 5px;color:var(--texto);font:400 22px 'DM Serif Display',Georgia,serif}.vacio-productos p{max-width:390px;margin:0 0 15px;color:var(--suave);font-size:10px;line-height:1.7}
.tarjeta-agregar{display:grid;justify-items:center;padding:32px;text-align:center}.icono-agregar{display:grid;place-items:center;width:55px;height:55px;margin-bottom:15px;border-radius:17px;background:var(--rosa);color:var(--vino);font-size:27px}.tarjeta-agregar h2{max-width:570px;margin:8px 0;color:var(--texto);font:400 32px/1.15 'DM Serif Display',Georgia,serif}.tarjeta-agregar>p{max-width:550px;margin:0;color:var(--suave);font-size:11px;line-height:1.7}.pasos-agregar{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;width:100%;margin:23px 0;text-align:left}.pasos-agregar>div{display:grid;gap:6px;padding:14px;border:1px solid var(--borde);border-radius:14px;background:var(--amancay-superficie)}.pasos-agregar span{color:var(--vino);font-size:9px;font-weight:700}.pasos-agregar strong{color:var(--texto);font-size:9px}.pasos-agregar small{color:var(--suave);font-size:8px;line-height:1.5}.nota-web{display:flex;align-items:center;gap:9px;width:100%;max-width:630px;margin-bottom:17px;padding:11px 13px;border-radius:12px;background:var(--crema);text-align:left}.nota-web>span{display:grid;place-items:center;flex:0 0 20px;height:20px;border:1px solid var(--amancay-borde);border-radius:50%;color:var(--vino);font-size:10px;font-weight:700}.nota-web p{margin:0;color:var(--suave);font-size:9px;line-height:1.6}
.tarjeta-cuenta{max-width:720px}.tarjeta-cuenta>h2{margin:6px 0;color:var(--texto);font:400 25px 'DM Serif Display',Georgia,serif}.estado-cuenta{margin:16px 0}.boton-secundario{border:1px solid var(--borde);background:var(--amancay-superficie);color:var(--vino)}.navegacion-movil{display:none}button:focus-visible,a:focus-visible{outline:2px solid var(--vino);outline-offset:3px}
@media(max-width:940px){.envoltura{grid-template-columns:215px minmax(0,1fr)}.barra-lateral{padding-right:13px;padding-left:13px}.vista-ingreso{grid-template-columns:minmax(0,1fr) minmax(300px,380px);gap:24px;width:min(100% - 40px,800px)}.presentacion-ingreso h1{font-size:55px}.contenido-panel{width:calc(100% - 40px)}}
@media(max-width:740px){.envoltura{display:block}.barra-lateral{position:sticky;height:auto;padding:11px 14px 7px;border-right:0;border-bottom:1px solid var(--borde);box-shadow:0 5px 18px rgba(46,27,32,.04)}.marca img{width:38px;height:38px}.marca strong{font-size:19px}.etiqueta-lateral,.perfil-lateral,.ayuda-lateral{display:none}.menu-lateral{display:flex;gap:6px;margin-top:8px;overflow-x:auto}.opcion-menu{width:auto;min-height:37px;flex:0 0 auto;gap:7px;padding:5px 9px;font-size:10px;white-space:nowrap}.icono-menu{width:24px;height:24px;font-size:15px}.principal{min-height:calc(100vh - 94px)}.barra-superior{height:48px;padding:0 15px;font-size:8px}.volver-catalogo{padding:7px 10px;font-size:11px;gap:6px}.vista-ingreso{grid-template-columns:1fr;gap:14px;width:min(100% - 30px,500px);min-height:auto;padding:24px 0 45px}.presentacion-ingreso{padding:6px 2px 0}.presentacion-ingreso h1{margin:8px 0;font-size:44px}.presentacion-ingreso>p{max-width:420px;font-size:11px;line-height:1.65}.detalle-ingreso{margin-top:11px;font-size:9px}.detalle-icono{width:27px;height:27px}.flor-decorativa{right:8px;bottom:-30px;font-size:85px}.tarjeta-ingreso{padding:21px;border-radius:20px}.logo-ingreso{display:none}.tarjeta-ingreso h2{font-size:26px}.formulario-ingreso{margin-top:15px}.contenido-panel{width:calc(100% - 30px);padding:23px 0 98px}.cabecera-panel{align-items:flex-start;flex-direction:column;gap:6px;margin-bottom:16px}.cabecera-panel h1{font-size:34px}.fecha-hoy{order:-1}.tarjeta-accion{align-items:flex-start;flex-direction:column;min-height:195px;gap:15px;padding:21px}.tarjeta-accion h2{font-size:27px}.boton-blanco{min-height:41px}.flor-tarjeta{right:-12px;top:5px;font-size:145px}.tarjeta-tienda{padding:17px}.cabecera-tienda{align-items:flex-start;flex-direction:column;gap:9px}.cabecera-tienda h2,.encabezado-seccion h2{font-size:21px}.progreso-cabecera{align-items:flex-start;flex-direction:column;gap:4px}.encabezado-seccion{align-items:flex-start}.tarjeta-producto{align-items:flex-start;flex-wrap:wrap;gap:10px}.imagen-producto{flex-basis:56px;height:56px;font-size:24px}.datos-producto{flex-basis:calc(100% - 70px);align-self:center}.estado-producto{margin-left:66px;margin-top:-2px}.tarjeta-agregar{padding:23px 16px}.tarjeta-agregar h2{font-size:27px}.pasos-agregar{grid-template-columns:1fr;margin:17px 0}.pasos-agregar>div{grid-template-columns:28px 1fr;align-items:center}.pasos-agregar small{grid-column:2;margin-top:-4px}.navegacion-movil{position:fixed;z-index:5;right:0;bottom:0;left:0;display:grid;grid-template-columns:repeat(4,1fr);padding:7px 6px calc(7px + env(safe-area-inset-bottom));border-top:1px solid var(--borde);background:rgba(255,251,242,.97);box-shadow:0 -5px 25px rgba(46,27,32,.06)}.navegacion-movil button{display:grid;justify-items:center;gap:3px;padding:2px;border:0;background:transparent;color:var(--suave);font:inherit;cursor:pointer}.navegacion-movil button>span{display:grid;place-items:center;width:30px;height:24px;border-radius:8px;font-size:18px}.navegacion-movil button.seleccionada{color:var(--vino);font-weight:700}.navegacion-movil button.seleccionada>span{background:var(--rosa)}.navegacion-movil small{font-size:8px}}
@media(max-width:400px){.barra-superior{padding:0 10px;gap:6px;letter-spacing:.03em}.volver-catalogo{padding:7px 8px;font-size:10px;gap:5px}.vista-ingreso,.contenido-panel{width:calc(100% - 22px)}.presentacion-ingreso h1{font-size:40px}.tarjeta-ingreso{padding:18px}}
@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;animation-iteration-count:1!important;scroll-behavior:auto!important;transition-duration:.01ms!important}}

.envoltura{background:var(--amancay-fondo)}
.barra-lateral{padding:28px 20px 20px}
.marca img{width:52px;height:52px}
.marca strong{font-size:25px}
.etiqueta-lateral{margin:42px 10px 15px;font-size:11px}
.opcion-menu{min-height:54px;padding:9px 12px;border-radius:16px;font-size:14px}
.icono-menu{width:38px;height:38px;border-radius:13px;font-size:21px}
.contador-menu{width:27px;height:27px;font-size:12px}
.datos-perfil strong{font-size:13px}
.datos-perfil small{font-size:11px}
.avatar{flex-basis:42px;height:42px;font-size:16px}
.barra-superior{height:78px;font-size:10px}
.vista-ingreso{width:min(1280px,calc(100% - 72px));grid-template-columns:minmax(0,1.15fr) minmax(420px,.95fr);gap:clamp(44px,6vw,110px);min-height:calc(100vh - 78px);padding:52px 0}
.presentacion-ingreso{max-width:650px}
.presentacion-ingreso h1{font-size:clamp(64px,7.3vw,100px);line-height:.94}
.presentacion-ingreso>p{max-width:560px;font-size:18px;line-height:1.75}
.sobre-titulo{font-size:11px}
.detalle-ingreso{margin-top:32px;font-size:14px}
.detalle-icono{width:42px;height:42px;border-radius:14px;font-size:21px}
.flor-decorativa{right:3%;bottom:-62px;width:clamp(210px,27vw,390px);height:clamp(210px,27vw,390px);font-size:0;color:transparent;background:url('/logo-amancay.png') center/contain no-repeat;opacity:.16;transform:rotate(-8deg)}
.tarjeta-ingreso{padding:clamp(32px,3.5vw,52px);border-radius:30px;box-shadow:0 24px 72px rgba(46,27,32,.08)}
.logo-ingreso{width:64px;height:64px;margin-bottom:23px}
.tarjeta-ingreso h2{margin:10px 0;font-size:clamp(32px,3.3vw,43px)}
.tarjeta-ingreso>p{font-size:14px}
.formulario-ingreso{gap:12px;margin-top:25px}
.formulario-ingreso label{font-size:13px}
.formulario-ingreso input{height:56px;padding:0 16px;border-radius:15px;font-size:14px}
.campo-clave{position:relative}
.campo-clave input{padding-right:54px}
.mostrar-clave{position:absolute;top:50%;right:13px;display:grid;place-items:center;width:34px;height:34px;transform:translateY(-50%);border:0;border-radius:10px;background:transparent;color:var(--suave);font-size:20px;cursor:pointer}
.mostrar-clave:hover{background:var(--rosa);color:var(--vino)}
.boton-recuperacion{font-size:13px}
.boton-principal,.boton-vino,.boton-secundario{min-height:52px;padding:0 22px;border-radius:15px;font-size:14px}
.boton-principal{margin-top:7px}
.enlace-invitacion{margin-top:19px;font-size:12px}
.contenido-panel{width:min(1240px,calc(100% - 72px));padding:48px 0 68px}
.cabecera-panel{margin-bottom:28px}
.cabecera-panel h1{font-size:clamp(42px,4.4vw,62px)}
.cabecera-panel p{font-size:15px}
.fecha-hoy{font-size:12px}
.tarjeta-accion{min-height:220px;margin-bottom:18px;padding:34px 40px;border-radius:28px}
.tarjeta-accion h2{font-size:clamp(32px,3.4vw,46px)}
.tarjeta-accion p{font-size:15px}
.boton-blanco{min-height:56px;padding:0 22px;border-radius:16px;font-size:14px}
.flor-tarjeta{top:-55px;right:14%;width:270px;height:270px;font-size:0;color:transparent;background:url('/logo-amancay.png') center/contain no-repeat;opacity:.1}
.tarjeta-resumen{min-height:108px;margin-bottom:18px;padding:18px 24px;border-radius:22px}
.icono-resumen{flex-basis:68px;height:68px;border-radius:19px;font-size:31px}
.texto-resumen{gap:7px}
.texto-resumen strong{font-size:17px}
.texto-resumen small{font-size:13px}
.flecha-resumen{font-size:38px}
.tarjeta-tienda,.tarjeta-agregar,.vacio-productos{border-radius:24px}
.tarjeta-tienda{padding:28px}
.cabecera-tienda h2,.encabezado-seccion h2{font-size:32px}
.estado-tienda{padding:9px 14px;font-size:12px}
.tarjeta-tienda>p{font-size:14px}
.separador-tienda{margin:23px 0 19px}
.progreso-cabecera strong{font-size:15px}
.progreso-cabecera>span{font-size:12px}
.pasos-progreso{grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:18px}
.paso-progreso{align-items:flex-start;min-height:95px;padding:16px;border:1px solid var(--borde);border-radius:18px;background:var(--amancay-superficie)}
.check-progreso{flex-basis:34px;height:34px;font-size:13px}
.paso-progreso>span:last-child{gap:6px}
.paso-progreso strong{font-size:12px}
.paso-progreso small{font-size:11px;line-height:1.45}
.encabezado-seccion{margin-bottom:20px}
.encabezado-seccion h2{font-size:30px}
.boton-vino.compacto{min-height:46px;padding:0 18px}
.filtros-productos{gap:10px;margin-bottom:19px}
.filtros-productos button{min-height:44px;padding:0 17px;font-size:12px}
.lista-productos{gap:13px}
.tarjeta-producto{min-height:124px;gap:17px;padding:15px 18px;border-radius:20px}
.imagen-producto{flex-basis:92px;height:92px;border-radius:17px}
.datos-producto{gap:7px}
.datos-producto>strong{font-size:16px}
.datos-producto p{font-size:12px}
.metadatos-producto{gap:8px;font-size:12px}
.estado-producto{padding:9px 13px;font-size:11px}
:deep(.imagen-publica){width:100%;height:100%;overflow:hidden;border-radius:inherit;background:linear-gradient(140deg,var(--amancay-naranja-fondo),var(--amancay-mostaza-suave))}
:deep(.imagen-publica img){width:100%;height:100%;object-fit:contain}
:deep(.imagen-publica>span){gap:3px;font-size:24px}
.tarjeta-agregar{padding:44px}
.icono-agregar{width:84px;height:84px;margin-bottom:20px;border-radius:24px}
.icono-agregar img{width:58px;height:58px;object-fit:contain}
.tarjeta-agregar h2{font-size:clamp(34px,3.7vw,48px)}
.tarjeta-agregar>p{font-size:15px}
.pasos-agregar{gap:15px;margin:30px 0}
.pasos-agregar>div{min-height:180px;gap:12px;padding:22px;border-radius:20px}
.pasos-agregar span:first-child{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;background:var(--rosa);font-size:13px}
.pasos-agregar strong{font-size:14px}
.pasos-agregar small{font-size:12px}
.nota-web{max-width:820px;margin-bottom:22px;padding:16px 19px;border-radius:16px}
.nota-web>span{flex-basis:28px;height:28px;font-size:14px}
.nota-web p{font-size:13px}
.tarjeta-cuenta{max-width:none}
.cuenta-cabecera{display:flex;align-items:center;justify-content:space-between;gap:20px}
.cuenta-cabecera h2{margin:8px 0 5px;color:var(--texto);font:400 clamp(30px,3vw,42px)/1.1 'DM Serif Display',Georgia,serif}
.cuenta-cabecera p{margin:0;color:var(--suave);font-size:15px}
.boton-restablecer{display:inline-flex;align-items:center;gap:10px;background:var(--amancay-superficie)}
.boton-restablecer>span{font-size:19px}
.cuenta-panel{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(280px,.9fr);gap:34px;align-items:stretch}
.cuenta-ficha{padding:8px 0}
.cuenta-ficha h2{margin:10px 0 7px;color:var(--texto);font:400 clamp(30px,3vw,42px)/1.1 'DM Serif Display',Georgia,serif}
.cuenta-ficha p{margin:0 0 18px;color:var(--suave);font-size:15px;line-height:1.7}
.cuenta-completitud{padding:8px 0 8px 30px;border-left:1px solid var(--borde)}
.cuenta-completitud>p{margin:10px 0 18px;color:var(--suave);font-size:14px;line-height:1.6}
.lista-completitud{display:grid;gap:13px;margin:0;padding:0;list-style:none}
.lista-completitud li{display:flex;align-items:flex-start;gap:12px;color:var(--suave)}
.lista-completitud li>span:last-child{display:grid;gap:4px}
.lista-completitud strong{color:var(--texto);font-size:14px}
.lista-completitud small{font-size:12px;line-height:1.5}
.check-completitud{display:grid;place-items:center;flex:0 0 30px;height:30px;border:1px solid var(--borde);border-radius:50%;background:var(--amancay-superficie);color:var(--suave);font-size:15px;font-weight:700}
.lista-completitud li.completado .check-completitud{border-color:var(--amancay-verde-texto);background:var(--amancay-verde-texto);color:#fff}
.lista-completitud li.completado strong{color:var(--amancay-verde-texto)}
.cuenta-acciones{margin-top:25px;padding-top:20px;border-top:1px solid var(--borde)}
.cuenta-acciones .boton-secundario{background:var(--amancay-superficie)}
button:focus-visible,a:focus-visible{outline:3px solid rgba(107,30,61,.52);outline-offset:3px}
@media(max-width:940px){
  .barra-lateral{padding-right:13px;padding-left:13px}
  .vista-ingreso{width:min(100% - 40px,880px);grid-template-columns:minmax(0,1fr) minmax(340px,.9fr);gap:30px}
  .presentacion-ingreso h1{font-size:clamp(52px,6vw,74px)}
  .contenido-panel{width:calc(100% - 48px)}
}
@media(max-width:740px){
  .envoltura{display:block}
  .barra-lateral{position:sticky;height:auto;padding:11px 14px 7px}
  .marca img{width:38px;height:38px}
  .marca strong{font-size:19px}
  .etiqueta-lateral,.perfil-lateral,.ayuda-lateral{display:none}
  .menu-lateral{display:flex;gap:6px;margin-top:8px;overflow-x:auto}
  .opcion-menu{width:auto;min-height:38px;flex:0 0 auto;gap:7px;padding:5px 9px;font-size:11px;white-space:nowrap}
  .icono-menu{width:25px;height:25px;font-size:15px}
  .principal{min-height:calc(100vh - 94px)}
  .barra-superior{height:56px;gap:7px;padding:0 12px;font-size:7px}
  .volver-catalogo{padding:7px 9px;font-size:10px;gap:5px}
  .vista-ingreso{grid-template-columns:1fr;gap:14px;width:min(100% - 28px,520px);min-height:auto;padding:20px 0 42px}
  .presentacion-ingreso{padding:6px 2px 0}
  .presentacion-ingreso h1{margin:8px 0;font-size:clamp(43px,11vw,60px)}
  .presentacion-ingreso>p{max-width:440px;font-size:13px}
  .detalle-ingreso{margin-top:13px;font-size:11px}
  .detalle-icono{width:32px;height:32px}
  .flor-decorativa{right:1%;bottom:-22px;width:130px;height:130px}
  .tarjeta-ingreso{padding:23px;border-radius:22px}
  .logo-ingreso{width:48px;height:48px;margin-bottom:14px}
  .tarjeta-ingreso h2{font-size:29px}
  .tarjeta-ingreso>p{font-size:12px}
  .formulario-ingreso{gap:9px;margin-top:17px}
  .formulario-ingreso label{font-size:11px}
  .formulario-ingreso input{height:48px;font-size:12px}
  .boton-recuperacion{font-size:11px}
  .boton-principal,.boton-vino,.boton-secundario{min-height:46px;font-size:12px}
  .enlace-invitacion{font-size:10px}
  .contenido-panel{width:calc(100% - 30px);padding:25px 0 100px}
  .cabecera-panel{align-items:flex-start;flex-direction:column;gap:7px;margin-bottom:18px}
  .cabecera-panel h1{font-size:clamp(36px,9vw,47px)}
  .cabecera-panel p{font-size:12px}
  .fecha-hoy{order:-1;font-size:10px}
  .tarjeta-accion{align-items:flex-start;flex-direction:column;min-height:190px;gap:15px;padding:23px}
  .tarjeta-accion h2{font-size:clamp(29px,7vw,37px)}
  .tarjeta-accion p{font-size:12px}
  .boton-blanco{min-height:46px;font-size:12px}
  .flor-tarjeta{top:0;right:-24px;width:140px;height:140px}
  .tarjeta-resumen{min-height:88px;padding:14px}
  .icono-resumen{flex-basis:52px;height:52px;font-size:24px}
  .texto-resumen strong{font-size:13px}
  .texto-resumen small{font-size:10px}
  .tarjeta-tienda{padding:19px}
  .cabecera-tienda{align-items:flex-start;flex-direction:column;gap:10px}
  .cabecera-tienda h2,.encabezado-seccion h2{font-size:24px}
  .tarjeta-tienda>p{font-size:12px}
  .progreso-cabecera{align-items:flex-start;flex-direction:column;gap:5px}
  .pasos-progreso{grid-template-columns:1fr;gap:9px}
  .paso-progreso{min-height:0;padding:12px}
  .check-progreso{flex-basis:30px;height:30px}
  .paso-progreso strong,.paso-progreso small{font-size:11px}
  .encabezado-seccion{align-items:flex-start;flex-wrap:wrap}
  .encabezado-seccion h2{font-size:23px}
  .filtros-productos{gap:7px}
  .filtros-productos button{min-height:38px;padding:0 12px;font-size:10px}
  .tarjeta-producto{align-items:flex-start;flex-wrap:wrap;gap:11px;padding:12px}
  .imagen-producto{flex-basis:68px;height:68px}
  .datos-producto{flex-basis:calc(100% - 83px);align-self:center}
  .datos-producto>strong{font-size:13px}
  .datos-producto p,.metadatos-producto{font-size:10px}
  .estado-producto{margin-left:79px;margin-top:-2px;font-size:10px}
  .tarjeta-agregar{padding:27px 18px}
  .icono-agregar{width:64px;height:64px}
  .icono-agregar img{width:46px;height:46px}
  .tarjeta-agregar h2{font-size:30px}
  .tarjeta-agregar>p{font-size:12px}
  .pasos-agregar{grid-template-columns:1fr;gap:10px;margin:20px 0}
  .pasos-agregar>div{grid-template-columns:40px 1fr;align-items:center;min-height:0;padding:15px}
  .pasos-agregar span:first-child{grid-row:span 2}
  .pasos-agregar small{grid-column:2;margin-top:-6px}
  .nota-web{padding:12px}
  .nota-web p{font-size:11px}
  .cuenta-cabecera{align-items:flex-start;flex-direction:column}
  .cuenta-cabecera h2,.cuenta-ficha h2{font-size:30px}
  .cuenta-cabecera p,.cuenta-ficha p{font-size:12px}
  .cuenta-panel{grid-template-columns:1fr;gap:19px}
  .cuenta-completitud{padding:20px 0 0;border-top:1px solid var(--borde);border-left:0}
  .lista-completitud strong{font-size:12px}
  .lista-completitud small,.cuenta-completitud>p{font-size:11px}
  .cuenta-acciones{margin-top:18px;padding-top:15px}
}
@media(max-width:400px){
  .barra-superior{padding:0 8px;gap:5px;font-size:6px}
  .volver-catalogo{padding:6px 7px;font-size:9px}
  .contenido-panel,.vista-ingreso{width:calc(100% - 22px)}
  .tarjeta-ingreso{padding:19px}
  .filtros-productos button{padding:0 9px;font-size:9px}
}
.boton-editar-tienda{margin-top:17px}
.perfil-cabecera-editor{display:flex;align-items:center;gap:12px;min-width:220px;color:var(--texto);letter-spacing:0}
.perfil-cabecera-editor>span{display:grid;place-items:center;width:52px;height:52px;border-radius:50%;background:var(--vino);color:#fff;font-size:15px;font-weight:700}
.perfil-cabecera-editor>div{display:grid;gap:3px}.perfil-cabecera-editor strong{font-size:13px}.perfil-cabecera-editor small{color:var(--suave);font-size:11px}.perfil-cabecera-editor b{margin-left:4px;color:var(--vino);font-size:19px}
.contenido-panel.modo-edicion-tienda{width:min(1680px,calc(100% - 72px));padding:34px 0 0}
.editor-ficha{padding-bottom:0}
.titulo-editor-ficha{margin:0 0 23px}
.titulo-editor-ficha h1{margin:7px 0 4px;color:var(--amancay-bordo-oscuro);font:400 clamp(42px,4.5vw,58px)/1.05 'DM Serif Display',Georgia,serif}
.titulo-editor-ficha p{margin:0;color:var(--suave);font-size:15px;line-height:1.55}
.rejilla-editor-ficha{display:grid;grid-template-columns:minmax(0,1.62fr) minmax(340px,.82fr);align-items:start;gap:20px}
.formulario-ficha-tienda,.panel-vista-previa-tienda{border:1px solid var(--borde);border-radius:20px;background:var(--amancay-superficie);box-shadow:0 8px 25px rgba(46,27,32,.04)}
.formulario-ficha-tienda{padding:22px 26px}
.paso-edicion-tienda{padding:4px 0 25px;border-bottom:1px solid var(--borde)}
.paso-edicion-tienda+.paso-edicion-tienda{padding-top:23px}
.paso-edicion-tienda:last-of-type{border-bottom:0;padding-bottom:7px}
.cabecera-paso-tienda{display:flex;align-items:center;gap:16px;margin-bottom:18px}
.numero-paso-tienda{display:grid;place-items:center;flex:0 0 58px;height:58px;border-radius:50%;background:var(--amancay-naranja-fondo);color:var(--amancay-bordo-oscuro);font:400 28px 'DM Serif Display',Georgia,serif}
.cabecera-paso-tienda h2{margin:0 0 3px;color:var(--vino);font:400 24px/1.15 'DM Serif Display',Georgia,serif}
.cabecera-paso-tienda p{margin:0;color:var(--suave);font-size:13px}
.campos-ficha-tienda{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 22px;padding-left:74px}
.campo-ficha-tienda{display:grid;align-content:start;gap:8px;min-width:0;color:var(--texto);font-size:13px;font-weight:700}
.campo-ficha-tienda input,.campo-ficha-tienda textarea,.campo-ficha-tienda select{width:100%;min-width:0;min-height:49px;padding:12px 15px;border:1px solid #e8d7c5;border-radius:13px;background:#fffdf9;color:var(--texto);font:500 14px 'Plus Jakarta Sans',sans-serif;outline:none;transition:border-color .15s,box-shadow .15s}
.campo-ficha-tienda textarea{min-height:94px;resize:vertical;line-height:1.55}
.campo-ficha-tienda input:focus,.campo-ficha-tienda textarea:focus,.campo-ficha-tienda select:focus{border-color:var(--naranja);box-shadow:0 0 0 3px rgba(246,193,119,.22)}
.campo-ficha-tienda input::placeholder,.campo-ficha-tienda textarea::placeholder{color:#8c7b80;font-weight:400}
.campo-ficha-tienda small{color:var(--suave);font-size:11px;font-weight:500;text-align:right}
.interruptor-contacto-tienda{position:relative;display:flex;grid-column:1/-1;align-items:center;gap:13px;min-height:42px;color:var(--texto);cursor:pointer}
.interruptor-contacto-tienda>input{position:absolute;left:0;width:47px;height:26px;margin:0;opacity:0;cursor:pointer}
.pista-interruptor{position:relative;flex:0 0 47px;height:26px;border-radius:999px;background:#d9d2cf;transition:background .18s}
.pista-interruptor::after{position:absolute;top:3px;left:3px;width:20px;height:20px;border-radius:50%;background:#fff;box-shadow:0 1px 4px rgba(46,27,32,.2);content:'';transition:transform .18s}
.interruptor-contacto-tienda>input:checked+.pista-interruptor{background:#58a777}
.interruptor-contacto-tienda>input:checked+.pista-interruptor::after{transform:translateX(21px)}
.interruptor-contacto-tienda>input:focus-visible+.pista-interruptor{outline:3px solid rgba(246,193,119,.45);outline-offset:2px}
.interruptor-contacto-tienda>span:last-child{display:grid;gap:4px}.interruptor-contacto-tienda strong{font-size:13px}.interruptor-contacto-tienda small{color:var(--suave);font-size:11px}
.selector-logo-tienda{display:flex;align-items:center;gap:17px;padding-left:74px}
.marco-logo-tienda{display:grid;place-items:center;flex:0 0 112px;height:112px;overflow:hidden;border:1px solid #f0dcc6;border-radius:18px;background:var(--amancay-naranja-fondo)}
.marco-logo-tienda img{width:100%;height:100%;object-fit:contain;padding:11px}
.datos-logo-tienda{display:grid;justify-items:start;gap:9px}.entrada-logo-oculta{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;clip-path:inset(50%)}
.boton-cambiar-logo{min-height:47px;padding:0 18px;border:1px solid #efc8a6;background:#fff7ef;color:var(--vino);font-size:13px}
.boton-cambiar-logo:hover{background:var(--amancay-naranja-fondo)}.boton-cambiar-logo span{font-size:17px}
.datos-logo-tienda small,.nombre-logo-seleccionado{max-width:330px;color:var(--suave);font-size:11px;line-height:1.5}.nombre-logo-seleccionado{color:var(--amancay-bordo);font-weight:600}
.panel-vista-previa-tienda{padding:24px}
.cabecera-vista-previa{display:flex;align-items:center;gap:14px;margin-bottom:19px}
.icono-vista-previa{display:grid;place-items:center;flex:0 0 56px;height:56px;border-radius:50%;background:var(--amancay-naranja-fondo);color:var(--vino);font-size:25px}
.cabecera-vista-previa h2{margin:0 0 4px;color:var(--vino);font:400 23px/1.15 'DM Serif Display',Georgia,serif}
.cabecera-vista-previa p{margin:0;color:var(--suave);font-size:12px;line-height:1.45}
.tarjeta-vista-previa-tienda{overflow:hidden;border:1px solid var(--borde);border-radius:18px;background:#fffdf9}
.portada-vista-previa{position:relative;display:flex;align-items:center;min-height:180px;padding:24px 28px;overflow:hidden;background:linear-gradient(90deg,rgba(46,27,32,.68),rgba(46,27,32,.08)),url('/logo-amancay.png') 92% 50%/auto 115% no-repeat,linear-gradient(125deg,#765b31,#586444)}
.portada-vista-previa span{position:relative;z-index:1;color:#fff;font:400 26px/1.13 'DM Serif Display',Georgia,serif}
.portada-vista-previa i{position:absolute;bottom:27px;left:28px;width:46px;height:4px;border-radius:3px;background:var(--amancay-mostaza)}
.cuerpo-vista-previa{display:flex;align-items:center;gap:14px;padding:21px 19px}
.logo-vista-previa{display:grid;place-items:center;flex:0 0 88px;height:88px;border-radius:17px;background:#fff7ef}.logo-vista-previa img{width:100%;height:100%;object-fit:contain;padding:9px}
.texto-vista-previa{display:grid;gap:6px;min-width:0}.texto-vista-previa h3{overflow:hidden;margin:0;color:var(--amancay-bordo-oscuro);font:400 24px/1.1 'DM Serif Display',Georgia,serif;text-overflow:ellipsis;white-space:nowrap}.texto-vista-previa p{overflow:hidden;margin:0;color:var(--texto);font-size:14px;text-overflow:ellipsis;white-space:nowrap}.sector-vista-previa{display:flex;align-items:center;gap:8px;color:var(--texto);font-size:12px}.sector-vista-previa>span{font-size:20px}
.contacto-vista-previa{display:flex;align-items:center;justify-content:center;gap:10px;width:calc(100% - 40px);min-height:54px;margin:0 20px 20px;border:0;border-radius:14px;background:var(--amancay-verde-fondo);color:var(--amancay-verde-texto);font:700 13px 'Plus Jakarta Sans',sans-serif}.contacto-vista-previa>span{font-size:20px}.contacto-inactivo{background:var(--amancay-superficie-variante);color:var(--suave);font-weight:600}
.nota-vista-previa{display:flex;align-items:center;gap:13px;margin-top:16px;padding:15px 17px;border:1px solid #f0dcc6;border-radius:15px;background:#fffaf4}.nota-vista-previa>span{display:grid;place-items:center;flex:0 0 32px;height:32px;border:2px solid var(--naranja);border-radius:50%;color:var(--naranja);font-size:18px;font-weight:700}.nota-vista-previa p{margin:0;color:var(--suave);font-size:12px;line-height:1.55}
.barra-acciones-editor{position:sticky;bottom:0;z-index:3;display:flex;justify-content:flex-end;gap:13px;margin:18px -2px 0;padding:14px 2px 16px;border-top:1px solid rgba(231,213,190,.8);background:rgba(251,243,228,.96);backdrop-filter:blur(10px)}
.boton-cancelar-editor,.boton-guardar-editor{min-width:154px;min-height:52px;border-radius:15px;font-size:14px}
.boton-cancelar-editor{border:1px solid #e8a98f;background:#fffaf4;color:var(--vino);font:700 14px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-cancelar-editor:hover{background:#fff1e5}.boton-guardar-editor{min-width:230px}
@media(max-width:1120px){.rejilla-editor-ficha{grid-template-columns:1fr}.panel-vista-previa-tienda .tarjeta-vista-previa-tienda{max-width:760px}}
@media(max-width:740px){
  .contenido-panel.modo-edicion-tienda{width:calc(100% - 30px);padding:24px 0 0}
  .perfil-cabecera-editor{display:none}
  .titulo-editor-ficha h1{font-size:40px}.titulo-editor-ficha p{font-size:12px}
  .rejilla-editor-ficha{gap:15px}.formulario-ficha-tienda,.panel-vista-previa-tienda{padding:18px;border-radius:17px}
  .cabecera-paso-tienda{align-items:flex-start;gap:12px}.numero-paso-tienda{flex-basis:43px;height:43px;font-size:22px}.cabecera-paso-tienda h2{font-size:20px}.cabecera-paso-tienda p{font-size:11px}
  .campos-ficha-tienda{grid-template-columns:1fr;gap:13px;padding-left:0}
  .selector-logo-tienda{align-items:flex-start;gap:12px;padding-left:0}.marco-logo-tienda{flex-basis:82px;height:82px}.boton-cambiar-logo{min-height:42px;font-size:11px}.datos-logo-tienda small{font-size:10px}
  .cabecera-vista-previa{align-items:flex-start}.icono-vista-previa{flex-basis:44px;height:44px;font-size:20px}.cabecera-vista-previa h2{font-size:19px}.cabecera-vista-previa p{font-size:10px}
  .portada-vista-previa{min-height:140px;padding:19px}.portada-vista-previa span{font-size:22px}.portada-vista-previa i{left:19px;bottom:18px}
  .cuerpo-vista-previa{gap:11px;padding:15px}.logo-vista-previa{flex-basis:66px;height:66px}.texto-vista-previa h3{font-size:20px}.texto-vista-previa p{font-size:11px}.sector-vista-previa{font-size:10px}
  .barra-acciones-editor{gap:8px;margin-top:14px;padding:10px 0 calc(10px + env(safe-area-inset-bottom))}.boton-cancelar-editor,.boton-guardar-editor{min-width:0;min-height:47px;flex:1;font-size:12px}.boton-guardar-editor{flex:1.25}
}</style>
