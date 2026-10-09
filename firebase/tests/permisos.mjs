import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, updateDoc, setDoc, deleteDoc, serverTimestamp, setLogLevel, writeBatch } from 'firebase/firestore';
import { seed } from '../scripts/seed.mjs';
import { project } from '../scripts/local.mjs';

setLogLevel('silent');
const env = await initializeTestEnvironment({
  projectId: project,
  firestore: { host: '127.0.0.1', port: 8080, rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
});
const visitor = env.unauthenticatedContext().firestore();
const owner = env.authenticatedContext('u_emprendedora_demo').firestore();
const admin = env.authenticatedContext('u_admin_demo').firestore();
const other = env.authenticatedContext('u_sin_accesos').firestore();
const store = 'tiendasPrivadas/t_demo_01';
const pub = 'tiendasPublicas/t_demo_01';
const product = '/productos/p_gorro_01';
const variant = product + '/variantes/v_azul_m';
const tecnicos = [`ejecucionesCuentas/${randomUUID()}`, `recuperacionesCuentas/${randomUUID()}`];
let count = 0;
async function check(name, action) { await action(); count++; console.log(`OK ${count}: ${name}`); }
const read = (db, path) => getDoc(doc(db, path));
const change = (db, path, values) => updateDoc(doc(db, path), values);
async function serverChange(path, values) {
  await env.withSecurityRulesDisabled(ctx => change(ctx.firestore(), path, values));
}
async function serverBatch(changes) {
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    const batch = writeBatch(db);
    for (const [path, values] of changes) batch.update(doc(db, path), values);
    await batch.commit();
  });
}
try {
  await seed();
  await check('visitante lee tienda publicada', () => assertSucceeds(read(visitor, pub)));
  await check('visitante lista tiendas publicadas habilitadas', () => assertSucceeds(getDocs(query(collection(visitor, 'tiendasPublicas'), where('estadoPublicacion', '==', 'publicado'), where('habilitada', '==', true)))));
  await check('visitante lista productos publicados', () => assertSucceeds(getDocs(query(collection(visitor, pub + '/productos'), where('estadoPublicacion', '==', 'publicado')))));
  await check('visitante lee variante pública', () => assertSucceeds(read(visitor, pub + variant)));
  await check('visitante lista categorías activas', () => assertSucceeds(getDocs(query(collection(visitor, 'categorias'), where('activo', '==', true)))));
  await check('consulta de tiendas sin filtros es rechazada', () => assertFails(getDocs(collection(visitor, 'tiendasPublicas'))));
  await check('visitante lista variantes activas', () => assertSucceeds(getDocs(query(collection(visitor, pub + product + '/variantes'), where('activa', '==', true)))));
  const forged = env.authenticatedContext('u_sin_accesos', { administrador: true, roles: ['administrador'] }).firestore();
  await check('claims no sustituyen accesos autorizados', () => assertFails(read(forged, 'accesos/u_admin_demo')));
  await check('visitante no lee perfil privado', () => assertFails(read(visitor, 'usuarios/u_emprendedora_demo')));
  await check('visitante no lee accesos', () => assertFails(read(visitor, 'accesos/u_admin_demo')));
  await check('visitante no lee tienda privada', () => assertFails(read(visitor, store)));
  await check('propietaria lee tienda privada', () => assertSucceeds(read(owner, store)));
  await check('propietaria lee variantes privadas', () => assertSucceeds(read(owner, store + variant)));
  await check('cuenta sin asignación no lee tienda ajena', () => assertFails(read(other, store)));
  await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), 'accesos/u_otra_propietaria_test'), { estado: 'activo', roles: ['emprendedora'], tiendaId: 't_otra_test' }));
  const otraPropietaria = env.authenticatedContext('u_otra_propietaria_test').firestore();
  await check('otra emprendedora activa no lee tienda ajena', () => assertFails(read(otraPropietaria, store)));
  await check('administrador lee metadatos de emprendedora', () => assertSucceeds(read(admin, 'emprendedoras/u_emprendedora_demo')));
  await check('administrador no lee tienda privada', () => assertFails(read(admin, store)));
  await check('administrador no lee ventas', () => assertFails(read(admin, store + '/ventas/ejemplo')));
  for (const ruta of tecnicos) {
    await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), ruta), { uid: 'u_emprendedora_demo', operador: 'prueba' }));
    await check(`${ruta.split('/')[0]} solo accesible por herramienta técnica`, async () => {
      for (const cliente of [visitor, owner, admin]) {
        await assertFails(read(cliente, ruta));
        await assertFails(getDocs(collection(cliente, ruta.split('/')[0])));
        await assertFails(setDoc(doc(cliente, ruta), { uid: 'otro' }));
        await assertFails(deleteDoc(doc(cliente, ruta)));
      }
    });
  }
  await check('propietaria actualiza su nombre con fecha de servidor', () => assertSucceeds(change(owner, 'usuarios/u_emprendedora_demo', { nombreMostrar: 'Nombre de prueba', actualizadoEn: serverTimestamp() })));
  await check('no agrega rol en perfil', () => assertFails(change(owner, 'usuarios/u_emprendedora_demo', { roles: ['administrador'], actualizadoEn: serverTimestamp() })));
  await check('no modifica sus permisos', () => assertFails(change(owner, 'accesos/u_emprendedora_demo', { roles: ['administrador'] })));
  await check('administrador tampoco escribe roles directamente', () => assertFails(change(admin, 'accesos/u_emprendedora_demo', { roles: ['administrador'] })));
  await check('propietaria modifica texto privado', () => assertSucceeds(change(owner, store, { nombre: 'Nombre privado actualizado', actualizadoEn: serverTimestamp() })));
  await check('otra cuenta no modifica tienda', () => assertFails(change(other, store, { nombre: 'Otro', actualizadoEn: serverTimestamp() })));
  await check('no cambia propiedad', () => assertFails(change(owner, store, { propietarioUid: 'u_sin_accesos', actualizadoEn: serverTimestamp() })));
  await check('no usa fecha de cliente', () => assertFails(change(owner, store, { nombre: 'Nombre', actualizadoEn: new Date('2000-01-01') })));
  await check('no publica desde el cliente', () => assertFails(setDoc(doc(owner, pub), { nombre: 'Copia no autorizada' })));
  await check('no modifica stock directamente', () => assertFails(change(owner, store + variant, { stock: 999 })));
  await check('no borra tienda directamente', () => assertFails(deleteDoc(doc(owner, store))));
  await serverBatch([['accesos/u_emprendedora_demo', { estado: 'desactivado' }], [pub, { habilitada: false }]]);
  await check('cuenta desactivada no lee tienda privada', () => assertFails(read(owner, store)));
  await check('cuenta desactivada conserva lectura de su estado', () => assertSucceeds(read(owner, 'accesos/u_emprendedora_demo')));
  await check('desactivar propietaria bloquea ficha pública antigua', () => assertFails(read(visitor, pub)));
  await check('desactivar propietaria bloquea variante por enlace directo', () => assertFails(read(visitor, pub + variant)));
  await serverBatch([['accesos/u_emprendedora_demo', { estado: 'activo' }], [pub, { habilitada: true }]]);
  await serverBatch([[store, { estadoPublicacion: 'archivado' }], [pub, { estadoPublicacion: 'archivado' }]]);
  await check('archivar tienda bloquea producto público', () => assertFails(read(visitor, pub + product)));
  await serverBatch([[store, { estadoPublicacion: 'publicado' }], [pub, { estadoPublicacion: 'publicado' }]]);
  await serverBatch([[store + product, { estadoPublicacion: 'borrador' }], [pub + product, { estadoPublicacion: 'borrador' }]]);
  await check('producto borrador bloquea vista pública antigua', () => assertFails(read(visitor, pub + product)));
  await check('producto borrador bloquea variantes públicas', () => assertFails(read(visitor, pub + variant)));
  await serverBatch([[store + product, { estadoPublicacion: 'publicado' }], [pub + product, { estadoPublicacion: 'publicado' }]]);
  await serverBatch([[store + variant, { activa: false }], [pub + variant, { activa: false }]]);
  await check('variante inactiva no se lee públicamente', () => assertFails(read(visitor, pub + variant)));
  await serverBatch([[store + '/configuracion/general', { mostrarPrecios: false }], [pub, { mostrarPrecios: false }]]);
  await check('ocultar precios bloquea proyección antigua con importes', () => assertFails(read(visitor, pub + product)));
  console.log(`PASARON ${count} casos de permisos locales.`);
} finally {
  for (const ruta of tecnicos) await env.withSecurityRulesDisabled(ctx => deleteDoc(doc(ctx.firestore(), ruta)));
  await env.withSecurityRulesDisabled(ctx => deleteDoc(doc(ctx.firestore(), 'accesos/u_otra_propietaria_test')));
  await seed();
  await env.cleanup();
}
