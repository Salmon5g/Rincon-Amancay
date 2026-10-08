import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { FieldValue } from 'firebase-admin/firestore';
import { auth, db } from '../src/config/emulador.ts';

// Herramienta técnica exclusiva del proyecto demo. No se expone en la API.
const id = randomUUID();
const accounts = [
  {uid:`demo_admin_${id}`,email:`admin-${id}@example.test`,password:randomUUID(),nombreMostrar:'Administración local'},
  {uid:`demo_candidata_${id}`,email:`candidata-${id}@example.test`,password:randomUUID(),nombreMostrar:'Emprendedora local'},
];
const created: string[] = [];
const output = new URL(`../.local/cuentas-${id}.json`, import.meta.url);
try {
  for (const path of ['sectores/sector_demo','tiposEmprendimiento/mixto_demo']) {
    if ((await db.doc(path).get()).data()?.activo !== true) throw new Error('Ejecutar npm run seed desde firebase/ antes de crear cuentas demo.');
  }
  for (const account of accounts) {
    await auth.createUser({uid:account.uid,email:account.email,password:account.password,displayName:account.nombreMostrar});
    created.push(account.uid);
  }
  const batch=db.batch(); const now=FieldValue.serverTimestamp();
  batch.create(db.doc(`usuarios/${accounts[0].uid}`),{nombreMostrar:accounts[0].nombreMostrar,creadoEn:now,actualizadoEn:now});
  batch.create(db.doc(`accesos/${accounts[0].uid}`),{estado:'activo',roles:['administrador'],creadoEn:now,actualizadoEn:now});
  await batch.commit();
  const solicitudAlta={
    operacionId:randomUUID(),uidDestino:accounts[1].uid,tiendaId:`demo_tienda_${id}`,
    nombreMostrar:accounts[1].nombreMostrar,nombreTienda:'Tienda de práctica',descripcion:'Ejemplo local para el equipo',
    sectorId:'sector_demo',tipoEmprendimientoId:'mixto_demo',mostrarPrecios:true,historialVentasActivo:false,formaContacto:'formulario',
  };
  await mkdir(new URL('../.local/',import.meta.url),{recursive:true});
  await writeFile(output,JSON.stringify({soloEmulador:true,projectId:'demo-rincon-amancay',administradora:accounts[0],candidata:accounts[1],solicitudAlta},null,2)+'\n',{flag:'wx',mode:0o600});
  console.log(`Cuentas demo creadas. Credenciales y solicitud de ejemplo: ${fileURLToPath(output)}`);
  console.log('La candidata todavía no tiene roles ni tienda. Ejecutar altaEmprendedora con la sesión de la administradora.');
} catch(error) {
  // Solo las identidades nuevas de esta ejecución; nunca modifica las existentes.
  for(const uid of created) {
    await db.doc(`usuarios/${uid}`).delete(); await db.doc(`accesos/${uid}`).delete();
    await auth.deleteUser(uid);
  }
  throw error;
} finally { await db.terminate(); }
