import { deleteDoc, doc, getDoc, onSnapshot, setDoc } from 'firebase/firestore';
import type { ServiciosCliente } from './cliente-base.ts';

export type TipoSeguimiento = 'tienda' | 'asociacion';

const sdkPorDefecto = { deleteDoc, doc, getDoc, onSnapshot, setDoc };
export type SdkSeguimientos = typeof sdkPorDefecto;

export class ErrorSeguimiento extends Error {
    code: string;
    constructor(code: string, mensaje: string) {
        super(mensaje);
        this.code = code;
    }
}

function idValido(valor: string) {
    if (!/^[a-zA-Z0-9_-]{1,128}$/.test(valor)) throw new ErrorSeguimiento('datos-invalidos', 'Identificador inválido.');
    return valor;
}

/*
 * Seguimiento de tiendas y asociaciones desde la web del comprador.
 * Escribe solo en el documento propio (`usuarios/{uid}/...Seguidas/{id}`), que las
 * reglas de Firestore permiten cuando la tienda o asociación es pública.
 */
export function crearSeguimientosComprador(
    firebase: Pick<ServiciosCliente, 'auth' | 'db'>,
    sdk: SdkSeguimientos = sdkPorDefecto,
) {
    const { auth, db } = firebase;

    function referencia(uid: string, tipo: TipoSeguimiento, entidadId: string) {
        const identificador = idValido(entidadId);
        return tipo === 'tienda'
            ? sdk.doc(db, 'usuarios', uid, 'tiendasSeguidas', identificador)
            : sdk.doc(db, 'usuarios', uid, 'asociacionesSeguidas', identificador);
    }

    function campo(tipo: TipoSeguimiento) {
        return tipo === 'tienda' ? 'tiendaId' : 'asociacionId';
    }

    return {
        /** Observa si la entidad sigue el documento del usuario actual; sin sesión, siempre false. */
        observar(tipo: TipoSeguimiento, entidadId: string, escuchar: (siguiendo: boolean) => void): () => void {
            const usuario = auth.currentUser;
            if (!usuario) {
                escuchar(false);
                return () => undefined;
            }
            return sdk.onSnapshot(
                referencia(usuario.uid, tipo, entidadId),
                documento => escuchar(documento.exists()),
                () => escuchar(false),
            );
        },

        /** Crea o elimina el seguimiento y devuelve el estado final. */
        async alternar(tipo: TipoSeguimiento, entidadId: string): Promise<boolean> {
            const usuario = auth.currentUser;
            if (!usuario) throw new ErrorSeguimiento('no-autenticado', 'Inicia sesión para seguir.');
            const documento = referencia(usuario.uid, tipo, entidadId);
            const actual = await sdk.getDoc(documento);
            if (actual.exists()) {
                await sdk.deleteDoc(documento);
                return false;
            }
            await sdk.setDoc(documento, { [campo(tipo)]: entidadId });
            return true;
        },
    };
}

export type SeguimientosComprador = ReturnType<typeof crearSeguimientosComprador>;
