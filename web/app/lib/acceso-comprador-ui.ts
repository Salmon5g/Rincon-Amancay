import { ref } from 'vue';

export type VistaAccesoComprador = 'ingreso' | 'registro';

/*
 * Solicitud compartida para abrir el diálogo del comprador desde cualquier
 * botón (por ejemplo, al intentar seguir una tienda sin sesión). El componente
 * AccesoComprador escucha este contador y muestra el ingreso.
 */
export const solicitudAccesoComprador = ref(0);
export const vistaSolicitudAccesoComprador = ref<VistaAccesoComprador>('ingreso');

export function pedirAccesoComprador(vista: VistaAccesoComprador = 'ingreso') {
    vistaSolicitudAccesoComprador.value = vista;
    solicitudAccesoComprador.value += 1;
}
