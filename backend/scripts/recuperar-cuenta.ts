import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { applicationDefault, deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { exigirEntornoCompartido } from '../src/config/compartido.ts';
import { inspeccionarReserva, liberarReserva } from '../src/modules/recuperacion-cuentas.ts';

export function argumentosRecuperacion(args: string[]) {
  const valores = new Map<string, string>();
  let confirmar = false;
  for (let i = 0; i < args.length; i++) {
    const key = args[i];
    if (key === '--confirmar-servidores-detenidos' && !confirmar) { confirmar = true; continue; }
    if (!['--entorno', '--uid', '--ejecutor', '--operador', '--motivo'].includes(key) || valores.has(key) || !args[i + 1] || args[i + 1].startsWith('--')) throw new Error('Argumentos inválidos. Consultar docs/recuperacion-cuentas.md.');
    valores.set(key, args[++i]);
  }
  const entorno = valores.get('--entorno'), uid = valores.get('--uid');
  if (!['local', 'compartido'].includes(entorno ?? '') || !uid || !/^[a-zA-Z0-9_-]{1,128}$/.test(uid)) throw new Error('Especificar --entorno local|compartido y --uid válido.');
  const ejecutor = valores.get('--ejecutor');
  if (ejecutor && (!confirmar || !valores.get('--operador')?.trim() || !valores.get('--motivo')?.trim())) throw new Error('Liberar exige --ejecutor, --operador, --motivo y --confirmar-servidores-detenidos.');
  if (!ejecutor && (confirmar || valores.has('--operador') || valores.has('--motivo'))) throw new Error('La inspección solo usa --entorno y --uid.');
  return { entorno, uid, ejecutor, operador: valores.get('--operador') ?? '', motivo: valores.get('--motivo') ?? '', confirmar };
}

const directo = process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (directo) {
  (async () => {
    const args = argumentosRecuperacion(process.argv.slice(2));
    const app = args.entorno === 'local'
      ? (await import('../src/config/emulador.ts')).auth.app
      : initializeApp({ projectId: exigirEntornoCompartido(process.env), credential: applicationDefault() }, `recuperacion-${randomUUID()}`);
    const db = getFirestore(app);
    try {
      const resultado = args.ejecutor
        ? await liberarReserva(db, { uid: args.uid, ejecutorId: args.ejecutor, operador: args.operador, motivo: args.motivo, servidoresDetenidos: args.confirmar })
        : await inspeccionarReserva(db, args.uid);
      console.log(JSON.stringify(resultado, null, 2));
      console.log('No se modificó Auth ni el catálogo. Si hay operación pendiente, reintentar su solicitud original con la cuenta administradora original.');
    } finally { await db.terminate(); await deleteApp(app); }
  })().catch((error: unknown) => {
    console.error(error instanceof Error ? error.message : 'No se pudo revisar la reserva.');
    process.exitCode = 1;
  });
}
