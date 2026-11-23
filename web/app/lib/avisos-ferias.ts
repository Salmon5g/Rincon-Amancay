import { collection, deleteDoc, doc, getDocs, limit, onSnapshot, query, serverTimestamp, setDoc } from 'firebase/firestore';
import type { ServiciosCliente } from './cliente-base.ts';

/*
 * Avisos de ferias para el comprador web (W18).
 * - Con permiso del navegador: guarda un token FCM en `usuarios/{uid}/dispositivosWeb`
 *   y el backend envía la notificación push web al confirmarse una participación.
 * - Sin permiso o sin llave VAPID: el backend envía el aviso por correo.
 * La preferencia vive en `usuarios/{uid}.avisosFerias`, la misma que usa la app.
 */

export type CanalAvisos = 'navegador' | 'correo';

export class ErrorAvisos extends Error {
    code: string;
    constructor(code: string, mensaje: string) {
        super(mensaje);
        this.code = code;
    }
}

export type ConfiguracionAvisos = {
    vapidKey: string;
    apiKey: string;
    projectId: string;
    messagingSenderId: string;
    appId: string;
};

const RUTA_SERVICIO = '/firebase-messaging-sw.js';

export function crearAvisosFerias(
    firebase: Pick<ServiciosCliente, 'app' | 'auth' | 'db'>,
    configuracion: ConfiguracionAvisos,
) {
    const { app, auth, db } = firebase;

    function usuarioActual() {
        const usuario = auth.currentUser;
        if (!usuario) throw new ErrorAvisos('no-autenticado', 'Inicia sesión para recibir avisos.');
        return usuario;
    }

    function soportaNavegador(): boolean {
        return Boolean(configuracion.vapidKey)
            && typeof window !== 'undefined'
            && 'Notification' in window
            && 'serviceWorker' in navigator;
    }

    function parametrosServicio(): string {
        const parametros = new URLSearchParams();
        const valores: Record<string, string> = {
            apiKey: configuracion.apiKey,
            projectId: configuracion.projectId,
            messagingSenderId: configuracion.messagingSenderId,
            appId: configuracion.appId,
        };
        for (const [clave, valor] of Object.entries(valores)) if (valor) parametros.set(clave, valor);
        return parametros.toString();
    }

    async function registrarToken(uid: string): Promise<boolean> {
        if (!soportaNavegador()) return false;
        const { getMessaging, getToken, isSupported, onMessage } = await import('firebase/messaging');
        if (!(await isSupported())) return false;
        const registro = await navigator.serviceWorker.register(`${RUTA_SERVICIO}?${parametrosServicio()}`);
        const mensajeria = getMessaging(app);
        const token = await getToken(mensajeria, {
            vapidKey: configuracion.vapidKey,
            serviceWorkerRegistration: registro,
        });
        if (!token) return false;
        await setDoc(doc(db, 'usuarios', uid, 'dispositivosWeb', token), {
            token,
            plataforma: 'web',
            actualizadoEn: serverTimestamp(),
        });
        // Con la pestaña abierta FCM entrega aquí; mostramos la notificación del sistema.
        onMessage(mensajeria, (carga) => {
            const datos = (carga.data ?? {}) as Record<string, string>;
            const titulo = datos.titulo || carga.notification?.title || 'Rincón Amancay';
            const cuerpo = datos.mensaje || carga.notification?.body || '';
            const url = datos.feriaId
                ? `/comprador/ferias?feria=${encodeURIComponent(datos.feriaId)}`
                : '/comprador/ferias';
            void registro.showNotification(titulo, {
                body: cuerpo,
                icon: '/logo-amancay.png',
                badge: '/logo-amancay.png',
                data: { url },
            });
        });
        return true;
    }

    return {
        /** Observa la preferencia guardada en el perfil del comprador. */
        observar(escuchar: (activo: boolean) => void): () => void {
            const usuario = auth.currentUser;
            if (!usuario) {
                escuchar(false);
                return () => undefined;
            }
            return onSnapshot(
                doc(db, 'usuarios', usuario.uid),
                documento => escuchar(documento.data()?.avisosFerias === true),
                () => escuchar(false),
            );
        },

        /** Activa la preferencia y devuelve el canal que quedó disponible. */
        async activar(): Promise<CanalAvisos> {
            const usuario = usuarioActual();
            let canal: CanalAvisos = 'correo';
            if (soportaNavegador()) {
                try {
                    const permiso = Notification.permission === 'granted'
                        ? 'granted'
                        : await Notification.requestPermission();
                    if (permiso === 'granted' && await registrarToken(usuario.uid)) canal = 'navegador';
                } catch {
                    canal = 'correo';
                }
            }
            await setDoc(
                doc(db, 'usuarios', usuario.uid),
                { avisosFerias: true, avisosConsentimientoEn: serverTimestamp() },
                { merge: true },
            );
            return canal;
        },

        /** Apaga la preferencia y olvida los tokens web de este navegador. */
        async desactivar(): Promise<void> {
            const usuario = usuarioActual();
            await setDoc(
                doc(db, 'usuarios', usuario.uid),
                { avisosFerias: false, avisosConsentimientoEn: serverTimestamp() },
                { merge: true },
            );
            const dispositivos = await getDocs(query(
                collection(db, 'usuarios', usuario.uid, 'dispositivosWeb'),
                limit(50),
            ));
            await Promise.all(dispositivos.docs.map(dispositivo => deleteDoc(dispositivo.ref)));
        },

        soportaNavegador,
    };
}

export type AvisosFerias = ReturnType<typeof crearAvisosFerias>;
