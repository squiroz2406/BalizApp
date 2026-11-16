import type { GeoPosition } from './models';

const R = 6_371_008.8; // radio medio de la Tierra, en metros
const rad = (d: number) => (d * Math.PI) / 180;
const deg = (r: number) => (r * 180) / Math.PI;

/** Distancia en metros entre dos posiciones (haversine). */
export function distance(a: GeoPosition, b: GeoPosition): number {
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function normalizeDegrees(degrees: number): number {
  return ((degrees % 360) + 360) % 360;
}

/** Rumbo (0-360°, respecto del norte geográfico) de [a] hacia [b]. */
export function bearing(a: GeoPosition, b: GeoPosition): number {
  const dLon = rad(b.longitude - a.longitude);
  const y = Math.sin(dLon) * Math.cos(rad(b.latitude));
  const x =
    Math.cos(rad(a.latitude)) * Math.sin(rad(b.latitude)) -
    Math.sin(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.cos(dLon);
  return normalizeDegrees(deg(Math.atan2(y, x)));
}

/** Índice del punto de la ruta más cercano a [me] y su distancia. */
export function closestPoint(route: GeoPosition[], me: GeoPosition): { index: number; distance: number } {
  let best = 0;
  let bestDistance = Number.MAX_VALUE;
  route.forEach((p, i) => {
    const d = distance(me, p);
    if (d < bestDistance) {
      best = i;
      bestDistance = d;
    }
  });
  return { index: best, distance: bestDistance };
}

/** Metros que faltan recorrer por la ruta desde [me] (pasando por el punto más cercano). */
export function remainingMeters(route: GeoPosition[], me: GeoPosition): number {
  if (route.length === 0) return 0;
  const { index, distance: toClosest } = closestPoint(route, me);
  let total = toClosest;
  for (let i = index; i < route.length - 1; i++) total += distance(route[i], route[i + 1]);
  return total;
}

/**
 * Punto hacia el que tiene que apuntar la flecha: el primero de la ruta, más adelante del usuario,
 * que esté a por lo menos [lookAheadMeters]. Así la flecha sigue las esquinas.
 */
export function nextTarget(route: GeoPosition[], me: GeoPosition, lookAheadMeters = 25): GeoPosition | null {
  if (route.length === 0) return null;
  const { index } = closestPoint(route, me);
  for (let i = index; i < route.length; i++) {
    if (distance(me, route[i]) >= lookAheadMeters) return route[i];
  }
  return route[route.length - 1];
}

const CARDINAL_KEYS = ['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'];

/** Clave del texto del punto cardinal más cercano a [degrees] (nav_cardinal_n, ...). */
export function cardinalKey(degrees: number): string {
  const index = Math.round(normalizeDegrees(degrees) / 45) % 8;
  return `nav_cardinal_${CARDINAL_KEYS[index]}`;
}
