import { createHash } from 'node:crypto';
import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';
import { prepararImagen, type ImagenPreparada } from './imagenes.ts';

const maxBytes=5*1024*1024;
const uidTecnico='4a91b0f5-72ec-432a-ae3c-007a7eed1b8f';
const buckets={originales:'amancay-originales',catalogo:'amancay-catalogo'} as const;
const extensiones:Record<string,string>={'image/jpeg':'jpg','image/png':'png','image/webp':'webp'};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const id=/^[a-zA-Z0-9_-]{1,128}$/;
type ConfigSupabase={url:string;anonKey:string;email:string;password:string;uid:string};
type SesionSupabase={accessToken:string;refreshToken:string;expiraEn:number};

function exigir(ok:unknown,code:string,mensaje:string):asserts ok {
  if(!ok)throw new ErrorOperacion(code,mensaje);
}
function partes(ruta:string) { return ruta.split('/').map(encodeURIComponent).join('/'); }
function esDuplicado(status:number,texto:string) {
  if(status===409)return true;
  if(status!==400)return false;
  try { const body=JSON.parse(texto); return /already exists|duplicate/i.test(`${body.message??''} ${body.error??''}`); }
  catch { return false; }
}

export function validarConfigSupabase(env:NodeJS.ProcessEnv):ConfigSupabase {
  const rawUrl=env.AMANCAY_SUPABASE_URL?.replace(/\/$/,'');
  let url:URL;
  try { url=new URL(rawUrl??''); } catch { throw new Error('Configurar AMANCAY_SUPABASE_URL.'); }
  if(url.protocol!=='https:' || url.hostname!=='supabase-fixmol.codepulse.cl' || url.pathname!=='/' || url.search || url.hash)throw new Error('AMANCAY_SUPABASE_URL debe apuntar a la instancia HTTPS del curso.');
  const anonKey=env.AMANCAY_SUPABASE_ANON_KEY??'';
  let role='';
  try { role=JSON.parse(Buffer.from(anonKey.split('.')[1]??'','base64url').toString('utf8')).role; } catch {}
  if(role!=='anon')throw new Error('AMANCAY_SUPABASE_ANON_KEY debe ser la anon key del correo del profesor; service_role no se admite.');
  const email=env.AMANCAY_SUPABASE_MEDIA_EMAIL?.trim()??'';
  const password=env.AMANCAY_SUPABASE_MEDIA_PASSWORD??'';
  const uid=env.AMANCAY_SUPABASE_MEDIA_UID??'';
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || password.length<8 || uid!==uidTecnico)throw new Error('Configurar la cuenta técnica de Supabase con el UID autorizado.');
  return {url:url.origin,anonKey,email,password,uid};
}

export class ClienteStorageSupabase {
  private sesion?:SesionSupabase;
  private renovacion?:Promise<SesionSupabase>;
  private readonly config:ConfigSupabase;

  constructor(config:ConfigSupabase) { this.config=config; }

  private async obtenerSesion():Promise<SesionSupabase> {
    if(this.sesion && Date.now()<this.sesion.expiraEn-60_000)return this.sesion;
    if(this.renovacion)return this.renovacion;
    this.renovacion=this.renovarSesion();
    try { this.sesion=await this.renovacion; return this.sesion; }
    finally { this.renovacion=undefined; }
  }

  private async renovarSesion():Promise<SesionSupabase> {
    const token=this.sesion?.refreshToken;
    if(token) {
      try { return await this.solicitarSesion('refresh_token',{refresh_token:token}); }
      catch { this.sesion=undefined; }
    }
    return this.solicitarSesion('password',{email:this.config.email,password:this.config.password});
  }

