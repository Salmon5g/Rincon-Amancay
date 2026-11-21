import { validarConfigHttp, httpLocal } from '../config/http.ts';
import type { ConfigHttp } from '../config/http.ts';
import { accionesCuentas } from '../modules/ciclo-cuentas.ts';
import type { crearCicloCuentas, AccionCuenta } from '../modules/ciclo-cuentas.ts';
import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Auth } from 'firebase-admin/auth';
import { ErrorOperacion } from '../modules/publicacion.ts';
import type { crearPublicacion, Accion, Solicitud } from '../modules/publicacion.ts';
import type { crearCuentas } from '../modules/cuentas.ts';
import type { crearProductos, AccionProducto } from '../modules/productos.ts';
import type { crearStock } from '../modules/stock.ts';
import type { crearDesactivacion } from '../modules/desactivacion.ts';
import { accionesCatalogo } from '../modules/catalogos.ts';
import type { AccionCatalogo, crearCatalogos } from '../modules/catalogos.ts';
import { accionesAsociacion } from '../modules/asociaciones.ts';
import type { AccionAsociacion, crearAsociaciones } from '../modules/asociaciones.ts';
import { accionesAdministracion } from '../modules/administracion-plataforma.ts';
import type { AccionAdministracion, crearAdministracionPlataforma } from '../modules/administracion-plataforma.ts';
import { accionesFeria } from '../modules/ferias.ts';
import type { AccionFeria, crearFerias } from '../modules/ferias.ts';
import type { crearEdicionTiendaEmprendedora } from '../modules/tienda-emprendedora.ts';
import { accionesVenta } from '../modules/ventas.ts';
import type { AccionVenta, crearVentas } from '../modules/ventas.ts';
import type { SolicitudConsultaPublica } from '../modules/consultas-comprador.ts';

const acciones: Accion[] = ['publicarTienda', 'retirarTienda', 'publicarProducto', 'retirarProducto'];
const status: Record<string, number> = {
  'no-autenticado': 401, 'sin-permiso': 403, 'datos-invalidos': 400,
  'no-encontrado': 404, conflicto: 409, 'id-reutilizado': 409,
  'correo-ocupado': 409,
  'sin-destinatario': 422,
  'servicio-no-configurado': 503,
  'envio-incierto': 503,
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
async function cuerpoImagen(req: IncomingMessage): Promise<Buffer> {
  const contentType = req.headers['content-type']?.split(';')[0].trim();
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(contentType ?? '')) throw new ErrorHttp(415, 'tipo-no-admitido', 'Usar una imagen JPEG, PNG o WebP.');
  if (req.headers['content-encoding'] && req.headers['content-encoding'] !== 'identity') throw new ErrorHttp(415, 'tipo-no-admitido', 'No se admite contenido comprimido.');
  const declared = Number(req.headers['content-length'] ?? 0);
  if (declared > 5 * 1024 * 1024) throw new ErrorHttp(413, 'cuerpo-demasiado-grande', 'Máximo 5 MiB.');
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 5 * 1024 * 1024) throw new ErrorHttp(413, 'cuerpo-demasiado-grande', 'Máximo 5 MiB.');
    chunks.push(Buffer.from(chunk));
  }
  if (size === 0) throw new ErrorHttp(400, 'datos-invalidos', 'La imagen está vacía.');
  return Buffer.concat(chunks);
}
function solicitudPublicacion(data: Record<string, unknown>, accion: Accion): Solicitud {
  const required = ['tiendaId', 'operacionId', 'versionEsperada', ...(accion.endsWith('Producto') ? ['productoId'] : [])];
  if (Object.keys(data).some(k => !required.includes(k)) || required.some(k => typeof data[k] !== 'string')) throw new ErrorHttp(400, 'datos-invalidos', 'Campos ausentes o no permitidos. No enviar uid ni roles.');
  if (!/^[0-9]{1,12}:[0-9]{1,9}$/.test(data.versionEsperada as string)) throw new ErrorHttp(400, 'datos-invalidos', 'Versión inválida.');
  return data as Solicitud;
}

