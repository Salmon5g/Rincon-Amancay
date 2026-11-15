import type { DocumentData } from 'firebase-admin/firestore';

export function crearProductoSinPrecios(producto: DocumentData): DocumentData {
  const vista = { ...producto };
  delete vista.precioBase;
  return vista;
}

export function crearVarianteSinPrecio(variante: DocumentData): DocumentData {
  const vista = { ...variante };
  delete vista.precio;
  return vista;
}
