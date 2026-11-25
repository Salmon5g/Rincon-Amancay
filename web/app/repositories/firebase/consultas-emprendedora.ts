import { collection, doc, getCountFromServer, getDocsFromServer, limit, orderBy, query, serverTimestamp, startAfter, updateDoc, where } from 'firebase/firestore'
import type { DocumentData, QueryDocumentSnapshot } from 'firebase/firestore'
import type { ServiciosCliente } from '~/lib/cliente-base'

export type EstadoConsulta = 'nueva' | 'leida' | 'respondida'

export type ConsultaEmprendedoraWeb = {
  id: string
  nombre: string
  correo: string
  mensaje: string
  producto: string
  creadaEn: Date
  estado: EstadoConsulta
  respuesta: string
  respondidaEn: Date | null
}

export type PaginaConsultasEmprendedora = {
  consultas: ConsultaEmprendedoraWeb[]
  cursor: QueryDocumentSnapshot<DocumentData> | null
  hayMas: boolean
}

export const TAMANO_PAGINA_CONSULTAS = 20

function texto(valor: unknown): string {
  return typeof valor === 'string' ? valor.trim() : ''
}

function fecha(valor: unknown): Date | null {
  return valor && typeof valor === 'object' && 'toDate' in valor && typeof valor.toDate === 'function'
    ? (valor.toDate() as Date)
    : null
}

export function mapearConsultaEmprendedora(id: string, datos: DocumentData): ConsultaEmprendedoraWeb | null {
  const creadaEn = fecha(datos.creadaEn)
  if (!creadaEn) return null
  const respuesta = texto(datos.respuesta)
  const estadoGuardado = texto(datos.estado)
  const estado: EstadoConsulta = respuesta ? 'respondida' : estadoGuardado === 'leida' ? 'leida' : estadoGuardado === 'respondida' ? 'respondida' : 'nueva'
  return {
    id,
    nombre: texto(datos.nombre) || 'Compradora',
    correo: texto(datos.correo),
    mensaje: texto(datos.mensaje),
    producto: texto(datos.producto),
    creadaEn,
    estado,
    respuesta,
    respondidaEn: fecha(datos.respondidaEn),
  }
}

export async function listarPaginaConsultasEmprendedora(
  firebase: ServiciosCliente,
  tiendaId: string,
  cursor: QueryDocumentSnapshot<DocumentData> | null = null,
): Promise<PaginaConsultasEmprendedora> {
  const base = collection(firebase.db, 'tiendasPrivadas', tiendaId, 'consultas')
  const consulta = cursor
    ? query(base, orderBy('creadaEn', 'desc'), startAfter(cursor), limit(TAMANO_PAGINA_CONSULTAS))
    : query(base, orderBy('creadaEn', 'desc'), limit(TAMANO_PAGINA_CONSULTAS))
  const resultado = await getDocsFromServer(consulta)
  return {
    consultas: resultado.docs.flatMap((documento) => {
      const consultaMapeada = mapearConsultaEmprendedora(documento.id, documento.data())
      return consultaMapeada ? [consultaMapeada] : []
    }),
    cursor: resultado.docs.at(-1) ?? null,
    hayMas: resultado.docs.length === TAMANO_PAGINA_CONSULTAS,
  }
}

export async function contarConsultasNuevasEmprendedora(firebase: ServiciosCliente, tiendaId: string): Promise<number> {
  const resultado = await getCountFromServer(query(collection(firebase.db, 'tiendasPrivadas', tiendaId, 'consultas'), where('estado', '==', 'nueva')))
  return resultado.data().count
}

export async function marcarConsultaLeidaEmprendedora(firebase: ServiciosCliente, tiendaId: string, consultaId: string): Promise<void> {
  await updateDoc(doc(firebase.db, 'tiendasPrivadas', tiendaId, 'consultas', consultaId), {
    estado: 'leida',
    leidaEn: serverTimestamp(),
  })
}
