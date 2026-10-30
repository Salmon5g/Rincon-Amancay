export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  devtools: { enabled: true },
  nitro: { prerender: { routes: ['/invitacion-emprendedora'] } },
  runtimeConfig: {
    public: {
      firebaseModo: 'local',
      firebaseApiKey: '',
      firebaseAuthDomain: '',
      firebaseProjectId: '',
      firebaseStorageBucket: '',
      firebaseMessagingSenderId: '',
      firebaseAppId: '',
      apiUrl: '',
    },
  },
})
