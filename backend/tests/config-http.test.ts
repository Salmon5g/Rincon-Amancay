import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import type { Auth } from 'firebase-admin/auth';
import { crearApi } from '../src/http/api.ts';
import { validarConfigHttp, httpLocal } from '../src/config/http.ts';

test('configuración HTTP no mezcla entornos ni acepta orígenes ambiguos',()=>{
  assert.deepEqual(validarConfigHttp(httpLocal),httpLocal);
  for(const origen of ['*','https://*.example.com','http://example.com','https://example.com/ruta','https://u:p@example.com','https://example.com/']) {
    assert.throws(()=>validarConfigHttp({...httpLocal,origenes:[origen]}));
  }
  assert.throws(()=>validarConfigHttp({...httpLocal,proyecto:'rincon-amancay'}));
  assert.throws(()=>validarConfigHttp({...httpLocal,entorno:'compartido'}));
});

test('origen compartido y salud configurables; no omite autenticación',async()=>{
  const ejecutar:Parameters<typeof crearApi>[1]=async(accion,_identidad,solicitud)=>({accion,operacionId:solicitud.operacionId});
  const server=crearApi({} as Auth,ejecutar,undefined,undefined,undefined,undefined,undefined,undefined,{entorno:'compartido',proyecto:'rincon-amancay',origenes:['https://web.example.test']});
  server.listen(0,'127.0.0.1');await once(server,'listening');const address=server.address();assert(address&&typeof address!=='string');
  const base=`http://127.0.0.1:${address.port}`;
  try {
    const health=await fetch(base+'/health',{headers:{Origin:'https://web.example.test'}});
    assert.equal(health.headers.get('Access-Control-Allow-Origin'),'https://web.example.test');
    assert.deepEqual(await health.json(),{estado:'ok',entorno:'compartido',proyecto:'rincon-amancay'});
    assert.equal((await fetch(base+'/health',{headers:{Origin:'https://ajeno.example.test'}})).status,403);
    assert.equal((await fetch(base+'/api/v1/publicarTienda',{method:'POST'})).status,401);
    assert.equal((await fetch(base+'/api/v1/publicarTienda',{method:'OPTIONS',headers:{Origin:'https://web.example.test'}})).status,204);
  } finally {await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});}
});
