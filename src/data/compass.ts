import * as Location from 'expo-location';
import { Magnetometer } from 'expo-sensors';
import { normalizeDegrees } from './routeMath';

/** Lectura de la brújula: hacia dónde apunta la parte superior del teléfono. */
export interface CompassReading {
  /** Grados respecto del norte GEOGRÁFICO (0 = norte, 90 = este). */
  headingDegrees: number;
  /** true si el sistema indica que el magnetómetro necesita calibrarse. */
  unreliable: boolean;
}

/** Los emuladores no siempre tienen magnetómetro: en ese caso se muestra solo el rumbo en texto. */
export async function isCompassAvailable(): Promise<boolean> {
  try {
    return await Magnetometer.isAvailableAsync();
  } catch {
    return false;
  }
}

/**
 * Brújula del teléfono. expo-location combina magnetómetro y acelerómetro y, con el permiso de ubicación,
 * entrega el rumbo respecto del norte geográfico (ya corregida la declinación magnética, que en Buenos
 * Aires ronda los -9°). Devuelve la función que apaga los sensores.
 */
export function watchCompass(cb: (reading: CompassReading) => void): () => void {
  let subscription: Location.LocationSubscription | null = null;
  let cancelled = false;
  Location.watchHeadingAsync((h) => {
    // trueHeading es -1 cuando no se pudo calcular la declinación: se usa el norte magnético.
    const heading = h.trueHeading >= 0 ? h.trueHeading : h.magHeading;
    // accuracy: 0 = sin calibrar, 1 = baja, 2 = media, 3 = alta
    cb({ headingDegrees: normalizeDegrees(heading), unreliable: h.accuracy <= 1 });
  })
    .then((sub) => {
      if (cancelled) sub.remove();
      else subscription = sub;
    })
    .catch((e) => console.warn('No se pudo iniciar la brújula', e));
  return () => {
    cancelled = true;
    subscription?.remove();
  };
}
