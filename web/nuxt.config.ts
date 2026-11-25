export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  devtools: { enabled: true },
  nitro: { prerender: { routes: ['/invitacion-emprendedora', '/auth/accion', '/emprendedora', '/comprador', '/comprador/directorio', '/comprador/ferias'] } },
  runtimeConfig: {
    public: {
      firebaseModo: 'local',
      firebaseApiKey: '',
      firebaseAuthDomain: '',
      firebaseProjectId: '',
      firebaseProjectIdEsperado: '',
      firebaseStorageBucket: '',
      firebaseMessagingSenderId: '',
      firebaseAppId: '',
      firebaseVapidKey: '',
      apiUrl: '',
      supabaseUrl: 'https://supabase-fixmol.codepulse.cl',
    },
  },
})
