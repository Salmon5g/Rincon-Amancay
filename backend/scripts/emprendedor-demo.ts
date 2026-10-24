import { FieldValue } from 'firebase-admin/firestore';
import { auth, db } from '../src/config/emulador.ts';

const correo = process.env.DEMO_EMPRENDEDORA_CORREO ?? 'emprendedora@demo.test';
const clave = process.env.DEMO_EMPRENDEDORA_CLAVE ?? 'demo12345';
const uid = 'u_emprendedora_demo';
const tiendaId = 't_demo_01';

const tipos: Record<string, { nombre: string; campos: string[] }> = {
  decoracion: { nombre: 'Decoración', campos: ['material', 'cuidados', 'tamano'] },
  bienestar: { nombre: 'Bienestar', campos: ['material', 'cuidados', 'tamano'] },
  papeleria: { nombre: 'Papelería', campos: ['material', 'cuidados', 'tamano'] },
  textil: { nombre: 'Textil', campos: ['material', 'cuidados', 'tamano'] },
};

function sanitizar(valor: string) {
  return valor.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 128);
}

try {
  try {
    await auth.getUser(uid);
    await auth.updateUser(uid, { email: correo.trim().toLowerCase(), password: clave, displayName: 'Emprendedora Demo' });
  } catch {
    await auth.createUser({ uid, email: correo.trim().toLowerCase(), password: clave, displayName: 'Emprendedora Demo' });
  }

  const now = FieldValue.serverTimestamp();
  const batch = db.batch();
  batch.set(db.doc(`usuarios/${uid}`), { nombreMostrar: 'Emprendedora Demo', creadoEn: now, actualizadoEn: now }, { merge: true });
  batch.set(db.doc(`accesos/${uid}`), { roles: ['emprendedora'], estado: 'activo', tiendaId, creadoEn: now, actualizadoEn: now }, { merge: true });
  batch.set(db.doc(`emprendedoras/${uid}`), { tiendaId, creadaPor: 'seed', creadoEn: now, actualizadoEn: now }, { merge: true });
  batch.set(db.doc(`tiendasPrivadas/${tiendaId}`), { propietarioUid: uid }, { merge: true });
  batch.set(db.doc(`tiendasPrivadas/${tiendaId}/configuracion/general`), { mostrarPrecios: true, historialVentasActivo: false, formaContacto: 'formulario', actualizadoEn: now }, { merge: true });

  for (const [id, tipo] of Object.entries(tipos)) {
    batch.set(db.doc(`tiposProducto/${id}`), { nombre: tipo.nombre, activo: true, versionActual: 1, creadoEn: now, actualizadoEn: now }, { merge: true });
    batch.set(db.doc(`tiposProducto/${id}/versiones/1`), {
      version: 1,
      campos: tipo.campos.map(clave => ({ clave, etiqueta: clave, tipo: 'texto', obligatorio: false })),
      opcionesVariante: ['color'],
      creadoEn: now,
      actualizadoEn: now,
    }, { merge: true });
    batch.set(db.doc(`categorias/${id}`), { nombre: tipo.nombre, activo: true, creadoEn: now, actualizadoEn: now }, { merge: true });
  }

  await batch.commit();
  console.log(`Emprendedora demo lista: ${correo} / ${clave} (tienda ${tiendaId})`);
  console.log('Catálogos asegurados:', Object.keys(tipos).map(sanitizar).join(', '), 'y alimento/plantas/vestuario ya venían del seed.');
} finally {
  await db.terminate();
}
