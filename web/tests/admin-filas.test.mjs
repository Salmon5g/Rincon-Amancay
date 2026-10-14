import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Timestamp } from 'firebase/firestore';
import { filaEmprendedora } from '../app/lib/admin-lecturas.ts';

test('la fila de emprendedora conserva UID, tienda, estado y versión para operaciones seguras', () => {
  const fila = filaEmprendedora('uid-emprendedora', {
    estado: 'desactivado',
    tiendaId: 'tienda-1',
    actualizadoEn: new Timestamp(123, 456),
  });
  assert.deepEqual(fila, {
    titulo: 'uid-emprendedora',
    detalle: 'Tienda: tienda-1',
    estado: 'desactivado',
    uidDestino: 'uid-emprendedora',
    tiendaId: 'tienda-1',
    versionEsperada: '123:456',
  });
});

test('una fila sin Timestamp no habilita una acción con versión obsoleta o inventada', () => {
  const fila = filaEmprendedora('uid-emprendedora', { estado: 'activo', tiendaId: 'tienda-1' });
  assert.equal(fila.versionEsperada, undefined);
});
