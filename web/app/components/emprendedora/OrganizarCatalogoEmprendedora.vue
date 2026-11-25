<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  leerProductosPorIdEmprendedora,
  listarPaginaProductosEmprendedora,
  type CuentaEmprendedoraWeb,
  type ProductoEmprendedoraWeb,
} from '~/repositories/firebase/emprendedora'
import type { Operacion, ServiciosCliente } from '~/lib/cliente-base'
import { alternarSeleccion, crearLotesDisponibilidad, filtrarOrganizables, type FiltroOrganizar, type LoteDisponibilidad } from '~/lib/organizar-catalogo'

type Pendiente = { disponible: boolean; lotes: LoteDisponibilidad[]; indice: number }

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb; categorias: { id: string; nombre: string }[] }>()
const emit = defineEmits<{ actualizados: [productos: ProductoEmprendedoraWeb[]]; cerrar: [] }>()

const productos = ref<ProductoEmprendedoraWeb[]>([])
const cursor = ref<string | null>(null)
const cargando = ref(false)
const error = ref('')
const aviso = ref('')
const seleccion = ref(new Set<string>())
const filtro = reactive<FiltroOrganizar>({ texto: '', categoriaId: '', disponibilidad: 'todas' })
const enviando = ref(false)
const pendiente = ref<Pendiente | null>(null)
const confirmacion = ref<{ disponible: boolean; cantidad: number } | null>(null)

const visibles = computed(() => filtrarOrganizables(productos.value, filtro))
const seleccionados = computed(() => productos.value.filter((producto) => seleccion.value.has(producto.id)))
const todosVisiblesMarcados = computed(() => visibles.value.length > 0 && visibles.value.every((producto) => seleccion.value.has(producto.id)))
const categoriasConProductos = computed(() => {
  const usadas = new Set(productos.value.map((producto) => producto.categoriaId))
  return props.categorias.filter((categoria) => usadas.has(categoria.id))
})
const disponibles = computed(() => productos.value.filter((producto) => producto.disponible).length)

function servicios() {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  return $amancay as typeof $amancay & {
    firebase: ServiciosCliente
    llamar?: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>
  }
}

function nombreCategoria(id: string) {
  return props.categorias.find((categoria) => categoria.id === id)?.nombre ?? ''
}

function stockLegible(producto: ProductoEmprendedoraWeb) {
  if (!producto.manejaStock) return 'Sin control de stock'
  if (producto.tieneVariantes) return 'Stock por variantes'
  return `${producto.stock ?? 0} ${producto.stock === 1 ? 'unidad' : 'unidades'}`
}

function reglaAgotado(producto: ProductoEmprendedoraWeb) {
  if (!producto.manejaStock) return ''
  return producto.alAgotarse === 'ocultar' ? 'Al agotarse se oculta' : producto.alAgotarse === 'pasar_a_pedido' ? 'Al agotarse pasa a pedido' : 'Al agotarse se muestra agotado'
}

function estadoLegible(estado: string) {
  return estado === 'publicado' ? 'Publicado' : estado === 'archivado' ? 'Retirado' : 'Borrador'
}

