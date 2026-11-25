<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { renderSVG } from 'uqr'
import {
  leerTipoProductoEmprendedora,
  leerVariantesProductoEmprendedora,
  type CuentaEmprendedoraWeb,
  type ProductoEmprendedoraWeb,
  type VarianteEmprendedoraWeb,
} from '~/repositories/firebase/emprendedora'
import type { ServiciosCliente } from '~/lib/cliente-base'
import { atributosConEtiqueta } from '~/lib/atributos-producto'
import { disponibilidadPrevia, motivoSinCompartir, nombreArchivoQr, nombreVariantePrevia, precioPrevio, urlPublicaProducto } from '~/lib/vista-previa-producto'
import { compartirEnlacePublico } from '~/utils/compartir-enlace-publico'

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb; producto: ProductoEmprendedoraWeb; categoria: string }>()
const emit = defineEmits<{ cerrar: []; editar: [productoId: string]; variantes: [producto: ProductoEmprendedoraWeb] }>()

const variantes = ref<VarianteEmprendedoraWeb[]>(props.producto.variantesCargadas ? props.producto.variantes : [])
const cargandoVariantes = ref(false)
const errorVariantes = ref('')
const tipoNombre = ref('')
const etiquetas = ref<Record<string, string>>({})
const varianteId = ref('')
const aviso = ref('')
const qrAbierto = ref(false)
let temporizadorAviso: ReturnType<typeof setTimeout> | undefined

const productoActual = computed(() => ({ ...props.producto, variantes: variantes.value }))
const activas = computed(() => variantes.value.filter((variante) => variante.activa))
const varianteElegida = computed(() => activas.value.find((variante) => variante.id === varianteId.value) ?? null)
const precio = computed(() => (props.producto.tieneVariantes && !variantes.value.length ? 'Precio según variante' : precioPrevio(productoActual.value, varianteElegida.value)))
const disponibilidad = computed(() => disponibilidadPrevia(productoActual.value, varianteElegida.value))
const imagen = computed(() => varianteElegida.value?.imagenPublica || props.producto.imagenPublica || '')
const detalles = computed(() => atributosConEtiqueta(props.producto.atributosEspecificos, etiquetas.value))
const tiendaPublicada = computed(() => props.cuenta.tienda.estadoPublicacion === 'publicado')
const motivo = computed(() => motivoSinCompartir(props.producto, tiendaPublicada.value))
const enlace = computed(() => (import.meta.client ? urlPublicaProducto(window.location.origin, props.cuenta.tienda.id, props.producto.id) : ''))
const qrSvg = computed(() => (qrAbierto.value && enlace.value ? renderSVG(enlace.value, { ecc: 'M', border: 2, pixelSize: 8, blackColor: '#3f222c', whiteColor: '#ffffff' }) : ''))
const textoMotivo = computed(() => {
  if (motivo.value === 'tienda') return 'Publica tu tienda en Más → Mi tienda para poder compartir tus productos.'
  if (motivo.value === 'producto') return props.producto.estado === 'archivado'
    ? 'Este producto está retirado del catálogo. Publícalo de nuevo para compartirlo.'
    : 'Este producto es un borrador. Cuando lo publiques podrás compartir su enlace.'
  if (motivo.value === 'disponible') return 'Este producto está marcado como no disponible y no se ve en tu tienda. Actívalo en Organizar catálogo para compartirlo.'
  return ''
})
const notaEstado = computed(() => {
  if (props.producto.estado === 'publicado' && tiendaPublicada.value && props.producto.disponible) return 'Este producto ya está publicado en tu tienda.'
  return 'Solo tú puedes ver esta vista previa. Las compradoras lo verán cuando esté publicado.'
})

function servicios(): ServiciosCliente {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  return $amancay.firebase as ServiciosCliente
}

async function cargarVariantes() {
  if (!props.producto.tieneVariantes || props.producto.variantesCargadas) return
  cargandoVariantes.value = true
  errorVariantes.value = ''
  try {
    variantes.value = await leerVariantesProductoEmprendedora(servicios(), props.cuenta.tienda.id, props.producto.id)
    emit('variantes', { ...props.producto, variantes: variantes.value, variantesCargadas: true })
  } catch {
    errorVariantes.value = 'No se pudieron cargar las variantes.'
  } finally {
    cargandoVariantes.value = false
  }
}

