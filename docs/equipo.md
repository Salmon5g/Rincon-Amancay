# Trabajo del equipo

| Área | Responsabilidad |
|---|---|
| Backend/base compartida | contratos/, firebase/, backend/ y coordinación de adaptadores cliente |
| Web comprador y administrador | Una sola carpeta web/, con rutas y componentes acordados |
| Android | Una carpeta android/, con los modos que corresponda implementar |

Las pantallas pueden avanzar con datos ficticios una vez acordado el contrato. No necesitan esperar a que todo el backend esté terminado. Nadie cambia unilateralmente nombres de campos, estados o permisos: el cambio se registra en contratos/ y se coordina con ambos clientes.

Los accesos Firebase se centralizarán en los repositories de cada cliente; las pantallas llamarán a esa capa. La inicialización compartida dentro de la web se prepara una sola vez.

El adaptador local web y los ejemplos Android ya están preparados en [integracion-equipos.md](integracion-equipos.md). El cliente web se probó contra emuladores; Android requiere integración y compilación en el proyecto del equipo. Los contratos y servicios son comunes, pero cada computador mantiene sus datos demo hasta que se despliegue un entorno remoto compartido.

Agregar nuevas carpetas cuando exista código que las necesite. No se crean ahora módulos de asistente, voz, QR o mapa: se incorporarán según los requerimientos y usarán los mismos servicios y contratos.
