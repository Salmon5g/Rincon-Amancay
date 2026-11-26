<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  actualizarInvitacionEnCache,
  agruparPorDia,
  bloqueFecha,
  claveDia,
  cuantoFalta,
  diaFeria,
  enlaceComoLlegar,
  haceCuanto,
  horarioFeria,
  invitacionesEnCache,
  obtenerInvitacionesFeria,
  seccionesFerias,
  type EstadoInvitacionFeria,
  type InvitacionFeriaWeb,
} from '~/lib/ferias-emprendedora'
import type { CuentaEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { Operacion } from '~/lib/cliente-base'

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb }>()
const emit = defineEmits<{ pendientes: [cantidad: number] }>()

const invitaciones = ref<InvitacionFeriaWeb[]>(invitacionesEnCache(props.cuenta.uid) ?? [])
const cargando = ref(false)
const error = ref('')
const aviso = ref('')
const respondiendo = ref<string | null>(null)
const porRechazar = ref<InvitacionFeriaWeb | null>(null)
const diaSeleccionado = ref<string | null>(null)
const expandida = ref<string | null>(null)
const pasadasAbiertas = ref(false)
const mesVisible = ref(inicioMes(new Date()))
const ahora = ref(new Date())

const etiquetas: Record<EstadoInvitacionFeria, string> = {
  pendiente: 'Por responder',
  aceptada: 'Confirmada',
  rechazada: 'No participas',
  agregada_admin: 'Te inscribió la administración',
}

const secciones = computed(() => seccionesFerias(invitaciones.value, ahora.value))
const agenda = computed(() => secciones.value.agenda
  .map((mes) => ({ ...mes, ferias: mes.ferias.filter((feria) => feria.id !== secciones.value.proxima?.id) }))
  .filter((mes) => mes.ferias.length))
const pendientes = computed(() => invitaciones.value.filter((invitacion) => invitacion.estado === 'pendiente'))
const porDia = computed(() => agruparPorDia(invitaciones.value))
const delDia = computed(() => diaSeleccionado.value ? porDia.value.get(diaSeleccionado.value) ?? [] : [])
const tituloDia = computed(() => delDia.value[0]?.fechaInicio ? diaFeria(delDia.value[0].fechaInicio) : '')

function inicioMes(fecha: Date) {
  const [anio, mes] = claveDia(fecha).split('-').map(Number)
  return { anio: anio!, mes: mes! }
}

const nombreMes = computed(() => {
  const texto = new Intl.DateTimeFormat('es-CL', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(mesVisible.value.anio, mesVisible.value.mes - 1, 1)))
  return texto.charAt(0).toLocaleUpperCase('es-CL') + texto.slice(1)
})

const celdas = computed(() => {
  const { anio, mes } = mesVisible.value
  const primero = new Date(Date.UTC(anio, mes - 1, 1))
  const desplazamiento = (primero.getUTCDay() + 6) % 7
  const diasMes = new Date(Date.UTC(anio, mes, 0)).getUTCDate()
  const hoy = claveDia(ahora.value)
  const lista: ({ clave: string; numero: number; ferias: InvitacionFeriaWeb[]; hoy: boolean } | null)[] = Array.from({ length: desplazamiento }, () => null)
  for (let dia = 1; dia <= diasMes; dia++) {
    const clave = `${anio}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`
    lista.push({ clave, numero: dia, ferias: porDia.value.get(clave) ?? [], hoy: clave === hoy })
  }
  return lista
})

function tipoCelda(ferias: InvitacionFeriaWeb[]) {
  if (ferias.some((feria) => feria.estado === 'pendiente')) return 'pendiente'
  if (ferias.length) return 'confirmada'
  return ''
}

function cambiarMes(delta: number) {
  const total = mesVisible.value.anio * 12 + (mesVisible.value.mes - 1) + delta
  mesVisible.value = { anio: Math.floor(total / 12), mes: (total % 12) + 1 }
}

function enfocarProximoMes() {
  const siguiente = [secciones.value.proxima, secciones.value.pendientes[0]]
    .filter((feria): feria is InvitacionFeriaWeb => Boolean(feria?.fechaInicio))
    .sort((a, b) => a.fechaInicio!.getTime() - b.fechaInicio!.getTime())[0]
  if (siguiente?.fechaInicio) mesVisible.value = inicioMes(siguiente.fechaInicio)
}

