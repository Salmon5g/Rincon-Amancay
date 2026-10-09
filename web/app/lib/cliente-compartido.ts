import { crearCliente } from './cliente-base.ts';
import type { ServiciosCliente } from './cliente-base.ts';

// El cliente compartido habla con el proyecto real y la API desplegada.
// Rechaza el proyecto demo/local por diseño: usar cliente-local.ts para emuladores.
const PROYECTO_COMPARTIDO = 'rincon-amancay';

export { ErrorApi, versionDocumento } from './cliente-base.ts';
export type { Operacion } from './cliente-base.ts';

// El entorno real exige HTTPS. Los emuladores usan cliente-local.ts.
function apiCompartida(apiUrl: string): string {
  let url: URL;
  try { url = new URL(apiUrl); } catch { throw new Error('NUXT_PUBLIC_API_URL debe ser una URL absoluta.'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('Usar el origen HTTPS de la API, sin rutas, credenciales, consulta ni fragmento.');
  }
  return `${url.origin}/api/v1/`;
}

export function crearClienteCompartido(firebase: ServiciosCliente, config: { apiUrl: string }) {
  if (firebase.app.options.projectId !== PROYECTO_COMPARTIDO) {
    throw new Error(`Este cliente solo admite el proyecto compartido ${PROYECTO_COMPARTIDO}.`);
  }
  return crearCliente(firebase, apiCompartida(config.apiUrl));
}
