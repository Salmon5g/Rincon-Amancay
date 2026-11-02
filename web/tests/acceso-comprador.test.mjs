import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crearAccesoComprador, mensajeErrorAcceso, nombreMostrar } from '../app/lib/acceso-comprador.ts';

/*SDK simulado: no usa emuladores ni red. Registra qué se llamó y en qué orden.*/
function escenario({ verificado = true, acceso = null, conApi = true, falla = {} } = {}) {
    const llamadas = [];
    const usuario = {
        uid: 'u1', email: 'ana.perez@example.test', displayName: 'Ana Pérez', emailVerified: verificado,
        reload: async () => { llamadas.push('reload'); },
        getIdToken: async () => { llamadas.push('token'); return 't'; },
    };
    const auth = { currentUser: null };
    const entrar = async () => { auth.currentUser = usuario; return { user: usuario }; };
    const sdk = {
        GoogleAuthProvider: class { setCustomParameters() { } },
        createUserWithEmailAndPassword: async () => {
            llamadas.push('crear');
            if (falla.crear) throw Object.assign(new Error('x'), { code: falla.crear });
            usuario.emailVerified = false; usuario.displayName = null; return entrar();
        },
        updateProfile: async (u, datos) => { llamadas.push('perfil'); u.displayName = datos.displayName; },
        sendEmailVerification: async () => { llamadas.push('verificacion'); if (falla.verificacion && llamadas.filter(l => l === 'verificacion').length === 1) throw new Error('fallo'); },
        signInWithEmailAndPassword: async () => { llamadas.push('ingreso-correo'); return entrar(); },
        signInWithPopup: async () => { llamadas.push('popup'); return entrar(); },
        sendPasswordResetEmail: async () => { llamadas.push('recuperar'); if (falla.recuperar) throw Object.assign(new Error('x'), { code: falla.recuperar }); },
        signOut: async () => { llamadas.push('salir'); auth.currentUser = null; },
        onAuthStateChanged: (_auth, cb) => { cb(auth.currentUser); return () => llamadas.push('desuscribir'); },
        doc: (_db, ...ruta) => ruta.join('/'),
        getDocFromServer: async ruta => {
            llamadas.push(`leer:${ruta}`);
            return { exists: () => acceso !== null, data: () => acceso };
        },
    };
    const registros = [];
    const llamar = conApi ? async (operacion, solicitud) => { llamadas.push(`api:${operacion}`); registros.push(solicitud); return {}; } : undefined;
    return { servicio: crearAccesoComprador({ auth, db: {} }, llamar, sdk), llamadas, registros, auth, usuario };
}

test('correo y contraseña: crea la identidad, guarda el nombre y pide confirmar el correo sin crear la cuenta todavía', async () => {
    const { servicio, llamadas } = escenario();
    const r = await servicio.registrarConCorreo({ nombre: ' Ana Pérez ', correo: ' ana@example.test ', clave: 'secreta1', repetirClave: 'secreta1' });
    assert.deepEqual(r, { estado: 'verificacion-pendiente', correo: 'ana@example.test' });
    assert.deepEqual(llamadas, ['crear', 'perfil', 'verificacion']);
});

test('registro: si falla el envío del correo conserva la identidad y permite reenviar', async () => {
    const { servicio, llamadas } = escenario({ falla: { verificacion: true } });
    const r = await servicio.registrarConCorreo({ nombre: 'Ana', correo: 'ana@example.test', clave: 'secreta1', repetirClave: 'secreta1' });
    assert.equal(r.estado, 'verificacion-pendiente');
    assert.equal(r.envioFallido, true);
    await servicio.reenviarVerificacion();
    assert.equal(llamadas.filter(l => l === 'crear').length, 1);
    assert.equal(llamadas.filter(l => l === 'verificacion').length, 2);
});

test('registro valida nombre y contraseñas antes de crear nada en Authentication', async () => {
    const { servicio, llamadas } = escenario();
    await assert.rejects(servicio.registrarConCorreo({ nombre: '  ', correo: 'a@b.cl', clave: 'secreta1', repetirClave: 'secreta1' }), { code: 'datos-invalidos' });
    await assert.rejects(servicio.registrarConCorreo({ nombre: 'Ana', correo: 'a@b.cl', clave: '12345', repetirClave: '12345' }), { code: 'auth/weak-password' });
    await assert.rejects(servicio.registrarConCorreo({ nombre: 'Ana', correo: 'a@b.cl', clave: 'secreta1', repetirClave: 'otra-cosa' }), { code: 'datos-invalidos' });
    assert.deepEqual(llamadas, []);
});

test('registro propaga el error de correo ya usado', async () => {
    const { servicio } = escenario({ falla: { crear: 'auth/email-already-in-use' } });
    await assert.rejects(servicio.registrarConCorreo({ nombre: 'Ana', correo: 'a@b.cl', clave: 'secreta1', repetirClave: 'secreta1' }), { code: 'auth/email-already-in-use' });
});

test('ingreso con correo sin verificar no llama a la API y deja la sesión abierta para reenviar', async () => {
    const { servicio, llamadas, auth } = escenario({ verificado: false });
    const r = await servicio.iniciarConCorreo('ana.perez@example.test', 'secreta1');
    assert.equal(r.estado, 'verificacion-pendiente');
    assert.equal(llamadas.some(l => l.startsWith('api:')), false);
    assert.notEqual(auth.currentUser, null);
});

