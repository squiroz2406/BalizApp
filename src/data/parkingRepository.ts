import {
  GeoPoint,
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  getDocsFromCache,
  limit,
  onSnapshot,
  query,
  setDoc,
  updateDoc,
  waitForPendingWrites,
  where,
} from '@react-native-firebase/firestore';
import { t } from '../i18n';
import { awaitOrPending, db, observeUid, requireUid, type Unsubscribe } from './firebase';
import { parkingFromData, type Parking, type ParkingStatus } from './models';
import { deleteAllPhotos } from './photoRepository';

/** El vehículo elegido ya tiene un estacionamiento activo (regla: uno activo por vehículo). */
export class ActiveParkingExistsError extends Error {
  constructor() {
    super(t('form_err_active_exists'));
    this.name = 'ActiveParkingExistsError';
  }
}

const parkings = (uid: string) => collection(db(), 'users', uid, 'parkings');

/** Conversión a los campos de Firestore (los mismos nombres que usa la app Kotlin). */
export function parkingToData(p: Parking) {
  return {
    vehicleId: p.vehicleId,
    status: p.status,
    location: p.location ? new GeoPoint(p.location.latitude, p.location.longitude) : null,
    addressText: p.addressText,
    note: p.note,
    levelSector: p.levelSector,
    placePhotoUrl: p.placePhotoPath,
    signPhotoUrl: p.signPhotoPath,
    startedAt: Timestamp.fromMillis(p.startedAt),
    expiresAt: p.expiresAt != null ? Timestamp.fromMillis(p.expiresAt) : null,
    ruleSummary: p.ruleSummary,
    source: p.source,
    agentConfidence: p.agentConfidence,
    finishedAt: p.finishedAt != null ? Timestamp.fromMillis(p.finishedAt) : null,
  };
}

/*
 * Se filtra por status en Firestore y se ordena en el teléfono: igualdad en un campo + orden por otro
 * exigiría crear un índice compuesto, y la cantidad de registros por usuario es chica.
 */
function observeByStatus(status: ParkingStatus, cb: (list: Parking[]) => void): Unsubscribe {
  let inner: Unsubscribe | null = null;
  const outer = observeUid((uid) => {
    inner?.();
    inner = null;
    if (!uid) return cb([]);
    inner = onSnapshot(
      query(parkings(uid), where('status', '==', status)),
      (snap) => cb(snap.docs.map((d) => parkingFromData(d.id, d.data())).sort((a, b) => b.startedAt - a.startedAt)),
      (e) => {
        console.warn(`Error leyendo estacionamientos (${status})`, e);
        cb([]);
      },
    );
  });
  return () => {
    inner?.();
    outer();
  };
}

/** Estacionamientos activos (uno por vehículo como máximo), más recientes primero. */
export const observeActive = (cb: (list: Parking[]) => void) => observeByStatus('active', cb);

/** Historial: estacionamientos ya retirados, más recientes primero. */
export const observeHistory = (cb: (list: Parking[]) => void) => observeByStatus('finished', cb);

/** Un estacionamiento en tiempo real; null si no existe (por ejemplo, si se borró). */
export function observeParking(id: string, cb: (parking: Parking | null) => void): Unsubscribe {
  let inner: Unsubscribe | null = null;
  const outer = observeUid((uid) => {
    inner?.();
    inner = null;
    if (!uid) return cb(null);
    inner = onSnapshot(
      doc(parkings(uid), id),
      (snap) => cb(snap.exists() ? parkingFromData(snap.id, snap.data()) : null),
      (e) => {
        console.warn(`Error leyendo el estacionamiento ${id}`, e);
        cb(null);
      },
    );
  });
  return () => {
    inner?.();
    outer();
  };
}

/** true mientras haya cambios guardados en el teléfono que todavía no llegaron al servidor. */
export function observePendingWrites(cb: (pending: boolean) => void): Unsubscribe {
  let inner: Unsubscribe | null = null;
  let token = 0;
  const outer = observeUid((uid) => {
    inner?.();
    inner = null;
    if (!uid) return cb(false);
    inner = onSnapshot(
      parkings(uid),
      { includeMetadataChanges: true },
      (snap) => {
        const mine = ++token;
        const pending = snap.metadata.hasPendingWrites;
        cb(pending);
        // La marca puede quedar desactualizada tras reconectar; waitForPendingWrites termina cuando el
        // servidor confirmó (o rechazó) todo lo pendiente.
        if (pending) {
          waitForPendingWrites(db())
            .then(() => mine === token && cb(false))
            .catch(() => undefined);
        }
      },
      () => cb(false),
    );
  });
  return () => {
    inner?.();
    outer();
  };
}

export async function getParking(id: string): Promise<Parking | null> {
  const snap = await getDoc(doc(parkings(requireUid()), id));
  return snap.exists() ? parkingFromData(snap.id, snap.data()) : null;
}

export async function hasActiveParking(vehicleId: string): Promise<boolean> {
  const q = query(
    parkings(requireUid()),
    where('vehicleId', '==', vehicleId),
    where('status', '==', 'active'),
    limit(1),
  );
  // Sin conexión, getDocs espera al servidor; si tarda, se responde con el cache local.
  const server = await awaitOrPending(getDocs(q), 3000);
  const snapshot = server ?? (await getDocsFromCache(q));
  return !snapshot.empty;
}

/** Crea un estacionamiento activo y devuelve su id. */
export async function createParking(parking: Parking): Promise<string> {
  if (await hasActiveParking(parking.vehicleId)) throw new ActiveParkingExistsError();
  const ref = doc(parkings(requireUid()));
  await awaitOrPending(setDoc(ref, parkingToData({ ...parking, id: ref.id, status: 'active' })));
  return ref.id;
}

export async function updateParking(parking: Parking) {
  if (!parking.id) throw new Error('Falta el id del estacionamiento');
  await awaitOrPending(setDoc(doc(parkings(requireUid()), parking.id), parkingToData(parking)));
}

/** "Ya lo retiré": pasa al historial. */
export async function finishParking(id: string) {
  await awaitOrPending(
    updateDoc(doc(parkings(requireUid()), id), { status: 'finished', finishedAt: Timestamp.now() }),
  );
}

/** Guarda las rutas de las fotos (null = sin foto). */
export async function setPhotoPaths(id: string, placePhotoPath: string | null, signPhotoPath: string | null) {
  await awaitOrPending(
    updateDoc(doc(parkings(requireUid()), id), { placePhotoUrl: placePhotoPath, signPhotoUrl: signPhotoPath }),
  );
}

/** Borra el estacionamiento y sus fotos (Firestore no borra subcolecciones solo). */
export async function deleteParking(id: string) {
  await deleteAllPhotos(id);
  await awaitOrPending(deleteDoc(doc(parkings(requireUid()), id)));
}