export function crearApi(auth: Auth, ejecutar: ReturnType<typeof crearPublicacion>, alta?: ReturnType<typeof crearCuentas>, productos?: ReturnType<typeof crearProductos>, stock?: ReturnType<typeof crearStock>, desactivar?: ReturnType<typeof crearDesactivacion>, ciclo?: ReturnType<typeof crearCicloCuentas>, sesion?: (uid: string, authTime: number) => Promise<boolean>, configHttp: ConfigHttp = httpLocal, catalogos?: ReturnType<typeof crearCatalogos>, asociaciones?: ReturnType<typeof crearAsociaciones>, administracion?: ReturnType<typeof crearAdministracionPlataforma>, ferias?: ReturnType<typeof crearFerias>, editarTiendaEmprendedora?: ReturnType<typeof crearEdicionTiendaEmprendedora>, imagenes?: { subir(uid: string, tiendaId: string, productoId: string, archivoId: string, contentType: string, bytes: Buffer, reemplazarIndice?: number): Promise<string>; leer?: (uid: string, tiendaId: string, productoId: string, indice: number) => Promise<{ bytes: Buffer; contentType: string }>; subirTienda?: (uid: string, tiendaId: string, archivoId: string, contentType: string, bytes: Buffer) => Promise<string>; subirLogoAsociacion?: (uid: string, archivoId: string, contentType: string, bytes: Buffer) => Promise<string>; subirImagenFeria?: (uid: string, archivoId: string, contentType: string, bytes: Buffer) => Promise<string> }, ventas?: ReturnType<typeof crearVentas>, guardarConsulta?: (solicitud: SolicitudConsultaPublica) => Promise<unknown>, leerRespuestaConsulta?: (tiendaId: string, operacionId: string) => Promise<unknown>, responderConsulta?: (uid: string, solicitud: { tiendaId: string; consultaId: string; respuesta: string }) => Promise<unknown>, leerMuestraFeriaPublica?: (feriaId: string) => Promise<unknown>, leerFeriasPublicas?: () => Promise<unknown>, leerPoliticaNavegacionPublica?: () => Promise<unknown>) {
  if (ciclo && !sesion) throw new Error('El ciclo de cuentas requiere validar el corte de sesión.');
  const config = validarConfigHttp(configHttp);
  const origins = new Set(config.origenes);
  const enviosConsulta = new Map<string, number[]>();
  const lecturasRespuesta = new Map<string, number[]>();
  const lecturasFeriaPublica = new Map<string, number[]>();
  const server = createServer(async (req, res) => {
    try {
      const origin = req.headers.origin;
      if (origin && !origins.has(origin)) throw new ErrorHttp(403, 'origen-no-permitido', 'Origen no permitido.');
      if (origin) { res.setHeader('Access-Control-Allow-Origin', origin); res.setHeader('Vary', 'Origin'); }
      if (req.method === 'GET' && req.url === '/health') return json(res, 200, { estado: 'ok', entorno: config.entorno, proyecto: config.proyecto });
      if (req.url === '/api/v1/configuracionPublica/navegacion' && leerPoliticaNavegacionPublica) {
        if (req.method === 'OPTIONS') {
          res.writeHead(204, { 'Access-Control-Allow-Methods': 'GET, OPTIONS', 'Access-Control-Allow-Headers': 'Accept', 'Cache-Control': 'no-store' });
          return res.end();
        }
        if (req.method !== 'GET') { res.setHeader('Allow', 'GET, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar GET.'); }
        const ahora = Date.now();
        const ip = req.socket.remoteAddress ?? 'desconocida';
        const recientes = (lecturasFeriaPublica.get(ip) ?? []).filter(t => ahora - t < 60_000);
        if (recientes.length >= 30) throw new ErrorHttp(429, 'limite-consultas', 'Espera un momento antes de volver a consultar el acceso.');
        recientes.push(ahora);
        lecturasFeriaPublica.set(ip, recientes);
        return json(res, 200, { datos: await leerPoliticaNavegacionPublica() });
      }
      if (req.url === '/api/v1/feriasPublicas' && leerFeriasPublicas) {
        if (req.method === 'OPTIONS') {
          res.writeHead(204, { 'Access-Control-Allow-Methods': 'GET, OPTIONS', 'Access-Control-Allow-Headers': 'Accept', 'Cache-Control': 'no-store' });
          return res.end();
        }
        if (req.method !== 'GET') { res.setHeader('Allow', 'GET, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar GET.'); }
        const ahora = Date.now();
        const ip = req.socket.remoteAddress ?? 'desconocida';
        const recientes = (lecturasFeriaPublica.get(ip) ?? []).filter(t => ahora - t < 60_000);
        if (recientes.length >= 30) throw new ErrorHttp(429, 'limite-consultas', 'Espera un momento antes de volver a cargar las ferias.');
        recientes.push(ahora);
        lecturasFeriaPublica.set(ip, recientes);
        return json(res, 200, { datos: await leerFeriasPublicas() });
      }
      const rutaMuestraFeria = req.url?.match(/^\/api\/v1\/feriasPublicas\/([a-zA-Z0-9_-]{1,128})$/);
      if (rutaMuestraFeria && leerMuestraFeriaPublica) {
        if (req.method === 'OPTIONS') {
          res.writeHead(204, { 'Access-Control-Allow-Methods': 'GET, OPTIONS', 'Access-Control-Allow-Headers': 'Accept', 'Cache-Control': 'no-store' });
          return res.end();
        }
        if (req.method !== 'GET') { res.setHeader('Allow', 'GET, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar GET.'); }
        const ahora = Date.now();
        const ip = req.socket.remoteAddress ?? 'desconocida';
        const recientes = (lecturasFeriaPublica.get(ip) ?? []).filter(t => ahora - t < 60_000);
        if (recientes.length >= 30) throw new ErrorHttp(429, 'limite-consultas', 'Espera un momento antes de volver a cargar los participantes.');
        recientes.push(ahora);
        lecturasFeriaPublica.set(ip, recientes);
        if (lecturasFeriaPublica.size > 1000) for (const [clave, tiempos] of lecturasFeriaPublica) if (!tiempos.some(t => ahora - t < 60_000)) lecturasFeriaPublica.delete(clave);
        return json(res, 200, { datos: await leerMuestraFeriaPublica(rutaMuestraFeria[1]) });
      }
      const rutaRespuesta = req.url?.match(/^\/api\/v1\/consultasPublicas\/([a-zA-Z0-9_-]{1,128})\/([0-9a-fA-F-]{36})$/);
      if (req.method === 'GET' && rutaRespuesta && leerRespuestaConsulta) {
        const ahora = Date.now();
        const ip = req.socket.remoteAddress ?? 'desconocida';
        const recientes = (lecturasRespuesta.get(ip) ?? []).filter(t => ahora - t < 60_000);
        if (recientes.length >= 30) throw new ErrorHttp(429, 'limite-consultas', 'Espera un momento antes de volver a consultar el estado.');
        recientes.push(ahora);
        lecturasRespuesta.set(ip, recientes);
        if (lecturasRespuesta.size > 1000) for (const [clave, tiempos] of lecturasRespuesta) if (!tiempos.some(t => ahora - t < 60_000)) lecturasRespuesta.delete(clave);
        const resultado = await leerRespuestaConsulta(rutaRespuesta[1], rutaRespuesta[2]);
        return json(res, 200, { datos: resultado });
      }
      if (req.url === '/api/v1/consultasPublicas' && guardarConsulta) {
        if (req.method === 'OPTIONS') {
          res.writeHead(204, { 'Access-Control-Allow-Methods': 'POST, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type', 'Cache-Control': 'no-store' });
          return res.end();
        }
        if (req.method !== 'POST') { res.setHeader('Allow', 'POST, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar POST.'); }
        const ahora = Date.now();
        const ip = req.socket.remoteAddress ?? 'desconocida';
        const recientes = (enviosConsulta.get(ip) ?? []).filter(t => ahora - t < 10 * 60_000);
        if (recientes.length >= 5) throw new ErrorHttp(429, 'limite-consultas', 'Espera unos minutos antes de enviar otra consulta.');
        recientes.push(ahora); enviosConsulta.set(ip, recientes);
        if (enviosConsulta.size > 1000) for (const [clave, tiempos] of enviosConsulta) if (!tiempos.some(t => ahora - t < 10 * 60_000)) enviosConsulta.delete(clave);
        const input = await body(req);
        const permitidos = ['tiendaId', 'operacionId', 'nombre', 'correo', 'mensaje', 'producto'];
        if (Object.keys(input).some(k => !permitidos.includes(k)) ||
          !/^[a-zA-Z0-9_-]{1,128}$/.test(String(input.tiendaId ?? '')) ||
          !/^[0-9a-fA-F-]{36}$/.test(String(input.operacionId ?? '')) ||
          typeof input.nombre !== 'string' || input.nombre.trim().length < 2 || input.nombre.length > 100 ||
          typeof input.correo !== 'string' || input.correo.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.correo) ||
          typeof input.mensaje !== 'string' || input.mensaje.trim().length < 5 || input.mensaje.length > 2000 ||
          (input.producto !== undefined && (typeof input.producto !== 'string' || input.producto.length > 160))) {
          throw new ErrorHttp(400, 'datos-invalidos', 'Revisa los datos del formulario.');
        }
        const datos: SolicitudConsultaPublica = { tiendaId: input.tiendaId as string, operacionId: input.operacionId as string, nombre: input.nombre.trim(), correo: input.correo.trim(), mensaje: input.mensaje.trim(), ...(typeof input.producto === 'string' && input.producto.trim() ? { producto: input.producto.trim() } : {}) };
        const result = await guardarConsulta(datos);
        return json(res, 200, { datos: result });
      }
      const action = req.url?.match(/^\/api\/v1\/(publicarTienda|retirarTienda|publicarProducto|retirarProducto)$/)?.[1] as Accion | undefined;
      const accionCuenta = ciclo && accionesCuentas.find(a => req.url === `/api/v1/${a}`) as AccionCuenta | undefined;
      const esAlta = req.url === '/api/v1/altaEmprendedora' && alta !== undefined;
      const esStock = req.url === '/api/v1/ajustarStock' && stock !== undefined;
      const esDesactivacion = req.url === '/api/v1/desactivarEmprendedora' && desactivar !== undefined;
      const accionProducto = productos && req.url?.match(/^\/api\/v1\/(crearProducto|editarProducto|eliminarProducto|asignarImagenVariante|cambiarDisponibilidadProductos)$/)?.[1] as AccionProducto | undefined;
      const accionCatalogo = catalogos ? accionesCatalogo.find(a => req.url === `/api/v1/${a}`) as AccionCatalogo | undefined : undefined;
      const accionAsociacion = asociaciones ? accionesAsociacion.find(a => req.url === `/api/v1/${a}`) as AccionAsociacion | undefined : undefined;
      const accionAdministracion = administracion ? accionesAdministracion.find(a => req.url === `/api/v1/${a}`) as AccionAdministracion | undefined : undefined;
      const accionFeria = ferias ? accionesFeria.find(a => req.url === `/api/v1/${a}`) as AccionFeria | undefined : undefined;
      const esEdicionTiendaEmprendedora = req.url === '/api/v1/editarTiendaEmprendedora' && editarTiendaEmprendedora !== undefined;
      const esRespuestaConsulta = req.url === '/api/v1/responderConsulta' && responderConsulta !== undefined;
      const accionVenta = ventas ? accionesVenta.find(a => req.url === `/api/v1/${a}`) as AccionVenta | undefined : undefined;
      const subidaImagen = req.url?.match(/^\/api\/v1\/tiendas\/([a-zA-Z0-9_-]{1,128})\/productos\/([a-zA-Z0-9_-]{1,128})\/imagenes$/);
      const lecturaImagen = req.url?.match(/^\/api\/v1\/tiendas\/([a-zA-Z0-9_-]{1,128})\/productos\/([a-zA-Z0-9_-]{1,128})\/imagenes\/([0-4])$/);
      const subidaLogo = req.url?.match(/^\/api\/v1\/tiendas\/([a-zA-Z0-9_-]{1,128})\/logo$/);
      const subidaLogoAsociacion = req.url === '/api/v1/asociaciones/logo';
      const subidaImagenFeria = req.url === '/api/v1/ferias/imagen';
      const esSubidaImagen = Boolean(subidaImagen && imagenes);
      const esLecturaImagen = Boolean(lecturaImagen && imagenes?.leer);
      const esSubidaLogo = Boolean(subidaLogo && imagenes?.subirTienda);
      const esSubidaLogoAsociacion = Boolean(subidaLogoAsociacion && imagenes?.subirLogoAsociacion);
      const esSubidaImagenFeria = Boolean(subidaImagenFeria && imagenes?.subirImagenFeria);
      if (!accionCuenta && !esAlta && !esStock && !esDesactivacion && !accionProducto && !accionCatalogo && !accionAsociacion && !accionAdministracion && !accionFeria && !esEdicionTiendaEmprendedora && !esRespuestaConsulta && !accionVenta && !esSubidaImagen && !esLecturaImagen && !esSubidaLogo && !esSubidaLogoAsociacion && !esSubidaImagenFeria && (!action || !acciones.includes(action))) throw new ErrorHttp(404, 'ruta-no-encontrada', 'Ruta inexistente.');
      if (req.method === 'OPTIONS') {
        res.writeHead(204, { 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Authorization, Content-Type, X-Archivo-Id, X-Reemplazar-Indice', 'Cache-Control': 'no-store' });
        return res.end();
      }
      if (esLecturaImagen && req.method !== 'GET') { res.setHeader('Allow', 'GET, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar GET.'); }
      if (!esLecturaImagen && req.method !== 'POST') { res.setHeader('Allow', 'POST, OPTIONS'); throw new ErrorHttp(405, 'metodo-no-admitido', 'Usar POST.'); }
      const match = req.headers.authorization?.match(/^Bearer ([^\s]+)$/i);
      if (!match) throw new ErrorHttp(401, 'no-autenticado', 'Se requiere un ID token de Firebase.');
      let uid: string, authTime: number;
      try { const token = await auth.verifyIdToken(match[1], true); uid = token.uid; authTime = token.auth_time; }
      catch (error) {
        const code = (error as { code?: string }).code;
        if (code && ['auth/id-token-expired', 'auth/id-token-revoked', 'auth/argument-error', 'auth/invalid-id-token', 'auth/user-disabled', 'auth/user-not-found'].includes(code)) {
          throw new ErrorHttp(401, 'no-autenticado', 'Sesión inválida, vencida o deshabilitada.');
        }
        throw new ErrorHttp(503, 'autenticacion-no-disponible', 'No se pudo comprobar la sesión.');
      }
      if (sesion && !await sesion(uid, authTime)) throw new ErrorHttp(401, 'no-autenticado', 'Iniciar una nueva sesión después de la reactivación.');
      if (esLecturaImagen) {
        const imagen = await imagenes!.leer!(uid, lecturaImagen![1], lecturaImagen![2], Number(lecturaImagen![3]));
        res.writeHead(200, {
          'Content-Type': imagen.contentType,
          'Content-Length': imagen.bytes.length,
          'Cache-Control': 'private, no-store',
          'X-Content-Type-Options': 'nosniff',
        });
        return res.end(imagen.bytes);
      }
      if (esSubidaImagen || esSubidaLogo || esSubidaLogoAsociacion || esSubidaImagenFeria) {
        const archivoId = req.headers['x-archivo-id'];
        if (typeof archivoId !== 'string' || archivoId.length > 64) throw new ErrorHttp(400, 'datos-invalidos', 'Enviar un X-Archivo-Id UUID para que el reintento sea idempotente.');
        const indiceHeader = req.headers['x-reemplazar-indice'];
        if (indiceHeader !== undefined && (typeof indiceHeader !== 'string' || !/^[0-4]$/.test(indiceHeader))) throw new ErrorHttp(400, 'datos-invalidos', 'X-Reemplazar-Indice debe ser una posición entre 0 y 4.');
        const reemplazarIndice = typeof indiceHeader === 'string' ? Number(indiceHeader) : undefined;
        const bytes = await cuerpoImagen(req);
        const contentType = req.headers['content-type']!.split(';')[0].trim();
        const ruta = esSubidaImagen
          ? await imagenes!.subir(uid, subidaImagen![1], subidaImagen![2], archivoId, contentType, bytes, reemplazarIndice)
          : esSubidaLogoAsociacion
            ? await imagenes!.subirLogoAsociacion!(uid, archivoId, contentType, bytes)
            : esSubidaImagenFeria
              ? await imagenes!.subirImagenFeria!(uid, archivoId, contentType, bytes)
              : await imagenes!.subirTienda!(uid, subidaLogo![1], archivoId, contentType, bytes);
        return json(res, 200, { datos: { ruta } });
      }
      const input = await body(req);
      if (esRespuestaConsulta) {
        if (Object.keys(input).some(k => !['tiendaId', 'consultaId', 'respuesta'].includes(k)) ||
          typeof input.tiendaId !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(input.tiendaId) ||
          typeof input.consultaId !== 'string' || !/^[0-9a-fA-F-]{36}$/.test(input.consultaId) ||
          typeof input.respuesta !== 'string' || input.respuesta.trim().length < 1 || input.respuesta.length > 2000) {
          throw new ErrorHttp(400, 'datos-invalidos', 'Revisa el texto de la respuesta.');
        }
        const result = await responderConsulta!(uid, { tiendaId: input.tiendaId, consultaId: input.consultaId, respuesta: input.respuesta });
        return json(res, 200, { datos: result });
      }
      const result = accionCuenta ? await ciclo!(accionCuenta, { uid }, input) : accionCatalogo ? await catalogos!(accionCatalogo, { uid }, input) : accionAsociacion ? await asociaciones!(accionAsociacion, { uid }, input) : accionAdministracion ? await administracion!(accionAdministracion, { uid }, input) : accionFeria ? await ferias!(accionFeria, { uid }, input) : esEdicionTiendaEmprendedora ? await editarTiendaEmprendedora!({ uid }, input) : esAlta ? await alta!({ uid }, input)
        : esDesactivacion ? await desactivar!({ uid }, input)
          : accionVenta ? await ventas!(accionVenta, { uid }, input)
            : esStock ? await stock!({ uid }, input)
              : accionProducto ? await productos!(accionProducto, { uid }, input)
                : await ejecutar(action!, { uid }, solicitudPublicacion(input, action!));
      json(res, 200, { datos: result });
    } catch (error) {
      if (res.destroyed) return;
      if (error instanceof ErrorHttp) return json(res, error.status, { error: { code: error.code, mensaje: error.message } });
      if (error instanceof ErrorOperacion) return json(res, status[error.code] ?? 400, { error: { code: error.code, mensaje: error.message } });
      if (error instanceof ErrorOperacion) return json(res, status[error.code] ?? 400, { error: { code: error.code, mensaje: error.message } });
      // No devolver stack, tokens, credenciales o mensajes internos del SDK.
      const detalle = error instanceof Error ? error.message.slice(0, 300) : 'Error desconocido.';
      const codigoOriginal = error && typeof error === 'object' && 'code' in error ? error.code : undefined;
      const codigo = typeof codigoOriginal === 'string' || typeof codigoOriginal === 'number'
        ? String(codigoOriginal).slice(0, 80)
        : undefined;
      console.error('[api] error interno', { ruta: req.url, nombre: error instanceof Error ? error.name : 'Error', codigo, detalle });
      if (codigo === '8' || codigo === 'resource-exhausted' || detalle.includes('RESOURCE_EXHAUSTED')) {
        return json(res, 503, { error: { code: 'cuota-firestore', mensaje: 'Firestore alcanzó una cuota o límite temporal. Conserva esta misma solicitud y revisa el uso de Firestore antes de reintentarla.' } });
      }
      json(res, 500, { error: { code: 'error-interno', mensaje: 'No se pudo completar la operación.' } });
    }
  });
  server.requestTimeout = 45000;
  server.headersTimeout = 10000;
  return server;
}
