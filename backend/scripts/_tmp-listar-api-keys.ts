import { applicationDefault } from 'firebase-admin/app';

const credencial = applicationDefault();
const token = await credencial.getAccessToken();
const autorizacion = { Authorization: `Bearer ${token.access_token}`, 'x-goog-user-project': 'rincon-amancay' };
const respuesta = await fetch('https://firebase.googleapis.com/v1beta1/projects/rincon-amancay/webApps', { headers: autorizacion });
console.log('estado webApps:', respuesta.status);
console.log((await respuesta.text()).slice(0, 3000));
