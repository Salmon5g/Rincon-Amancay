<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { DocumentData, QueryDocumentSnapshot } from 'firebase/firestore'
import { listarPaginaVentasEmprendedora, resumirPeriodoVentasEmprendedora, type ResumenPeriodoVentas, type VentaRegistradaWeb } from '~/repositories/firebase/ventas-emprendedora'
import type { CuentaEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { ServiciosCliente } from '~/lib/cliente-base'
import {
  agruparPorDia,
  barrasPorDia,
  etiquetaDia,
  fechaCorta,
  fechaSantiago,
  filtrarVentas,
  opcionesFiltro,
  periodoHistorial,
  productoMasVendido,
  rangoHistorial,
  restarDias,
  resumirVentas,
  variacionTotal,
  ventasCsv,
  type TipoPeriodo,
} from '~/lib/historial-ventas'

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb }>()
const emit = defineEmits<{ configurar: [] }>()

const hoy = fechaSantiago(new Date())
const tipoPeriodo = ref<TipoPeriodo>('mes')
const desdeLibre = ref(restarDias(hoy, 6))
const hastaLibre = ref(hoy)
const eligiendoFechas = ref(false)
const texto = ref('')
const lugar = ref('')
const diaElegido = ref('')
const ventas = ref<VentaRegistradaWeb[]>([])
const cursor = ref<QueryDocumentSnapshot<DocumentData> | null>(null)
const hayMas = ref(false)
const cargando = ref(false)
const cargandoMas = ref(false)
const error = ref('')
const resumenPeriodo = ref<ResumenPeriodoVentas | null>(null)
const resumenAnterior = ref<ResumenPeriodoVentas | null>(null)
let rangoActual: { desde: Date; hasta: Date } | null = null
let version = 0

const periodo = computed(() => periodoHistorial(tipoPeriodo.value, hoy, desdeLibre.value, hastaLibre.value))
const opciones = computed(() => opcionesFiltro(ventas.value))
const hayFiltros = computed(() => Boolean(texto.value.trim() || lugar.value || diaElegido.value))
const visibles = computed(() => filtrarVentas(ventas.value, { lugar: lugar.value, texto: texto.value, dia: diaElegido.value }))
const grupos = computed(() => agruparPorDia(visibles.value))
const barras = computed(() => barrasPorDia(filtrarVentas(ventas.value, { lugar: lugar.value, texto: texto.value }), periodo.value.desde, periodo.value.hasta))
const resumenCargado = computed(() => resumirVentas(visibles.value))
const resumen = computed(() => (!hayFiltros.value && resumenPeriodo.value) ? resumenPeriodo.value : resumenCargado.value)
const resumenParcial = computed(() => hayMas.value && (hayFiltros.value || !resumenPeriodo.value))
const masVendido = computed(() => productoMasVendido(visibles.value))
const variacion = computed(() => (!hayFiltros.value && resumenPeriodo.value && resumenAnterior.value)
  ? variacionTotal(resumenPeriodo.value.total, resumenAnterior.value.total)
  : null)
const nombreLugar = computed(() => opciones.value.lugares.find((opcion) => opcion.id === lugar.value)?.nombre ?? '')
const tituloPeriodo = computed(() => tipoPeriodo.value === 'personalizado' ? periodo.value.titulo : periodo.value.titulo.charAt(0).toLocaleUpperCase('es-CL') + periodo.value.titulo.slice(1))

const pesos = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })
const horaChile = new Intl.DateTimeFormat('es-CL', { timeZone: 'America/Santiago', hour: '2-digit', minute: '2-digit', hour12: false })

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

async function cargarResumenes(actual: number) {
  resumenPeriodo.value = null
  resumenAnterior.value = null
  try {
    const anterior = rangoHistorial(periodo.value.anterior.desde, periodo.value.anterior.hasta)
    const [periodoActual, periodoAnterior] = await Promise.all([
      resumirPeriodoVentasEmprendedora(firebase(), props.cuenta.tienda.id, rangoActual!.desde, rangoActual!.hasta),
      resumirPeriodoVentasEmprendedora(firebase(), props.cuenta.tienda.id, anterior.desde, anterior.hasta),
    ])
    if (actual !== version) return
    resumenPeriodo.value = periodoActual
    resumenAnterior.value = periodoAnterior
  } catch {
  }
}

