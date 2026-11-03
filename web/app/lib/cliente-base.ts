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

export const operaciones = ['publicarTienda','retirarTienda','publicarProducto','retirarProducto','altaEmprendedora','crearProducto','editarProducto','ajustarStock','desactivarEmprendedora','registrarComprador','invitarEmprendedora','consultarInvitacion','aceptarInvitacion','cancelarInvitacion','reactivarEmprendedora','listarEmprendedorasAdmin','editarCuentaEmprendedora','crearElementoCatalogo','editarElementoCatalogo','cambiarEstadoCatalogo','listarAsociaciones','leerIntegrantesAsociacion','crearAsociacion','editarAsociacion','cambiarEstadoAsociacion','asignarEmprendedora','quitarEmprendedora','leerConfiguracionNavegacion','guardarConfiguracionNavegacion','listarAdministradores','agregarAdministrador','quitarAdministrador','listarFeriasAdmin','crearFeria','editarFeria','cambiarEstadoFeria','leerParticipantesFeria','invitarParticipanteFeria','agregarParticipanteFeria','retirarParticipanteFeria','registrarAsistenciaFeria','responderInvitacionFeria'] as const;
export type Operacion = typeof operaciones[number];
export { ErrorApi };
function id(value: string) {if (!/^[a-zA-Z0-9_-]{1,128}$/.test(value)) throw new Error('ID inválido.');return value;}
export { versionDocumento };

// Lógica común a los clientes web. `apiBase` debe terminar en /api/v1/.
export function crearCliente(firebase: ServiciosCliente, apiBase: string) {
  const {auth,db,storage}=firebase;
  const repositorioFirestore = crearRepositorioFirestoreCliente({ auth, db });
  return {
    firebase,
    ...repositorioFirestore,
    iniciarSesion: (correo: string, clave: string) => signInWithEmailAndPassword(auth,correo,clave),
    // Crear identidad y enviar verificación son pasos separados: si el envío falla,
    // se reenvía el correo sin intentar registrar otra vez la misma identidad.
    crearIdentidad: (correo: string, clave: string) => createUserWithEmailAndPassword(auth,correo,clave),
    async enviarVerificacion(urlContinuacion?: string) {
      await auth.authStateReady();
      if(!auth.currentUser) throw new ErrorApi('no-autenticado','Iniciar sesión.',401);
      const ajustes = urlContinuacion ? { url: urlContinuacion, handleCodeInApp: false } : undefined;
      return sendEmailVerification(auth.currentUser, ajustes);
    },
    async confirmarCorreo(codigo: string) {
      await applyActionCode(auth,codigo);
      await auth.authStateReady();
      if(auth.currentUser) {await auth.currentUser.reload();await auth.currentUser.getIdToken(true);}
    },
    async solicitarRecuperacion(correo: string) {
      auth.languageCode = 'es';
      try {await sendPasswordResetEmail(auth,correo);}
      catch(error) {if((error as {code?:string}).code!=='auth/user-not-found') throw error;}
      return {mensaje:'Si el correo corresponde a una cuenta, recibirás instrucciones.'};
    },
    comprobarRecuperacion: (codigo: string) => verifyPasswordResetCode(auth,codigo),
    async confirmarRecuperacion(codigo: string, clave: string) {
      await confirmPasswordReset(auth,codigo,clave);await signOut(auth);
    },
    cerrarSesion: () => signOut(auth),
    async subirImagen(tiendaId: string, productoId: string, archivo: Blob): Promise<string> {
      const extension: Record<string,string>={'image/png':'png','image/jpeg':'jpg','image/webp':'webp'};
      if(!extension[archivo.type] || archivo.size<=0 || archivo.size>5*1024*1024) throw new Error('Usar PNG, JPEG o WebP de hasta 5 MiB.');
      const ruta=`tiendas/${id(tiendaId)}/productos/${id(productoId)}/${crypto.randomUUID()}.${extension[archivo.type]}`;
      await uploadBytes(ref(storage,ruta),archivo,{contentType:archivo.type});return ruta;
    },
    leerImagen: (ruta: string) => getBlob(ref(storage,ruta),5*1024*1024),
    async llamar(operacion: Operacion, solicitud: Record<string,unknown>): Promise<Record<string,unknown>> {
      if(!operaciones.includes(operacion)) throw new Error('Operación desconocida.');
      await auth.authStateReady();
      const user=auth.currentUser;
      if(!user) throw new ErrorApi('no-autenticado','Iniciar sesión antes de continuar.',401);
      const token=await user.getIdToken();
      const cuerpo=JSON.stringify(solicitud);
      if(new TextEncoder().encode(cuerpo).length>16384) throw new ErrorApi('cuerpo-demasiado-grande','Máximo 16 KiB por solicitud.',413);
      // No genera operacionId ni reintenta: una intención conserva su ID.
      const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),20000);
      try {
        const response=await fetch(`${apiBase}${operacion}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:cuerpo,signal:controller.signal,redirect:'error'});
        const body=await response.json();
        if(!response.ok) throw new ErrorApi(body.error?.code??'error-api',body.error?.mensaje??'No se pudo completar la operación.',response.status,response.status>=500);
        if(!body.datos || typeof body.datos!=='object' || Array.isArray(body.datos)) throw new ErrorApi('respuesta-invalida','Revisar el estado antes de reintentar.',response.status,true);
        return body.datos;
      } catch(error) {
        if(error instanceof ErrorApi) throw error;
        throw new ErrorApi('conexion-interrumpida','No se confirmó el resultado. Conservar la solicitud y su operacionId al reintentar.',0,true);
      } finally {clearTimeout(timeout);}
    },
  };
}
