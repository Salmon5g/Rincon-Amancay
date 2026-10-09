import { validarConfigHttp } from './http.ts';
// Guardas comunes a toda herramienta que use el proyecto compartido con ADC.
export function exigirEntornoCompartido(env:NodeJS.ProcessEnv):string {
  if(env.AMANCAY_ENTORNO!=='compartido')throw new Error('Configurar explícitamente AMANCAY_ENTORNO=compartido.');
  for(const key of ['FIRESTORE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST','FIREBASE_STORAGE_EMULATOR_HOST','STORAGE_EMULATOR_HOST','GOOGLE_APPLICATION_CREDENTIALS']) {
    if(env[key]!==undefined)throw new Error(`Variable incompatible con identidad de servicio: ${key}.`);
  }
  const proyecto=env.AMANCAY_PROJECT_ID;
  if(proyecto!=='rincon-amancay')throw new Error('Proyecto compartido no autorizado por esta configuración.');
  for(const key of ['GCLOUD_PROJECT','GOOGLE_CLOUD_PROJECT'])if(env[key] && env[key]!==proyecto)throw new Error('Proyectos de entorno inconsistentes.');
  return proyecto;
}
export function leerConfigCompartida(env:NodeJS.ProcessEnv) {
  const proyecto=exigirEntornoCompartido(env);
  const bucket=env.AMANCAY_STORAGE_BUCKET;
  if(bucket!==`${proyecto}.firebasestorage.app`)throw new Error('Confirmar el bucket del proyecto antes de arrancar.');
  const port=Number(env.PORT??8080);
  if(!Number.isInteger(port) || port<1 || port>65535)throw new Error('PORT inválido.');
  const origenes=(env.AMANCAY_ORIGENES_WEB??'').split(',').map(v=>v.trim()).filter(Boolean);
  const http=validarConfigHttp({entorno:'compartido',proyecto,origenes});
  return {proyecto,bucket,port,http};
}
