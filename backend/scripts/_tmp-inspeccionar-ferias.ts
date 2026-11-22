import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const app = initializeApp({ projectId: 'rincon-amancay', credential: applicationDefault() });
const db = getFirestore(app);
const doc = await db.doc('tiendasPublicas/tienda_b10bebea870c49f095f014c5e1a7a9b9/productos/tmp_borrador_w13').get();
console.log('producto temporal existe:', doc.exists, 'estado:', doc.get('estadoPublicacion'));
const productos = await db.collection('tiendasPublicas/tienda_b10bebea870c49f095f014c5e1a7a9b9/productos')
  .where('estadoPublicacion', '==', 'publicado').limit(5).get();
console.log('muestra publicada:', JSON.stringify(productos.docs.map(item => item.get('nombre'))));