async function cargarTipo() {
  if (!props.producto.tipoProductoId || !props.producto.versionTipoProducto) return
  try {
    const tipo = await leerTipoProductoEmprendedora(servicios(), props.producto.tipoProductoId, props.producto.versionTipoProducto)
    tipoNombre.value = tipo.nombre
    etiquetas.value = Object.fromEntries(tipo.campos.map((campo) => [campo.clave, campo.etiqueta]))
  } catch {
  }
}

function mostrarAviso(texto: string) {
  aviso.value = texto
  clearTimeout(temporizadorAviso)
  temporizadorAviso = setTimeout(() => { aviso.value = '' }, 4000)
}

async function compartir() {
  const tienda = props.cuenta.tienda.nombre || 'mi tienda'
  const resultado = await compartirEnlacePublico({ titulo: props.producto.nombre, texto: `${props.producto.nombre} en ${tienda} · Rincón Amancay`, url: enlace.value })
  if (resultado === 'copiado') mostrarAviso('Enlace copiado. Ya puedes pegarlo donde quieras.')
  else if (resultado === 'compartido') mostrarAviso('Enlace compartido.')
  else if (resultado === 'fallo') mostrarAviso('No se pudo compartir. Copia el enlace a mano.')
}

async function copiar() {
  try {
    await navigator.clipboard.writeText(enlace.value)
    mostrarAviso('Enlace copiado. Ya puedes pegarlo donde quieras.')
  } catch {
    mostrarAviso('No se pudo copiar. Selecciona el enlace y cópialo a mano.')
  }
}

