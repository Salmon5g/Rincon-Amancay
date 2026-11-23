import { applicationDefault, deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { randomUUID } from 'node:crypto';
import { exigirEntornoCompartido } from '../src/config/compartido.ts';
import { crearActualizacionParticipantesPublicosFeria } from '../src/modules/ferias-publicas.ts';

const PROYECTO = exigirEntornoCompartido(process.env);
const [modo, proyectoConfirmado, ...extras] = process.argv.slice(2);
if (extras.length || !['--dry-run', '--aplicar'].includes(modo ?? '') || proyectoConfirmado !== PROYECTO) {
  throw new Error('Uso: npm run ferias:actualizar-participantes-publicos -- --dry-run|--aplicar rincon-amancay');
}
const aplicar = modo === '--aplicar';
const app = initializeApp({ projectId: PROYECTO, credential: applicationDefault() }, `participantes-publicos-ferias-${randomUUID()}`);
const db = getFirestore(app);
const actualizar = crearActualizacionParticipantesPublicosFeria(db);

const resumen = { publicadas: 0, fallos: 0 };
try {
  const ferias = await db.collection('ferias').where('estado', '==', 'publicada').limit(100).get();
  for (const feria of ferias.docs) {
    resumen.publicadas += 1;
    if (!aplicar) continue;
    try {
      await actualizar(feria.id);
    } catch (error) {
      resumen.fallos += 1;
      console.error(`Falló ${feria.id}: ${error instanceof Error ? error.message : 'error desconocido'}`);
    }
  }
  console.log(
    `${aplicar ? 'ESCRITURA' : 'SIMULACIÓN'} en ${PROYECTO}: ${resumen.publicadas} ferias publicadas actualizadas; fallos: ${resumen.fallos}.`,
  );
  if (resumen.fallos) process.exitCode = 1;
} finally {
  await deleteApp(app);
}
