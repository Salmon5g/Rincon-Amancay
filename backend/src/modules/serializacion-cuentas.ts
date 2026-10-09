// Serializa las fases Auth/Firestore en esta API local (un solo proceso).
// El bloqueo persistido en accesos protege además los reintentos tras reiniciar.
// Antes de desplegar varias instancias se requiere una cola por UID.
const pendientes = new Map<string, Promise<unknown>>();
export async function porCuenta<T>(uid: string, ejecutar: () => Promise<T>): Promise<T> {
  const anterior = pendientes.get(uid) ?? Promise.resolve();
  const actual = anterior.catch(() => undefined).then(ejecutar);
  pendientes.set(uid, actual);
  try {return await actual;} finally {if (pendientes.get(uid) === actual) pendientes.delete(uid);}
}
