import { applicationDefault, deleteApp, initializeApp } from 'firebase-admin/app';
import { FieldPath, getFirestore, type QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { randomUUID } from 'node:crypto';
import { exigirEntornoCompartido } from '../src/config/compartido.ts';
import { construirProyeccionAsociacionPublica } from '../src/modules/proyecciones-asociaciones-publicas.ts';

const PROYECTO = exigirEntornoCompartido(process.env);
const [modo, proyectoConfirmado, ...extras] = process.argv.slice(2);
if (extras.length || !['--dry-run', '--aplicar'].includes(modo ?? '') || proyectoConfirmado !== PROYECTO) {
  throw new Error('Uso: npm run asociaciones:sincronizar-publicas -- --dry-run|--aplicar <ID_PROYECTO_CONFIRMADO>');
}
const aplicar = modo === '--aplicar';
const app = initializeApp({ projectId: PROYECTO, credential: applicationDefault() }, `sincronizar-asociaciones-${randomUUID()}`);
const db = getFirestore(app);

async function recorrer(procesar: (doc: QueryDocumentSnapshot) => Promise<void>) {
  let cursor: QueryDocumentSnapshot | undefined;
  while (true) {
    let query = db.collection('asociaciones').orderBy(FieldPath.documentId()).limit(50);
    if (cursor) query = query.startAfter(cursor);
    const pagina = await query.get();
    for (const doc of pagina.docs) await procesar(doc);
    if (pagina.size < 50) return;
    cursor = pagina.docs.at(-1);
  }
}

const resumen = { publicadas: 0, retiradas: 0, fallos: 0 };
try {
  await recorrer(async doc => {
    try {
      const projection = await construirProyeccionAsociacionPublica(db, doc.id);
      const target = db.doc(`asociacionesPublicas/${doc.id}`);
      if (projection) {
        resumen.publicadas++;
        if (aplicar) await target.set(projection);
      } else {
        resumen.retiradas++;
        if (aplicar) await target.delete().catch(() => undefined);
      }
    } catch (error) {
      resumen.fallos++;
      console.error(`Falló ${doc.id}: ${error instanceof Error ? error.message : 'error desconocido'}`);
    }
  });
  console.log(`${aplicar ? 'ESCRITURA' : 'SIMULACIÓN'} en ${PROYECTO}: ${resumen.publicadas} asociaciones públicas y ${resumen.retiradas} retiradas; fallos: ${resumen.fallos}.`);
  if (resumen.fallos) process.exitCode = 1;
} finally {
  await deleteApp(app);
}
