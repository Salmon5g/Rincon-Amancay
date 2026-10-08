import assert from 'node:assert/strict';
import { seed } from './seed.mjs';
import { request, fields } from './local.mjs';

const fixture = await seed();
// Repetir la carga actualiza los mismos IDs, sin duplicar documentos.
await seed();
const docs = new Map(fixture.documentos.map(d => [d.ruta, d.datos]));
for (const { ruta, datos } of fixture.documentos) {
  const result = await request('/' + ruta);
  assert.equal(result.status, 200, ruta);
  assert.deepEqual(result.data.fields, fields(datos), ruta);
  if (ruta.includes('/productos/') && !ruta.includes('/variantes/')) {
    assert(docs.has(`tiposProducto/${datos.tipoProductoId}/versiones/${datos.versionTipoProducto}`));
    assert(docs.has(`categorias/${datos.categoriaId}`));
    if (datos.tieneVariantes && ruta.startsWith('tiendasPrivadas/')) {
      assert(!('precioBase' in datos) && !('stock' in datos));
      assert(fixture.documentos.some(d => d.ruta.startsWith(ruta + '/variantes/')));
    }
  }
  if (ruta.startsWith('tiendasPublicas/')) {
    assert(!('propietarioUid' in datos) && !('stock' in datos) && !('historialVentasActivo' in datos));
  }
}
const productos = await request('/tiendasPrivadas/t_demo_01/productos');
assert.equal(productos.data.documents.length, 3);
const gorro = docs.get('tiendasPrivadas/t_demo_01/productos/p_gorro_01');
assert.equal(gorro.tieneVariantes, true);
assert.equal(docs.get('tiendasPrivadas/t_demo_01').propietarioUid, 'u_emprendedora_demo');
assert.equal(docs.get('accesos/u_emprendedora_demo').tiendaId, 't_demo_01');
for (const path of ['/usuarios/u_emprendedora_demo']) {
  const result = await request(path, { admin: false });
  assert.equal(result.status, 403, 'Las reglas iniciales deben seguir cerradas: ' + path);
}
assert.equal((await request('/tiendasPublicas/t_demo_01', { admin: false })).status, 200);
const deniedWrite = await request('/usuarios/intento_sin_sesion', {
  admin: false, method: 'PATCH', body: { fields: fields({ nombreMostrar: 'No debe crearse' }) },
});
assert.equal(deniedWrite.status, 403);
assert.equal((await request('/usuarios/intento_sin_sesion')).status, 404);
console.log('OK: datos, referencias, variantes, carga repetible, lectura pública y rechazo de accesos privados sin sesión.');
