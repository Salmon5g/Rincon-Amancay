import { readFileSync } from 'node:fs';
// Informe local. No importa Firebase Admin, no usa credenciales ni llama a la red.
const p=JSON.parse(readFileSync(new URL('../../infra/plan-desarrollo.json',import.meta.url),'utf8').replace(/^\uFEFF/,''));
if(p.estado!=='propuesta_no_desplegable') throw new Error('Este archivo describe una propuesta, no autoriza despliegues.');
const u=p.usoMensualSupuesto;
for(const [key,value] of Object.entries(u)) if(typeof value!=='number' || !Number.isFinite(value) || value<0)throw new Error(`Supuesto inválido: ${key}`);
const fotos=u.productos*u.fotosPorProducto;
const almacenGiB=fotos*(u.originalKiB+u.principalKiB+u.miniaturaKiB)/1024**2;
const salidaGiB=(u.descargasMiniaturas*u.miniaturaKiB+u.descargasPrincipales*u.principalKiB)/1024**2;
const segundos=u.solicitudesApiSinImagenes*u.segundosPorSolicitud+u.imagenesProcesadas*u.segundosPorImagen;
console.log(`PROPUESTA LOCAL: ${p.proyecto} / ${p.regionPropuesta}`);
console.log(`Escenario supuesto: ${fotos} fotos; ${almacenGiB.toFixed(2)} GiB almacenados; ${salidaGiB.toFixed(2)} GiB de imágenes descargadas al mes.`);
console.log(`Cómputo orientativo: ${(segundos*p.cloudRun.cpu).toFixed(0)} vCPU-s y ${(segundos*p.cloudRun.memoriaGiB).toFixed(0)} GiB-s; no incluye arranques, reintentos ni solapamientos.`);
console.log(`Alerta de presupuesto propuesta: USD ${p.alertaPresupuestoUsdPropuesta}. No es una cotización ni un tope de gasto.`);
console.log('Faltan tarifas regionales verificadas y consumos de Firestore, Auth, compilación, registro de imágenes y logs para calcular un total.');
console.log(`Bucket: ${p.bucketConfirmado ?? 'por confirmar'} (${p.storageEstado ?? 'estado desconocido'}).`);
console.log(`URL API: ${p.apiUrlConfirmada ?? 'por confirmar'}. applicationId Gradle: ${p.androidApplicationId ?? 'por confirmar'}. Firebase App ID Android (mobilesdk_app_id): ${p.androidFirebaseAppId ?? 'por confirmar'}.`);
console.log('Esta lista es un estado registrado, no una verificación en vivo de la consola.');
for(const bloqueo of p.bloqueosTecnicos)console.log(`PENDIENTE: ${bloqueo}`);
