import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import { leerConfigCompartida } from './config/compartido.ts';
import { montarApi } from './montar-api.ts';

// Este archivo es una entrada explícita; npm run dev sigue usando emuladores.
const config=leerConfigCompartida(process.env);
const app=initializeApp({projectId:config.proyecto,storageBucket:config.bucket,credential:applicationDefault()},'backend-compartido');
const db=getFirestore(app);
const server=montarApi(getAuth(app),db,getStorage(app).bucket(config.bucket),config.http);
server.listen(config.port,'0.0.0.0',()=>console.log(`API compartida: ${config.proyecto}, puerto ${config.port}`));
server.on('error',error=>{console.error(`No se pudo iniciar la API: ${(error as NodeJS.ErrnoException).code}`);process.exitCode=1;});
let cerrando=false;
for(const signal of ['SIGINT','SIGTERM'] as const)process.on(signal,()=>{
  if(cerrando)return;cerrando=true;
  server.close(()=>{void db.terminate().then(()=>process.exit(0));});
  server.closeIdleConnections();
  // Cloud Run puede terminar el proceso antes: la reserva durable permanece.
  setTimeout(()=>process.exit(1),8000).unref();
});
