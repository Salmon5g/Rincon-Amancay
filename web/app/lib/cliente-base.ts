import { signInWithEmailAndPassword, signOut, createUserWithEmailAndPassword, sendEmailVerification, sendPasswordResetEmail, applyActionCode, verifyPasswordResetCode, confirmPasswordReset } from 'firebase/auth';
import type { Auth } from 'firebase/auth';
import { collection, doc, getDoc, getDocs, limit, query, startAfter, where, runTransaction, serverTimestamp } from 'firebase/firestore';
import type { Firestore, QueryDocumentSnapshot, Timestamp } from 'firebase/firestore';
import { getBlob, ref, uploadBytes } from 'firebase/storage';
import type { FirebaseStorage } from 'firebase/storage';
import type { FirebaseApp } from 'firebase/app';

// Servicios que debe entregar cualquier inicializador (local o compartido).
export interface ServiciosCliente {
  app: FirebaseApp;
  auth: Auth;
  db: Firestore;
  storage: FirebaseStorage;
}

export const operaciones = ['publicarTienda','retirarTienda','publicarProducto','retirarProducto','altaEmprendedora','crearProducto','editarProducto','ajustarStock','desactivarEmprendedora','registrarComprador','invitarEmprendedora','consultarInvitacion','aceptarInvitacion','cancelarInvitacion','reactivarEmprendedora'] as const;
export type Operacion = typeof operaciones[number];
export class ErrorApi extends Error {
  code: string; status: number; resultadoIncierto: boolean;
  constructor(code: string, mensaje: string, status = 0, resultadoIncierto = false) {
    super(mensaje); this.code=code; this.status=status; this.resultadoIncierto=resultadoIncierto;
  }
}
export function versionDocumento(fecha: Timestamp): string {return `${fecha.seconds}:${fecha.nanoseconds}`;}
function id(value: string) {if (!/^[a-zA-Z0-9_-]{1,128}$/.test(value)) throw new Error('ID inválido.');return value;}

// Lógica común a los clientes web. `apiBase` debe terminar en /api/v1/.
export function crearCliente(firebase: ServiciosCliente, apiBase: string) {
  const {auth,db,storage}=firebase;
  return {
    firebase,
    iniciarSesion: (correo: string, clave: string) => signInWithEmailAndPassword(auth,correo,clave),
    // Crear identidad y enviar verificación son pasos separados: si el envío falla,
    // se reenvía el correo sin intentar registrar otra vez la misma identidad.
    crearIdentidad: (correo: string, clave: string) => createUserWithEmailAndPassword(auth,correo,clave),
    async enviarVerificacion() {
      await auth.authStateReady();
      if(!auth.currentUser) throw new ErrorApi('no-autenticado','Iniciar sesión.',401);
      return sendEmailVerification(auth.currentUser);
    },
    async confirmarCorreo(codigo: string) {
      await applyActionCode(auth,codigo);
      await auth.authStateReady();
      if(auth.currentUser) {await auth.currentUser.reload();await auth.currentUser.getIdToken(true);}
    },
    async solicitarRecuperacion(correo: string) {
      try {await sendPasswordResetEmail(auth,correo);}
      catch(error) {if((error as {code?:string}).code!=='auth/user-not-found') throw error;}
      return {mensaje:'Si el correo corresponde a una cuenta, recibirás instrucciones.'};
    },
    comprobarRecuperacion: (codigo: string) => verifyPasswordResetCode(auth,codigo),
    async confirmarRecuperacion(codigo: string, clave: string) {
      await confirmPasswordReset(auth,codigo,clave);await signOut(auth);
    },
    cerrarSesion: () => signOut(auth),
    async consultarAcceso() {
      await auth.authStateReady();
      if(!auth.currentUser) return null;
      const snapshot=await getDoc(doc(db,'accesos',auth.currentUser.uid));
      return snapshot.exists() ? snapshot.data() : null;
    },
    async listarTiendas(ultimo?: QueryDocumentSnapshot) {
      const q=query(collection(db,'tiendasPublicas'),where('estadoPublicacion','==','publicado'),where('habilitada','==',true),...(ultimo?[startAfter(ultimo)]:[]),limit(20));
      return getDocs(q);
    },
    async listarProductos(tiendaId: string, ultimo?: QueryDocumentSnapshot) {
      const q=query(collection(db,'tiendasPublicas',id(tiendaId),'productos'),where('estadoPublicacion','==','publicado'),...(ultimo?[startAfter(ultimo)]:[]),limit(20));
      return getDocs(q);
    },
    listarVariantes: (tiendaId: string, productoId: string) => getDocs(query(collection(db,'tiendasPublicas',id(tiendaId),'productos',id(productoId),'variantes'),where('activa','==',true),limit(100))),
    leerProductoPrivado: (tiendaId: string, productoId: string) => getDoc(doc(db,'tiendasPrivadas',id(tiendaId),'productos',id(productoId))),
    async seleccionarImagenes(tiendaId: string, productoId: string, rutas: string[], versionEsperada: string) {
      if(rutas.length>5 || new Set(rutas).size!==rutas.length) throw new Error('Seleccionar hasta cinco imágenes distintas.');
      const producto=doc(db,'tiendasPrivadas',id(tiendaId),'productos',id(productoId));
      await runTransaction(db,async tx=>{
        const snapshot=await tx.get(producto);
        if(!snapshot.exists() || versionDocumento(snapshot.data().actualizadoEn)!==versionEsperada) throw new ErrorApi('conflicto','El producto cambió; volver a leer.',409);
        tx.update(producto,{imagenes:rutas,actualizadoEn:serverTimestamp()});
      });
    },
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
