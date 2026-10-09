// Proceso separado de prueba: alcanza la fase Auth y queda esperando hasta que
// el padre lo termine. Nunca realiza peticiones Auth reales.
import type { Auth } from 'firebase-admin/auth';
import { db } from '../../src/config/emulador.ts';
import { crearDesactivacion } from '../../src/modules/desactivacion.ts';
import { crearCicloCuentas } from '../../src/modules/ciclo-cuentas.ts';

const [uid, admin, versionEsperada, accion] = process.argv.slice(2);
const detener = async () => {
  console.log('FASE_AUTH');
  await new Promise<void>(() => { setInterval(() => {}, 1000); });
};
const auth = { updateUser: detener, revokeRefreshTokens: async () => {} } as unknown as Auth;
const solicitud = { uidDestino: uid, operacionId: 'interrumpida', versionEsperada, motivo: 'Prueba de interrupción' };
if (accion === 'desactivarEmprendedora') await crearDesactivacion(db, auth)({ uid: admin }, solicitud);
else await crearCicloCuentas(db, auth)('reactivarEmprendedora', { uid: admin }, solicitud);
