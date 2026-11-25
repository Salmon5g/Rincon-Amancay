import { crearCliente } from './cliente-base.ts';
import type { ServiciosCliente } from './cliente-base.ts';

// El cliente compartido exige un ID esperado explícito y rechaza proyectos demo.
// Para emuladores se usa cliente-local.ts.

export { ErrorApi, versionDocumento } from './cliente-base.ts';
export type { Operacion } from './cliente-base.ts';

// El entorno real exige HTTPS. Los emuladores usan cliente-local.ts.
function apiCompartida(apiUrl: string, permitirHttpLocal = false): string {
  let url: URL;
  try { url = new URL(apiUrl); } catch { throw new Error('NUXT_PUBLIC_API_URL debe ser una URL absoluta.'); }
  const httpLocal = permitirHttpLocal && url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname);
  if ((url.protocol !== 'https:' && !httpLocal) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('Usar el origen HTTPS de la API, o localhost HTTP solo durante desarrollo, sin rutas ni credenciales.');
  }
  return `${url.origin}/api/v1/`;
}

export function crearClienteCompartido(firebase: ServiciosCliente, config: { apiUrl: string; projectIdEsperado: string; permitirHttpLocal?: boolean }) {
  const projectId = firebase.app.options.projectId;
  const projectIdEsperado = config.projectIdEsperado.trim();
  if (!projectIdEsperado || projectIdEsperado.startsWith('demo-') || projectId !== projectIdEsperado) {
    throw new Error('El cliente compartido requiere un proyecto que coincida con el ID esperado.');
  }
  return crearCliente(firebase, apiCompartida(config.apiUrl, config.permitirHttpLocal));
}
