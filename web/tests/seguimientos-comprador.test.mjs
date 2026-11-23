import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crearSeguimientosComprador } from '../app/lib/seguimientos-comprador.ts';

/* SDK simulado: no usa emuladores ni red. Guarda los documentos seguidos en memoria. */
function escenario({ conSesion = true } = {}) {
    const llamadas = [];
    const documentos = new Map();
    const sdk = {
        doc: (_db, ...ruta) => ({ ruta: ruta.join('/') }),
        getDoc: async referencia => {
            llamadas.push(`leer:${referencia.ruta}`);
            return { exists: () => documentos.has(referencia.ruta) };
        },
        setDoc: async (referencia, datos) => {
            llamadas.push(`crear:${referencia.ruta}`);
            documentos.set(referencia.ruta, datos);
        },
        deleteDoc: async referencia => {
            llamadas.push(`borrar:${referencia.ruta}`);
            documentos.delete(referencia.ruta);
        },
        onSnapshot: (referencia, siguiente) => {
            llamadas.push(`observar:${referencia.ruta}`);
            siguiente({ exists: () => documentos.has(referencia.ruta) });
            return () => llamadas.push('desuscribir');
        },
    };
    return {
        servicio: crearSeguimientosComprador({ auth: { currentUser: conSesion ? { uid: 'u1' } : null }, db: {} }, sdk),
        llamadas,
        documentos,
    };
}

test('sin sesión no escribe ni observa y pide iniciar sesión', async () => {
    const { servicio, llamadas } = escenario({ conSesion: false });
    const observado = [];
    const dejar = servicio.observar('tienda', 'tienda-1', valor => observado.push(valor));
    dejar();
    assert.deepEqual(observado, [false]);
    assert.deepEqual(llamadas, []);
    await assert.rejects(servicio.alternar('tienda', 'tienda-1'), { code: 'no-autenticado' });
});

test('seguir una tienda crea solo el documento propio con tiendaId', async () => {
    const { servicio, documentos, llamadas } = escenario();
    assert.equal(await servicio.alternar('tienda', 'tienda-1'), true);
    assert.deepEqual(documentos.get('usuarios/u1/tiendasSeguidas/tienda-1'), { tiendaId: 'tienda-1' });
    assert.deepEqual(llamadas, ['leer:usuarios/u1/tiendasSeguidas/tienda-1', 'crear:usuarios/u1/tiendasSeguidas/tienda-1']);
});

test('seguir deja el documento creado sin alternar', async () => {
    const { servicio, documentos } = escenario();
    assert.equal(await servicio.seguir('tienda', 'tienda-1'), true);
    assert.deepEqual(documentos.get('usuarios/u1/tiendasSeguidas/tienda-1'), { tiendaId: 'tienda-1' });
    assert.equal(await servicio.seguir('tienda', 'tienda-1'), true);
    assert.deepEqual(documentos.get('usuarios/u1/tiendasSeguidas/tienda-1'), { tiendaId: 'tienda-1' });
});

test('seguir sin sesión pide iniciar sesión y no escribe', async () => {
    const { servicio, llamadas } = escenario({ conSesion: false });
    await assert.rejects(servicio.seguir('tienda', 'tienda-1'), { code: 'no-autenticado' });
    assert.deepEqual(llamadas, []);
});

test('volver a tocar el botón elimina el seguimiento', async () => {
    const { servicio, documentos } = escenario();
    await servicio.alternar('tienda', 'tienda-1');
    assert.equal(await servicio.alternar('tienda', 'tienda-1'), false);
    assert.equal(documentos.has('usuarios/u1/tiendasSeguidas/tienda-1'), false);
});

test('seguir una asociación usa asociacionesSeguidas y asociacionId', async () => {
    const { servicio, documentos } = escenario();
    assert.equal(await servicio.alternar('asociacion', 'asoc-1'), true);
    assert.deepEqual(documentos.get('usuarios/u1/asociacionesSeguidas/asoc-1'), { asociacionId: 'asoc-1' });
});

test('observar refleja el documento y se puede desuscribir', () => {
    const { servicio, llamadas } = escenario();
    const observado = [];
    const dejar = servicio.observar('asociacion', 'asoc-1', valor => observado.push(valor));
    dejar();
    assert.deepEqual(observado, [false]);
    assert.deepEqual(llamadas, ['observar:usuarios/u1/asociacionesSeguidas/asoc-1', 'desuscribir']);
});

test('rechaza identificadores inválidos antes de tocar Firestore', async () => {
    const { servicio, llamadas } = escenario();
    await assert.rejects(servicio.alternar('tienda', '../otra'), { code: 'datos-invalidos' });
    assert.deepEqual(llamadas, []);
});
