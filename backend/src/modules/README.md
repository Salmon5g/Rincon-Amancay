# Organización por módulo

Al implementar el primer caso de uso, crear su módulo, por ejemplo tiendas/:

```text
tiendas/
  publicarTienda.ts      Entrada: identidad y datos recibidos
  tienda.service.ts     Reglas del negocio y coordinación
  tienda.repository.ts  Lecturas/escrituras Firestore de servidor
  tienda.validation.ts  Validación de campos permitidos
```

Esto es una guía, no cuatro archivos obligatorios para cada acción. Mantener simple el código; separar responsabilidades cuando lo necesite. Los roles se verifican también en servidor. Los repositories de aquí no reemplazan los de los clientes: ejecutan código con otra autoridad.
