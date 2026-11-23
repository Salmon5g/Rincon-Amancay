<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { FeriaPublica } from '~/types/comprador-publico'

const props = defineProps<{ ferias: FeriaPublica[] }>()

const DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
const montado = ref(false)
const hoy = ref(new Date())
const anio = ref(hoy.value.getFullYear())
const mes = ref(hoy.value.getMonth())
let mesElegidoPorUsuario = false

function inicioDelDia(fecha: Date) {
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate())
}

function mismoDia(primera: Date, segunda: Date) {
  return primera.getFullYear() === segunda.getFullYear()
    && primera.getMonth() === segunda.getMonth()
    && primera.getDate() === segunda.getDate()
}

function fechaReferencia(feria: FeriaPublica) {
  return feria.fechaInicio ?? feria.fechaFin
}

const proximas = computed(() => {
  const desde = inicioDelDia(hoy.value)
  return props.ferias
    .filter((feria) => {
      const referencia = feria.fechaFin ?? feria.fechaInicio
      return referencia ? inicioDelDia(referencia) >= desde : false
    })
    .sort((a, b) => (fechaReferencia(a)?.getTime() ?? Infinity) - (fechaReferencia(b)?.getTime() ?? Infinity))
})

function irAlMesDeProximas() {
  const referencia = proximas.value[0]
  const fecha = referencia ? fechaReferencia(referencia) : null
  if (fecha) {
    anio.value = fecha.getFullYear()
    mes.value = fecha.getMonth()
  } else {
    anio.value = hoy.value.getFullYear()
    mes.value = hoy.value.getMonth()
  }
}

function moverMes(delta: number) {
  mesElegidoPorUsuario = true
  const fecha = new Date(anio.value, mes.value + delta, 1)
  anio.value = fecha.getFullYear()
  mes.value = fecha.getMonth()
}

function volverAHoy() {
  mesElegidoPorUsuario = true
  hoy.value = new Date()
  anio.value = hoy.value.getFullYear()
  mes.value = hoy.value.getMonth()
}

const celdas = computed(() => {
  const primero = new Date(anio.value, mes.value, 1)
  const desplazamiento = (primero.getDay() + 6) % 7
  const inicio = new Date(anio.value, mes.value, 1 - desplazamiento)
  return Array.from({ length: 42 }, (_, indice) => {
    const fecha = new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + indice)
    const eventos = proximas.value
      .filter((feria) => feria.fechaInicio !== null && mismoDia(feria.fechaInicio, fecha))
      .sort((a, b) => (a.fechaInicio?.getTime() ?? 0) - (b.fechaInicio?.getTime() ?? 0))
    return {
      fecha,
      delMes: fecha.getMonth() === mes.value,
      esHoy: mismoDia(fecha, hoy.value),
      eventos,
    }
  })
})

const nombreMes = computed(() => MESES[mes.value])
const resumenMes = computed(() => celdas.value
  .filter((celda) => celda.delMes)
  .reduce((total, celda) => total + celda.eventos.length, 0))

watch(() => proximas.value.length, () => {
  if (!montado.value || mesElegidoPorUsuario || !proximas.value.length) return
  irAlMesDeProximas()
})

onMounted(() => {
  montado.value = true
  hoy.value = new Date()
  irAlMesDeProximas()
})
</script>

<template>
  <section v-if="montado && proximas.length" class="calendario" aria-labelledby="titulo-calendario-ferias">
    <div class="calendario-cabecera">
      <div>
        <p class="calendario-sobretitulo">AGENDA LOCAL</p>
        <h2 id="titulo-calendario-ferias">Calendario de próximas ferias</h2>
        <p class="calendario-bajada">Selecciona una feria en su fecha para abrir la ficha completa.</p>
      </div>
      <div class="calendario-controles">
        <button class="calendario-hoy" type="button" @click="volverAHoy">Hoy</button>
        <button class="calendario-flecha" type="button" aria-label="Mes anterior" @click="moverMes(-1)">←</button>
        <strong class="calendario-mes" aria-live="polite">{{ nombreMes }} {{ anio }}</strong>
        <button class="calendario-flecha" type="button" aria-label="Mes siguiente" @click="moverMes(1)">→</button>
      </div>
    </div>

    <div class="calendario-tabla" role="grid" :aria-label="`Calendario de ferias de ${nombreMes} de ${anio}`">
      <div class="calendario-semana" role="row">
        <span v-for="dia in DIAS" :key="dia" role="columnheader">{{ dia }}</span>
      </div>
      <div class="calendario-celdas">
        <div
          v-for="(celda, indice) in celdas"
          :key="indice"
          class="calendario-celda"
          :class="{ fuera: !celda.delMes, hoy: celda.esHoy, conferia: celda.eventos.length }"
          role="gridcell"
          :aria-label="`${celda.fecha.getDate()} de ${MESES[celda.fecha.getMonth()]}${celda.eventos.length ? `, ${celda.eventos.length} feria${celda.eventos.length === 1 ? '' : 's'}` : ''}`"
        >
          <span class="calendario-numero">{{ celda.fecha.getDate() }}</span>
          <NuxtLink
            v-for="feria in celda.eventos.slice(0, 2)"
            :key="feria.id"
            class="calendario-evento"
            :to="{ path: '/comprador/ferias', query: { feria: feria.id } }"
            :title="feria.nombre"
          >
            {{ feria.nombre }}
          </NuxtLink>
          <span v-if="celda.eventos.length > 2" class="calendario-mas">+{{ celda.eventos.length - 2 }} más</span>
        </div>
      </div>
    </div>

    <p class="calendario-resumen" role="status">
      <template v-if="resumenMes">{{ resumenMes }} feria{{ resumenMes === 1 ? '' : 's' }} en {{ nombreMes }}.</template>
      <template v-else>No hay ferias programadas en {{ nombreMes }}.</template>
      La lista completa está más abajo.
    </p>
  </section>
