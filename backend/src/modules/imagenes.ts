import { createHash } from 'node:crypto';
import sharp from 'sharp';
import type { bucket as bucketLocal } from '../config/emulador.ts';
import { ErrorOperacion } from './publicacion.ts';

const copiasEnCurso=new Map<string,Promise<void>>();
const maxBytes=5*1024*1024;
const tipos: Record<string,string>={jpg:'image/jpeg',png:'image/png',webp:'image/webp'};
export type ImagenPreparada={imagen:string;miniatura:string};

async function decodificar(bucket:typeof bucketLocal,ruta:string,tienda:string,producto:string) {
  const match=/^tiendas\/([a-zA-Z0-9_-]{1,128})\/productos\/([a-zA-Z0-9_-]{1,128})\/([a-zA-Z0-9_-]{1,128})\.(jpg|png|webp)$/.exec(ruta);
  if(!match || match[1]!==tienda || match[2]!==producto) return null;
  let bytes:Buffer;
  try {
    const file=bucket.file(ruta),[metadata]=await file.getMetadata(),size=Number(metadata.size);
    if(!Number.isSafeInteger(size) || size<=0 || size>maxBytes || metadata.contentType!==tipos[match[4]] || (metadata.contentEncoding && metadata.contentEncoding!=='identity')) return null;
    const chunks:Buffer[]=[];let total=0;
    for await(const chunk of file.createReadStream({start:0,end:maxBytes,validation:false})) {
      total+=chunk.length;if(total>maxBytes)return null;chunks.push(Buffer.from(chunk));
    }
    bytes=Buffer.concat(chunks);if(bytes.length!==size)return null;
  } catch(error) {
    if(Number((error as {code?:unknown}).code)===404)return null;
    throw new ErrorOperacion('pendiente','No se pudo leer Storage. Conservar solicitud y reintentar.');
  }
  const extension=match[4];
  if(extension==='png') {
    if(!bytes.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex')))return null;
    for(let offset=8;offset+12<=bytes.length;) {
      const length=bytes.readUInt32BE(offset),tipo=bytes.toString('ascii',offset+4,offset+8);
      if(tipo==='acTL')return null; // APNG, incluso si el decodificador solo ve la primera imagen.
      if(tipo==='IEND')break;
      offset+=12+length;
    }
  } else if(extension==='jpg') {
    if(bytes.length<3 || bytes[0]!==0xff || bytes[1]!==0xd8 || bytes[2]!==0xff)return null;
  } else if(bytes.toString('ascii',0,4)!=='RIFF' || bytes.toString('ascii',8,12)!=='WEBP')return null;
  try {
    const image=sharp(bytes,{failOn:'warning',limitInputPixels:20_000_000});
    const metadata=await image.metadata();
    if(metadata.format!==(match[4]==='jpg'?'jpeg':match[4]) || (metadata.pages??1)!==1 || !metadata.width || !metadata.height || metadata.width>10000 || metadata.height>10000)return null;
    // Decodificar, orientar y volver a codificar elimina metadatos y detecta
    // archivos truncados. No se conservan EXIF, GPS, XMP ni ICC.
    const principal=await image.rotate().resize({width:1600,height:1600,fit:'inside',withoutEnlargement:true}).webp({quality:82}).timeout({seconds:5}).toBuffer();
    const miniatura=await sharp(principal).resize({width:320,height:320,fit:'inside',withoutEnlargement:true}).webp({quality:78}).timeout({seconds:5}).toBuffer();
    if(principal.length>maxBytes || miniatura.length>maxBytes)return null;
    return {principal,miniatura};
  } catch {return null;}
}

export function crearVerificadorImagenes(bucket:typeof bucketLocal) {
  return async(ruta:string,tienda:string,producto:string):Promise<boolean> => Boolean(await decodificar(bucket,ruta,tienda,producto));
}

export function crearProcesadorImagenes(bucket:typeof bucketLocal) {
  return async(ruta:string,tienda:string,producto:string):Promise<ImagenPreparada|false> => {
    const image=await decodificar(bucket,ruta,tienda,producto);if(!image)return false;
    const digest=createHash('sha256').update('amancay-webp-v1').update(image.principal).update(image.miniatura).digest('hex');
    const base=`catalogo/${tienda}/productos/${producto}/${digest}`;
    const result={imagen:`${base}_1600.webp`,miniatura:`${base}_320.webp`};
    for(const [path,bytes] of [[result.imagen,image.principal],[result.miniatura,image.miniatura]] as const) {
      try {
        let trabajo=copiasEnCurso.get(path);
        if(!trabajo) {
          trabajo=(async()=>{
            const file=bucket.file(path);
            if((await file.exists())[0]) return;
            await file.save(bytes,{resumable:false,preconditionOpts:{ifGenerationMatch:0},metadata:{contentType:'image/webp',cacheControl:'private, max-age=0, no-store'}});
          })();
          copiasEnCurso.set(path,trabajo);
        }
        try {await trabajo;} finally {if(copiasEnCurso.get(path)===trabajo)copiasEnCurso.delete(path);}
      } catch(error) {
        // Solo el servidor crea estas rutas por contenido. Un reintento reutiliza
        // la copia existente, sin sobrescribir ni entregar URLs con token.
        if(Number((error as {code?:unknown}).code)!==412) throw new ErrorOperacion('pendiente','No se pudo preparar la imagen. Conservar solicitud y reintentar.');
      }
    }
    return result;
  };
}
