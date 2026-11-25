import { signInWithEmailAndPassword, signOut, createUserWithEmailAndPassword, sendEmailVerification, sendPasswordResetEmail, applyActionCode, verifyPasswordResetCode, confirmPasswordReset } from 'firebase/auth';
import type { Auth } from 'firebase/auth';
import type { Firestore } from 'firebase/firestore';
import { getBlob, ref, uploadBytes } from 'firebase/storage';
import type { FirebaseStorage } from 'firebase/storage';
import type { FirebaseApp } from 'firebase/app';
import { crearRepositorioFirestoreCliente, versionDocumento } from '../repositories/firebase/cliente-firestore.ts';
import { ErrorApi } from './error-api.ts';

// Servicios que debe entregar cualquier inicializador (local o compartido).
export interface ServiciosCliente {
  app: FirebaseApp;
  auth: Auth;
  db: Firestore;
  storage: FirebaseStorage;
}

export const operaciones = ['publicarTienda', 'retirarTienda', 'editarTiendaEmprendedora', 'configurarMostrarPrecios', 'listarFeriasVenta', 'registrarVenta', 'publicarProducto', 'retirarProducto', 'altaEmprendedora', 'crearProducto', 'editarProducto', 'eliminarProducto', 'asignarImagenVariante', 'ajustarStock', 'desactivarEmprendedora', 'registrarComprador', 'invitarEmprendedora',
  'consultarInvitacion', 'aceptarInvitacion', 'cancelarInvitacion', 'reactivarEmprendedora', 'listarEmprendedorasAdmin', 'editarCuentaEmprendedora', 'crearElementoCatalogo', 'editarElementoCatalogo', 'cambiarEstadoCatalogo', 'listarAsociaciones', 'leerIntegrantesAsociacion', 'crearAsociacion', 'editarAsociacion', 'cambiarEstadoAsociacion', 'asignarEmprendedora', 'quitarEmprendedora', 'leerConfiguracionNavegacion', 'guardarConfiguracionNavegacion', 'listarAdministradores',
  'agregarAdministrador', 'quitarAdministrador', 'listarFeriasAdmin', 'listarInvitacionesFeria', 'crearFeria', 'editarFeria', 'cambiarEstadoFeria', 'leerParticipantesFeria', 'invitarParticipanteFeria', 'agregarParticipanteFeria', 'cancelarInvitacionFeria', 'retirarParticipanteFeria', 'registrarAsistenciaFeria', 'responderInvitacionFeria', 'enviarRecordatorioFeria', 'enviarAvisoConfirmadasFeria'] as const;
export type Operacion = typeof operaciones[number];
export { ErrorApi };
function id(value: string) { if (!/^[a-zA-Z0-9_-]{1,128}$/.test(value)) throw new Error('ID inválido.'); return value; }
export { versionDocumento };
type TipoMimeImagen = 'image/jpeg' | 'image/png' | 'image/webp';
async function detectarTipoMimeImagen(archivo: Blob): Promise<TipoMimeImagen> {
  const bytes = new Uint8Array(await archivo.slice(0, 12).arrayBuffer());
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if (bytes.length >= 8 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 && bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a) return 'image/png';
  if (bytes.length >= 12 && bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 && bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) return 'image/webp';
  throw new Error('El contenido del archivo no es una imagen JPEG, PNG o WebP válida.');
}