test('ingreso con correo verificado y sin cuenta registra al comprador con el nombre del perfil', async () => {
    const { servicio, llamadas, registros } = escenario({ acceso: null });
    const r = await servicio.iniciarConCorreo('ana.perez@example.test', 'secreta1');
    assert.deepEqual(r, { estado: 'listo', nombre: 'Ana Pérez' });
    assert.deepEqual(registros, [{ nombreMostrar: 'Ana Pérez' }]);
    assert.ok(llamadas.indexOf('token') < llamadas.indexOf('api:registrarComprador'));
});

test('ingreso con Google registra al comprador una sola vez y no vuelve a llamar si la cuenta ya existe', async () => {
    const nuevo = escenario({ acceso: null });
    assert.equal((await nuevo.servicio.iniciarConGoogle()).estado, 'listo');
    assert.equal(nuevo.registros.length, 1);
    const existente = escenario({ acceso: { roles: ['comprador'], estado: 'activo' } });
    assert.equal((await existente.servicio.iniciarConGoogle()).estado, 'listo');
    assert.equal(existente.registros.length, 0);
});

test('una cuenta con otro rol (emprendedora o administradora) entra sin intentar crear acceso de comprador', async () => {
    const { servicio, registros } = escenario({ acceso: { roles: ['emprendedora'], estado: 'activo', tiendaId: 't1' } });
    assert.equal((await servicio.iniciarConCorreo('x@y.cl', 'secreta1')).estado, 'listo');
    assert.equal(registros.length, 0);
});

test('una cuenta desactivada cierra la sesión y muestra un error claro', async () => {
    const { servicio, llamadas, auth } = escenario({ acceso: { roles: ['comprador'], estado: 'desactivado' } });
    await assert.rejects(servicio.iniciarConGoogle(), { code: 'cuenta-desactivada' });
    assert.ok(llamadas.includes('salir'));
    assert.equal(auth.currentUser, null);
});

test('sin API (modo real-lectura) la sesión funciona pero informa que no se creó la cuenta', async () => {
    const { servicio } = escenario({ acceso: null, conApi: false });
    const r = await servicio.iniciarConGoogle();
    assert.equal(r.estado, 'sin-registro');
});

test('"ya confirmé mi correo" termina el alta cuando el correo quedó verificado', async () => {
    const { servicio, usuario, registros } = escenario({ verificado: false });
    await servicio.iniciarConCorreo('x@y.cl', 'secreta1');
    usuario.emailVerified = true; // el usuario abrió el enlace del correo
    assert.equal((await servicio.comprobarVerificacion()).estado, 'listo');
    assert.equal(registros.length, 1);
});

test('comprobar o reenviar sin sesión pide iniciar sesión', async () => {
    const { servicio } = escenario();
    await assert.rejects(servicio.comprobarVerificacion(), { code: 'no-autenticado' });
    await assert.rejects(servicio.reenviarVerificacion(), { code: 'no-autenticado' });
});

test('recuperar contraseña responde igual exista o no la cuenta, pero no esconde otros errores', async () => {
    const sinCuenta = escenario({ falla: { recuperar: 'auth/user-not-found' } });
    const conCuenta = escenario();
    assert.deepEqual(await sinCuenta.servicio.solicitarRecuperacion('x@y.cl'), await conCuenta.servicio.solicitarRecuperacion('x@y.cl'));
    const sinRed = escenario({ falla: { recuperar: 'auth/network-request-failed' } });
    await assert.rejects(sinRed.servicio.solicitarRecuperacion('x@y.cl'), { code: 'auth/network-request-failed' });
});

test('cerrar sesión y observar la sesión', async () => {
    const { servicio, llamadas, auth } = escenario();
    const vistas = [];
    const dejar = servicio.observar(sesion => vistas.push(sesion));
    assert.deepEqual(vistas, [null]);
    await servicio.iniciarConGoogle();
    assert.equal(servicio.sesionActual().correo, 'ana.perez@example.test');
    await servicio.cerrarSesion();
    assert.equal(auth.currentUser, null);
    assert.equal(servicio.sesionActual(), null);
    dejar();
    assert.ok(llamadas.includes('desuscribir'));
});

test('mensajes de error en lenguaje sencillo, sin códigos técnicos', () => {
    for (const code of ['auth/email-already-in-use', 'auth/invalid-credential', 'auth/weak-password', 'auth/popup-closed-by-user', 'auth/unauthorized-domain', 'cuenta-desactivada', 'conflicto', 'algo-desconocido']) {
        const m = mensajeErrorAcceso({ code });
        assert.ok(m.length > 10 && !m.includes('auth/'), code);
    }
    assert.equal(mensajeErrorAcceso({ code: 'auth/cancelled-popup-request' }), '');
    assert.equal(mensajeErrorAcceso({ code: 'auth/user-not-found' }), mensajeErrorAcceso({ code: 'auth/wrong-password' }));
});

test('nombre por defecto: el del perfil, si no el correo, siempre hasta 100 caracteres', () => {
    assert.equal(nombreMostrar({ displayName: ' Ana ', email: 'a@b.cl' }), 'Ana');
    assert.equal(nombreMostrar({ displayName: null, email: 'ana.perez@b.cl' }), 'ana.perez');
    assert.equal(nombreMostrar({}), 'Comprador');
    assert.equal(nombreMostrar({ displayName: 'x'.repeat(300) }).length, 100);
});
