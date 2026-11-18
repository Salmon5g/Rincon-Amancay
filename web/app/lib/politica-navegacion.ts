export type ModoPoliticaNavegacion = 'permitir' | 'invitar' | 'exigir'

export function evaluarPoliticaNavegacion(
    modo: ModoPoliticaNavegacion,
    sesion: { conGoogle: boolean } | null,
    omitido: boolean,
): { bloqueado: boolean; sugerencia: boolean } {
    return {
        bloqueado: modo === 'exigir' && sesion?.conGoogle !== true,
        sugerencia: modo === 'invitar' && sesion === null && !omitido,
    };
}
