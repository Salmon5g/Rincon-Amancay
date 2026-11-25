<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { DocumentData, QueryDocumentSnapshot } from 'firebase/firestore'
import { listarPaginaVentasEmprendedora, type VentaRegistradaWeb } from '~/repositories/firebase/ventas-emprendedora'
import type { CuentaEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { ServiciosCliente } from '~/lib/cliente-base'
import { fechaSantiago, filtrarVentas, opcionesFiltro, rangoHistorial, restarDias, resumirVentas } from '~/lib/historial-ventas'

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb }>()
const emit = defineEmits<{ configurar: [] }>()

const hoy = fechaSantiago(new Date())
const desde = ref(restarDias(hoy, 29))
const hasta = ref(hoy)
const productoId = ref('')
const lugar = ref('')
const ventas = ref<VentaRegistradaWeb[]>([])
const cursor = ref<QueryDocumentSnapshot<DocumentData> | null>(null)
const hayMas = ref(false)
const cargando = ref(false)
const cargandoMas = ref(false)
const error = ref('')
let rangoActual: { desde: Date; hasta: Date } | null = null
let version = 0

const opciones = computed(() => opcionesFiltro(ventas.value))
const visibles = computed(() => filtrarVentas(ventas.value, { productoId: productoId.value, lugar: lugar.value }))
const resumen = computed(() => resumirVentas(visibles.value))

function firebase() {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  return $amancay.firebase as ServiciosCliente
}

function mensajeError(fallo: unknown, alternativo: string) {
  const codigo = (fallo as { code?: string })?.code
  if (codigo === 'permission-denied') return 'Tu cuenta no tiene acceso al historial de esta tienda.'
  if (codigo === 'unavailable') return 'No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.'
  return fallo instanceof Error && !codigo ? fallo.message : alternativo
}

async function buscar() {
  const actual = ++version
  error.value = ''
  try {
    rangoActual = rangoHistorial(desde.value, hasta.value)
  } catch (fallo) {
    error.value = fallo instanceof Error ? fallo.message : 'Revisa las fechas.'
    return
  }
  cargando.value = true
  try {
    const pagina = await listarPaginaVentasEmprendedora(firebase(), props.cuenta.tienda.id, rangoActual.desde, rangoActual.hasta)
    if (actual !== version) return
    ventas.value = pagina.ventas
    cursor.value = pagina.cursor
    hayMas.value = pagina.hayMas
    if (productoId.value && !opciones.value.productos.some((opcion) => opcion.id === productoId.value)) productoId.value = ''
    if (lugar.value && !opciones.value.lugares.some((opcion) => opcion.id === lugar.value)) lugar.value = ''
  } catch (fallo) {
    if (actual === version) error.value = mensajeError(fallo, 'No se pudo cargar el historial. Inténtalo de nuevo.')
  } finally {
    if (actual === version) cargando.value = false
  }
}

async function cargarMas() {
  if (!cursor.value || !rangoActual || cargandoMas.value) return
  const actual = version
  cargandoMas.value = true
  error.value = ''
  try {
    const pagina = await listarPaginaVentasEmprendedora(firebase(), props.cuenta.tienda.id, rangoActual.desde, rangoActual.hasta, cursor.value)
    if (actual !== version) return
    const conocidas = new Set(ventas.value.map((venta) => venta.id))
    ventas.value = [...ventas.value, ...pagina.ventas.filter((venta) => !conocidas.has(venta.id))]
    cursor.value = pagina.cursor ?? cursor.value
    hayMas.value = pagina.hayMas
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudieron cargar más ventas.')
  } finally {
    cargandoMas.value = false
  }
}

function rangoRapido(dias: number) {
  hasta.value = hoy
  desde.value = restarDias(hoy, dias - 1)
  void buscar()
}

