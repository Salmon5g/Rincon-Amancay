import { readFileSync, writeFileSync } from 'node:fs';
import { applicationDefault } from 'firebase-admin/app';

const credencial = applicationDefault();
const token = await credencial.getAccessToken();
const autorizacion = { Authorization: `Bearer ${token.access_token}`, 'x-goog-user-project': 'rincon-amancay' };
const respuesta = await fetch('https://apikeys.googleapis.com/v2/projects/rincon-amancay/locations/global/keys/9f1564b7-8520-48d5-8d1f-03c8fff12f6f/keyString', { headers: autorizacion });
const datos = await respuesta.json() as { keyString?: string };
if (!datos.keyString) throw new Error('No se pudo leer la clave del navegador.');

const ruta = '../web/.env';
const lineas = readFileSync(ruta, 'utf8').split(/\r?\n/).map(linea => {
  if (linea.startsWith('NUXT_PUBLIC_FIREBASE_API_KEY=')) return `NUXT_PUBLIC_FIREBASE_API_KEY=${datos.keyString}`;
  if (linea.startsWith('NUXT_PUBLIC_FIREBASE_APP_ID=')) return 'NUXT_PUBLIC_FIREBASE_APP_ID=1:980190819335:web:f9d4dabed7a776f4d5a6a2';
  return linea;
});
writeFileSync(ruta, lineas.join('\n'), 'utf8');
console.log('web/.env actualizado con la clave vigente y el App ID del proyecto.');
