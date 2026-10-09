import { crearCliente } from './cliente-base.ts';
import type { ServiciosCliente } from './cliente-base.ts';

// El cliente compartido habla con el proyecto real y la API desplegada.
// Rechaza el proyecto demo/local por diseño: usar cliente-local.ts para emuladores.
const PROYECTO_COMPARTIDO = 'rincon-amancay';

export { ErrorApi, versionDocumento } from './cliente-base.ts';
export type { Operacion } from './cliente-base.ts';

// Acepta una URL absoluta HTTPS. Solo admite http para loopback (pruebas con túnel local).
function apiCompartida(apiUrl: string): string {
  let url: URL;
  try { url = new URL(apiUrl); } catch { throw new Error('NUXT_PUBLIC_API_URL debe ser una URL absoluta.'); }
  const loopback = url.hostname === 'localhost' || url.hostname === '127.0.0.1' || url.hostname === '[::1]';
  if (url.protocol !== 'https:' && !(loopback && url.protocol === 'http:')) {
    throw new Error('La API compartida debe usar HTTPS, salvo en loopback.');
  }
  return `${url.toString().replace(/\/+$/, '')}/api/v1/`;
}

export function crearClienteCompartido(firebase: ServiciosCliente, config: { apiUrl: string }) {
  if (firebase.app.options.projectId !== PROYECTO_COMPARTIDO) {
    throw new Error(`Este cliente solo admite el proyecto compartido ${PROYECTO_COMPARTIDO}.`);
  }
  return crearCliente(firebase, apiCompartida(config.apiUrl));
}
