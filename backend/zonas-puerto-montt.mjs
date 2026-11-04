// Reorganización del catálogo: sectores = zonas geográficas de Puerto Montt y alrededores.
// Crea las zonas y desactiva los sectores industriales redundantes y "Productos deportivos".
// Idempotente (operacionId fijos + recibos de la API).
const API_KEY = 'AIzaSyDyc1QJyFggbbFLUVvRwudGesjqzSRzCAE';
const API = 'http://127.0.0.1:8080/api/v1';
const CORREO = 'rinconamancay67@gmail.com';
const CLAVE = '123456789';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const ses = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: CORREO, password: CLAVE, returnSecureToken: true })
});
const sesion = await ses.json();
if (!sesion.idToken) { console.log('LOGIN_FALLO ' + JSON.stringify(sesion).slice(0, 300)); process.exit(1); }
console.log('LOGIN_OK uid=' + sesion.localId);

const zonas = [
  'Puerto Montt - Centro', 'Puerto Montt - Alerce', 'Puerto Montt - Pelluco', 'Puerto Montt - Angelmó',
  'Puerto Montt - Mirasol', 'Puerto Montt - Maillen', 'Puerto Montt - Pargua',
  'Puerto Varas', 'Llanquihue', 'Frutillar', 'Calbuco'
];
const desactivarSectores = ['deportivo', 'alimentos_y_reposteria', 'artesania_y_manualidades', 'vestuario_y_accesorios', 'cosmetica_natural_y_cuidado_personal', 'plantas_huerta_y_productos_agricolas'];
const desactivarTipos = ['productos_deportivos'];

const slug = n => n.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
const app = initializeApp({ projectId: 'rincon-amancay' });
const db = getFirestore(app);

let ok = 0, ya = 0, fallos = 0;
async function crear(coleccion, nombre) {
  const r = await fetch(`${API}/crearElementoCatalogo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${sesion.idToken}` },
    body: JSON.stringify({ operacionId: `zonas-2026-${coleccion}-${slug(nombre)}`, coleccion, nombre })
  });
  const c = await r.json().catch(() => ({}));
  if (r.ok) { ok++; console.log(`OK ${coleccion} "${nombre}"`); }
  else if (c?.codigo === 'id-reutilizado' || c?.codigo === 'conflicto') { ya++; console.log(`YA ${coleccion} "${nombre}" (${c.codigo})`); }
  else { fallos++; console.log(`FALLO ${coleccion} "${nombre}" HTTP ${r.status} ${JSON.stringify(c).slice(0, 200)}`); }
}
async function desactivar(coleccion, documentoId) {
  const doc = await db.doc(`${coleccion}/${documentoId}`).get();
  if (!doc.exists) { ya++; console.log(`YA ${coleccion}/${documentoId} no existe`); return; }
  const a = doc.data().actualizadoEn;
  const versionEsperada = `${a.seconds}:${a.nanoseconds}`;
  const r = await fetch(`${API}/cambiarEstadoCatalogo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${sesion.idToken}` },
    body: JSON.stringify({ operacionId: `zonas-2026-desactivar-${coleccion}-${documentoId}`, coleccion, documentoId, versionEsperada, activo: false })
  });
  const c = await r.json().catch(() => ({}));
  if (r.ok) { ok++; console.log(`OK desactivado ${coleccion}/${documentoId}`); }
  else if (c?.codigo === 'conflicto' || c?.codigo === 'id-reutilizado') { ya++; console.log(`YA ${coleccion}/${documentoId} (${c.mensaje ?? c.codigo})`); }
  else { fallos++; console.log(`FALLO ${coleccion}/${documentoId} HTTP ${r.status} ${JSON.stringify(c).slice(0, 200)}`); }
}

for (const z of zonas) await crear('sectores', z);
for (const id of desactivarSectores) await desactivar('sectores', id);
for (const id of desactivarTipos) await desactivar('tiposEmprendimiento', id);
console.log(`RESUMEN ok=${ok} yaEstaban=${ya} fallos=${fallos}`);
