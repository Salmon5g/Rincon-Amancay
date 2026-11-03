import {
    GoogleAuthProvider,
    applyActionCode,
    checkActionCode,
    verifyPasswordResetCode,
    confirmPasswordReset,
    createUserWithEmailAndPassword,
    onAuthStateChanged,
    sendEmailVerification,
    sendPasswordResetEmail,
    signInWithEmailAndPassword,
    signInWithPopup,
    signOut,
    updateProfile,
} from 'firebase/auth';
import type { User } from 'firebase/auth';
import { doc, getDocFromServer } from 'firebase/firestore';
import type { ServiciosCliente } from './cliente-base.ts';

/* Acceso del comprador web.
- Navegar el catálogo NO requiere sesión: este módulo nunca bloquea lecturas públicas.
- Google y correo/contraseña terminan igual: sesión iniciada + cuenta de comprador creada con `registrarComprador` (el backend toma el UID del token, nunca del cuerpo).
- `llamar` es opcional: en modo `real-lectura` no hay API, así que la sesión funciona pero el alta de comprador queda sin hacer ("sin-registro").

Funciones del SDK agrupadas para poder reemplazarlas en las pruebas unitarias.*/
const sdkPorDefecto = {
    GoogleAuthProvider,
    createUserWithEmailAndPassword,
    onAuthStateChanged,
    sendEmailVerification,
    sendPasswordResetEmail,
    applyActionCode,
    checkActionCode,
    verifyPasswordResetCode,
    confirmPasswordReset,
    signInWithEmailAndPassword,
    signInWithPopup,
    signOut,
    updateProfile,
    doc,
    getDocFromServer,
};
export type SdkAcceso = typeof sdkPorDefecto;

export type LlamarRegistro = (operacion: 'registrarComprador', solicitud: Record<string, unknown>) => Promise<unknown>;

export type ResultadoAcceso =
    | { estado: 'listo'; nombre: string }
    // Hay sesión, pero el correo aún no se confirmó. `envioFallido`: la cuenta se creó
    // y solo falta reenviar el correo (no se debe crear otra identidad).
    | { estado: 'verificacion-pendiente'; correo: string; envioFallido?: boolean }
    // Hay sesión, pero este entorno no tiene API para crear la cuenta de comprador.
    | { estado: 'sin-registro'; nombre: string };

export interface SesionComprador {
    uid: string;
    nombre: string;
    correo: string;
    verificado: boolean;
}

export class ErrorAcceso extends Error {
    code: string;
    constructor(code: string, mensaje: string) {
        super(mensaje);
        this.code = code;
    }
}

const MAX_NOMBRE = 100;
const MIN_CLAVE = 6; // mínimo que exige Firebase

export function nombreMostrar(usuario: { displayName?: string | null; email?: string | null }): string {
    const elegido = usuario.displayName?.trim();
    const base = elegido || usuario.email?.split('@')[0]?.trim() || 'Comprador';
    return base.slice(0, MAX_NOMBRE);
}

export function mensajeErrorAcceso(error: unknown): string {
    const e = error as { code?: string; resultadoIncierto?: boolean };
    switch (e?.code) {
        case 'datos-invalidos': return (error as Error).message;
        case 'auth/email-already-in-use': return 'Ese correo ya tiene una cuenta. Ingresa con tu contraseña o con Google.';
        case 'auth/invalid-email': return 'Revisa el correo: no parece estar bien escrito.';
        case 'auth/invalid-credential':
        case 'auth/user-not-found':
        case 'auth/wrong-password': return 'No se pudo ingresar. Revisa el correo y la contraseña.';
        case 'auth/weak-password': return `La contraseña debe tener al menos ${MIN_CLAVE} caracteres.`;
        case 'auth/too-many-requests': return 'Hubo varios intentos seguidos. Espera un momento antes de probar otra vez.';
        case 'auth/popup-closed-by-user': return 'Cerraste la ventana de Google antes de terminar. Puedes intentarlo de nuevo.';
        case 'auth/popup-blocked': return 'El navegador bloqueó la ventana de Google. Permite las ventanas emergentes e inténtalo otra vez.';
        case 'auth/cancelled-popup-request': return '';
        case 'auth/account-exists-with-different-credential': return 'Ese correo ya se registró con contraseña. Ingresa con tu correo y contraseña.';
        case 'auth/unauthorized-domain': return 'Firebase todavía no autoriza el dominio de esta página para ingresar con Google.';
        case 'auth/operation-not-allowed': return 'Este método de ingreso no está habilitado en el proyecto.';
        case 'auth/network-request-failed':
        case 'unavailable': return 'No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.';
        case 'cuenta-desactivada': return 'Esta cuenta está desactivada. Contacta a la administración.';
        case 'conflicto': return 'No se pudo crear tu cuenta de comprador: esta cuenta requiere revisión de la administración.';
        case 'conexion-interrumpida': return 'No pudimos confirmar el resultado. Revisa tu conexión y vuelve a intentarlo.';
        default:
            if (e?.resultadoIncierto) return 'No pudimos confirmar el resultado. Revisa tu conexión y vuelve a intentarlo.';
            return 'No se pudo completar el proceso. Revisa los datos e inténtalo de nuevo.';
    }
}

