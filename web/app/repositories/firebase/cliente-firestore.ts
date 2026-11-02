import { collection, doc, getDoc, getDocs, limit, query, startAfter, where, runTransaction, serverTimestamp } from 'firebase/firestore'
import type { Firestore, QueryDocumentSnapshot, Timestamp } from 'firebase/firestore'
import type { Auth } from 'firebase/auth'
import { ErrorApi } from '../../lib/error-api.ts'

function id(value: string) {
  if (!/^[a-zA-Z0-9_-]{1,128}$/.test(value)) throw new Error('ID inválido.')
  return value
}

export function versionDocumento(fecha: Timestamp): string {
  return `${fecha.seconds}:${fecha.nanoseconds}`
}

export function crearRepositorioFirestoreCliente({ auth, db }: { auth: Auth; db: Firestore }) {
  return {
    async consultarAcceso() {
      await auth.authStateReady()
      if (!auth.currentUser) return null
      const snapshot = await getDoc(doc(db, 'accesos', auth.currentUser.uid))
      return snapshot.exists() ? snapshot.data() : null
    },
    async listarTiendas(ultimo?: QueryDocumentSnapshot) {
      const consulta = query(
        collection(db, 'tiendasPublicas'),
        where('estadoPublicacion', '==', 'publicado'),
        where('habilitada', '==', true),
        ...(ultimo ? [startAfter(ultimo)] : []),
        limit(20),
      )
      return getDocs(consulta)
    },
    async listarProductos(tiendaId: string, ultimo?: QueryDocumentSnapshot) {
      const consulta = query(
        collection(db, 'tiendasPublicas', id(tiendaId), 'productos'),
        where('estadoPublicacion', '==', 'publicado'),
        ...(ultimo ? [startAfter(ultimo)] : []),
        limit(20),
      )
      return getDocs(consulta)
    },
    listarVariantes(tiendaId: string, productoId: string) {
      return getDocs(query(
        collection(db, 'tiendasPublicas', id(tiendaId), 'productos', id(productoId), 'variantes'),
        where('activa', '==', true),
        limit(100),
      ))
    },
    leerProductoPrivado(tiendaId: string, productoId: string) {
      return getDoc(doc(db, 'tiendasPrivadas', id(tiendaId), 'productos', id(productoId)))
    },
    async seleccionarImagenes(tiendaId: string, productoId: string, rutas: string[], versionEsperada: string) {
      if (rutas.length > 5 || new Set(rutas).size !== rutas.length) throw new Error('Seleccionar hasta cinco imágenes distintas.')
      const producto = doc(db, 'tiendasPrivadas', id(tiendaId), 'productos', id(productoId))
      await runTransaction(db, async (transaccion) => {
        const snapshot = await transaccion.get(producto)
        if (!snapshot.exists() || versionDocumento(snapshot.data().actualizadoEn) !== versionEsperada) {
          throw new ErrorApi('conflicto', 'El producto cambió; volver a leer.', 409)
        }
        transaccion.update(producto, { imagenes: rutas, actualizadoEn: serverTimestamp() })
      })
    },
  }
}
