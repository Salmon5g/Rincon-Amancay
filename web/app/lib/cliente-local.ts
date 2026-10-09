import { crearCliente } from './cliente-base.ts';
import type { ServiciosCliente } from './cliente-base.ts';

// El cliente local solo habla con los emuladores del proyecto demo.
// La URL apunta al servidor Node local (por defecto 8787).
const API_LOCAL = 'http://127.0.0.1:8787/api/v1/';

export { ErrorApi, versionDocumento } from './cliente-base.ts';
export type { Operacion } from './cliente-base.ts';

export function crearClienteLocal(firebase: ServiciosCliente) {
  if (firebase.app.options.projectId !== 'demo-rincon-amancay') throw new Error('Este cliente solo admite el proyecto demo.');
  return crearCliente(firebase, API_LOCAL);
}