export function crearAccesoComprador(
    firebase: Pick<ServiciosCliente, 'auth' | 'db'>,
    llamar?: LlamarRegistro,
    sdk: SdkAcceso = sdkPorDefecto,
) {
    const { auth, db } = firebase;

    function aSesion(usuario: User | null): SesionComprador | null {
        if (!usuario) return null;
        return {
            uid: usuario.uid,
            nombre: nombreMostrar(usuario),
            correo: usuario.email ?? '',
            verificado: usuario.emailVerified,
        };
    }

    // Deja la cuenta lista: exige correo verificado y crea la cuenta de comprador si no existe.
    // Si ya existe un acceso (por ejemplo emprendedora o administradora), no se toca.
    async function completarSesion(usuario: User): Promise<ResultadoAcceso> {
        if (!usuario.emailVerified) {
            await usuario.reload();
            if (!usuario.emailVerified) return { estado: 'verificacion-pendiente', correo: usuario.email ?? '' };
        }
        await usuario.getIdToken(true); // el token debe reflejar el correo ya verificado
        const nombre = nombreMostrar(usuario);
        const acceso = await sdk.getDocFromServer(sdk.doc(db, 'accesos', usuario.uid));
        if (acceso.exists()) {
            if (acceso.data()?.estado !== 'activo') {
                await sdk.signOut(auth);
                throw new ErrorAcceso('cuenta-desactivada', 'Cuenta desactivada.');
            }
            return { estado: 'listo', nombre };
        }
        if (!llamar) return { estado: 'sin-registro', nombre };
        await llamar('registrarComprador', { nombreMostrar: nombre });
        return { estado: 'listo', nombre };
    }

    return {
        sesionActual: () => aSesion(auth.currentUser),
        observar(escuchar: (sesion: SesionComprador | null) => void): () => void {
            return sdk.onAuthStateChanged(auth, usuario => escuchar(aSesion(usuario)));
        },

        async registrarConCorreo(datos: { nombre: string; correo: string; clave: string; repetirClave: string }): Promise<ResultadoAcceso> {
            const nombre = datos.nombre.trim();
            if (!nombre || nombre.length > MAX_NOMBRE) {
                throw new ErrorAcceso('datos-invalidos', `Escribe tu nombre (hasta ${MAX_NOMBRE} caracteres).`);
            }
            if (datos.clave.length < MIN_CLAVE) throw new ErrorAcceso('auth/weak-password', 'Contraseña débil.');
            if (datos.clave !== datos.repetirClave) throw new ErrorAcceso('datos-invalidos', 'Las contraseñas no coinciden.');
            const correo = datos.correo.trim();
            const credencial = await sdk.createUserWithEmailAndPassword(auth, correo, datos.clave);
            await sdk.updateProfile(credencial.user, { displayName: nombre }).catch(() => undefined);
            try {
                await sdk.sendEmailVerification(credencial.user);
            } catch {
                return { estado: 'verificacion-pendiente', correo, envioFallido: true };
            }
            return { estado: 'verificacion-pendiente', correo };
        },

        async iniciarConCorreo(correo: string, clave: string): Promise<ResultadoAcceso> {
            const credencial = await sdk.signInWithEmailAndPassword(auth, correo.trim(), clave);
            return completarSesion(credencial.user);
        },

        async iniciarConGoogle(): Promise<ResultadoAcceso> {
            const proveedor = new sdk.GoogleAuthProvider();
            proveedor.setCustomParameters?.({ prompt: 'select_account' });
            const credencial = await sdk.signInWithPopup(auth, proveedor);
            return completarSesion(credencial.user);
        },

        async reenviarVerificacion(): Promise<void> {
            const usuario = auth.currentUser;
            if (!usuario) throw new ErrorAcceso('no-autenticado', 'Inicia sesión primero.');
            await sdk.sendEmailVerification(usuario);
        },

        async comprobarVerificacion(): Promise<ResultadoAcceso> {
            const usuario = auth.currentUser;
            if (!usuario) throw new ErrorAcceso('no-autenticado', 'Inicia sesión primero.');
            return completarSesion(usuario);
        },

        async solicitarRecuperacion(correo: string): Promise<{ mensaje: string }> {
            try {
                auth.languageCode = 'es';
                await sdk.sendPasswordResetEmail(auth, correo.trim());
            } catch (error) {
                if ((error as { code?: string }).code !== 'auth/user-not-found') throw error;
            }
            return { mensaje: 'Si el correo corresponde a una cuenta, recibirás instrucciones para crear una contraseña nueva.' };
        },

        comprobarCodigoRecuperacion: (codigo: string) => sdk.verifyPasswordResetCode(auth, codigo),

        async confirmarVerificacionCorreo(codigo: string): Promise<string> {
            const info = await sdk.checkActionCode(auth, codigo);
            await sdk.applyActionCode(auth, codigo);
            if (auth.currentUser) {
                await auth.currentUser.reload();
                await auth.currentUser.getIdToken(true);
            }
            return info.data.email ?? '';
        },

        async recuperarDireccionCorreo(codigo: string): Promise<string> {
            const info = await sdk.checkActionCode(auth, codigo);
            await sdk.applyActionCode(auth, codigo);
            await sdk.signOut(auth);
            return info.data.email ?? '';
        },

        async confirmarRecuperacion(codigo: string, clave: string): Promise<void> {
            await sdk.confirmPasswordReset(auth, codigo, clave);
            await sdk.signOut(auth);
        },

        cerrarSesion: () => sdk.signOut(auth),
    };
}
export type AccesoComprador = ReturnType<typeof crearAccesoComprador>;
