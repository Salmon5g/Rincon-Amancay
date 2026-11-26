import { initializeApp, applicationDefault, cert } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getMessaging } from 'firebase-admin/messaging';
import { leerConfigCompartida } from './config/compartido.ts';
import { montarApi } from './montar-api.ts';
import { ClienteStorageSupabase, crearServicioImagenesSupabase, validarConfigSupabase } from './modules/imagenes-supabase.ts';

// En contenedores (Cloud Run, o AMANCAY_HOST=0.0.0.0 en Coolify) se escucha en todas las interfaces;
// localmente, la API compartida permanece limitada a loopback. Sin ADC, se usa la cuenta de servicio
// entregada en AMANCAY_CREDENCIAL_SERVICIO.
const config=leerConfigCompartida(process.env);
const app=initializeApp({projectId:config.proyecto,credential:config.credencial?cert(config.credencial):applicationDefault()},'backend-compartido');
const db=getFirestore(app);
const imagenes=crearServicioImagenesSupabase(db,new ClienteStorageSupabase(validarConfigSupabase(process.env)));
const server=montarApi(getAuth(app),db,undefined,config.http,imagenes,mensaje=>getMessaging(app).send(mensaje));
const host=config.host;
server.listen(config.port,host,()=>console.log(`API compartida: ${config.proyecto}, ${host}:${config.port}`));
server.on('error',error=>{console.error(`No se pudo iniciar la API: ${(error as NodeJS.ErrnoException).code}`);process.exitCode=1;});
let cerrando=false;
for(const signal of ['SIGINT','SIGTERM'] as const)process.on(signal,()=>{
  if(cerrando)return;cerrando=true;
  server.close(()=>{void db.terminate().then(()=>process.exit(0));});
  server.closeIdleConnections();
  // La reserva durable permanece si el cierre local no termina a tiempo.
  setTimeout(()=>process.exit(1),8000).unref();
});