  private async solicitarSesion(tipo:'password'|'refresh_token',body:Record<string,string>):Promise<SesionSupabase> {
    let response:Response;
    try {
      response=await fetch(`${this.config.url}/auth/v1/token?grant_type=${tipo}`,{
        method:'POST',headers:{apikey:this.config.anonKey,'Content-Type':'application/json'},
        body:JSON.stringify(body),signal:AbortSignal.timeout(12_000),
      });
    } catch { throw new ErrorOperacion('pendiente','Supabase Auth no está disponible; revisar la conexión de la API local.'); }
    let data:any;
    try { data=await response.json(); } catch { data={}; }
    if(!response.ok || data?.user?.id!==this.config.uid || typeof data?.access_token!=='string' || typeof data?.refresh_token!=='string') {
      throw new ErrorOperacion('pendiente','Supabase rechazó la sesión técnica o su UID no coincide con la configuración.');
    }
    return {accessToken:data.access_token,refreshToken:data.refresh_token,expiraEn:Date.now()+Math.max(60,Number(data.expires_in)||3600)*1000};
  }

  private async solicitudObjeto(method:'POST'|'GET'|'DELETE',bucket:string,path?:string,bytes?:Buffer,contentType?:string):Promise<Response> {
    const sesion=await this.obtenerSesion();
    const encodedBucket=encodeURIComponent(bucket);
    let endpoint=`${this.config.url}/storage/v1/object/${encodedBucket}`;
    const headers:Record<string,string>={apikey:this.config.anonKey,Authorization:`Bearer ${sesion.accessToken}`};
    let body:ArrayBuffer|string|undefined;
    if(method==='GET')endpoint=`${this.config.url}/storage/v1/object/authenticated/${encodedBucket}/${partes(path!)}`;
    if(method==='POST') {
      endpoint+=`/${partes(path!)}`;
      headers['Content-Type']=contentType!;
      headers['x-upsert']='false';
      headers['Cache-Control']=bucket===buckets.catalogo?'public, max-age=31536000, immutable':'private, max-age=0, no-store';
      const copy=new Uint8Array(bytes!.length);
      copy.set(bytes!);
      body=copy.buffer;
    }
    if(method==='DELETE') {
      headers['Content-Type']='application/json';
      body=JSON.stringify({prefixes:[path!]});
    }
    const inicio=Date.now();
    try {
      return await fetch(endpoint,{method,headers,body,signal:AbortSignal.timeout(60_000)});
    } catch (error) {
      const codigo=typeof error==='object' && error!==null && 'code' in error ? String((error as {code?:unknown}).code) : undefined;
      console.warn('[Supabase Storage] Falló una solicitud de red.',{
        method,bucket,error:error instanceof Error?error.name:'UnknownError',codigo,
        duracionMs:Date.now()-inicio,
      });
      throw new ErrorOperacion('pendiente','No se pudo comunicar con Supabase Storage; conservar la operación y reintentar.');
    }
  }

  async subirInmutable(bucket:string,path:string,bytes:Buffer,contentType:string):Promise<void> {
    const response=await this.solicitudObjeto('POST',bucket,path,bytes,contentType);
    if(response.ok)return;
    const detail=await response.text().catch(()=> '');
    if(!esDuplicado(response.status,detail))throw new ErrorOperacion('pendiente','Supabase no pudo guardar la imagen; conservar la operación y reintentar.');
    const existing=await this.solicitudObjeto('GET',bucket,path);
    if(!existing.ok || !Buffer.from(await existing.arrayBuffer()).equals(bytes))throw new ErrorOperacion('conflicto','La ruta de imagen ya existe con otro contenido.');
  }

  async leerOriginal(path:string):Promise<{bytes:Buffer;contentType:string}|null> {
    const response=await this.solicitudObjeto('GET',buckets.originales,path);
    if(response.status===404)return null;
    if(!response.ok)throw new ErrorOperacion('pendiente','No se pudo leer el original privado en Supabase Storage.');
    const size=Number(response.headers.get('content-length'));
    if(Number.isFinite(size) && size>maxBytes)throw new ErrorOperacion('imagen-no-autorizada','El original excede el límite permitido.');
    const bytes=Buffer.from(await response.arrayBuffer());
    if(bytes.length===0 || bytes.length>maxBytes)throw new ErrorOperacion('imagen-no-autorizada','El original excede el límite permitido.');
    return {bytes,contentType:response.headers.get('content-type')?.split(';')[0].trim()??''};
  }