async function cargar(reiniciar = false) {
  if (cargando.value) return
  cargando.value = true
  error.value = ''
  try {
    const pagina = await listarPaginaProductosEmprendedora(
      servicios().firebase, props.cuenta.tienda.id, props.cuenta.tienda.mostrarPrecios, 'todos',
      reiniciar ? null : cursor.value, false,
    )
    const conocidos = reiniciar ? new Map<string, ProductoEmprendedoraWeb>() : new Map(productos.value.map((producto) => [producto.id, producto]))
    for (const producto of pagina.productos) conocidos.set(producto.id, producto)
    productos.value = [...conocidos.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'))
    cursor.value = pagina.siguienteCursor
    if (reiniciar) seleccion.value = new Set()
  } catch (fallo) {
    error.value = fallo instanceof Error && !(fallo as { code?: string }).code ? fallo.message : 'No se pudieron cargar los productos. Inténtalo de nuevo.'
  } finally {
    cargando.value = false
  }
}

function marcarVisibles() {
  seleccion.value = alternarSeleccion(seleccion.value, visibles.value.map((producto) => producto.id), !todosVisiblesMarcados.value)
}

function alternar(id: string) {
  seleccion.value = alternarSeleccion(seleccion.value, [id], !seleccion.value.has(id))
}

function mensajeError(fallo: unknown) {
  const codigo = (fallo as { code?: string })?.code
  if (codigo === 'solo-consulta') return (fallo as Error).message
  if (codigo === 'conflicto') return 'Algún producto cambió mientras lo editabas. Actualizamos la lista; vuelve a intentarlo.'
  if (codigo === 'no-encontrado') return 'Uno de los productos ya no existe. Actualiza la lista.'
  if (codigo === 'sin-permiso') return 'Tu cuenta no puede cambiar estos productos.'
  return 'No se pudo cambiar la disponibilidad. Inténtalo de nuevo.'
}

function incierto(fallo: unknown) {
  const codigo = (fallo as { code?: string })?.code
  return codigo === 'conexion-interrumpida' || (fallo as { resultadoIncierto?: boolean })?.resultadoIncierto === true
}

async function refrescar(ids: string[]) {
  if (!ids.length) return
  const actualizados = await leerProductosPorIdEmprendedora(servicios().firebase, props.cuenta.tienda.id, ids)
  const porId = new Map(actualizados.map((producto) => [producto.id, producto]))
  productos.value = productos.value.flatMap((producto) => {
    const nuevo = porId.get(producto.id)
    if (ids.includes(producto.id) && !nuevo) return []
    return [nuevo ? { ...producto, ...nuevo, imagenPublica: producto.imagenPublica } : producto]
  })
  emit('actualizados', actualizados)
}

async function ejecutar(estado: Pendiente) {
  const cliente = servicios()
  if (typeof cliente.llamar !== 'function') {
    error.value = 'Este sitio está en modo de solo consulta; no se pueden guardar cambios.'
    return
  }
  enviando.value = true
  error.value = ''
  aviso.value = ''
  const cambiados: string[] = []
  try {
    for (; estado.indice < estado.lotes.length; estado.indice++) {
      const lote = estado.lotes[estado.indice]!
      await cliente.llamar('cambiarDisponibilidadProductos', { tiendaId: props.cuenta.tienda.id, operacionId: lote.operacionId, disponible: estado.disponible, items: lote.items })
      cambiados.push(...lote.items.map((item) => item.productoId))
    }
    pendiente.value = null
    const total = estado.lotes.reduce((suma, lote) => suma + lote.items.length, 0)
    aviso.value = `${total} ${total === 1 ? 'producto quedó' : 'productos quedaron'} ${estado.disponible ? 'disponible' : 'no disponible'}${total === 1 ? '' : 's'}.`
    seleccion.value = new Set()
  } catch (fallo) {
    if (incierto(fallo)) {
      pendiente.value = estado
      error.value = 'No se confirmó si el cambio se guardó. Usa «Reintentar» para completarlo sin duplicarlo.'
    } else {
      pendiente.value = null
      error.value = mensajeError(fallo)
      if ((fallo as { code?: string })?.code === 'conflicto') cambiados.push(...(estado.lotes[estado.indice]?.items.map((item) => item.productoId) ?? []))
    }
  } finally {
    enviando.value = false
    try { await refrescar(cambiados) } catch {}
  }
}

function solicitarCambio(disponible: boolean, objetivo: ProductoEmprendedoraWeb[]) {
  const lotes = crearLotesDisponibilidad(objetivo, disponible, () => crypto.randomUUID())
  const cantidad = lotes.reduce((suma, lote) => suma + lote.items.length, 0)
  if (!cantidad) {
    aviso.value = disponible ? 'Los productos elegidos ya están disponibles.' : 'Los productos elegidos ya están no disponibles.'
    return
  }
  if (cantidad > 1) {
    confirmacion.value = { disponible, cantidad }
    pendiente.value = { disponible, lotes, indice: 0 }
    return
  }
  void ejecutar({ disponible, lotes, indice: 0 })
}

function confirmar() {
  const estado = pendiente.value
  confirmacion.value = null
  if (estado) void ejecutar(estado)
}

function cancelarConfirmacion() {
  confirmacion.value = null
  pendiente.value = null
}

onMounted(() => { void cargar(true) })
</script>

<template>
  <section class="organizar" aria-label="Organizar catálogo">
    <div class="cabeza">
      <div>
        <span class="sobre">ORGANIZAR CATÁLOGO</span>
        <h2>Disponibilidad de tus productos</h2>
        <p>Elige qué productos se muestran ahora en tu tienda. Un producto no disponible deja de verse en el catálogo, pero se conserva en tu lista con sus datos y fotos para volver a activarlo cuando quieras.</p>
      </div>
      <button class="boton-claro" type="button" @click="emit('cerrar')">← Volver a productos</button>
    </div>

    <div class="herramientas">
      <input v-model="filtro.texto" type="search" placeholder="Buscar producto" aria-label="Buscar producto" />
      <select v-model="filtro.categoriaId" aria-label="Filtrar por categoría">
        <option value="">Todas las categorías</option>
        <option v-for="categoria in categoriasConProductos" :key="categoria.id" :value="categoria.id">{{ categoria.nombre }}</option>
      </select>
      <select v-model="filtro.disponibilidad" aria-label="Filtrar por disponibilidad">
        <option value="todas">Disponibles y no disponibles</option>
        <option value="disponibles">Solo disponibles</option>
        <option value="no_disponibles">Solo no disponibles</option>
      </select>
    </div>

    <div class="barra-acciones" :class="{ activa: seleccion.size }">
      <label class="marcar-todos">
        <input type="checkbox" :checked="todosVisiblesMarcados" :disabled="!visibles.length || enviando" @change="marcarVisibles" />
        {{ seleccion.size ? `${seleccion.size} seleccionado${seleccion.size === 1 ? '' : 's'}` : `Seleccionar ${filtro.categoriaId ? 'la categoría' : 'todos'} (${visibles.length})` }}
      </label>
      <div class="botones">
        <button class="boton-claro" type="button" :disabled="!seleccion.size || enviando" @click="solicitarCambio(false, seleccionados)">Marcar no disponibles</button>
        <button class="boton-vino" type="button" :disabled="!seleccion.size || enviando" @click="solicitarCambio(true, seleccionados)">Marcar disponibles</button>
      </div>
    </div>

    <p v-if="error" class="mensaje error" role="alert">
      {{ error }}
      <button v-if="pendiente && !confirmacion" type="button" :disabled="enviando" @click="ejecutar(pendiente)">Reintentar</button>
    </p>
    <p v-if="aviso" class="mensaje ok" role="status">{{ aviso }}</p>
    <p v-if="enviando" class="estado" role="status">Guardando cambios…</p>

    <p class="resumen">{{ disponibles }} de {{ productos.length }} productos cargados están disponibles.</p>

    <p v-if="cargando && !productos.length" class="estado" role="status">Cargando productos…</p>
    <p v-else-if="!productos.length && !error" class="estado">Aún no tienes productos.</p>
    <p v-else-if="productos.length && !visibles.length" class="estado">No hay productos con estos filtros.</p>

    <ul v-if="visibles.length" class="lista">
      <li v-for="producto in visibles" :key="producto.id" class="fila" :class="{ marcada: seleccion.has(producto.id), apagado: !producto.disponible }">
        <label class="check">
          <input type="checkbox" :checked="seleccion.has(producto.id)" :disabled="enviando" @change="alternar(producto.id)" />
          <span class="sr-only">Seleccionar {{ producto.nombre }}</span>
        </label>
        <div class="datos">
          <small v-if="nombreCategoria(producto.categoriaId)">{{ nombreCategoria(producto.categoriaId) }}</small>
          <strong>{{ producto.nombre }}</strong>
          <span class="meta">
            <span class="chip" :class="producto.estado">{{ estadoLegible(producto.estado) }}</span>
            <span>{{ stockLegible(producto) }}</span>
            <span v-if="reglaAgotado(producto)">· {{ reglaAgotado(producto) }}</span>
          </span>
        </div>
        <div class="disponibilidad">
          <span :class="producto.disponible ? 'si' : 'no'">{{ producto.disponible ? 'Disponible' : 'No disponible' }}</span>
          <button
            type="button"
            role="switch"
            class="interruptor"
            :aria-checked="producto.disponible"
            :aria-label="`Disponible para la venta: ${producto.nombre}`"
            :disabled="enviando"
            @click="solicitarCambio(!producto.disponible, [producto])"
          />
        </div>
      </li>
    </ul>
    <button v-if="cursor" class="boton-claro cargar-mas" type="button" :disabled="cargando" @click="cargar()">{{ cargando ? 'Cargando…' : 'Cargar más productos' }}</button>

    <Teleport to="body">
      <div v-if="confirmacion" class="fondo-dialogo" @click.self="cancelarConfirmacion">
        <div class="dialogo" role="dialog" aria-modal="true" aria-labelledby="titulo-confirmar-disponibilidad">
          <h3 id="titulo-confirmar-disponibilidad">{{ confirmacion.disponible ? '¿Marcar como disponibles?' : '¿Marcar como no disponibles?' }}</h3>
          <p>Cambiarás {{ confirmacion.cantidad }} productos. {{ confirmacion.disponible ? 'Los productos publicados volverán a mostrarse en tu tienda.' : 'Dejarán de mostrarse en tu tienda hasta que los vuelvas a activar. Sus datos se conservan.' }}</p>
          <div class="botones">
            <button class="boton-claro" type="button" @click="cancelarConfirmacion">Volver</button>
            <button class="boton-vino" type="button" @click="confirmar">Sí, cambiar</button>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.organizar{display:grid;gap:14px}
.cabeza{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px}
.sobre{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.15em}
.cabeza h2{margin:6px 0 4px;color:var(--vino,#74244e);font:400 30px/1.1 'DM Serif Display',Georgia,serif}
.cabeza p{max-width:620px;margin:0;color:var(--suave,#7a6468);font-size:14px;line-height:1.5}
.herramientas{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr) minmax(0,1fr);gap:10px}
.herramientas input,.herramientas select{min-height:44px;padding:0 12px;border:1px solid var(--borde,#ecd8cb);border-radius:13px;background:#fffdf9;color:var(--texto,#3f222c);font:500 14px 'Plus Jakarta Sans',sans-serif}
.barra-acciones{position:sticky;top:8px;z-index:2;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;padding:10px 14px;border:1px solid var(--borde,#ecd8cb);border-radius:16px;background:#fffdf9}
.barra-acciones.activa{border-color:var(--vino,#74244e);box-shadow:0 8px 20px rgba(63,34,44,.08)}
.marcar-todos{display:flex;align-items:center;gap:10px;color:var(--texto,#3f222c);font-size:14px;font-weight:700}
.marcar-todos input,.check input{width:20px;height:20px;accent-color:var(--vino,#74244e)}
.botones{display:flex;flex-wrap:wrap;gap:8px}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 16px;border-radius:13px;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fff;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.resumen,.estado{margin:0;color:var(--suave,#7a6468);font-size:13px}
.lista{display:grid;gap:8px;margin:0;padding:0;list-style:none}
.fila{display:grid;grid-template-columns:36px minmax(0,1fr) auto;align-items:center;gap:12px;padding:12px 14px;border:1px solid var(--borde,#ecd8cb);border-radius:16px;background:#fff}
.fila.marcada{border-color:var(--vino,#74244e);background:#fff7f2}
.fila.apagado .datos strong{color:var(--suave,#7a6468)}
.check{display:grid;place-items:center;min-height:44px;cursor:pointer}
.datos{display:grid;gap:3px;min-width:0}
.datos small{color:#e76b62;font-size:10.5px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}
.datos strong{overflow:hidden;color:var(--vino,#74244e);font-size:15px;text-overflow:ellipsis;white-space:nowrap}
.meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;color:var(--suave,#7a6468);font-size:12px}
.chip{padding:2px 8px;border-radius:999px;background:#f1e8e8;color:var(--vino,#74244e);font-size:11px;font-weight:800}
.chip.publicado{background:#e3f1e5;color:#2f6b40}
.disponibilidad{display:flex;align-items:center;gap:10px}
.disponibilidad span{font-size:12px;font-weight:800}
.disponibilidad .si{color:#2f6b40}
.disponibilidad .no{color:#9a4a10}
.interruptor{position:relative;width:52px;height:30px;padding:0;border:0;border-radius:999px;background:#e6d4d8;cursor:pointer;transition:background .15s ease}
.interruptor::after{position:absolute;top:3px;left:3px;width:24px;height:24px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.2);content:'';transition:transform .15s ease}
.interruptor[aria-checked=true]{background:#3c7a48}
.interruptor[aria-checked=true]::after{transform:translateX(22px)}
.interruptor:disabled{opacity:.6;cursor:default}
.interruptor:focus-visible,.boton-claro:focus-visible,.boton-vino:focus-visible,.check input:focus-visible,.marcar-todos input:focus-visible{outline:3px solid rgba(116,36,78,.3);outline-offset:2px}
.cargar-mas{width:100%}
.mensaje{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:0;padding:12px 14px;border-radius:14px;font-size:13px}
.mensaje.error{background:#fff0ee;color:#852e3f}
.mensaje.ok{background:#edf7ed;color:#286344}
.mensaje button{min-height:36px;padding:0 12px;border:1px solid currentColor;border-radius:10px;background:#fff;color:inherit;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.fondo-dialogo{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:16px;background:rgba(43,20,28,.45)}
.dialogo{display:grid;gap:12px;width:min(440px,100%);padding:22px;border-radius:22px;background:#fffaf3;box-shadow:0 24px 60px rgba(43,20,28,.3)}
.dialogo h3{margin:0;color:#5b1b3c;font:400 24px 'DM Serif Display',Georgia,serif}
.dialogo p{margin:0;color:#5f4b50;font-size:14px;line-height:1.5}
.dialogo .botones{justify-content:flex-end}
.dialogo .boton-claro{border-color:#ecd8cb;color:#74244e}
.dialogo .boton-vino{background:#74244e}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
@media(prefers-reduced-motion:reduce){.interruptor,.interruptor::after{transition:none}}
@media(max-width:640px){
  .herramientas{grid-template-columns:minmax(0,1fr)}
  .fila{grid-template-columns:32px minmax(0,1fr)}
  .disponibilidad{grid-column:2;justify-content:space-between}
  .botones{width:100%}
  .botones .boton-claro,.botones .boton-vino{flex:1}
}
</style>
