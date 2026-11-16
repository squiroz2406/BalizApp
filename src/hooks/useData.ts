import { useEffect, useState } from 'react';
import { observeActive, observeHistory, observeParking, observePendingWrites } from '../data/parkingRepository';
import { observeProfile } from '../data/userRepository';
import { observeVehicles } from '../data/vehicleRepository';
import type { Parking, UserProfile, Vehicle } from '../data/models';
import { observeOnline } from '../data/connectivity';

/** Se suscribe a un listener de Firestore mientras el componente está en pantalla. */
function useObserved<T>(subscribe: (cb: (value: T) => void) => () => void, initial: T, deps: unknown[] = []) {
  const [value, setValue] = useState<T>(initial);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    return subscribe((v) => {
      setValue(v);
      setLoading(false);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { value, loading };
}

export const useActiveParkings = () => useObserved<Parking[]>(observeActive, []);
export const useHistory = () => useObserved<Parking[]>(observeHistory, []);
export const useVehicles = () => useObserved<Vehicle[]>(observeVehicles, []);
export const useProfile = () => useObserved<UserProfile | null>(observeProfile, null);
export const useParking = (id: string) =>
  useObserved<Parking | null>((cb) => observeParking(id, cb), null, [id]);

/** Hora actual que se actualiza cada [intervalMs]; sirve para las cuentas regresivas. */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

export type SyncStatus = 'online' | 'offline' | 'syncing';

/** Combina el estado de la red con las escrituras de Firestore que todavía no se subieron. */
export function useSyncStatus(): SyncStatus {
  const [online, setOnline] = useState(true);
  const [pending, setPending] = useState(false);
  useEffect(() => observeOnline(setOnline), []);
  useEffect(() => observePendingWrites(setPending), []);
  return !online ? 'offline' : pending ? 'syncing' : 'online';
}