async function buscar() {
  const actual = ++version
  error.value = ''
  diaElegido.value = ''
  try {
    rangoActual = rangoHistorial(periodo.value.desde, periodo.value.hasta)
  } catch (fallo) {
    error.value = fallo instanceof Error ? fallo.message : 'Revisa las fechas.'
    return
  }
  cargando.value = true
  void cargarResumenes(actual)
  try {
    const pagina = await listarPaginaVentasEmprendedora(firebase(), props.cuenta.tienda.id, rangoActual.desde, rangoActual.hasta)
    if (actual !== version) return
    ventas.value = pagina.ventas
    cursor.value = pagina.cursor
    hayMas.value = pagina.hayMas
    if (lugar.value && !opciones.value.lugares.some((opcion) => opcion.id === lugar.value)) lugar.value = ''
  } catch (fallo) {
    if (actual === version) {
      ventas.value = []
      hayMas.value = false
      error.value = mensajeError(fallo, 'No se pudo cargar el historial. Inténtalo de nuevo.')
    }
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

function elegirPeriodo(tipo: Exclude<TipoPeriodo, 'personalizado'>) {
  eligiendoFechas.value = false
  if (tipoPeriodo.value === tipo && ventas.value.length) return
  tipoPeriodo.value = tipo
  void buscar()
}

function aplicarFechas() {
  if (!desdeLibre.value || !hastaLibre.value) {
    error.value = 'Elige las dos fechas.'
    return
  }
  if (hastaLibre.value < desdeLibre.value) {
    error.value = 'La fecha final debe ser igual o posterior a la inicial.'
    return
  }
  tipoPeriodo.value = 'personalizado'
  eligiendoFechas.value = false
  void buscar()
}

function alternarDia(dia: string) {
  diaElegido.value = diaElegido.value === dia ? '' : dia
}

function limpiarFiltros() {
  texto.value = ''
  lugar.value = ''
  diaElegido.value = ''
}

function descargar() {
  const archivo = new Blob([ventasCsv(visibles.value)], { type: 'text/csv;charset=utf-8' })
  const enlace = document.createElement('a')
  enlace.href = URL.createObjectURL(archivo)
  enlace.download = `ventas-${periodo.value.desde}-a-${periodo.value.hasta}.csv`
  enlace.click()
  setTimeout(() => URL.revokeObjectURL(enlace.href), 1000)
}

function etiquetaBarra(barra: { dia: string; total: number; conFeria: boolean }) {
  return `${etiquetaDia(barra.dia, hoy)}: ${barra.total ? pesos.format(barra.total) : 'sin ventas'}${barra.conFeria ? ', con venta en feria' : ''}`
}

onMounted(() => { void buscar() })
</script>

<template>
  <section class="historial" data-guia="historial-seccion" aria-labelledby="titulo-historial">
    <div v-if="!cuenta.tienda.configuracion.historialVentasActivo" class="aviso-historial" role="status">
      <span aria-hidden="true">ⓘ</span>
      <p>El historial está apagado: las ventas que registres desde ahora descuentan stock, pero no se guardan aquí.</p>
      <button type="button" @click="emit('configurar')">Activar historial</button>
    </div>

    <header class="cabeza">
      <div>
        <span class="sobre">TUS VENTAS</span>
        <h2 id="titulo-historial">¿Cómo te ha ido?</h2>
        <p>Revisa lo que vendiste, cuándo y dónde.</p>
      </div>
      <button class="boton-claro" data-guia="historial-descargar" type="button" :disabled="!visibles.length" :title="visibles.length ? 'Descarga una planilla con las ventas que estás viendo' : 'No hay ventas para descargar'" @click="descargar">
        <span aria-hidden="true">↓</span> Descargar
      </button>
    </header>

    <div class="periodos" data-guia="historial-periodos" role="group" aria-label="Período">
      <button type="button" :aria-pressed="tipoPeriodo === 'hoy'" :class="{ activo: tipoPeriodo === 'hoy' }" @click="elegirPeriodo('hoy')">Hoy</button>
      <button type="button" :aria-pressed="tipoPeriodo === 'semana'" :class="{ activo: tipoPeriodo === 'semana' }" @click="elegirPeriodo('semana')">Esta semana</button>
      <button type="button" :aria-pressed="tipoPeriodo === 'mes'" :class="{ activo: tipoPeriodo === 'mes' }" @click="elegirPeriodo('mes')">Este mes</button>
      <button type="button" :aria-pressed="tipoPeriodo === 'personalizado'" :aria-expanded="eligiendoFechas" :class="{ activo: tipoPeriodo === 'personalizado' }" @click="eligiendoFechas = !eligiendoFechas">
        <span aria-hidden="true">▦</span> {{ tipoPeriodo === 'personalizado' ? periodo.titulo : 'Elegir fechas' }}
      </button>
    </div>

    <form v-if="eligiendoFechas" class="fechas" @submit.prevent="aplicarFechas">
      <label>Desde<input v-model="desdeLibre" type="date" :max="hastaLibre || hoy" required /></label>
      <label>Hasta<input v-model="hastaLibre" type="date" :min="desdeLibre" :max="hoy" required /></label>
      <button class="boton-vino" type="submit">Ver ventas</button>
      <button class="boton-claro" type="button" @click="eligiendoFechas = false">Cancelar</button>
    </form>

    <p v-if="error" class="mensaje error" role="alert">{{ error }} <button type="button" @click="buscar">Reintentar</button></p>

    <div class="resumen" data-guia="historial-resumen" aria-live="polite">
      <div class="tarjeta destacada">
        <small>Total vendido · {{ tituloPeriodo }}</small>
        <strong>{{ cargando && !ventas.length ? '…' : pesos.format(resumen.total) }}</strong>
        <span v-if="variacion && variacion.tendencia !== 'igual'" class="variacion">
          {{ variacion.tendencia === 'sube' ? '↑' : '↓' }} {{ pesos.format(variacion.diferencia) }} {{ variacion.tendencia === 'sube' ? 'más' : 'menos' }} {{ periodo.comparacion }}
        </span>
        <span v-else-if="variacion" class="variacion">Igual {{ periodo.comparacion }}</span>
        <span v-else-if="hayFiltros" class="variacion">Según los filtros elegidos</span>
      </div>
      <div class="tarjeta">
        <small>Ventas</small>
        <strong>{{ cargando && !ventas.length ? '…' : resumen.ventas }}</strong>
        <span>{{ resumen.unidades }} {{ resumen.unidades === 1 ? 'unidad' : 'unidades' }}</span>
      </div>
      <div class="tarjeta">
        <small>Lo más vendido</small>
        <b class="mas-vendido">{{ masVendido?.nombre ?? '—' }}</b>
        <span v-if="masVendido">{{ masVendido.unidades }} {{ masVendido.unidades === 1 ? 'unidad' : 'unidades' }}</span>
      </div>
    </div>
    <p v-if="resumenParcial" class="nota">Estos números consideran las ventas cargadas. Usa «Ver ventas anteriores» para sumar el resto del período.</p>

    <div v-if="barras.length && ventas.length" class="grafico" data-guia="historial-grafico">
      <div class="grafico-cabeza">
        <small>Ventas por día</small>
        <small>Toca una barra para ver ese día</small>
      </div>
      <div class="barras" role="group" aria-label="Ventas por día">
        <button
          v-for="barra in barras"
          :key="barra.dia"
          type="button"
          class="barra"
          :class="{ feria: barra.conFeria, elegida: diaElegido === barra.dia, vacia: !barra.total }"
          :aria-pressed="diaElegido === barra.dia"
          :aria-label="etiquetaBarra(barra)"
          :title="etiquetaBarra(barra)"
          :disabled="!barra.total"
          @click="alternarDia(barra.dia)"
        ><span :style="{ height: `${Math.max(barra.proporcion * 100, barra.total ? 6 : 2)}%` }" /></button>
      </div>
      <div class="ejes"><small>{{ fechaCorta(barras[0]!.dia) }}</small><small>{{ fechaCorta(barras.at(-1)!.dia) }}</small></div>
      <div class="leyenda"><small><i class="muestra feria" /> Día con feria</small><small><i class="muestra" /> Otro lugar</small></div>
    </div>

    <div v-if="ventas.length" class="filtros" data-guia="historial-filtros">
      <label class="buscador">
        <span class="sr-only">Buscar producto</span>
        <span aria-hidden="true">⌕</span>
        <input v-model="texto" type="search" placeholder="Buscar producto" />
      </label>
      <label class="selector">
        <span class="sr-only">Lugar</span>
        <select v-model="lugar">
          <option value="">Todos los lugares</option>
          <option v-for="opcion in opciones.lugares" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
        </select>
      </label>
    </div>
    <div v-if="hayFiltros" class="activos" aria-label="Filtros activos">
      <button v-if="diaElegido" type="button" class="chip-activo" @click="diaElegido = ''">{{ etiquetaDia(diaElegido, hoy) }} <span aria-hidden="true">×</span><span class="sr-only">Quitar filtro de día</span></button>
      <button v-if="lugar" type="button" class="chip-activo" @click="lugar = ''">{{ nombreLugar }} <span aria-hidden="true">×</span><span class="sr-only">Quitar filtro de lugar</span></button>
      <button v-if="texto.trim()" type="button" class="chip-activo" @click="texto = ''">«{{ texto.trim() }}» <span aria-hidden="true">×</span><span class="sr-only">Quitar búsqueda</span></button>
      <button type="button" class="limpiar" @click="limpiarFiltros">Quitar filtros</button>
    </div>

    <p v-if="cargando && !ventas.length" class="estado" role="status">Cargando tus ventas…</p>
    <div v-else-if="!ventas.length && !error" class="vacio">
      <span aria-hidden="true">✿</span>
      <strong>Sin ventas {{ tipoPeriodo === 'hoy' ? 'hoy' : 'en este período' }}</strong>
      <p>Cuando registres una venta aparecerá aquí. También puedes elegir otras fechas.</p>
    </div>
    <div v-else-if="ventas.length && !visibles.length" class="vacio">
      <strong>No hay ventas con estos filtros</strong>
      <button type="button" class="limpiar" @click="limpiarFiltros">Quitar filtros</button>
    </div>

    <div v-if="grupos.length" class="lista">
      <section v-for="grupo in grupos" :key="grupo.dia" class="dia" :aria-label="etiquetaDia(grupo.dia, hoy)">
        <header class="dia-cabeza">
          <h3>{{ etiquetaDia(grupo.dia, hoy) }}</h3>
          <small>{{ grupo.ventas.length }} {{ grupo.ventas.length === 1 ? 'venta' : 'ventas' }} · {{ pesos.format(grupo.total) }}</small>
        </header>
        <ul>
          <li v-for="venta in grupo.ventas" :key="venta.id" class="fila">
            <span class="inicial" aria-hidden="true">{{ venta.productoNombre.trim().charAt(0).toUpperCase() || '✿' }}</span>
            <div class="detalle">
              <strong>{{ venta.productoNombre }}</strong>
              <div class="meta">
                <span>{{ venta.varianteNombre ? `${venta.varianteNombre} · ` : '' }}{{ venta.cantidad }} × {{ pesos.format(venta.precioUnitario) }}</span>
                <span class="etiqueta" :class="venta.lugarTipo">{{ venta.lugarTipo === 'feria' && !/^feria\b/i.test(venta.lugarNombre) ? 'Feria · ' : '' }}{{ venta.lugarNombre }}</span>
                <time :datetime="venta.fecha.toISOString()">{{ horaChile.format(venta.fecha) }}</time>
              </div>
            </div>
            <strong class="total">{{ pesos.format(venta.total) }}</strong>
          </li>
        </ul>
      </section>
    </div>
    <button v-if="hayMas" class="boton-claro cargar-mas" type="button" :disabled="cargandoMas" @click="cargarMas">{{ cargandoMas ? 'Cargando…' : 'Ver ventas anteriores' }}</button>
  </section>
</template>

<style scoped>
.historial{display:grid;gap:16px}
.aviso-historial{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding:13px 16px;border:1px solid #edceaa;border-radius:16px;background:#fff4e3;color:#66482e}
.aviso-historial p{flex:1;min-width:200px;margin:0;font-size:13px;line-height:1.5}
.aviso-historial button{min-height:40px;padding:0 14px;border:1px solid #d9a96a;border-radius:12px;background:#fff;color:#66482e;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.cabeza{display:flex;align-items:flex-end;justify-content:space-between;flex-wrap:wrap;gap:12px}
.sobre{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.15em}
.cabeza h2{margin:6px 0 4px;color:var(--vino,#74244e);font:400 30px/1.1 'DM Serif Display',Georgia,serif}
.cabeza p{margin:0;color:var(--suave,#7a6468);font-size:14px}
.periodos{display:flex;flex-wrap:wrap;gap:8px}
.periodos button,.chip-activo{display:inline-flex;align-items:center;gap:6px;min-height:42px;padding:0 16px;border:1px solid var(--borde,#ecd8cb);border-radius:999px;background:#fffdf9;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer;transition:background .15s ease,border-color .15s ease}
.periodos button:hover,.chip-activo:hover{border-color:#d9a981}
.periodos button.activo{border-color:var(--vino,#74244e);background:var(--vino,#74244e);color:#fff}
.fechas{display:flex;align-items:flex-end;flex-wrap:wrap;gap:10px;padding:14px;border:1px solid var(--borde,#ecd8cb);border-radius:18px;background:#fffdf9}
.fechas label{display:grid;gap:6px;color:var(--texto,#3f222c);font-size:12px;font-weight:800}
.fechas input,.filtros input,.filtros select{min-height:44px;padding:0 12px;border:1px solid var(--borde,#ecd8cb);border-radius:13px;background:#fffdf9;color:var(--texto,#3f222c);font:500 14px 'Plus Jakarta Sans',sans-serif}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:44px;padding:0 18px;border-radius:13px;font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fffdf9;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.resumen{display:grid;grid-template-columns:minmax(0,1.4fr) repeat(2,minmax(0,1fr));gap:12px}
.tarjeta{display:grid;align-content:start;gap:4px;padding:16px 18px;border:1px solid var(--borde,#ecd8cb);border-radius:20px;background:#fffdf9}
.tarjeta small{color:var(--suave,#7a6468);font-size:12px;font-weight:700}
.tarjeta strong{color:var(--vino,#74244e);font:400 30px/1.1 'DM Serif Display',Georgia,serif}
.tarjeta span{color:var(--suave,#7a6468);font-size:12px}
.tarjeta.destacada{border-color:var(--vino,#74244e);background:var(--vino,#74244e)}
.tarjeta.destacada small{color:#f3dee3}
.tarjeta.destacada strong{color:#fff;font-size:36px}
.tarjeta.destacada .variacion{color:#fde2b0;font-weight:700}
.mas-vendido{overflow:hidden;margin-top:6px;color:var(--texto,#3f222c);font-size:16px;text-overflow:ellipsis;white-space:nowrap}
.grafico{display:grid;gap:8px;padding:16px 18px;border:1px solid var(--borde,#ecd8cb);border-radius:20px;background:#fffdf9}
.grafico-cabeza,.ejes,.leyenda{display:flex;justify-content:space-between;gap:12px}
.leyenda{justify-content:flex-start;gap:16px}
.grafico small{color:var(--suave,#7a6468);font-size:12px}
.barras{display:flex;align-items:stretch;gap:3px;height:96px}
.barra{display:flex;flex:1;align-items:flex-end;min-width:0;padding:0;border:0;border-radius:6px 6px 0 0;background:transparent;cursor:pointer}
.barra span{width:100%;border-radius:6px 6px 0 0;background:#f6c177;transition:background .15s ease}
.barra.feria span{background:var(--vino,#74244e)}
.barra.vacia{cursor:default}
.barra.vacia span{background:#efe1cd}
.barra:hover:not(:disabled) span{filter:brightness(.92)}
.barra.elegida span{outline:3px solid #e76b62;outline-offset:1px}
.muestra{display:inline-block;width:10px;height:10px;margin-right:4px;border-radius:3px;background:#f6c177;vertical-align:-1px}
.muestra.feria{background:var(--vino,#74244e)}
.filtros{display:flex;flex-wrap:wrap;gap:10px}
.buscador{position:relative;flex:1 1 240px}
.buscador>span[aria-hidden]{position:absolute;top:50%;left:14px;color:var(--suave,#7a6468);transform:translateY(-50%)}
.buscador input{width:100%;padding-left:36px;border-radius:999px}
.selector{flex:0 1 240px}
.selector select{width:100%;border-radius:999px}
.activos{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.chip-activo{min-height:36px;border-color:var(--vino,#74244e);background:var(--vino,#74244e);color:#fff}
.limpiar{min-height:36px;padding:0 8px;border:0;background:none;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;text-decoration:underline;cursor:pointer}
.lista{display:grid;gap:14px}
.dia{overflow:hidden;border:1px solid var(--borde,#ecd8cb);border-radius:20px;background:#fffdf9}
.dia-cabeza{display:flex;align-items:baseline;justify-content:space-between;flex-wrap:wrap;gap:6px 12px;padding:11px 18px;background:#f6e8d3}
.dia-cabeza h3{margin:0;color:var(--vino,#74244e);font:400 18px 'DM Serif Display',Georgia,serif}
.dia-cabeza small{color:#66482e;font-size:12px;font-weight:700}
.dia ul{margin:0;padding:0;list-style:none}
.fila{display:grid;grid-template-columns:44px minmax(0,1fr) auto;align-items:center;gap:14px;padding:13px 18px}
.fila+.fila{border-top:1px solid #efe1cd}
.inicial{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;background:#fbe3b8;color:var(--vino,#74244e);font:400 20px 'DM Serif Display',Georgia,serif}
.detalle{display:grid;gap:5px;min-width:0}
.detalle strong{overflow:hidden;color:var(--texto,#3f222c);font-size:15px;text-overflow:ellipsis;white-space:nowrap}
.meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px 10px;color:var(--suave,#7a6468);font-size:12px}
.etiqueta{padding:3px 9px;border-radius:999px;background:#fef0cd;color:#7a5200;font-weight:700}
.etiqueta.feria{background:#ddebd3;color:#2f6b2e}
.total{color:var(--vino,#74244e);font:400 19px 'DM Serif Display',Georgia,serif;white-space:nowrap}
.cargar-mas{width:100%}
.mensaje{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:0;padding:12px 14px;border-radius:14px;font-size:13px}
.mensaje.error{background:#fff0ee;color:#852e3f}
.mensaje button{min-height:36px;padding:0 12px;border:1px solid currentColor;border-radius:10px;background:#fff;color:inherit;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.nota,.estado{margin:0;color:var(--suave,#7a6468);font-size:13px}
.vacio{display:grid;justify-items:center;gap:6px;padding:32px 20px;border:1px dashed var(--borde,#ecd8cb);border-radius:20px;text-align:center;color:var(--suave,#7a6468)}
.vacio span{color:#e76b62;font-size:30px}
.vacio strong{color:var(--vino,#74244e);font:400 20px 'DM Serif Display',Georgia,serif}
.vacio p{max-width:360px;margin:0;font-size:13px;line-height:1.5}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}
button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid rgba(116,36,78,.3);outline-offset:2px}
@media(prefers-reduced-motion:reduce){.periodos button,.chip-activo,.barra span{transition:none}}
@media(max-width:760px){
  .resumen{grid-template-columns:repeat(2,minmax(0,1fr))}
  .tarjeta.destacada{grid-column:1 / -1}
  .barras{gap:2px}
}
@media(max-width:560px){
  .cabeza h2{font-size:25px}
  .periodos button{flex:1 1 auto;justify-content:center;padding:0 12px}
  .tarjeta.destacada strong{font-size:30px}
  .selector{flex:1 1 100%}
  .fila{grid-template-columns:40px minmax(0,1fr);padding:12px 14px}
  .inicial{width:40px;height:40px}
  .total{grid-column:2}
  .fechas label{flex:1 1 140px}
  .fechas input{width:100%}
}
</style>
