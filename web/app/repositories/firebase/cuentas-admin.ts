import { deleteApp, initializeApp } from 'firebase/app'
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  inMemoryPersistence,
  setPersistence,
  signOut,
} from 'firebase/auth'
import type { ServiciosCliente } from '~/lib/cliente-base'

export async function crearIdentidadEmprendedora(
  firebase: ServiciosCliente,
  correo: string,
  contrasenaInicial: string,
): Promise<string> {
  const app = initializeApp(firebase.app.options, `rincon-amancay-alta-${crypto.randomUUID()}`)
  const auth = getAuth(app)
  try {
    if (firebase.app.options.projectId === 'demo-rincon-amancay') {
      connectAuthEmulator(auth, 'http://127.0.0.1:9099')
    }
    await setPersistence(auth, inMemoryPersistence)
    const credencial = await createUserWithEmailAndPassword(auth, correo.trim(), contrasenaInicial)
    return credencial.user.uid
  } finally {
    await signOut(auth).catch(() => undefined)
    await deleteApp(app).catch(() => undefined)
  }
}