</template>

<style scoped>
.calendario {
  margin: 0 0 1.6rem;
  padding: clamp(1rem, 2.4vw, 1.5rem);
  border: 1.5px solid var(--amancay-borde);
  border-radius: 24px;
  background: var(--amancay-superficie);
  box-shadow: 0 8px 24px rgba(107, 30, 61, 0.07);
}

.calendario-cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.calendario-sobretitulo {
  margin: 0 0 0.3rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--amancay-mostaza);
}

.calendario h2 {
  margin: 0;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: clamp(1.35rem, 2.6vw, 1.75rem);
  color: var(--amancay-bordo);
}

.calendario-bajada {
  margin: 0.35rem 0 0;
  color: var(--amancay-tinta-suave);
  font-size: 0.9rem;
  line-height: 1.45;
}

.calendario-controles {
  flex: none;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.calendario-controles button {
  min-height: 38px;
  border: 1px solid var(--amancay-borde);
  border-radius: 999px;
  background: var(--amancay-superficie);
  color: var(--amancay-bordo);
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
}

.calendario-controles button:hover {
  border-color: var(--amancay-bordo);
  background: var(--amancay-fondo);
}

.calendario-hoy {
  padding: 0.4rem 0.8rem;
}

.calendario-flecha {
  width: 38px;
  font-size: 1rem !important;
}

.calendario-mes {
  min-width: 8.5rem;
  text-align: center;
  text-transform: capitalize;
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 1.05rem;
}

.calendario-tabla {
  border: 1px solid var(--amancay-borde);
  border-radius: 18px;
  overflow: hidden;
  background: var(--amancay-fondo);
}

.calendario-semana {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  background: var(--amancay-bordo);
}

.calendario-semana span {
  padding: 0.55rem 0.3rem;
  text-align: center;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #fff;
}

.calendario-celdas {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.calendario-celda {
  min-height: 82px;
  min-width: 0;
  padding: 0.4rem;
  border-top: 1px solid var(--amancay-borde);
  border-right: 1px solid var(--amancay-borde);
  background: var(--amancay-superficie);
  box-sizing: border-box;
}

.calendario-celda:nth-child(7n) {
  border-right: 0;
}

.calendario-celda.fuera {
  background: #faf3e9;
  opacity: 0.55;
}

.calendario-celda.hoy {
  box-shadow: inset 0 0 0 2px var(--amancay-mostaza);
}

.calendario-numero {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--amancay-tinta-suave);
}

.calendario-celda.hoy .calendario-numero {
  background: var(--amancay-mostaza);
  color: #3a2607;
}

.calendario-evento {
  display: -webkit-box;
  width: 100%;
  margin-top: 0.2rem;
  padding: 0.18rem 0.3rem;
  border-radius: 7px;
  background: var(--amancay-bordo-suave, #f3dee3);
  color: var(--amancay-bordo);
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.2;
  text-decoration: none;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.calendario-evento:hover,
.calendario-evento:focus-visible {
  background: var(--amancay-bordo);
  color: #fff;
}

.calendario-mas {
  display: block;
  margin-top: 0.2rem;
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--amancay-tinta-suave);
}

.calendario-resumen {
  margin: 0.8rem 0 0;
  color: var(--amancay-tinta-suave);
  font-size: 0.84rem;
}

@media (max-width: 760px) {
  .calendario-cabecera {
    flex-direction: column;
  }

  .calendario-controles {
    width: 100%;
    justify-content: space-between;
  }

  .calendario-celda {
    min-height: 66px;
    padding: 0.25rem;
  }

  .calendario-evento {
    font-size: 0.62rem;
    padding: 0.12rem 0.2rem;
  }
}
</style>
