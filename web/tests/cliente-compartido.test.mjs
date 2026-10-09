import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crearClienteCompartido } from '../app/lib/cliente-compartido.ts';

const firebase = {
  app: { options: { projectId: 'rincon-amancay' } },
  auth: { authStateReady: async () => {}, currentUser: { getIdToken: async () => 'token-de-prueba' } },
};
test('cliente compartido rechaza proyecto demo y destinos ambiguos antes de enviar tokens', () => {
  assert.throws(() => crearClienteCompartido({ app: { options: { projectId: 'demo-rincon-amancay' } } }, { apiUrl: 'https://api.example.test' }));
  for (const apiUrl of ['http://localhost:8787', 'http://api.example.test', 'https://user:pass@api.example.test', 'https://api.example.test/api/v1/', 'https://api.example.test?x=1', 'https://api.example.test#x', '/relativa']) {
    assert.throws(() => crearClienteCompartido(firebase, { apiUrl }));
  }
});
test('cliente compartido construye la ruta HTTPS y conserva intención y token', async t => {
  let llamada;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    llamada = { url, options };
    return new Response(JSON.stringify({ datos: { estado: 'ok' } }), { status: 200 });
  });
  for (const apiUrl of ['https://api.example.test', 'https://api.example.test/']) {
    const cliente = crearClienteCompartido(firebase, { apiUrl });
    const solicitud = { operacionId: 'intencion-original', tiendaId: 't' };
    assert.deepEqual(await cliente.llamar('publicarTienda', solicitud), { estado: 'ok' });
    assert.equal(llamada.url, 'https://api.example.test/api/v1/publicarTienda');
    assert.equal(llamada.options.headers.Authorization, 'Bearer token-de-prueba');
    assert.equal(llamada.options.redirect, 'error');
    assert.deepEqual(JSON.parse(llamada.options.body), solicitud);
  }
});
