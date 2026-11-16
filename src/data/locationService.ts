import * as Location from 'expo-location';
import type { GeoPosition } from './models';

/** Ubicación del teléfono (expo-location) y dirección legible (geocodificación inversa). */
export async function hasLocationPermission(): Promise<boolean> {
  const { status } = await Location.getForegroundPermissionsAsync();
  return status === 'granted';
}

/** Pide el permiso de ubicación (precisa o aproximada). true si el usuario concedió alguno. */
export async function requestLocationPermission(): Promise<boolean> {
  const { status } = await Location.requestForegroundPermissionsAsync();
  return status === 'granted';
}

function toPosition(l: Location.LocationObject): GeoPosition {
  return { latitude: l.coords.latitude, longitude: l.coords.longitude, accuracyMeters: l.coords.accuracy };
}

/**
 * Lectura única de la posición actual. Devuelve null si no hay permiso o si no se consiguió una
 * posición en [timeoutMs]; en ese caso se usa la última conocida.
 */
export async function currentPosition(timeoutMs = 15_000): Promise<GeoPosition | null> {
  if (!(await hasLocationPermission())) return null;
  try {
    const fresh = await Promise.race([
      Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), timeoutMs)),
    ]);
    const location = fresh ?? (await Location.getLastKnownPositionAsync());
    return location ? toPosition(location) : null;
  } catch (e) {
    console.warn('No se pudo obtener la ubicación', e);
    return null;
  }
}

/**
 * Posición en tiempo real mientras alguien observa (pantalla "Volver al auto"). Devuelve la función que
 * corta las actualizaciones para no gastar batería.
 */
export function watchPosition(cb: (p: GeoPosition) => void, intervalMs = 2000): () => void {
  let subscription: Location.LocationSubscription | null = null;
  let cancelled = false;
  (async () => {
    if (!(await hasLocationPermission())) return;
    // Mientras llega la primera lectura, se usa la última conocida.
    Location.getLastKnownPositionAsync()
      .then((last) => last && !cancelled && cb(toPosition(last)))
      .catch(() => undefined);
    const sub = await Location.watchPositionAsync(
      { accuracy: Location.Accuracy.High, timeInterval: intervalMs, distanceInterval: 0 },
      (l) => cb(toPosition(l)),
    );
    if (cancelled) sub.remove();
    else subscription = sub;
  })().catch((e) => console.warn('Error de ubicación', e));
  return () => {
    cancelled = true;
    subscription?.remove();
  };
}

/** "Av. Calchaquí 6200, Florencio Varela" o null si no se pudo resolver. */
export async function addressFor(latitude: number, longitude: number): Promise<string | null> {
  try {
    const results = await Promise.race([
      Location.reverseGeocodeAsync({ latitude, longitude }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), 5000)),
    ]);
    const a = results?.[0];
    if (!a) return null;
    return formatAddress(a);
  } catch (e) {
    console.warn('No se pudo resolver la dirección', e);
    return null;
  }
}

export function formatAddress(a: Pick<Location.LocationGeocodedAddress, 'street' | 'streetNumber' | 'city' | 'subregion' | 'name'>): string | null {
  const street = [a.street, a.streetNumber].filter(Boolean).join(' ').trim() || null;
  const city = a.city ?? a.subregion ?? null;
  const joined = [street, city].filter(Boolean).join(', ');
  return joined || a.name || null;
}
