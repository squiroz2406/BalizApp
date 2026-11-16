import { addDoc, collection, deleteDoc, doc, onSnapshot, orderBy, query, setDoc } from '@react-native-firebase/firestore';
import { db, observeUid, requireUid, type Unsubscribe } from './firebase';
import { vehicleFromData, vehicleToData, type Vehicle } from './models';

const vehicles = (uid: string) => collection(db(), 'users', uid, 'vehicles');

/** Vehículos del usuario con sesión, ordenados por alias, en tiempo real. */
export function observeVehicles(cb: (vehicles: Vehicle[]) => void): Unsubscribe {
  let inner: Unsubscribe | null = null;
  const outer = observeUid((uid) => {
    inner?.();
    inner = null;
    if (!uid) return cb([]);
    inner = onSnapshot(
      query(vehicles(uid), orderBy('alias', 'asc')),
      (snap) => cb(snap.docs.map((d) => vehicleFromData(d.id, d.data()))),
      (e) => {
        console.warn('Error leyendo vehículos', e);
        cb([]);
      },
    );
  });
  return () => {
    inner?.();
    outer();
  };
}

/** Crea el vehículo si no tiene id; si tiene, lo reemplaza. */
export async function saveVehicle(vehicle: Vehicle) {
  const col = vehicles(requireUid());
  const data = vehicleToData(vehicle);
  if (!vehicle.id) await addDoc(col, data);
  else await setDoc(doc(col, vehicle.id), data);
}

export async function deleteVehicle(vehicleId: string) {
  await deleteDoc(doc(vehicles(requireUid()), vehicleId));
}
