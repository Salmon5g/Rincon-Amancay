import { FieldValue, type Firestore } from 'firebase-admin/firestore';

const LIMITE = 200;

async function cargarEnLotes<T extends FirebaseFirestore.DocumentReference>(db: Firestore, refs: T[]) {
  const snapshots: FirebaseFirestore.DocumentSnapshot[] = [];
  for (let index = 0; index < refs.length; index += 100) {
    snapshots.push(...await db.getAll(...refs.slice(index, index + 100)));
  }
  return snapshots;
}

/** Construye campos públicos mínimos; nunca expone UID ni relaciones privadas. */
export async function construirProyeccionAsociacionPublica(db: Firestore, asociacionId: string) {
  const asociacionRef = db.doc(`asociaciones/${asociacionId}`);
  const asociacion = await asociacionRef.get();
  if (!asociacion.exists || asociacion.get('activo') !== true) return null;

  const relaciones = await asociacionRef.collection('integrantes').where('estado', '==', 'activa').limit(LIMITE + 1).get();
  if (relaciones.size > LIMITE) throw new Error('La asociación supera el máximo de 200 integrantes públicos.');
  const candidatas = relaciones.docs.map(relacion => ({
    uid: relacion.id,
    tiendaId: relacion.get('tiendaId'),
  })).filter((relacion): relacion is { uid: string; tiendaId: string } =>
    typeof relacion.tiendaId === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(relacion.tiendaId));

  const refs = candidatas.flatMap(({ uid, tiendaId }) => [
    db.doc(`accesos/${uid}`), db.doc(`emprendedoras/${uid}`), db.doc(`tiendasPrivadas/${tiendaId}`),
    db.doc(`tiendasPublicas/${tiendaId}`),
  ]);
  const snapshots = await cargarEnLotes(db, refs);
  const integrantes = candidatas.flatMap((persona, index) => {
    const [acceso, perfil, tiendaPrivada, tiendaPublica] = snapshots.slice(index * 4, index * 4 + 4);
    if (acceso.get('estado') !== 'activo' || !Array.isArray(acceso.get('roles')) || !acceso.get('roles').includes('emprendedora')) return [];
    if (perfil.get('tiendaId') !== persona.tiendaId || tiendaPrivada.get('propietarioUid') !== persona.uid) return [];
    if (tiendaPublica.get('estadoPublicacion') !== 'publicado' || tiendaPublica.get('habilitada') !== true) return [];
    return [{
      tiendaId: persona.tiendaId,
      nombre: typeof tiendaPublica.get('nombre') === 'string' ? tiendaPublica.get('nombre') as string : persona.tiendaId,
      sectorId: typeof tiendaPublica.get('sectorId') === 'string' ? tiendaPublica.get('sectorId') as string : '',
      tipoEmprendimientoId: typeof tiendaPublica.get('tipoEmprendimientoId') === 'string' ? tiendaPublica.get('tipoEmprendimientoId') as string : '',
      ...(typeof tiendaPublica.get('logoMiniatura') === 'string' ? { logoMiniatura: tiendaPublica.get('logoMiniatura') } : {}),
    }];
  }).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'));

  return {
    nombre: asociacion.get('nombre') ?? asociacionId,
    descripcion: asociacion.get('descripcion') ?? '',
    sectorId: asociacion.get('sectorId') ?? '',
    ...(typeof asociacion.get('logoUrl') === 'string' ? { logoUrl: asociacion.get('logoUrl') } : {}),
    integrantes,
    activo: true,
    actualizadoEn: FieldValue.serverTimestamp(),
  };
}

/** Publica o retira la copia destinada a lectura anónima. */
export async function sincronizarAsociacionPublica(db: Firestore, asociacionId: string) {
  const publicaRef = db.doc(`asociacionesPublicas/${asociacionId}`);
  const proyeccion = await construirProyeccionAsociacionPublica(db, asociacionId);
  if (proyeccion) await publicaRef.set(proyeccion);
  else await publicaRef.delete().catch(() => undefined);
}

/** Refresca las asociaciones públicas que incluyen una tienda cuando cambia su publicación. */
export async function sincronizarAsociacionesDeTienda(db: Firestore, tiendaId: string) {
  const relaciones = await db.collectionGroup('integrantes').where('tiendaId', '==', tiendaId).limit(501).get();
  if (relaciones.size > 500) throw new Error('La tienda supera el máximo de 500 relaciones de asociación.');
  const ids = [...new Set(relaciones.docs
    .filter(relacion => relacion.get('estado') === 'activa')
    .map(relacion => relacion.ref.parent.parent?.id)
    .filter((id): id is string => typeof id === 'string'))];
  for (const id of ids) await sincronizarAsociacionPublica(db, id);
}