function elegirDia(clave: string, ferias: InvitacionFeriaWeb[]) {
  if (!ferias.length) return
  diaSeleccionado.value = diaSeleccionado.value === clave ? null : clave
}

function alternarDetalle(id: string) {
  expandida.value = expandida.value === id ? null : id
}

function llamarApi() {
  const { $amancay } = useNuxtApp()
  const cliente = $amancay as typeof $amancay & { llamar?: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>> }
  if (typeof cliente?.llamar !== 'function') throw Object.assign(new Error('Este sitio está en modo de solo consulta; no se pueden cargar las ferias.'), { code: 'solo-consulta' })
  return cliente.llamar.bind(cliente)
}

function mensajeError(fallo: unknown, alternativo: string) {
  const codigo = (fallo as { code?: string })?.code
  if (codigo === 'solo-consulta') return (fallo as Error).message
  if (codigo === 'sin-permiso') return 'Tu cuenta no tiene acceso a las invitaciones a ferias.'
  if (codigo === 'no-encontrado') return 'La feria ya no está publicada.'
  if (codigo === 'conflicto') return 'Esta invitación ya fue respondida. Actualiza la lista.'
  if (codigo === 'conexion-interrumpida' || (fallo as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se pudo confirmar con el servidor. Revisa tu conexión e inténtalo de nuevo.'
  return alternativo
}

async function cargar(forzar = false) {
  cargando.value = true
  error.value = ''
  try {
    invitaciones.value = await obtenerInvitacionesFeria(props.cuenta.uid, llamarApi(), forzar)
    ahora.value = new Date()
    emit('pendientes', pendientes.value.length)
    enfocarProximoMes()
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudieron cargar las ferias. Inténtalo de nuevo.')
  } finally {
    cargando.value = false
  }
}

async function responder(invitacion: InvitacionFeriaWeb, acepta: boolean) {
  if (respondiendo.value) return
  respondiendo.value = invitacion.id
  error.value = ''
  aviso.value = ''
  try {
    const llamar = llamarApi()
    await llamar('responderInvitacionFeria', { feriaId: invitacion.id, respuesta: acepta ? 'confirmar' : 'rechazar' })
    const estado: EstadoInvitacionFeria = acepta ? 'aceptada' : 'rechazada'
    invitaciones.value = invitaciones.value.map((item) => item.id === invitacion.id ? { ...item, estado } : item)
    actualizarInvitacionEnCache(props.cuenta.uid, invitacion.id, estado)
    emit('pendientes', pendientes.value.length)
    aviso.value = acepta ? `¡Listo! Participarás en «${invitacion.nombre}». Quedó en tu agenda.` : `Avisamos que no podrás ir a «${invitacion.nombre}».`
    porRechazar.value = null
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudo enviar tu respuesta. Inténtalo de nuevo.')
  } finally {
    respondiendo.value = null
  }
}

onMounted(() => {
  if (invitaciones.value.length) enfocarProximoMes()
  void cargar()
})
</script>

<template>
  <section class="ferias" data-guia="ferias-seccion" aria-labelledby="titulo-ferias">
    <header class="cabeza">
      <div>
        <span class="sobre">TUS FERIAS</span>
        <h2 id="titulo-ferias">¿Dónde vas a vender?</h2>
        <p>Tus invitaciones y las ferias en que participas.</p>
      </div>
      <button class="boton-claro" type="button" :disabled="cargando" @click="cargar(true)"><span aria-hidden="true">↻</span> {{ cargando ? 'Actualizando…' : 'Actualizar' }}</button>
    </header>

    <p v-if="error" class="mensaje error" role="alert">{{ error }} <button type="button" @click="cargar(true)">Reintentar</button></p>
    <p v-if="aviso" class="mensaje ok" role="status">{{ aviso }}</p>

    <p v-if="cargando && !invitaciones.length" class="estado" role="status">Cargando tus ferias…</p>
    <div v-else-if="!invitaciones.length && !error" class="vacio">
      <span aria-hidden="true">✿</span>
      <strong>Aún no tienes ferias</strong>
      <p>Cuando la administración te invite o te inscriba en una feria, aparecerá aquí para que puedas responder.</p>
    </div>

    <template v-else-if="invitaciones.length">
      <article v-if="secciones.proxima && !diaSeleccionado" class="destacada" data-guia="ferias-proxima" aria-labelledby="titulo-proxima">
        <div class="foto">
          <img v-if="secciones.proxima.imagenUrl" :src="secciones.proxima.imagenUrl" :alt="`Imagen de ${secciones.proxima.nombre}`" loading="lazy" />
          <span v-else aria-hidden="true">✿</span>
        </div>
        <div class="contenido">
          <div class="etiquetas"><span class="chip-claro">Tu próxima feria</span><span class="falta">{{ cuantoFalta(secciones.proxima, ahora) }}</span></div>
          <h3 id="titulo-proxima">{{ secciones.proxima.nombre }}</h3>
          <p class="dato">{{ diaFeria(secciones.proxima.fechaInicio) }} · {{ horarioFeria(secciones.proxima.fechaInicio, secciones.proxima.fechaFin) }}</p>
          <p class="dato">{{ secciones.proxima.donde }} · Organiza {{ secciones.proxima.organiza }}</p>
          <p v-if="expandida === secciones.proxima.id && secciones.proxima.descripcion" class="descripcion">{{ secciones.proxima.descripcion }}</p>
          <div class="acciones">
            <a class="boton-blanco" :href="enlaceComoLlegar(secciones.proxima)" target="_blank" rel="noopener">Cómo llegar <span aria-hidden="true">↗</span></a>
            <button v-if="secciones.proxima.descripcion" class="boton-borde" type="button" :aria-expanded="expandida === secciones.proxima.id" @click="alternarDetalle(secciones.proxima.id)">{{ expandida === secciones.proxima.id ? 'Ocultar detalle' : 'Ver detalle' }}</button>
          </div>
        </div>
      </article>

      <div class="columnas">
        <div class="principal">
          <section v-if="diaSeleccionado" class="bloque" aria-labelledby="titulo-dia">
            <div class="titulo-bloque">
              <h3 id="titulo-dia">{{ tituloDia }}</h3>
              <button type="button" class="enlace" @click="diaSeleccionado = null">Ver todas</button>
            </div>
            <ul class="agenda">
              <li v-for="feria in delDia" :key="feria.id" class="fila-feria">
                <div class="fecha"><strong>{{ bloqueFecha(feria.fechaInicio).dia }}</strong><small>{{ bloqueFecha(feria.fechaInicio).mes }}</small></div>
                <div class="datos">
                  <strong>{{ feria.nombre }}</strong>
                  <small>{{ horarioFeria(feria.fechaInicio, feria.fechaFin) }} · {{ feria.donde }}</small>
                </div>
                <span class="estado-chip" :class="feria.estado">{{ etiquetas[feria.estado] }}</span>
              </li>
            </ul>
          </section>

          <template v-else>
            <section v-if="secciones.pendientes.length" class="bloque" data-guia="ferias-pendientes" aria-labelledby="titulo-pendientes">
              <div class="titulo-bloque">
                <h3 id="titulo-pendientes">Por responder</h3>
                <span class="contador">{{ secciones.pendientes.length }}</span>
              </div>
              <article v-for="feria in secciones.pendientes" :key="feria.id" class="invitacion">
                <div class="fila-invitacion">
                  <div class="fecha naranja"><strong>{{ bloqueFecha(feria.fechaInicio).dia }}</strong><small>{{ bloqueFecha(feria.fechaInicio).mes }}</small></div>
                  <div class="datos">
                    <strong>{{ feria.nombre }}</strong>
                    <small>{{ diaFeria(feria.fechaInicio) }} · {{ horarioFeria(feria.fechaInicio, feria.fechaFin) }}</small>
                    <small>{{ feria.donde }} · Organiza {{ feria.organiza }}</small>
                    <small v-if="feria.invitadaEn" class="hace">{{ haceCuanto(feria.invitadaEn, ahora) }}</small>
                  </div>
                </div>
                <p v-if="feria.descripcion" class="descripcion-pendiente">{{ feria.descripcion }}</p>
                <div class="acciones">
                  <button class="boton-claro" type="button" :disabled="!!respondiendo" @click="porRechazar = feria">No puedo ir</button>
                  <button class="boton-vino" type="button" :disabled="!!respondiendo" @click="responder(feria, true)">{{ respondiendo === feria.id ? 'Enviando…' : '✓ Sí, participo' }}</button>
                </div>
              </article>
            </section>

            <section class="bloque" data-guia="ferias-agenda" aria-labelledby="titulo-agenda">
              <div class="titulo-bloque"><h3 id="titulo-agenda">Tu agenda</h3></div>
              <p v-if="!agenda.length" class="estado">{{ secciones.proxima ? 'No tienes más ferias confirmadas por ahora.' : 'Todavía no tienes ferias confirmadas.' }} {{ secciones.pendientes.length ? 'Responde tus invitaciones para sumarlas aquí.' : '' }}</p>
              <div v-for="mes in agenda" :key="mes.clave" class="mes">
                <span class="nombre-mes">{{ mes.titulo }}</span>
                <ul class="agenda">
                  <li v-for="feria in mes.ferias" :key="feria.id" class="fila-feria" :class="{ abierta: expandida === feria.id }">
                    <button type="button" class="abrir-fila" :aria-expanded="expandida === feria.id" @click="alternarDetalle(feria.id)">
                      <div class="fecha"><strong>{{ bloqueFecha(feria.fechaInicio).dia }}</strong><small>{{ bloqueFecha(feria.fechaInicio).mes }}</small></div>
                      <div class="datos">
                        <strong>{{ feria.nombre }}</strong>
                        <small>{{ horarioFeria(feria.fechaInicio, feria.fechaFin) }} · {{ feria.donde }}</small>
                      </div>
                      <span class="estado-chip" :class="feria.estado">{{ etiquetas[feria.estado] }}</span>
                    </button>
                    <div v-if="expandida === feria.id" class="detalle-fila">
                      <p v-if="feria.descripcion">{{ feria.descripcion }}</p>
                      <p>{{ diaFeria(feria.fechaInicio) }} · Organiza {{ feria.organiza }} · {{ cuantoFalta(feria, ahora) }}</p>
                      <a class="enlace" :href="enlaceComoLlegar(feria)" target="_blank" rel="noopener">Cómo llegar ↗</a>
                    </div>
                  </li>
                </ul>
              </div>
            </section>

            <section v-if="secciones.pasadas.length" class="bloque">
              <button type="button" class="desplegar" :aria-expanded="pasadasAbiertas" @click="pasadasAbiertas = !pasadasAbiertas">
                Ferias pasadas y rechazadas ({{ secciones.pasadas.length }}) <span aria-hidden="true">{{ pasadasAbiertas ? '▴' : '▾' }}</span>
              </button>
              <ul v-if="pasadasAbiertas" class="agenda apagada">
                <li v-for="feria in secciones.pasadas" :key="feria.id" class="fila-feria">
                  <div class="fecha"><strong>{{ bloqueFecha(feria.fechaInicio).dia }}</strong><small>{{ bloqueFecha(feria.fechaInicio).mes }}</small></div>
                  <div class="datos">
                    <strong>{{ feria.nombre }}</strong>
                    <small>{{ diaFeria(feria.fechaInicio) }} · {{ feria.donde }}</small>
                  </div>
                  <span class="estado-chip" :class="feria.estado === 'rechazada' ? 'rechazada' : 'pasada'">{{ feria.estado === 'rechazada' ? 'No participaste' : feria.estado === 'pendiente' ? 'Sin responder' : 'Terminó' }}</span>
                </li>
              </ul>
            </section>
          </template>
        </div>

        <aside class="calendario" data-guia="ferias-calendario" aria-label="Calendario de ferias">
          <div class="cabeza-calendario">
            <button type="button" aria-label="Mes anterior" @click="cambiarMes(-1)">‹</button>
            <strong>{{ nombreMes }}</strong>
            <button type="button" aria-label="Mes siguiente" @click="cambiarMes(1)">›</button>
          </div>
          <div class="dias-semana" aria-hidden="true"><span>L</span><span>M</span><span>M</span><span>J</span><span>V</span><span>S</span><span>D</span></div>
          <div class="rejilla">
            <template v-for="(celda, indice) in celdas" :key="celda?.clave ?? `vacio-${indice}`">
              <span v-if="!celda" class="celda vacia" />
              <button
                v-else
                type="button"
                class="celda"
                :class="[tipoCelda(celda.ferias), { hoy: celda.hoy, seleccionada: diaSeleccionado === celda.clave }]"
                :disabled="!celda.ferias.length"
                :aria-pressed="diaSeleccionado === celda.clave"
                :aria-label="celda.ferias.length ? `${celda.numero}: ${celda.ferias.map((f) => f.nombre).join(', ')}` : String(celda.numero)"
                @click="elegirDia(celda.clave, celda.ferias)"
              >{{ celda.numero }}</button>
            </template>
          </div>
          <div class="leyenda">
            <small><i class="muestra confirmada" /> Confirmada</small>
            <small><i class="muestra pendiente" /> Por responder</small>
            <small><i class="muestra elegida" /> Día elegido</small>
          </div>
        </aside>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="porRechazar" class="fondo-dialogo-ferias" @click.self="porRechazar = null" @keydown.esc="porRechazar = null">
        <div class="dialogo-ferias" role="dialog" aria-modal="true" aria-labelledby="titulo-rechazo">
          <h3 id="titulo-rechazo">¿No podrás ir?</h3>
          <p>Avisaremos que no participarás en «{{ porRechazar.nombre }}». Si cambias de opinión tendrás que pedir una nueva invitación a la administración.</p>
          <div class="acciones-dialogo">
            <button class="dialogo-claro" type="button" :disabled="!!respondiendo" @click="porRechazar = null">Volver</button>
            <button class="dialogo-vino" type="button" :disabled="!!respondiendo" @click="responder(porRechazar, false)">{{ respondiendo ? 'Enviando…' : 'Sí, no podré ir' }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.ferias{display:grid;gap:18px}
.cabeza{display:flex;align-items:flex-end;justify-content:space-between;flex-wrap:wrap;gap:12px}
.sobre{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.15em}
.cabeza h2{margin:6px 0 4px;color:var(--vino,#74244e);font:400 30px/1.1 'DM Serif Display',Georgia,serif}
.cabeza p{margin:0;color:var(--suave,#7a6468);font-size:14px}
.boton-claro,.boton-vino,.boton-blanco,.boton-borde{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:44px;padding:0 18px;border-radius:13px;font:700 13px 'Plus Jakarta Sans',sans-serif;text-decoration:none;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fffdf9;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-blanco{border:0;background:#fff;color:var(--vino,#74244e)}
.boton-borde{border:1px solid rgba(255,255,255,.6);background:transparent;color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.destacada{display:grid;grid-template-columns:minmax(160px,240px) minmax(0,1fr);overflow:hidden;border-radius:24px;background:var(--vino,#74244e);color:#fff}
.foto{display:grid;place-items:center;min-height:190px;background:#f6c177;color:var(--vino,#74244e);font-size:56px}
.foto img{width:100%;height:100%;object-fit:cover}
.contenido{display:grid;align-content:center;gap:6px;padding:20px 22px}
.etiquetas{display:flex;align-items:center;flex-wrap:wrap;gap:10px}
.chip-claro{padding:4px 10px;border-radius:999px;background:#fde2b0;color:var(--vino,#74244e);font-size:11px;font-weight:800}
.falta{color:#fde2b0;font-size:13px;font-weight:700}
.destacada h3{margin:4px 0 2px;font:400 28px/1.15 'DM Serif Display',Georgia,serif}
.dato{margin:0;color:#f3dee3;font-size:14px}
.descripcion{margin:4px 0 0;color:#fff;font-size:14px;line-height:1.55;white-space:pre-line}
.acciones{display:flex;flex-wrap:wrap;gap:10px;margin-top:8px}
.columnas{display:grid;grid-template-columns:minmax(0,1fr) 290px;align-items:start;gap:18px}
.principal{display:grid;gap:18px;min-width:0}
.bloque{display:grid;gap:10px}
.titulo-bloque{display:flex;align-items:center;justify-content:space-between;gap:10px}
.titulo-bloque h3{margin:0;color:var(--vino,#74244e);font:400 22px 'DM Serif Display',Georgia,serif}
.titulo-bloque .contador{margin-right:auto}
.contador{display:inline-grid;place-items:center;min-width:24px;height:24px;padding:0 7px;border-radius:999px;background:var(--naranja,#e9812d);color:#fff;font-size:12px;font-weight:800}
.invitacion{display:grid;gap:10px;padding:16px;border:1px solid #f0c49a;border-radius:20px;background:#fff4e3}
.fila-invitacion{display:flex;gap:14px}
.invitacion .acciones{justify-content:flex-end;margin-top:0}
.descripcion-pendiente{margin:0;color:#66482e;font-size:13.5px;line-height:1.5}
.hace{color:#9a4a10 !important;font-weight:700}
.fecha{display:grid;flex:0 0 auto;place-content:center;width:58px;height:60px;border-radius:14px;background:#f6e8d3;color:var(--vino,#74244e);text-align:center}
.fecha strong{font:400 24px/1 'DM Serif Display',Georgia,serif}
.fecha small{margin-top:2px;font-size:11px;font-weight:800;text-transform:uppercase}
.fecha.naranja{background:#fde4d0;color:#9a4a10}
.datos{display:grid;gap:3px;min-width:0}
.datos strong{color:var(--texto,#3f222c);font-size:15px}
.datos small{color:var(--suave,#7a6468);font-size:12.5px}
.mes{display:grid;gap:8px}
.nombre-mes{color:var(--suave,#7a6468);font-size:12px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}
.agenda{overflow:hidden;margin:0;padding:0;border:1px solid var(--borde,#ecd8cb);border-radius:20px;background:#fffdf9;list-style:none}
.agenda>li+li{border-top:1px solid #efe1cd}
.fila-feria{display:flex;align-items:center;gap:14px;padding:12px 16px}
.fila-feria:has(.abrir-fila){display:block;padding:0}
.abrir-fila{display:flex;align-items:center;gap:14px;width:100%;padding:12px 16px;border:0;background:transparent;font:inherit;text-align:left;cursor:pointer}
.abrir-fila:hover{background:#fff7ee}
.fila-feria .datos{flex:1}
.detalle-fila{display:grid;gap:6px;padding:0 16px 14px 88px;color:#5f4b50;font-size:13.5px;line-height:1.5}
.detalle-fila p{margin:0;white-space:pre-line}
.agenda.apagada .fecha{background:#f1ebe4;color:var(--suave,#7a6468)}
.estado-chip{flex:0 0 auto;padding:4px 10px;border-radius:999px;background:#f1e8e8;color:var(--vino,#74244e);font-size:11px;font-weight:800}
.estado-chip.pendiente{background:#fde4d0;color:#9a4a10}
.estado-chip.aceptada{background:#ddebd3;color:#2f6b2e}
.estado-chip.agregada_admin{background:#f3dee3;color:var(--vino,#74244e)}
.estado-chip.rechazada,.estado-chip.pasada{background:#f1ebe4;color:var(--suave,#7a6468)}
.enlace{justify-self:start;padding:0;border:0;background:none;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;text-decoration:underline;cursor:pointer}
.desplegar{display:flex;align-items:center;justify-content:space-between;width:100%;min-height:48px;padding:0 16px;border:1px solid var(--borde,#ecd8cb);border-radius:16px;background:#fffdf9;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.calendario{position:sticky;top:12px;display:grid;gap:10px;padding:16px;border:1px solid var(--borde,#ecd8cb);border-radius:22px;background:#fffdf9}
.cabeza-calendario{display:flex;align-items:center;justify-content:space-between}
.cabeza-calendario strong{color:var(--vino,#74244e);font:400 19px 'DM Serif Display',Georgia,serif}
.cabeza-calendario button{width:38px;height:38px;border:1px solid var(--borde,#ecd8cb);border-radius:50%;background:#fff;color:var(--vino,#74244e);font-size:20px;cursor:pointer}
.dias-semana,.rejilla{display:grid;grid-template-columns:repeat(7,1fr);gap:3px;text-align:center}
.dias-semana span{color:var(--suave,#7a6468);font-size:11px;font-weight:800}
.celda{display:grid;place-items:center;aspect-ratio:1;border:0;border-radius:10px;background:transparent;color:var(--texto,#3f222c);font:600 13px 'Plus Jakarta Sans',sans-serif}
.celda:disabled{cursor:default}
.celda.hoy{box-shadow:inset 0 0 0 1.5px var(--vino,#74244e)}
.celda.confirmada{background:#ddebd3;color:#2f6b2e;font-weight:800;cursor:pointer}
.celda.pendiente{background:#fde4d0;color:#9a4a10;font-weight:800;cursor:pointer}
.celda.seleccionada{background:var(--vino,#74244e);color:#fff}
.leyenda{display:grid;gap:4px}
.leyenda small{color:var(--suave,#7a6468);font-size:12px}
.muestra{display:inline-block;width:10px;height:10px;margin-right:4px;border-radius:3px;vertical-align:-1px}
.muestra.confirmada{background:#ddebd3;border:1px solid #9fc28a}
.muestra.pendiente{background:#fde4d0;border:1px solid #ecb27d}
.muestra.elegida{background:var(--vino,#74244e)}
.mensaje{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:0;padding:12px 14px;border-radius:14px;font-size:13px}
.mensaje.error{background:#fff0ee;color:#852e3f}
.mensaje.ok{background:#edf7ed;color:#286344}
.mensaje button{min-height:36px;padding:0 12px;border:1px solid currentColor;border-radius:10px;background:#fff;color:inherit;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.estado{margin:0;color:var(--suave,#7a6468);font-size:14px}
.vacio{display:grid;justify-items:center;gap:6px;padding:32px 20px;border:1px dashed var(--borde,#ecd8cb);border-radius:20px;text-align:center;color:var(--suave,#7a6468)}
.vacio span{color:#e76b62;font-size:34px}
.vacio strong{color:var(--vino,#74244e);font:400 21px 'DM Serif Display',Georgia,serif}
.vacio p{max-width:380px;margin:0;font-size:13.5px;line-height:1.5}
button:focus-visible,a:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
.destacada a:focus-visible,.destacada button:focus-visible{outline-color:#fde2b0}
@media(max-width:980px){
  .columnas{grid-template-columns:minmax(0,1fr)}
  .calendario{position:static;max-width:440px}
}
@media(max-width:640px){
  .cabeza h2{font-size:25px}
  .destacada{grid-template-columns:minmax(0,1fr)}
  .foto{min-height:140px}
  .destacada h3{font-size:24px}
  .acciones>*{flex:1 1 auto}
  .fila-feria,.abrir-fila{flex-wrap:wrap}
  .estado-chip{margin-left:72px}
  .detalle-fila{padding-left:16px}
  .calendario{max-width:none}
}
</style>

<style>
.fondo-dialogo-ferias{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:16px;background:rgba(43,20,28,.45)}
.dialogo-ferias{display:grid;gap:12px;width:min(440px,100%);padding:22px;border-radius:22px;background:#fffaf3;box-shadow:0 24px 60px rgba(43,20,28,.3);font-family:'Plus Jakarta Sans',sans-serif}
.dialogo-ferias h3{margin:0;color:#5b1b3c;font:400 24px 'DM Serif Display',Georgia,serif}
.dialogo-ferias p{margin:0;color:#5f4b50;font-size:14px;line-height:1.5}
.acciones-dialogo{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:10px}
.dialogo-claro,.dialogo-vino{min-height:44px;padding:0 18px;border-radius:13px;font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.dialogo-claro{border:1px solid #ecd8cb;background:#fff;color:#6b1e3d}
.dialogo-vino{border:0;background:#6b1e3d;color:#fff}
.dialogo-claro:disabled,.dialogo-vino:disabled{opacity:.5;cursor:default}
</style>