function descargarQr() {
  if (!qrSvg.value) return
  const imagenQr = new Image()
  imagenQr.onload = () => {
    const lado = 720
    const lienzo = document.createElement('canvas')
    lienzo.width = lado
    lienzo.height = lado
    const contexto = lienzo.getContext('2d')
    if (!contexto) return
    contexto.fillStyle = '#ffffff'
    contexto.fillRect(0, 0, lado, lado)
    contexto.imageSmoothingEnabled = false
    contexto.drawImage(imagenQr, 0, 0, lado, lado)
    const enlaceDescarga = document.createElement('a')
    enlaceDescarga.href = lienzo.toDataURL('image/png')
    enlaceDescarga.download = nombreArchivoQr(props.producto.nombre)
    enlaceDescarga.click()
  }
  imagenQr.onerror = () => mostrarAviso('No se pudo preparar la imagen del código.')
  imagenQr.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(qrSvg.value)}`
}

function alTeclear(evento: KeyboardEvent) {
  if (evento.key === 'Escape' && qrAbierto.value) qrAbierto.value = false
}

onMounted(() => {
  window.addEventListener('keydown', alTeclear)
  void cargarVariantes()
  void cargarTipo()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', alTeclear)
  clearTimeout(temporizadorAviso)
})
</script>

<template>
  <section class="vista-previa" aria-labelledby="titulo-vista-previa">
    <div class="cabeza">
      <div>
        <span class="sobre">VISTA PREVIA</span>
        <h2 id="titulo-vista-previa">Así verá tu producto una compradora</h2>
        <p>Revisa la foto y los datos. {{ notaEstado }}</p>
      </div>
      <button class="boton-claro" type="button" @click="emit('cerrar')">← Volver a mis productos</button>
    </div>

    <div class="cuerpo">
      <article class="ficha">
        <div class="foto">
          <ImagenPublica v-if="imagen" :key="imagen" :ruta="imagen" :alt="producto.nombre" ajuste="contain" :zoom="true" />
          <div v-else class="sin-foto">
            <span aria-hidden="true">✿</span>
            <p>{{ !producto.imagenes.length ? 'Este producto aún no tiene foto.' : producto.estado === 'publicado' ? 'La foto se está preparando. Vuelve a abrir la vista previa en un momento.' : 'La foto se mostrará aquí cuando el producto esté publicado.' }}</p>
          </div>
        </div>
        <div class="datos">
          <small>{{ [tipoNombre, categoria].filter(Boolean).join(' · ') || 'Producto' }}</small>
          <h3>{{ producto.nombre || 'Producto sin nombre' }}</h3>
          <p class="tienda">de {{ cuenta.tienda.nombre || 'tu tienda' }}</p>
          <p v-if="producto.descripcion" class="descripcion">{{ producto.descripcion }}</p>

          <div class="precio">
            <strong>{{ precio }}</strong>
            <span :class="{ agotado: disponibilidad.agotado }">{{ disponibilidad.texto }}</span>
          </div>

          <div v-if="producto.tieneVariantes" class="variantes">
            <span class="titulo-bloque">Variantes</span>
            <p v-if="cargandoVariantes" class="nota">Cargando variantes…</p>
            <p v-else-if="errorVariantes" class="nota">{{ errorVariantes }} <button type="button" class="enlace" @click="cargarVariantes">Reintentar</button></p>
            <div v-else-if="activas.length" class="chips" role="group" aria-label="Elige una variante">
              <button
                v-for="variante in activas"
                :key="variante.id"
                type="button"
                :aria-pressed="varianteId === variante.id"
                :class="{ elegida: varianteId === variante.id }"
                @click="varianteId = varianteId === variante.id ? '' : variante.id"
              >{{ nombreVariantePrevia(variante) }}</button>
            </div>
            <p v-else class="nota">No hay variantes activas.</p>
          </div>

          <dl v-if="detalles.length" class="detalles">
            <div v-for="fila in detalles" :key="fila.clave"><dt>{{ fila.etiqueta }}</dt><dd>{{ fila.valor }}</dd></div>
          </dl>

          <button class="boton-contactar" type="button" disabled title="Las compradoras verán aquí el botón para contactarte.">Contactar a la emprendedora</button>
        </div>
      </article>

      <aside class="compartir" aria-labelledby="titulo-compartir">
        <h3 id="titulo-compartir">Compartir producto</h3>
        <template v-if="!motivo">
          <p>Envía el enlace por WhatsApp o redes, o imprime el código QR para tu puesto en la feria.</p>
          <label class="campo-enlace">
            <span class="sr-only">Enlace del producto</span>
            <input :value="enlace" type="text" readonly @focus="($event.target as HTMLInputElement).select()" />
          </label>
          <div class="acciones">
            <button class="boton-vino" type="button" @click="compartir">Compartir</button>
            <button class="boton-claro" type="button" @click="copiar">Copiar enlace</button>
            <button class="boton-claro" type="button" @click="qrAbierto = true">Código QR</button>
            <NuxtLink class="boton-claro" :to="{ path: '/comprador', query: { tienda: cuenta.tienda.id, producto: producto.id } }" target="_blank" rel="noopener">Ver en mi tienda ↗</NuxtLink>
          </div>
        </template>
        <p v-else class="motivo">{{ textoMotivo }}</p>
        <p v-if="aviso" class="aviso" role="status">{{ aviso }}</p>
        <div class="acciones">
          <button class="boton-vino" type="button" @click="emit('editar', producto.id)">Editar producto</button>
        </div>
      </aside>
    </div>

    <div v-if="qrAbierto" class="fondo-dialogo" @click.self="qrAbierto = false">
      <div class="dialogo" role="dialog" aria-modal="true" aria-labelledby="titulo-qr">
        <h3 id="titulo-qr">Código QR del producto</h3>
        <p>Al escanearlo con la cámara del teléfono se abre «{{ producto.nombre }}» en tu tienda.</p>
        <div class="qr" role="img" :aria-label="`Código QR de ${producto.nombre}`" v-html="qrSvg" />
        <div class="botones">
          <button class="boton-claro" type="button" @click="qrAbierto = false">Cerrar</button>
          <button class="boton-vino" type="button" @click="descargarQr">Descargar imagen</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.vista-previa{display:grid;gap:18px}
.cabeza{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px}
.sobre{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.15em}
.cabeza h2{margin:6px 0 4px;color:var(--vino,#74244e);font:400 30px/1.1 'DM Serif Display',Georgia,serif}
.cabeza p{max-width:640px;margin:0;color:var(--suave,#7a6468);font-size:14px;line-height:1.5}
.cuerpo{display:grid;grid-template-columns:minmax(0,1fr) minmax(260px,340px);align-items:start;gap:18px}
.ficha{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:22px;padding:18px;border:1px solid var(--borde,#ecd8cb);border-radius:24px;background:#fff}
.foto{overflow:hidden;aspect-ratio:1;border-radius:18px;background:#fdf1e7}
.foto :deep(img){width:100%;height:100%}
.sin-foto{display:grid;place-content:center;justify-items:center;gap:8px;height:100%;padding:20px;color:var(--suave,#7a6468);text-align:center}
.sin-foto span{color:#e76b62;font-size:44px}
.sin-foto p{max-width:240px;margin:0;font-size:13px;line-height:1.5}
.datos{display:grid;align-content:start;gap:10px;min-width:0}
.datos small{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}
.datos h3{margin:0;color:var(--texto,#3f222c);font:400 30px/1.1 'DM Serif Display',Georgia,serif;overflow-wrap:anywhere}
.tienda{margin:-4px 0 0;color:var(--vino,#74244e);font-size:13px;font-weight:700}
.descripcion{margin:0;color:#5f4b50;font-size:14px;line-height:1.6;white-space:pre-line}
.precio{display:flex;align-items:baseline;flex-wrap:wrap;gap:6px 14px;padding:12px 14px;border-radius:16px;background:#fff7f0}
.precio strong{color:var(--texto,#3f222c);font-size:24px}
.precio span{color:#2f6b40;font-size:13px;font-weight:800}
.precio span.agotado{color:#9a4a10}
.titulo-bloque{display:block;margin-bottom:6px;color:var(--texto,#3f222c);font-size:13px;font-weight:800}
.chips{display:flex;flex-wrap:wrap;gap:8px}
.chips button{min-height:40px;padding:0 14px;border:1px solid var(--borde,#ecd8cb);border-radius:999px;background:#fff;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.chips button.elegida{border-color:var(--vino,#74244e);background:var(--vino,#74244e);color:#fff}
.nota{margin:0;color:var(--suave,#7a6468);font-size:13px}
.enlace{padding:0;border:0;background:none;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;text-decoration:underline;cursor:pointer}
.detalles{display:grid;margin:0;border:1px solid var(--borde,#ecd8cb);border-radius:14px;overflow:hidden}
.detalles div{display:grid;grid-template-columns:minmax(110px,40%) minmax(0,1fr);gap:10px;padding:9px 12px}
.detalles div:nth-child(odd){background:#fff8f2}
.detalles dt{color:var(--suave,#7a6468);font-size:13px;font-weight:700}
.detalles dd{margin:0;color:var(--texto,#3f222c);font-size:13px;overflow-wrap:anywhere}
.boton-contactar{min-height:48px;border:0;border-radius:14px;background:var(--vino,#74244e);color:#fff;font:700 14px 'Plus Jakarta Sans',sans-serif;opacity:.55;cursor:not-allowed}
.compartir{position:sticky;top:12px;display:grid;gap:12px;padding:18px;border:1px solid var(--borde,#ecd8cb);border-radius:22px;background:#fffdf9}
.compartir h3{margin:0;color:var(--vino,#74244e);font:400 24px 'DM Serif Display',Georgia,serif}
.compartir p{margin:0;color:#5f4b50;font-size:14px;line-height:1.5}
.campo-enlace input{width:100%;min-height:44px;padding:0 12px;border:1px solid var(--borde,#ecd8cb);border-radius:12px;background:#fff;color:var(--texto,#3f222c);font:500 13px 'Plus Jakarta Sans',sans-serif}
.acciones{display:flex;flex-wrap:wrap;gap:8px}
.acciones>*{flex:1 1 auto}
.motivo{padding:12px 14px;border-radius:14px;background:#fff4e5}
.aviso{padding:10px 12px;border-radius:12px;background:#edf7ed;color:#286344 !important;font-weight:700}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 16px;border-radius:13px;font:700 13px 'Plus Jakarta Sans',sans-serif;text-decoration:none;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fff;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:focus-visible,.boton-vino:focus-visible,.chips button:focus-visible,.campo-enlace input:focus-visible{outline:3px solid rgba(116,36,78,.3);outline-offset:2px}
.fondo-dialogo{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:16px;background:rgba(43,20,28,.45)}
.dialogo{display:grid;gap:12px;width:min(400px,100%);padding:22px;border-radius:22px;background:#fffaf3;box-shadow:0 24px 60px rgba(43,20,28,.3)}
.dialogo h3{margin:0;color:#5b1b3c;font:400 24px 'DM Serif Display',Georgia,serif}
.dialogo p{margin:0;color:#5f4b50;font-size:14px;line-height:1.5}
.qr{justify-self:center;width:min(260px,100%);padding:10px;border-radius:16px;background:#fff}
.qr :deep(svg){display:block;width:100%;height:auto}
.botones{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:8px}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
@media(max-width:980px){.cuerpo{grid-template-columns:1fr}.compartir{position:static}}
@media(max-width:640px){
  .ficha{grid-template-columns:1fr;padding:12px}
  .cabeza h2{font-size:25px}
  .datos h3{font-size:25px}
  .cabeza>.boton-claro{width:100%}
}
</style>
