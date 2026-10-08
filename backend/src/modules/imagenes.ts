import type { bucket as bucketLocal } from '../config/emulador.ts';
import { ErrorOperacion } from './publicacion.ts';

const maxBytes = 5 * 1024 * 1024;
const tipos: Record<string, string> = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

// Comprueba metadatos y firma del archivo; no sustituye decodificación,
// eliminación de EXIF o análisis de contenido para el despliegue definitivo.
export function crearVerificadorImagenes(bucket: typeof bucketLocal) {
  return async (ruta: string, tiendaId: string, productoId: string): Promise<boolean> => {
    const match = /^tiendas\/([a-zA-Z0-9_-]{1,128})\/productos\/([a-zA-Z0-9_-]{1,128})\/([a-zA-Z0-9_-]{1,128})\.(jpg|png|webp)$/.exec(ruta);
    if (!match || match[1] !== tiendaId || match[2] !== productoId) return false;
    try {
      const file = bucket.file(ruta);
      const [metadata] = await file.getMetadata();
      const size = Number(metadata.size);
      if (!Number.isSafeInteger(size) || size <= 0 || size > maxBytes || metadata.contentType !== tipos[match[4]]) return false;
      if (metadata.contentEncoding && metadata.contentEncoding !== 'identity') return false;
      const chunks: Buffer[] = [];
      for await (const chunk of file.createReadStream({ start: 0, end: 11, validation: false })) chunks.push(Buffer.from(chunk));
      const bytes = Buffer.concat(chunks);
      if (match[4] === 'png') return bytes.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'));
      if (match[4] === 'jpg') return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
      return bytes.length >= 12 && bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WEBP';
    } catch (error) {
      if (Number((error as { code?: unknown }).code) === 404) return false;
      throw new ErrorOperacion('pendiente', 'No se pudo validar Storage. Reintentar más tarde.');
    }
  };
}
