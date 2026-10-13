import { signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { collection, doc, getDocFromServer, getDocsFromServer, limit, query, where } from 'firebase/firestore';
import type { ServiciosCliente } from './cliente-base';

export type FilaAdmin = { titulo: string; detalle: string; estado: string; invitacionId?: string };
export type SeccionLectura = 'administradores' | 'emprendedoras' | 'catalogos';

export function esAdministrador(acceso: { estado?: unknown; roles?: unknown } | null): boolean {
  return acceso?.estado === 'activo' && Array.isArray(acceso.roles) && acceso.roles.includes('administrador');
}

export function crearLecturasAdmin(firebase: ServiciosCliente) {
  const { auth, db } = firebase;
  async function consultarAcceso() {
    await auth.authStateReady();
    if (!auth.currentUser) return null;
    const resultado = await getDocFromServer(doc(db, 'accesos', auth.currentUser.uid));
    return resultado.exists() ? resultado.data() : null;
  }
  return {
    firebase,
    iniciarSesion: (correo: string, clave: string) => signInWithEmailAndPassword(auth, correo, clave),
    cerrarSesion: () => signOut(auth),
    consultarAcceso,
    async listarAdministracion(seccion: SeccionLectura): Promise<FilaAdmin[]> {
      // Comprobación de UX; la autoridad para cada lectura son las reglas Firestore.
      if (!esAdministrador(await consultarAcceso())) throw new Error('Se requiere una cuenta administradora activa.');
      if (seccion === 'administradores') {
        const resultado = await getDocsFromServer(query(collection(db, 'accesos'), where('roles', 'array-contains', 'administrador'), limit(50)));
        return resultado.docs.map(d => ({ titulo: d.id, detalle: 'Identificador de cuenta administradora', estado: String(d.data().estado ?? '') }));
      }
      if (seccion === 'emprendedoras') {
        const [resultado, invitaciones] = await Promise.all([
          getDocsFromServer(query(collection(db, 'emprendedoras'), limit(50))),
          getDocsFromServer(query(collection(db, 'invitaciones'), limit(50))),
        ]);
        // Las reglas no permiten al administrador consultar perfiles privados arbitrarios.
        return [
          ...resultado.docs.map(d => ({ titulo: d.id, detalle: `Tienda: ${String(d.data().tiendaId ?? '')}`, estado: 'Registrada' })),
          ...invitaciones.docs.map(d => ({
            titulo: String(d.data().correo ?? 'Invitación'),
            detalle: `Tienda: ${String(d.data().alta?.nombreTienda ?? '')} · Código: ${d.id}`,
            estado: String(d.data().estado ?? ''),
            invitacionId: d.id,
          })),
        ];
      }
      if (seccion !== 'catalogos') throw new Error('Sección no disponible para lectura.');
      const grupos = ['categorias', 'tiposEmprendimiento', 'tiposProducto'] as const;
      const resultados = await Promise.all(grupos.map(async grupo => {
        const resultado = await getDocsFromServer(query(collection(db, grupo), where('activo', '==', true), limit(50)));
        return resultado.docs.map(d => ({ titulo: String(d.data().nombre ?? d.id), detalle: `${grupo} / ${d.id}`, estado: 'Activo' }));
      }));
      return resultados.flat();
    },
  };
}
