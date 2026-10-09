import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeApp, deleteApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { exigirEntornoCompartido } from '../src/config/compartido.ts';

// Herramienta técnica: crea la PRIMERA administradora en el proyecto real.
// Usa Application Default Credentials; nunca acepta credenciales JSON ni emuladores.
// No modifica una identidad existente. Si falla, revierte lo creado en esta ejecución.
export async function crearPrimerAdministrador(env:NodeJS.ProcessEnv, correo:string, nombreMostrar:string) {
  const proyecto = exigirEntornoCompartido(env);
  if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo)) throw new Error('Correo inválido.');
  if(!nombreMostrar.trim()) throw new Error('Falta el nombre para mostrar.');
  const app = initializeApp({ projectId: proyecto }, `admin-compartido-${randomUUID()}`);
  const auth = getAuth(app);
  const db = getFirestore(app);
  let uid:string|undefined;
  try {
    const existe = await auth.getUserByEmail(correo).then(()=>true).catch((error:{code?:string})=>{
      if(error.code==='auth/user-not-found') return false;
      throw error;
    });
    if(existe) throw new Error('Ya existe una identidad con ese correo; no se modifica.');
    uid = (await auth.createUser({ email: correo, emailVerified: false, displayName: nombreMostrar })).uid;
    const ahora = FieldValue.serverTimestamp();
    const lote = db.batch();
    lote.create(db.doc(`usuarios/${uid}`), { nombreMostrar, creadoEn: ahora, actualizadoEn: ahora });
    lote.create(db.doc(`accesos/${uid}`), { estado:'activo', roles:['administrador'], creadoEn: ahora, actualizadoEn: ahora });
    await lote.commit();
    const enlaceConfiguracion = await auth.generatePasswordResetLink(correo);
    return { uid, correo, enlaceConfiguracion };
  } catch (error) {
    if(uid) {
      await db.doc(`usuarios/${uid}`).delete().catch(()=>{});
      await db.doc(`accesos/${uid}`).delete().catch(()=>{});
      await auth.deleteUser(uid).catch(()=>{});
    }
    throw error;
  } finally {
    await deleteApp(app).catch(()=>{});
  }
}

const directo = process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if(directo) {
  (async () => {
    if(!process.argv.includes('--confirmar')) throw new Error('Añadir --confirmar para crear la cuenta administradora.');
    const resultado = await crearPrimerAdministrador(process.env, process.env.AMANCAY_ADMIN_EMAIL ?? '', process.env.AMANCAY_ADMIN_NOMBRE ?? '');
    console.log(`Administradora creada: ${resultado.correo} (uid ${resultado.uid}).`);
    console.log(`Enlace de configuración de contraseña (un solo uso, no guardar): ${resultado.enlaceConfiguracion}`);
  })().catch((error:unknown) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}
