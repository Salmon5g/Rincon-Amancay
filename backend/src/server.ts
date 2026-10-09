import { auth, db, bucket } from './config/emulador.ts';
import { montarApi } from './montar-api.ts';
const server=montarApi(auth,db,bucket);
server.listen(8787, '127.0.0.1', () => console.log('API LOCAL: http://127.0.0.1:8787 — solo demo-rincon-amancay'));
server.on('error', error => { console.error(`No se pudo iniciar la API: ${(error as NodeJS.ErrnoException).code}`); process.exitCode = 1; });
for (const signal of ['SIGINT', 'SIGTERM'] as const) process.on(signal, () => {
  server.close(() => { void db.terminate().then(() => process.exit(0)); });
  server.closeAllConnections();
});
