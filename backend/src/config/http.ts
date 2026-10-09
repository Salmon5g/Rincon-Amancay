export type ConfigHttp = {
  entorno: 'emulador' | 'compartido';
  proyecto: string;
  origenes: string[];
};
export function validarConfigHttp(config: ConfigHttp): ConfigHttp {
  if(!['emulador','compartido'].includes(config.entorno)) throw new Error('Entorno HTTP inválido.');
  if(!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(config.proyecto)) throw new Error('Proyecto HTTP inválido.');
  if((config.entorno==='emulador')!==config.proyecto.startsWith('demo-')) throw new Error('No mezclar proyecto demo y entorno compartido.');
  if(!Array.isArray(config.origenes) || config.origenes.length===0) throw new Error('Especificar orígenes web.');
  for(const origin of config.origenes) {
    const url=new URL(origin);
    const local=url.protocol==='http:' && ['localhost','127.0.0.1'].includes(url.hostname);
    if(url.hostname.includes('*') || url.origin!==origin || url.username || url.password || (url.protocol!=='https:' && !local)) throw new Error('Origen inválido: usar HTTPS o localhost exacto, sin rutas ni comodines.');
  }
  return {...config,origenes:[...new Set(config.origenes)]};
}
export const httpLocal: ConfigHttp={entorno:'emulador',proyecto:'demo-rincon-amancay',origenes:['http://localhost:3000','http://127.0.0.1:3000']};
