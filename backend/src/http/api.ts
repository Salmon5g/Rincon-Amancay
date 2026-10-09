import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Auth } from 'firebase-admin/auth';
import { ErrorOperacion } from '../modules/publicacion.ts';
import type { crearPublicacion, Accion, Solicitud } from '../modules/publicacion.ts';
import type { crearCuentas } from '../modules/cuentas.ts';
import type { crearProductos, AccionProducto } from '../modules/productos.ts';
import type { crearStock } from '../modules/stock.ts';

const acciones: Accion[] = ['publicarTienda', 'retirarTienda', 'publicarProducto', 'retirarProducto'];
const status: Record<string, number> = {
  'no-autenticado': 401, 'sin-permiso': 403, 'datos-invalidos': 400,
  'no-encontrado': 404, conflicto: 409, 'id-reutilizado': 409,
  'imagen-requerida': 422, 'imagen-no-autorizada': 422,
  'no-publicable': 422, 'tienda-no-publicada': 409, pendiente: 503, 'limite-local': 422,
};
class ErrorHttp extends Error {
  status: number; code: string;
  constructor(status: number, code: string, message: string) { super(message); this.status = status; this.code = code; }
}
function json(res: ServerResponse, code: number, data: unknown) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(data));
}
async function body(req: IncomingMessage): Promise<Record<string, unknown>> {
  if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') throw new ErrorHttp(415, 'tipo-no-admitido', 'Usar application/json.');
  if (req.headers['content-encoding'] && req.headers['content-encoding'] !== 'identity') throw new ErrorHttp(415, 'tipo-no-admitido', 'No se admite contenido comprimido.');
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 16384) throw new ErrorHttp(413, 'cuerpo-demasiado-grande', 'Máximo 16 KiB.');
    chunks.push(chunk);
  }
  let input: unknown;
  try { input = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ErrorHttp(400, 'datos-invalidos', 'JSON inválido.'); }
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new ErrorHttp(400, 'datos-invalidos', 'Se requiere un objeto.');
  const data = input as Record<string, unknown>;
  return data;
}
function solicitudPublicacion(data: Record<string, unknown>, accion: Accion): Solicitud {
  const required = ['tiendaId', 'operacionId', 'versionEsperada', ...(accion.endsWith('Producto') ? ['productoId'] : [])];
  if (Object.keys(data).some(k => !required.includes(k)) || required.some(k => typeof data[k] !== 'string')) throw new ErrorHttp(400, 'datos-invalidos', 'Campos ausentes o no permitidos. No enviar uid ni roles.');
  if (!/^[0-9]{1,12}:[0-9]{1,9}$/.test(data.versionEsperada as string)) throw new ErrorHttp(400, 'datos-invalidos', 'Versión inválida.');
  return data as Solicitud;
}

export function crearApi(auth: Auth, ejecutar: ReturnType<typeof crearPublicacion>, alta?: ReturnType<typeof crearCuentas>, productos?: ReturnType<typeof crearProductos>, stock?: ReturnType<typeof crearStock>) {
  const origins = new Set(['http://localhost:3000', 'http://127.0.0.1:3000']);
  const server = createServer(async (req, res) => {
    try {
      const origin = req.headers.origin;
      if (origin && !origins.has(origin)) throw new ErrorHttp(403, 'origen-no-permitido', 'Origen no permitido.');
      if (origin) { res.setHeader('Access-Control-Allow-Origin', origin); res.setHeader('Vary', 'Origin'); }
      if (req.method === 'GET' && req.url === '/health') return json(res, 200, { estado: 'ok', entorno: 'emulador', proyecto: 'demo-rincon-amancay' });
      const action = req.url?.match(/^\/api\/v1\/(publicarTienda|retirarTienda|publicarProducto|retirarProducto)$/)?.[1] as Accion | undefined;
      const esAlta = req.url === '/api/v1/altaEmprendedora' && alta !== undefined;
      const esStock = req.url === '/api/v1/ajustarStock' && stock !== undefined;
      const accionProducto = productos && req.url?.match(/^\/api\/v1\/(crearProducto|editarProducto)$/)?.[1] as AccionProducto | undefined;
      if (!esAlta && !esStock && !accionProducto && (!action || !acciones.includes(action))) throw new ErrorHttp(404, 'ruta-no-encontrada', 'Ruta inexistente.');
      if (req.method === 'OPTIONS') {
        res.writeHead(204, { 'Access-Control-Allow-Methods': 'POST, OPTIONS', 'Access-Control-Allow-Headers': 'Authorization, Content-Type', 'Cache-Control': 'no-store' });
        return res.end();
      }
      if (req.method !== 'POST') { res.setHeader('Allow', 'POST, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar POST.'); }
      const match = req.headers.authorization?.match(/^Bearer ([^\s]+)$/i);
      if (!match) throw new ErrorHttp(401, 'no-autenticado', 'Se requiere un ID token de Firebase.');
      let uid: string;
      try { uid = (await auth.verifyIdToken(match[1], true)).uid; }
      catch (error) {
        const code = (error as {code?: string}).code;
        if (code && ['auth/id-token-expired', 'auth/id-token-revoked', 'auth/argument-error', 'auth/invalid-id-token', 'auth/user-disabled', 'auth/user-not-found'].includes(code)) {
          throw new ErrorHttp(401, 'no-autenticado', 'Sesión inválida, vencida o deshabilitada.');
        }
        throw new ErrorHttp(503, 'autenticacion-no-disponible', 'No se pudo comprobar la sesión.');
      }
      const input = await body(req);
      const result = esAlta ? await alta!({uid}, input)
        : esStock ? await stock!({uid}, input)
        : accionProducto ? await productos!(accionProducto, {uid}, input)
        : await ejecutar(action!, { uid }, solicitudPublicacion(input, action!));
      json(res, 200, { datos: result });
    } catch (error) {
      if (res.destroyed) return;
      if (error instanceof ErrorHttp) return json(res, error.status, { error: { code: error.code, mensaje: error.message } });
      if (error instanceof ErrorOperacion) return json(res, status[error.code] ?? 400, { error: { code: error.code, mensaje: error.message } });
      // No devolver stack, tokens, credenciales o mensajes internos del SDK.
      json(res, 500, { error: { code: 'error-interno', mensaje: 'No se pudo completar la operación.' } });
    }
  });
  server.requestTimeout = 15000;
  server.headersTimeout = 10000;
  return server;
}