// Lógica común a los clientes web. `apiBase` debe terminar en /api/v1/.
export function crearCliente(firebase: ServiciosCliente, apiBase: string) {
  const { auth, db, storage } = firebase;
  const repositorioFirestore = crearRepositorioFirestoreCliente({ auth, db });
  return {
    firebase,
    ...repositorioFirestore,
    iniciarSesion: (correo: string, clave: string) => signInWithEmailAndPassword(auth, correo, clave),
    // Crear identidad y enviar verificación son pasos separados: si el envío falla,
    // se reenvía el correo sin intentar registrar otra vez la misma identidad.
    crearIdentidad: (correo: string, clave: string) => createUserWithEmailAndPassword(auth, correo, clave),
    async enviarVerificacion(urlContinuacion?: string) {
      await auth.authStateReady();
      if (!auth.currentUser) throw new ErrorApi('no-autenticado', 'Iniciar sesión.', 401);
      const ajustes = urlContinuacion ? { url: urlContinuacion, handleCodeInApp: false } : undefined;
      return sendEmailVerification(auth.currentUser, ajustes);
    },
    async confirmarCorreo(codigo: string) {
      await applyActionCode(auth, codigo);
      await auth.authStateReady();
      if (auth.currentUser) { await auth.currentUser.reload(); await auth.currentUser.getIdToken(true); }
    },
    async solicitarRecuperacion(correo: string) {
      auth.languageCode = 'es';
      try { await sendPasswordResetEmail(auth, correo); }
      catch (error) { if ((error as { code?: string }).code !== 'auth/user-not-found') throw error; }
      return { mensaje: 'Si el correo corresponde a una cuenta, recibirás instrucciones.' };
    },
    comprobarRecuperacion: (codigo: string) => verifyPasswordResetCode(auth, codigo),
    async confirmarRecuperacion(codigo: string, clave: string) {
      await confirmPasswordReset(auth, codigo, clave); await signOut(auth);
    },
    cerrarSesion: () => signOut(auth),
    async subirImagen(tiendaId: string, productoId: string, archivo: Blob): Promise<string> {
      if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) throw new Error('Usar PNG, JPEG o WebP de hasta 5 MiB.');
      const contentType = await detectarTipoMimeImagen(archivo);
      const extension: Record<TipoMimeImagen, string> = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp' };
      const ruta = `tiendas/${id(tiendaId)}/productos/${id(productoId)}/${crypto.randomUUID()}.${extension[contentType]}`;
      await uploadBytes(ref(storage, ruta), archivo, { contentType }); return ruta;
    },
    async subirLogoTienda(tiendaId: string, archivo: Blob, archivoId: string): Promise<string> {
      const tienda = id(tiendaId);
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(archivoId)) throw new Error('El identificador de archivo no es válido.');
      if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) throw new Error('Usar una imagen JPEG, PNG o WebP de hasta 5 MiB.');
      const contentType = await detectarTipoMimeImagen(archivo);
      await auth.authStateReady();
      const user = auth.currentUser;
      if (!user) throw new ErrorApi('no-autenticado', 'Iniciar sesión antes de continuar.', 401);
      const token = await user.getIdToken();
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 45000);
      try {
        const response = await fetch(`${apiBase}tiendas/${tienda}/logo`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': contentType, 'X-Archivo-Id': archivoId }, body: archivo, signal: controller.signal, redirect: 'error' });
        const body = await response.json();
        if (!response.ok) throw new ErrorApi(body.error?.code ?? 'error-api', body.error?.mensaje ?? 'No se pudo guardar el logo.', response.status, response.status >= 500);
        const ruta = body.datos?.ruta;
        if (typeof ruta !== 'string') throw new ErrorApi('respuesta-invalida', 'No se confirmó la ruta del logo.', response.status, true);
        return ruta;
      } catch (error) {
        if (error instanceof ErrorApi) throw error;
        throw new ErrorApi('conexion-interrumpida', 'No se confirmó la carga. Reintenta con el mismo archivo.', 0, true);
      } finally { clearTimeout(timeout); }
    },
    async subirImagenProducto(tiendaId: string, productoId: string, archivo: Blob, archivoId: string, reemplazarIndice?: number): Promise<string> {
      const tienda = id(tiendaId), producto = id(productoId);
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(archivoId)) throw new Error('El identificador de archivo no es válido.');
      if (reemplazarIndice !== undefined && (!Number.isInteger(reemplazarIndice) || reemplazarIndice < 0 || reemplazarIndice > 4)) throw new Error('La posición de imagen a reemplazar no es válida.');
      if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) throw new Error('Usar una imagen JPEG, PNG o WebP de hasta 5 MiB.');
      const contentType = await detectarTipoMimeImagen(archivo);
      await auth.authStateReady();
      const user = auth.currentUser;
      if (!user) throw new ErrorApi('no-autenticado', 'Iniciar sesión antes de continuar.', 401);
      const token = await user.getIdToken();
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 45000);
      try {
        const headers: Record<string, string> = { Authorization: `Bearer ${token}`, 'Content-Type': contentType, 'X-Archivo-Id': archivoId };
        if (reemplazarIndice !== undefined) headers['X-Reemplazar-Indice'] = String(reemplazarIndice);
        const response = await fetch(`${apiBase}tiendas/${tienda}/productos/${producto}/imagenes`, { method: 'POST', headers, body: archivo, signal: controller.signal, redirect: 'error' });
        const body = await response.json();
        if (!response.ok) throw new ErrorApi(body.error?.code ?? 'error-api', body.error?.mensaje ?? 'No se pudo guardar la imagen del producto.', response.status, response.status >= 500);
        const ruta = body.datos?.ruta;
        if (typeof ruta !== 'string') throw new ErrorApi('respuesta-invalida', 'No se confirmó la ruta de la imagen.', response.status, true);
        return ruta;
      } catch (error) {
        if (error instanceof ErrorApi) throw error;
        throw new ErrorApi('conexion-interrumpida', 'No se confirmó la carga. Reintenta con el mismo archivo.', 0, true);
      } finally { clearTimeout(timeout); }
    },
    async subirLogoAsociacion(archivo: Blob, archivoId: string): Promise<string> {
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(archivoId)) throw new Error('El identificador de archivo no es válido.');
      if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) throw new Error('Usar una imagen JPEG, PNG o WebP de hasta 5 MiB.');
      const contentType = await detectarTipoMimeImagen(archivo);
      await auth.authStateReady();
      const user = auth.currentUser;
      if (!user) throw new ErrorApi('no-autenticado', 'Iniciar sesión antes de continuar.', 401);
      const token = await user.getIdToken();
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 45000);
      try {
        const response = await fetch(`${apiBase}asociaciones/logo`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': contentType, 'X-Archivo-Id': archivoId }, body: archivo, signal: controller.signal, redirect: 'error' });
        const body = await response.json();
        if (!response.ok) throw new ErrorApi(body.error?.code ?? 'error-api', body.error?.mensaje ?? 'No se pudo guardar el logo de la asociación.', response.status, response.status >= 500);
        const url = body.datos?.ruta;
        if (typeof url !== 'string' || !url.startsWith('https://')) throw new ErrorApi('respuesta-invalida', 'No se confirmó la URL pública del logo.', response.status, true);
        return url;
      } catch (error) {
        if (error instanceof ErrorApi) throw error;
        throw new ErrorApi('conexion-interrumpida', 'No se confirmó la carga. Reintenta con el mismo archivo.', 0, true);
      } finally { clearTimeout(timeout); }
    },
    async subirImagenFeria(archivo: Blob, archivoId: string): Promise<string> {
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(archivoId)) throw new Error('El identificador de archivo no es válido.');
      if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) throw new Error('Usar una imagen JPEG, PNG o WebP de hasta 5 MiB.');
      const contentType = await detectarTipoMimeImagen(archivo);
      await auth.authStateReady();
      const user = auth.currentUser;
      if (!user) throw new ErrorApi('no-autenticado', 'Iniciar sesión antes de continuar.', 401);
      const token = await user.getIdToken();
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 45000);
      try {
        const response = await fetch(`${apiBase}ferias/imagen`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': contentType, 'X-Archivo-Id': archivoId }, body: archivo, signal: controller.signal, redirect: 'error' });
        const body = await response.json();
        if (!response.ok) throw new ErrorApi(body.error?.code ?? 'error-api', body.error?.mensaje ?? 'No se pudo guardar la imagen de la feria.', response.status, response.status >= 500);
        const url = body.datos?.ruta;
        if (typeof url !== 'string' || !url.startsWith('https://')) throw new ErrorApi('respuesta-invalida', 'No se confirmó la URL pública de la imagen.', response.status, true);
        return url;
      } catch (error) {
        if (error instanceof ErrorApi) throw error;
        throw new ErrorApi('conexion-interrumpida', 'No se confirmó la carga. Reintenta con el mismo archivo.', 0, true);
      } finally { clearTimeout(timeout); }
    },
    leerImagen: (ruta: string) => getBlob(ref(storage, ruta), 5 * 1024 * 1024),
    async llamar(operacion: Operacion, solicitud: Record<string, unknown>): Promise<Record<string, unknown>> {
      if (!operaciones.includes(operacion)) throw new Error('Operación desconocida.');
      await auth.authStateReady();
      const user = auth.currentUser;
      if (!user) throw new ErrorApi('no-autenticado', 'Iniciar sesión antes de continuar.', 401);
      const token = await user.getIdToken();
      const cuerpo = JSON.stringify(solicitud);
      if (new TextEncoder().encode(cuerpo).length > 16384) throw new ErrorApi('cuerpo-demasiado-grande', 'Máximo 16 KiB por solicitud.', 413);
      // No genera operacionId ni reintenta: una intención conserva su ID.
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 20000);
      try {
        const response = await fetch(`${apiBase}${operacion}`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: cuerpo, signal: controller.signal, redirect: 'error' });
        const body = await response.json();
        if (!response.ok) throw new ErrorApi(body.error?.code ?? 'error-api', body.error?.mensaje ?? 'No se pudo completar la operación.', response.status, response.status >= 500);
        if (!body.datos || typeof body.datos !== 'object' || Array.isArray(body.datos)) throw new ErrorApi('respuesta-invalida', 'Revisar el estado antes de reintentar.', response.status, true);
        return body.datos;
      } catch (error) {
        if (error instanceof ErrorApi) throw error;
        throw new ErrorApi('conexion-interrumpida', 'No se confirmó el resultado. Conservar la solicitud y su operacionId al reintentar.', 0, true);
      } finally { clearTimeout(timeout); }
    },
  };
}