  async borrar(bucket:string,rutas:string[]):Promise<void> {
    for(let inicio=0;inicio<rutas.length;inicio+=100) {
      const trozo=rutas.slice(inicio,inicio+100);
      for(const path of trozo) {
        const response=await this.solicitudObjeto('DELETE',bucket,path);
        if(!response.ok)throw new ErrorOperacion('pendiente','Supabase no pudo retirar todas las imágenes; reintentar la misma operación.');
      }
    }
  }
}

export function crearServicioImagenesSupabase(db:Firestore,storage:ClienteStorageSupabase) {
  const prepararYPublicar=async(ruta:string,extension:string,base:string):Promise<ImagenPreparada|false>=>{
    const original=await storage.leerOriginal(ruta);
    if(!original)return false;
    const mime=`image/${extension==='jpg'?'jpeg':extension}`;
    if(original.contentType!==mime)return false;
    const prepared=await prepararImagen(original.bytes,extension);
    if(!prepared)return false;
    const digest=createHash('sha256').update('amancay-webp-v2').update(prepared.principal).update(prepared.miniatura).digest('hex');
    const result={imagen:`${base}/${digest}_1600.webp`,miniatura:`${base}/${digest}_320.webp`};
    await storage.subirInmutable(buckets.catalogo,result.imagen,prepared.principal,'image/webp');
    await storage.subirInmutable(buckets.catalogo,result.miniatura,prepared.miniatura,'image/webp');
    return result;
  };
  const procesar=async(ruta:string,tiendaId:string,productoId:string):Promise<ImagenPreparada|false>=>{
    const match=/^tiendas\/([a-zA-Z0-9_-]{1,128})\/productos\/([a-zA-Z0-9_-]{1,128})\/([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\.(jpg|png|webp)$/i.exec(ruta);
    if(!match || match[1]!==tiendaId || match[2]!==productoId)return false;
    return prepararYPublicar(ruta,match[4].toLowerCase(),`catalogo/${tiendaId}/productos/${productoId}`);
  };

  const procesarTienda=async(ruta:string,tiendaId:string):Promise<ImagenPreparada|false>=>{
    const match=/^tiendas\/([a-zA-Z0-9_-]{1,128})\/tienda\/([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\.(jpg|png|webp)$/i.exec(ruta);
    if(!match || match[1]!==tiendaId)return false;
    return prepararYPublicar(ruta,match[3].toLowerCase(),`catalogo/${tiendaId}/tienda`);
  };

  const subir=async(uid:string,tiendaId:string,productoId:string,archivoId:string,contentType:string,bytes:Buffer):Promise<string>=>{
    exigir(id.test(uid),'no-autenticado','Identidad Firebase no válida.');
    exigir(id.test(tiendaId) && id.test(productoId) && uuid.test(archivoId),'datos-invalidos','La ruta de tienda, producto o archivo no es válida.');
    const extension=extensiones[contentType];
    exigir(extension!==undefined && bytes.length>0 && bytes.length<=maxBytes,'datos-invalidos','Usar una imagen JPEG, PNG o WebP de hasta 5 MiB.');
    exigir(await prepararImagen(bytes,extension),'imagen-no-autorizada','El archivo no es una imagen válida o excede los límites de procesamiento.');
    const tiendaRef=db.doc(`tiendasPrivadas/${tiendaId}`);
    const productoRef=tiendaRef.collection('productos').doc(productoId);
    const comprobar=async()=>{
      const [access,store,product]=await Promise.all([
        db.doc(`accesos/${uid}`).get(),tiendaRef.get(),productoRef.get(),
      ]);
      const a=access.data();
      exigir(a?.estado==='activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && a.tiendaId===tiendaId && store.data()?.propietarioUid===uid,'sin-permiso','La cuenta no es propietaria activa de esta tienda.');
      exigir(product.exists,'no-encontrado','Guarda primero el borrador del producto.');
    };
    await comprobar();
    const ruta=`tiendas/${tiendaId}/productos/${productoId}/${archivoId}.${extension}`;
    await storage.subirInmutable(buckets.originales,ruta,bytes,contentType);
    try {
      await db.runTransaction(async tx=>{
        const [access,store,product]=await tx.getAll(db.doc(`accesos/${uid}`),tiendaRef,productoRef);
        const a=access.data(),d=product.data();
        exigir(a?.estado==='activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && a.tiendaId===tiendaId && store.data()?.propietarioUid===uid,'sin-permiso','La cuenta perdió acceso a la tienda.');
        exigir(d,'no-encontrado','El borrador del producto ya no existe.');
        const current=Array.isArray(d.imagenes)?d.imagenes.filter((value:unknown):value is string=>typeof value==='string'):[];
        if(current.includes(ruta))return;
        exigir(current.length<5,'limite-local','Un producto puede tener hasta cinco imágenes.');
        tx.update(productoRef,{imagenes:[...current,ruta],actualizadoEn:FieldValue.serverTimestamp()});
      });
    } catch(error) {
      await storage.borrar(buckets.originales,[ruta]).catch(()=>undefined);
      throw error;
    }
    return ruta;
  };

  const leer=async(uid:string,tiendaId:string,productoId:string,indice:number):Promise<{bytes:Buffer;contentType:string}>=>{
    exigir(id.test(uid),'no-autenticado','Identidad Firebase no válida.');
    exigir(id.test(tiendaId) && id.test(productoId) && Number.isInteger(indice) && indice>=0 && indice<5,'datos-invalidos','La referencia de imagen no es válida.');
    const tiendaRef=db.doc(`tiendasPrivadas/${tiendaId}`);
    const productoRef=tiendaRef.collection('productos').doc(productoId);
    const [access,store,product]=await Promise.all([
      db.doc(`accesos/${uid}`).get(),tiendaRef.get(),productoRef.get(),
    ]);
    const a=access.data();
    exigir(a?.estado==='activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && a.tiendaId===tiendaId && store.data()?.propietarioUid===uid,'sin-permiso','Solo la propietaria activa puede ver las imágenes privadas de esta tienda.');
    exigir(product.exists,'no-encontrado','El producto ya no existe en esta tienda.');
    const rutas=Array.isArray(product.data()?.imagenes)?product.data()!.imagenes.filter((value:unknown):value is string=>typeof value==='string'):[];
    const ruta=rutas[indice];
    exigir(typeof ruta==='string','no-encontrado','No hay una imagen guardada en esa posición.');
    const patron=new RegExp(`^tiendas/${tiendaId}/productos/${productoId}/([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\\.(jpg|png|webp)$`,'i');
    const coincidencia=patron.exec(ruta);
    exigir(coincidencia,'imagen-no-autorizada','La referencia de imagen del producto no es válida.');
    const original=await storage.leerOriginal(ruta);
    exigir(original,'no-encontrado','No se encontró el archivo privado de la imagen.');
    const extension=coincidencia[2].toLowerCase();
    const contentTypeEsperado=extension==='jpg'?'image/jpeg':`image/${extension}`;
    exigir(original.contentType===contentTypeEsperado,'imagen-no-autorizada','El tipo del archivo privado no coincide con su referencia.');
    return original;
  };

  const subirTienda=async(uid:string,tiendaId:string,archivoId:string,contentType:string,bytes:Buffer):Promise<string>=>{
    exigir(id.test(uid),'no-autenticado','Identidad Firebase no válida.');
    exigir(id.test(tiendaId) && uuid.test(archivoId),'datos-invalidos','La ruta de tienda o archivo no es válida.');
    const extension=extensiones[contentType];
    exigir(extension!==undefined && bytes.length>0 && bytes.length<=maxBytes,'datos-invalidos','Usar una imagen JPEG, PNG o WebP de hasta 5 MiB.');
    exigir(await prepararImagen(bytes,extension),'imagen-no-autorizada','El archivo no es una imagen válida o excede los límites de procesamiento.');
    const accessRef=db.doc(`accesos/${uid}`),profileRef=db.doc(`emprendedoras/${uid}`),storeRef=db.doc(`tiendasPrivadas/${tiendaId}`),publicRef=db.doc(`tiendasPublicas/${tiendaId}`);
    const validarPropiedad=(access:any,profile:any,store:any)=>{
      exigir(access?.estado==='activo' && Array.isArray(access.roles) && access.roles.includes('emprendedora') && access.tiendaId===tiendaId,'sin-permiso','La cuenta no es propietaria activa de esta tienda.');
      exigir(profile?.tiendaId===tiendaId && store?.propietarioUid===uid,'sin-permiso','La tienda no pertenece a esta cuenta.');
    };
    const [accessSnap,profileSnap,storeSnap]=await Promise.all([accessRef.get(),profileRef.get(),storeRef.get()]);
    const store=storeSnap.data();
    validarPropiedad(accessSnap.data(),profileSnap.data(),store);
    exigir(storeSnap.exists,'no-encontrado','No se encontró la ficha de la tienda.');
    const versionEsperada=version(store!);
    const publicada=store!.estadoPublicacion==='publicado';
    if(publicada) {
      const publicStore=(await publicRef.get()).data();
      exigir(publicStore?.estadoPublicacion==='publicado' && publicStore.habilitada===true,'conflicto','La proyección pública de la tienda no está disponible.');
    }
    const ruta=`tiendas/${tiendaId}/tienda/${archivoId}.${extension}`;
    await storage.subirInmutable(buckets.originales,ruta,bytes,contentType);
    const derivadas=publicada?await procesarTienda(ruta,tiendaId):false;
    if(publicada)exigir(derivadas,'imagen-no-autorizada','No se pudo preparar el logo para la tienda publicada.');
    const receiptRef=storeRef.collection('operacionesImagenes').doc(archivoId);
    const confirmado=await db.runTransaction(async tx=>{
      const [access,profile,currentStore,currentPublic,receipt]=await tx.getAll(accessRef,profileRef,storeRef,publicRef,receiptRef);
      validarPropiedad(access.data(),profile.data(),currentStore.data());
      if(receipt.exists) {
        exigir(receipt.data()?.ruta===ruta,'id-reutilizado','El identificador de archivo ya se usó con otra imagen.');
        return {ruta,limpieza:receipt.data()?.limpiezaImagenes as string[]|undefined};
      }
      const current=currentStore.data();
      exigir(current,'no-encontrado','No se encontró la ficha de la tienda.');
      exigir(version(current)===versionEsperada,'conflicto','La ficha de la tienda cambió durante la carga; vuelve a intentar.');
      const stillPublished=current.estadoPublicacion==='publicado';
      exigir(stillPublished===publicada,'conflicto','El estado de publicación cambió durante la carga.');
      const logoProcesado=derivadas && typeof derivadas==='object'?derivadas:undefined;
      if(stillPublished)exigir(currentPublic.data()?.estadoPublicacion==='publicado' && currentPublic.data()?.habilitada===true && logoProcesado,'conflicto','La proyección pública cambió durante la carga.');
      const limpieza=new Set<string>();
      if(stillPublished && logoProcesado)for(const path of [currentPublic.data()?.logo,currentPublic.data()?.logoMiniatura])if(typeof path==='string' && path!==logoProcesado.imagen && path!==logoProcesado.miniatura)limpieza.add(path);
      const now=FieldValue.serverTimestamp();
      tx.update(storeRef,{logo:ruta,actualizadoEn:now});
      if(stillPublished && logoProcesado)tx.update(publicRef,{logo:logoProcesado.imagen,logoMiniatura:logoProcesado.miniatura,actualizadoEn:now});
      const rutasLimpieza=[...limpieza];
      tx.create(receiptRef,{ruta,limpiezaImagenes:rutasLimpieza,realizadoPor:uid,creadoEn:now});
      return {ruta,limpieza:rutasLimpieza};
    });
    if(confirmado.limpieza?.length)await limpiar(confirmado.limpieza);
    return confirmado.ruta;
  };

  const limpiar=async(rutas:string[])=>{
    const permitidas=[...new Set(rutas)].filter(ruta=>/^catalogo\/[a-zA-Z0-9_-]{1,128}\/(?:productos\/[a-zA-Z0-9_-]{1,128}|tienda)\/[0-9a-f]{64}_(1600|320)\.webp$/.test(ruta));
    if(permitidas.length!==new Set(rutas).size)throw new ErrorOperacion('datos-invalidos','Ruta pública de imagen inválida para retirada.');
    await storage.borrar(buckets.catalogo,permitidas);
  };
  return {procesar,procesarTienda,subir,leer,subirTienda,limpiar};
}
