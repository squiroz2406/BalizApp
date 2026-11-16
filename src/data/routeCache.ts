import AsyncStorage from '@react-native-async-storage/async-storage';
import type { GeoPosition } from './models';
import { distance } from './routeMath';
import type { WalkingRoute } from './routeRepository';

/*
 * Guarda en el teléfono el último recorrido por calles de cada estacionamiento, para poder seguirlo sin
 * conexión. Los de más de 30 días se borran solos al guardar uno nuevo.
 */
const PREFIX = 'route.';
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;
const SAME_DESTINATION_METERS = 10;

interface Stored {
  destLat: number;
  destLon: number;
  distance: number;
  duration: number;
  points: [number, number][];
  savedAt: number;
}

export async function saveRoute(
  parkingId: string,
  destination: GeoPosition,
  route: WalkingRoute,
  now: number = Date.now(),
) {
  try {
    const stored: Stored = {
      destLat: destination.latitude,
      destLon: destination.longitude,
      distance: route.distanceMeters,
      duration: route.durationSeconds,
      points: route.points.map((p) => [p.latitude, p.longitude]),
      savedAt: now,
    };
    await AsyncStorage.setItem(PREFIX + parkingId, JSON.stringify(stored));
    await prune(now);
  } catch (e) {
    console.warn('No se pudo guardar el recorrido', e);
  }
}

/** El recorrido guardado, solo si termina donde está el vehículo ahora (a menos de 10 m). */
export async function loadRoute(parkingId: string, destination: GeoPosition): Promise<WalkingRoute | null> {
  try {
    const raw = await AsyncStorage.getItem(PREFIX + parkingId);
    if (!raw) return null;
    const stored: Stored = JSON.parse(raw);
    const saved = { latitude: stored.destLat, longitude: stored.destLon };
    if (distance(saved, destination) > SAME_DESTINATION_METERS) return null;
    return {
      points: stored.points.map(([latitude, longitude]) => ({ latitude, longitude })),
      distanceMeters: stored.distance,
      durationSeconds: stored.duration,
    };
  } catch (e) {
    console.warn('Recorrido guardado ilegible', e);
    return null;
  }
}

async function prune(now: number) {
  const keys = (await AsyncStorage.getAllKeys()).filter((k) => k.startsWith(PREFIX));
  const entries = await AsyncStorage.multiGet(keys);
  const stale = entries
    .filter(([, value]) => {
      try {
        return now - (JSON.parse(value ?? '{}').savedAt ?? 0) > MAX_AGE_MS;
      } catch {
        return true;
      }
    })
    .map(([key]) => key);
  if (stale.length) await AsyncStorage.multiRemove(stale);
}
