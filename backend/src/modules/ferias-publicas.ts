import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion } from './publicacion.ts';

const ID_VALIDO = /^[a-zA-Z0-9_-]{1,128}$/;
const MAX_PARTICIPANTES = 200;
const MAX_TIENDAS_ASOCIACION = 3;
const MAX_PRODUCTOS = 3;

type TipoParticipante = 'emprendedora' | 'asociacion';
type MuestraCatalogo = { nombreTienda: string; productos: string[] };
type SemillaParticipante = {
  tipo: TipoParticipante;
  nombre: string;
  tiendaIds: string[];
};

function cadena(value: unknown, fallback = ''): string {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function idValido(value: unknown): value is string {
  return typeof value === 'string' && ID_VALIDO.test(value);
}

/** Devuelve nombres de tiendas y productos publicados; nunca UID ni campos administrativos. */
export function crearLecturaMuestraFeriaPublica(db: Firestore) {
  return async (feriaId: string) => {
    if (!idValido(feriaId)) throw new ErrorOperacion('datos-invalidos', 'Identificador de feria inválido.');

    const feriaRef = db.doc('ferias/' + feriaId);
    const feria = await feriaRef.get();
    if (!feria.exists || feria.get('estado') !== 'publicada') {
      throw new ErrorOperacion('no-encontrado', 'La feria no está publicada o ya no existe.');
    }

    const participantes = await feriaRef.collection('participantes')
      .where('estado', '==', 'confirmada')
      .limit(MAX_PARTICIPANTES)
      .get();

    const candidatas = participantes.docs.map(documento => {
      const data = documento.data();
      const tipo = data.tipo === 'asociacion' ? 'asociacion' : data.tipo === 'emprendedora' ? 'emprendedora' : null;
      const entidadId = cadena(data.entidadId);
      return tipo && idValido(entidadId) ? { tipo, entidadId, data } : null;
    }).filter((item): item is NonNullable<typeof item> => item !== null);

    const uids = [...new Set(candidatas.filter(item => item.tipo === 'emprendedora').map(item => item.entidadId))];
    const asociacionesIds = [...new Set(candidatas.filter(item => item.tipo === 'asociacion').map(item => item.entidadId))];
    const [perfiles, asociaciones] = await Promise.all([
      uids.length ? db.getAll(...uids.map(uid => db.doc('emprendedoras/' + uid))) : Promise.resolve([]),
      asociacionesIds.length ? db.getAll(...asociacionesIds.map(id => db.doc('asociacionesPublicas/' + id))) : Promise.resolve([]),
    ]);
    const perfilPorUid = new Map(uids.map((uid, index) => [uid, perfiles[index]]));
    const asociacionPorId = new Map(asociacionesIds.map((id, index) => [id, asociaciones[index]]));

    const semillas: SemillaParticipante[] = [];
    for (const participante of candidatas) {
      if (participante.tipo === 'emprendedora') {
        const perfil = perfilPorUid.get(participante.entidadId);
        const tiendaId = perfil?.get('tiendaId');
        semillas.push({
          tipo: 'emprendedora',
          nombre: cadena(participante.data.nombreTienda, 'Emprendimiento confirmado'),
          tiendaIds: idValido(tiendaId) ? [tiendaId] : [],
        });
        continue;
      }

      const asociacion = asociacionPorId.get(participante.entidadId);
      if (!asociacion?.exists || asociacion.get('activo') !== true) continue;
      const integrantes = (asociacion.get('integrantes') as unknown[] | undefined) ?? [];
      const tiendaIds = [...new Set(integrantes.flatMap(item => {
        if (!item || typeof item !== 'object' || !('tiendaId' in item)) return [];
        const tiendaId = (item as { tiendaId?: unknown }).tiendaId;
        return idValido(tiendaId) ? [tiendaId] : [];
      }))].slice(0, MAX_TIENDAS_ASOCIACION);
      semillas.push({
        tipo: 'asociacion',
        nombre: cadena(asociacion.get('nombre'), 'Asociación confirmada'),
        tiendaIds,
      });
    }

    const tiendaIds = [...new Set(semillas.flatMap(item => item.tiendaIds))];
    const tiendas = tiendaIds.length
      ? await db.getAll(...tiendaIds.map(id => db.doc('tiendasPublicas/' + id)))
      : [];
    const tiendaPorId = new Map(tiendaIds.map((id, index) => [id, tiendas[index]]));
    const muestrasPorTienda = new Map<string, MuestraCatalogo>();

    for (let inicio = 0; inicio < tiendaIds.length; inicio += 20) {
      const lote = tiendaIds.slice(inicio, inicio + 20);
      const muestras = await Promise.all(lote.map(async tiendaId => {
        const tienda = tiendaPorId.get(tiendaId);
        if (!tienda?.exists || tienda.get('estadoPublicacion') !== 'publicado' || tienda.get('habilitada') !== true) return null;
        const coleccion = tienda.get('mostrarPrecios') === true ? 'productos' : 'productosConsulta';
        const productos = await db.collection('tiendasPublicas/' + tiendaId + '/' + coleccion)
          .where('estadoPublicacion', '==', 'publicado')
          .limit(MAX_PRODUCTOS)
          .get();
        return {
          tiendaId,
          muestra: {
            nombreTienda: cadena(tienda.get('nombre'), 'Tienda participante'),
            productos: productos.docs.map(producto => cadena(producto.get('nombre'))).filter(Boolean),
          },
        };
      }));
      muestras.forEach(item => {
        if (item) muestrasPorTienda.set(item.tiendaId, item.muestra);
      });
    }

    const resultado = semillas.map(item => ({
      tipo: item.tipo,
      nombre: item.tipo === 'emprendedora'
        ? item.tiendaIds.map(id => muestrasPorTienda.get(id)?.nombreTienda).find(Boolean) ?? item.nombre
        : item.nombre,
      catalogo: item.tiendaIds.flatMap(id => {
        const muestra = muestrasPorTienda.get(id);
        return muestra ? [muestra] : [];
      }),
    })).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'));

    return { feriaId, participantes: resultado };
  };
}
 
function fechaIso(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

/** Lista ferias publicadas para compradores sin exponer los documentos de participantes. */
export function crearLecturaFeriasPublicas(db: Firestore) {
  return async () => {
    const snapshot = await db.collection('ferias')
      .where('estado', '==', 'publicada')
      .limit(100)
      .get();
    const ferias = snapshot.docs.map(documento => {
      const data = documento.data();
      return {
        id: documento.id,
        nombre: cadena(data.nombre, 'Feria'),
        descripcion: cadena(data.descripcion),
        organizador: cadena(data.organizador),
        direccion: cadena(data.direccion),
        fechaInicio: fechaIso(data.fechaInicio),
        fechaFin: fechaIso(data.fechaFin),
        latitud: typeof data.latitud === 'number' && Number.isFinite(data.latitud) ? data.latitud : null,
        longitud: typeof data.longitud === 'number' && Number.isFinite(data.longitud) ? data.longitud : null,
      };
    }).sort((a, b) => String(a.fechaInicio ?? '').localeCompare(String(b.fechaInicio ?? '')));
    return { ferias };
  };
}

/** Devuelve únicamente el modo público de navegación almacenado por administración. */
export function crearLecturaPoliticaNavegacionPublica(db: Firestore) {
  return async () => {
    const snapshot = await db.doc('configuracionAplicacion/navegacion').get();
    const modo = snapshot.get('modo');
    return { modo: ['permitir', 'invitar', 'exigir'].includes(String(modo)) ? modo : 'permitir' };
  };
}
