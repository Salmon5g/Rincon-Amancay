import { validarConfigHttp } from './http.ts';
// Guardas comunes a toda herramienta que use el proyecto compartido con ADC.
export function exigirEntornoCompartido(env:NodeJS.ProcessEnv):string {
  if(env.AMANCAY_ENTORNO!=='compartido')throw new Error('Configurar explícitamente AMANCAY_ENTORNO=compartido.');
  for(const key of ['FIRESTORE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST','FIREBASE_STORAGE_EMULATOR_HOST','STORAGE_EMULATOR_HOST','GOOGLE_APPLICATION_CREDENTIALS']) {
    if(env[key]!==undefined)throw new Error(`Variable incompatible con identidad de servicio: ${key}.`);
  }
  const proyecto=env.AMANCAY_PROJECT_ID;
  const proyectoEsperado=env.AMANCAY_PROJECT_ID_ESPERADO;
  if(!proyecto || !proyectoEsperado || proyecto!==proyectoEsperado) {
    throw new Error('El proyecto Firebase debe coincidir con AMANCAY_PROJECT_ID_ESPERADO.');
  }
  for(const key of ['GCLOUD_PROJECT','GOOGLE_CLOUD_PROJECT'])if(env[key] && env[key]!==proyecto)throw new Error('Proyectos de entorno inconsistentes.');
  return proyecto;
}
export type CredencialServicio = { projectId: string; clientEmail: string; privateKey: string };

const HOSTS_PERMITIDOS=['127.0.0.1','0.0.0.0','::'];

export function leerHostCompartido(env:NodeJS.ProcessEnv):string {
  const host=env.AMANCAY_HOST?.trim() || (env.K_SERVICE?'0.0.0.0':'127.0.0.1');
  if(!HOSTS_PERMITIDOS.includes(host))throw new Error('AMANCAY_HOST inválido: usar 127.0.0.1, 0.0.0.0 o ::.');
  return host;
}

export function leerCredencialServicio(env:NodeJS.ProcessEnv, proyecto:string):CredencialServicio|null {
  const valor=env.AMANCAY_CREDENCIAL_SERVICIO?.trim();
  if(!valor)return null;
  let datos:unknown;
  try {
    datos=JSON.parse(valor.startsWith('{')?valor:Buffer.from(valor,'base64').toString('utf8'));
  } catch {
    throw new Error('AMANCAY_CREDENCIAL_SERVICIO no es un JSON válido de cuenta de servicio.');
  }
  const cuenta=datos as Record<string,unknown>;
  if(!cuenta || typeof cuenta!=='object' || cuenta.type!=='service_account'
    || typeof cuenta.client_email!=='string' || typeof cuenta.private_key!=='string' || !cuenta.private_key.includes('PRIVATE KEY')) {
    throw new Error('AMANCAY_CREDENCIAL_SERVICIO no corresponde a una cuenta de servicio.');
  }
  if(cuenta.project_id!==proyecto)throw new Error('La cuenta de servicio pertenece a otro proyecto.');
  return {projectId:proyecto,clientEmail:cuenta.client_email,privateKey:cuenta.private_key};
}

export function leerConfigCompartida(env:NodeJS.ProcessEnv) {
  const proyecto=exigirEntornoCompartido(env);
  const port=Number(env.PORT??8080);
  if(!Number.isInteger(port) || port<1 || port>65535)throw new Error('PORT inválido.');
  const origenes=(env.AMANCAY_ORIGENES_WEB??'').split(',').map(v=>v.trim()).filter(Boolean);
  const http=validarConfigHttp({entorno:'compartido',proyecto,origenes});
  return {proyecto,port,http,host:leerHostCompartido(env),credencial:leerCredencialServicio(env,proyecto)};
}