const pesos = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })
function fechaLegible(fecha: Date) {
  return new Intl.DateTimeFormat('es-CL', { timeZone: 'America/Santiago', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(fecha)
}

onMounted(() => { void buscar() })
</script>

<template>
  <section class="historial" aria-label="Historial de ventas">
    <div v-if="!cuenta.tienda.configuracion.historialVentasActivo" class="aviso-historial" role="status">
      <span aria-hidden="true">ⓘ</span>
      <p>El historial está desactivado: las ventas que registres desde ahora descuentan stock, pero no se guardan aquí.</p>
      <button type="button" @click="emit('configurar')">Activar historial</button>
    </div>

    <form class="filtros" @submit.prevent="buscar">
      <label>Desde<input v-model="desde" type="date" :max="hasta" required /></label>
      <label>Hasta<input v-model="hasta" type="date" :min="desde" :max="hoy" required /></label>
      <button class="boton-vino" type="submit" :disabled="cargando">{{ cargando ? 'Buscando…' : 'Buscar' }}</button>
      <div class="rapidos" role="group" aria-label="Rangos rápidos">
        <button type="button" @click="rangoRapido(1)">Hoy</button>
        <button type="button" @click="rangoRapido(7)">7 días</button>
        <button type="button" @click="rangoRapido(30)">30 días</button>
      </div>
    </form>

    <div v-if="ventas.length" class="filtros secundarios">
      <label>Producto
        <select v-model="productoId">
          <option value="">Todos los productos</option>
          <option v-for="opcion in opciones.productos" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
        </select>
      </label>
      <label>Lugar
        <select v-model="lugar">
          <option value="">Todos los lugares</option>
          <option v-for="opcion in opciones.lugares" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
        </select>
      </label>
    </div>

    <p v-if="error" class="mensaje error" role="alert">{{ error }}</p>

    <div v-if="ventas.length" class="resumen" aria-label="Resumen del período">
      <div><small>Ventas</small><strong>{{ resumen.ventas }}</strong></div>
      <div><small>Unidades</small><strong>{{ resumen.unidades }}</strong></div>
      <div><small>Total</small><strong>{{ pesos.format(resumen.total) }}</strong></div>
    </div>
    <p v-if="hayMas" class="nota">El resumen considera las ventas cargadas. Carga más para incluir todo el período.</p>

    <p v-if="cargando && !ventas.length" class="estado" role="status">Cargando ventas…</p>
    <div v-else-if="!ventas.length && !error" class="vacio">
      <span aria-hidden="true">↗</span>
      <strong>No hay ventas en este período</strong>
      <p>Prueba con otro rango de fechas o registra una venta.</p>
    </div>
    <p v-else-if="ventas.length && !visibles.length" class="estado">No hay ventas con estos filtros.</p>

    <ul v-if="visibles.length" class="lista">
      <li v-for="venta in visibles" :key="venta.id" class="fila">
        <div class="principal">
          <strong>{{ venta.productoNombre }}</strong>
          <small v-if="venta.varianteNombre">{{ venta.varianteNombre }}</small>
          <small class="lugar">{{ venta.lugarTipo === 'feria' ? 'Feria: ' : '' }}{{ venta.lugarNombre }}</small>
        </div>
        <div class="cifras">
          <strong>{{ pesos.format(venta.total) }}</strong>
          <small>{{ venta.cantidad }} × {{ pesos.format(venta.precioUnitario) }}</small>
          <time :datetime="venta.fecha.toISOString()">{{ fechaLegible(venta.fecha) }}</time>
        </div>
      </li>
    </ul>
    <button v-if="hayMas" class="boton-claro cargar-mas" type="button" :disabled="cargandoMas" @click="cargarMas">{{ cargandoMas ? 'Cargando…' : 'Cargar más ventas' }}</button>
  </section>
</template>

<style scoped>
.historial{display:grid;gap:16px}
.aviso-historial{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding:13px 16px;border:1px solid #edceaa;border-radius:16px;background:#fff4e3;color:#66482e}
.aviso-historial p{flex:1;min-width:200px;margin:0;font-size:13px;line-height:1.5}
.aviso-historial button{min-height:40px;padding:0 14px;border:1px solid #d9a96a;border-radius:12px;background:#fff;color:#66482e;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.filtros{display:flex;align-items:flex-end;flex-wrap:wrap;gap:12px}
.filtros label{display:grid;gap:6px;color:var(--texto,#3f222c);font-size:12px;font-weight:800}
.filtros input,.filtros select{min-height:44px;padding:0 12px;border:1px solid var(--borde,#ecd8cb);border-radius:13px;background:#fffdf9;color:var(--texto,#3f222c);font:500 14px 'Plus Jakarta Sans',sans-serif}
.filtros.secundarios label{flex:1;min-width:200px}
.filtros.secundarios select{width:100%}
.rapidos{display:flex;gap:6px}
.rapidos button{min-height:40px;padding:0 12px;border:1px solid var(--borde,#ecd8cb);border-radius:999px;background:#fff;color:var(--vino,#74244e);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 18px;border-radius:13px;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fff;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.boton-claro:focus-visible,.boton-vino:focus-visible,.rapidos button:focus-visible,.aviso-historial button:focus-visible,.filtros input:focus-visible,.filtros select:focus-visible{outline:3px solid rgba(116,36,78,.3);outline-offset:2px}
.resumen{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.resumen div{display:grid;gap:4px;padding:14px 16px;border:1px solid var(--borde,#ecd8cb);border-radius:18px;background:#fff}
.resumen small{color:var(--suave,#7a6468);font-size:12px;font-weight:700}
.resumen strong{color:var(--vino,#74244e);font:400 26px/1.1 'DM Serif Display',Georgia,serif}
.lista{display:grid;gap:10px;margin:0;padding:0;list-style:none}
.fila{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:14px 16px;border:1px solid var(--borde,#ecd8cb);border-radius:18px;background:#fff}
.principal,.cifras{display:grid;gap:3px;min-width:0}
.cifras{justify-items:end;text-align:right}
.principal strong{color:var(--vino,#74244e);font-size:15px}
.cifras strong{color:var(--texto,#3f222c);font-size:15px}
.fila small,.fila time{color:var(--suave,#7a6468);font-size:12px}
.lugar{color:#9a4a10 !important}
.cargar-mas{width:100%}
.mensaje{margin:0;padding:12px 14px;border-radius:14px;font-size:13px}
.mensaje.error{background:#fff0ee;color:#852e3f}
.nota,.estado{margin:0;color:var(--suave,#7a6468);font-size:13px}
.vacio{display:grid;justify-items:center;gap:6px;padding:32px 20px;border:1px dashed var(--borde,#ecd8cb);border-radius:20px;text-align:center;color:var(--suave,#7a6468)}
.vacio span{color:var(--vino,#74244e);font-size:30px}
.vacio strong{color:var(--vino,#74244e)}
.vacio p{margin:0;font-size:13px}
@media(max-width:560px){
  .resumen{grid-template-columns:minmax(0,1fr)}
  .fila{align-items:flex-start;flex-direction:column}
  .cifras{justify-items:start;text-align:left}
  .filtros label{flex:1}
}
</style>
