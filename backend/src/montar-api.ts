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
import { crearCatalogos } from './modules/catalogos.ts';
import { crearAsociaciones } from './modules/asociaciones.ts';
import { crearAdministracionPlataforma } from './modules/administracion-plataforma.ts';
import { crearFerias } from './modules/ferias.ts';
import { crearEdicionTiendaEmprendedora } from './modules/tienda-emprendedora.ts';
import type { ImagenPreparada } from './modules/imagenes.ts';

export function montarApi(auth:Auth,db:Firestore,bucket:ReturnType<ReturnType<typeof getStorage>['bucket']>|undefined,config?:ConfigHttp,imagenes?:{procesar:(ruta:string,tiendaId:string,productoId:string)=>Promise<boolean|ImagenPreparada>;procesarTienda?:(ruta:string,tiendaId:string)=>Promise<ImagenPreparada|false>;subir(uid:string,tiendaId:string,productoId:string,archivoId:string,contentType:string,bytes:Buffer):Promise<string>;leer?(uid:string,tiendaId:string,productoId:string,indice:number):Promise<{bytes:Buffer;contentType:string}>;subirTienda?:(uid:string,tiendaId:string,archivoId:string,contentType:string,bytes:Buffer)=>Promise<string>;subirLogoAsociacion?:(uid:string,archivoId:string,contentType:string,bytes:Buffer)=>Promise<string>;limpiar(rutas:string[]):Promise<void>}) {
  const procesar=imagenes?.procesar ?? (bucket?crearProcesadorImagenes(bucket):undefined);
  if(!procesar)throw new Error('Se requiere un proveedor de imágenes para montar la API.');
  return crearApi(auth,crearPublicacion(db,procesar,imagenes?.limpiar,imagenes?.procesarTienda),crearCuentas(db,auth),crearProductos(db,imagenes?.limpiar),crearStock(db),crearDesactivacion(db,auth),crearCicloCuentas(db,auth),crearValidadorSesion(db),config,crearCatalogos(db),crearAsociaciones(db),crearAdministracionPlataforma(db,auth),crearFerias(db),crearEdicionTiendaEmprendedora(db),imagenes?{subir:imagenes.subir,...(imagenes.leer?{leer:imagenes.leer}:{}),...(imagenes.subirTienda?{subirTienda:imagenes.subirTienda}:{}),...(imagenes.subirLogoAsociacion?{subirLogoAsociacion:imagenes.subirLogoAsociacion}:{})}:undefined);
}
