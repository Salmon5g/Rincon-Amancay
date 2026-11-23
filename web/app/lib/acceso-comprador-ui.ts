import { ref } from 'vue';
import type { TipoSeguimiento } from './seguimientos-comprador.ts';

export type VistaAccesoComprador = 'ingreso' | 'registro';
export type IntencionSeguimiento = { tipo: TipoSeguimiento; entidadId: string };

const CLAVE_INTENCION = 'amancay:intencion-seguimiento';
const ID_VALIDO = /^[a-zA-Z0-9_-]{1,128}$/;

/*
 * Solicitud compartida para abrir el diálogo del comprador desde cualquier
 * botón (por ejemplo, al intentar seguir una tienda sin sesión). El componente
 * AccesoComprador escucha este contador y muestra el ingreso.
 */
export const solicitudAccesoComprador = ref(0);
export const vistaSolicitudAccesoComprador = ref<VistaAccesoComprador>('ingreso');

function leerIntencion(): IntencionSeguimiento | null {
    if (typeof sessionStorage === 'undefined') return null;
    try {
        const valor = JSON.parse(sessionStorage.getItem(CLAVE_INTENCION) ?? 'null') as Partial<IntencionSeguimiento> | null;
        if (!valor || (valor.tipo !== 'tienda' && valor.tipo !== 'asociacion')) return null;
        if (typeof valor.entidadId !== 'string' || !ID_VALIDO.test(valor.entidadId)) return null;
        return { tipo: valor.tipo, entidadId: valor.entidadId };
    } catch {
        return null;
    }
}

/* Guarda la acción que el visitante quiso hacer para completarla después del ingreso. */
export const intencionSeguimiento = ref<IntencionSeguimiento | null>(leerIntencion());

function escribirIntencion(valor: IntencionSeguimiento | null) {
    intencionSeguimiento.value = valor;
    if (typeof sessionStorage === 'undefined') return;
    try {
        if (valor) sessionStorage.setItem(CLAVE_INTENCION, JSON.stringify(valor));
        else sessionStorage.removeItem(CLAVE_INTENCION);
    } catch {
        /* almacenamiento bloqueado: la intención queda solo en memoria */
    }
}

export function pedirAccesoComprador(vista: VistaAccesoComprador = 'ingreso') {
    vistaSolicitudAccesoComprador.value = vista;
    solicitudAccesoComprador.value += 1;
}

/* Sin sesión: recuerda qué tienda o asociación se quiso seguir y abre el ingreso. */
export function pedirAccesoParaSeguir(tipo: TipoSeguimiento, entidadId: string) {
    if (ID_VALIDO.test(entidadId)) escribirIntencion({ tipo, entidadId });
    pedirAccesoComprador('ingreso');
}

/*
 * Entrega la intención pendiente una sola vez. Con filtro, solo la devuelve si
 * coincide con esa tienda o asociación; así varios botones no la duplican.
 */
export function tomarIntencionSeguimiento(filtro?: IntencionSeguimiento): IntencionSeguimiento | null {
    const actual = intencionSeguimiento.value;
    if (!actual) return null;
    if (filtro && (actual.tipo !== filtro.tipo || actual.entidadId !== filtro.entidadId)) return null;
    escribirIntencion(null);
    return actual;
}
