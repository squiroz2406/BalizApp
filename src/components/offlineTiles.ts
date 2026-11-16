import { OfflineManager } from '@maplibre/maplibre-react-native';
import { isOnline } from '../data/connectivity';
import { hasMapTiler, mapStyleFor } from './mapStyle';

const RADIUS_METERS = 500;
const MIN_ZOOM = 15;
const MAX_ZOOM = 18;

/** Caja [oeste, sur, este, norte] de [radius] metros alrededor de un punto. */
export function boundsAround(latitude: number, longitude: number, radius = RADIUS_METERS): [number, number, number, number] {
  const dLat = radius / 111_320;
  const dLon = radius / (111_320 * Math.cos((latitude * Math.PI) / 180));
  return [longitude - dLon, latitude - dLat, longitude + dLon, latitude + dLat];
}

/**
 * Descarga a disco los mosaicos del mapa alrededor de un punto (claro y oscuro), para poder volver al
 * vehículo sin conexión. Es un área chica a propósito para cuidar la cuota gratuita de MapTiler. Los
 * servidores de OpenStreetMap no permiten descargas masivas, así que sin clave de MapTiler no hace nada.
 */
export async function precacheTilesAround(latitude: number, longitude: number) {
  if (!hasMapTiler || !(await isOnline())) return;
  try {
    const bounds = boundsAround(latitude, longitude);
    for (const dark of [false, true]) {
      await OfflineManager.createPack(
        {
          mapStyle: JSON.stringify(mapStyleFor(dark)),
          bounds,
          minZoom: MIN_ZOOM,
          maxZoom: MAX_ZOOM,
          metadata: { name: `baliz-${latitude.toFixed(4)}-${longitude.toFixed(4)}-${dark ? 'dark' : 'light'}` },
        },
        () => undefined,
        (_pack, error) => console.warn('Mosaicos con error', error),
      );
    }
  } catch (e) {
    console.warn('No se pudo precargar el mapa', e);
  }
}
