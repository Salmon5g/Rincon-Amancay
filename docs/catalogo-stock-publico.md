# Sincronizar el stock de fichas públicas existentes

Reiniciar la web o la API no modifica los documentos que ya están en Firestore. Las publicaciones anteriores pueden no tener `manejaStock` ni `stock`, aunque la ficha privada sí conserve los saldos. Esta herramienta agrega esos campos a las fichas públicas que ya existen.

La herramienta requiere ADC de una identidad autorizada para leer las fichas privadas y actualizar Firestore. No acepta emuladores, claves JSON ni otro proyecto. Primero ejecuta una simulación:

```powershell
gcloud auth application-default login
$env:AMANCAY_ENTORNO = "compartido"
$env:AMANCAY_PROJECT_ID = "rincon-amancay"
npm.cmd --prefix backend run catalogo:sincronizar-stock-publico -- --dry-run rincon-amancay
```

Revisa el resumen. Si corresponde a los productos publicados esperados, ejecuta la escritura explícita:

```powershell
npm.cmd --prefix backend run catalogo:sincronizar-stock-publico -- --aplicar rincon-amancay
```

Solo modifica fichas ya publicadas y sus variantes ya existentes en `tiendasPublicas`: escribe `manejaStock` en el producto y los saldos actuales en productos simples o en cada variante. No crea productos ni variantes, no publica campos del borrador y no cambia precios, disponibilidad, imágenes ni estados. Cuando el stock no se controla, escribe `stock: null`. La operación es idempotente; si se interrumpe, puede volver a ejecutarse.

Después de aplicar, recarga la ficha del comprador web. Los ajustes de stock, ventas y republicaciones posteriores mantienen esos saldos actualizados automáticamente por la API. No guardar credenciales ni el resultado de ADC en el repositorio.
