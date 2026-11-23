import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
    intencionSeguimiento,
    pedirAccesoParaSeguir,
    solicitudAccesoComprador,
    tomarIntencionSeguimiento,
} from '../app/lib/acceso-comprador-ui.ts';

test('pedir acceso recuerda la tienda que se quiso seguir', () => {
    const antes = solicitudAccesoComprador.value;
    pedirAccesoParaSeguir('tienda', 'tienda-1');
    assert.deepEqual(intencionSeguimiento.value, { tipo: 'tienda', entidadId: 'tienda-1' });
    assert.equal(solicitudAccesoComprador.value, antes + 1);
    tomarIntencionSeguimiento();
});

test('la intención se entrega una sola vez', () => {
    pedirAccesoParaSeguir('asociacion', 'asoc-1');
    assert.deepEqual(tomarIntencionSeguimiento(), { tipo: 'asociacion', entidadId: 'asoc-1' });
    assert.equal(tomarIntencionSeguimiento(), null);
    assert.equal(intencionSeguimiento.value, null);
});

test('con filtro solo se entrega si coincide con la entidad', () => {
    pedirAccesoParaSeguir('tienda', 'tienda-1');
    assert.equal(tomarIntencionSeguimiento({ tipo: 'tienda', entidadId: 'otra' }), null);
    assert.deepEqual(
        tomarIntencionSeguimiento({ tipo: 'tienda', entidadId: 'tienda-1' }),
        { tipo: 'tienda', entidadId: 'tienda-1' },
    );
    assert.equal(intencionSeguimiento.value, null);
});

test('descarta identificadores inválidos sin guardar intención', () => {
    pedirAccesoParaSeguir('tienda', '../otra');
    assert.equal(intencionSeguimiento.value, null);
});
