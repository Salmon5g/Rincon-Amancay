import type { Auth } from 'firebase-admin/auth';
import type { Firestore } from 'firebase-admin/firestore';
import type { getStorage } from 'firebase-admin/storage';
import type { ConfigHttp } from './config/http.ts';
import { crearValidadorSesion } from './modules/sesiones.ts';
import { crearCicloCuentas } from './modules/ciclo-cuentas.ts';
import { crearApi } from './http/api.ts';
import { crearPublicacion } from './modules/publicacion.ts';
import { crearProcesadorImagenes } from './modules/imagenes.ts';
import { crearCuentas } from './modules/cuentas.ts';
import { crearProductos } from './modules/productos.ts';
import { crearStock } from './modules/stock.ts';
import { crearDesactivacion } from './modules/desactivacion.ts';

export function montarApi(auth:Auth,db:Firestore,bucket:ReturnType<ReturnType<typeof getStorage>['bucket']>,config?:ConfigHttp) {
  return crearApi(auth,crearPublicacion(db,crearProcesadorImagenes(bucket)),crearCuentas(db,auth),crearProductos(db),crearStock(db),crearDesactivacion(db,auth),crearCicloCuentas(db,auth),crearValidadorSesion(db),config);
}
