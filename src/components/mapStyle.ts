import { mapTilerKey } from '../config/firebaseWeb';

/*
 * Estilo del mapa. Con clave de MapTiler (plan gratuito) se usan sus mosaicos de OpenStreetMap, que permiten
 * descargar áreas para usar sin conexión; sin clave se usan los mosaicos del servidor de OpenStreetMap, que
 * no admiten descargas masivas (por eso sin clave no se precarga el mapa).
 */
export const hasMapTiler = mapTilerKey !== '';

export function mapStyleFor(dark: boolean) {
  const tiles = hasMapTiler
    ? [`https://api.maptiler.com/maps/${dark ? 'streets-v2-dark' : 'streets-v2'}/256/{z}/{x}/{y}@2x.png?key=${mapTilerKey}`]
    : ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'];
  return {
    version: 8 as const,
    sources: {
      base: {
        type: 'raster' as const,
        tiles,
        tileSize: 256,
        maxzoom: 19,
        attribution: hasMapTiler ? '© MapTiler © OpenStreetMap contributors' : '© OpenStreetMap contributors',
      },
    },
    layers: [{ id: 'base', type: 'raster' as const, source: 'base' }],
  };
}

/** Centro por defecto cuando todavía no hay ubicación: Obelisco, CABA. */
export const DEFAULT_CENTER = { latitude: -34.6037, longitude: -58.3816 };
