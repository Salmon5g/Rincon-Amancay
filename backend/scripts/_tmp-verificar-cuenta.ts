import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

const app = initializeApp({ projectId: 'rincon-amancay', credential: applicationDefault() });
const auth = getAuth(app);
const usuario = await auth.getUser('hfnb3cPfiBX3pz4CK94hyQdaF1o1');
await auth.updateUser(usuario.uid, { emailVerified: true });
console.log('correo de la cuenta de prueba marcado como verificado');
