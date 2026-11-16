import { distance, bearing } from './routeMath';
import type { GeoPosition } from './models';

/** Recorrido a pie por calles: puntos de la línea, distancia total y tiempo estimado. */
export interface WalkingRoute {
  points: GeoPosition[];
  distanceMeters: number;
  durationSeconds: number;
}

/*
 * Ruta a pie por calles con el servidor público OSRM de FOSSGIS (datos de OpenStreetMap; sin clave).
 * Política de uso (https://routing.openstreetmap.de/about.html): máximo un pedido por segundo, sin uso
 * intensivo, User-Agent propio y atribución visible. La app pide la ruta solo al activarla y al desviarse,
 * con al menos MIN_INTERVAL_MS entre pedidos.
 */
const BASE_URL = 'https://routing.openstreetmap.de/routed-foot/route/v1/driving/';
const USER_AGENT = 'BalizApp/1.0 (React Native; trabajo practico UNAJ; +https://github.com/squiroz2406/BalizApp)';

/** Tiempo mínimo entre pedidos al servidor (su límite es 1 por segundo; somos más prudentes). */
export const MIN_INTERVAL_MS = 15_000;

export function parseOsrm(body: any): WalkingRoute {
  if (body?.code !== 'Ok') throw new Error(`No se encontró una ruta a pie (${body?.code})`);
  const route = body.routes[0];
  const points: GeoPosition[] = route.geometry.coordinates.map((pair: [number, number]) => ({
    latitude: pair[1], // OSRM entrega [longitud, latitud]
    longitude: pair[0],
  }));
  return { points, distanceMeters: route.distance, durationSeconds: route.duration };
}

export async function walkingRoute(
  from: GeoPosition,
  toLatitude: number,
  toLongitude: number,
  timeoutMs = 10_000,
): Promise<WalkingRoute> {
  const coords = `${from.longitude.toFixed(6)},${from.latitude.toFixed(6)};${toLongitude.toFixed(6)},${toLatitude.toFixed(6)}`;
  const url = `${BASE_URL}${coords}?overview=full&geometries=geojson&alternatives=false&steps=false`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      signal: controller.signal,
    });
    if (!response.ok) throw new Error(`El servidor de rutas respondió ${response.status}`);
    return parseOsrm(await response.json());
  } finally {
    clearTimeout(timer);
  }
}

export { distance, bearing };
