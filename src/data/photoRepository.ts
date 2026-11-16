import { Bytes, Timestamp, collection, deleteDoc, doc, getDoc, setDoc } from '@react-native-firebase/firestore';
import { awaitOrPending, db, requireUid } from './firebase';

/** Qué foto de un estacionamiento: la del lugar o la del cartel. */
export type PhotoKind = 'place' | 'sign';
export const PHOTO_KINDS: PhotoKind[] = ['place', 'sign'];

/*
 * Fotos de los estacionamientos, guardadas como JPEG comprimido dentro de Firestore:
 * users/{uid}/parkings/{parkingId}/photos/{place|sign} -> { data: bytes, createdAt }.
 * Storage exige el plan Blaze; así las fotos quedan bajo las mismas reglas de seguridad por usuario.
 * Van en una subcolección aparte para que la lista de estacionamientos no las descargue.
 * En la app las fotos viajan como base64 (string) y se convierten a bytes solo al guardar.
 */
const photoDoc = (uid: string, parkingId: string, kind: PhotoKind) =>
  doc(collection(db(), 'users', uid, 'parkings', parkingId, 'photos'), kind);

/** Ruta que se guarda en el estacionamiento (placePhotoUrl / signPhotoUrl). */
export function pathFor(parkingId: string, kind: PhotoKind): string {
  return `users/${requireUid()}/parkings/${parkingId}/photos/${kind}`;
}

/** Guarda (o reemplaza) la foto y devuelve su ruta. */
export async function savePhoto(parkingId: string, kind: PhotoKind, jpegBase64: string): Promise<string> {
  await awaitOrPending(
    setDoc(photoDoc(requireUid(), parkingId, kind), {
      data: Bytes.fromBase64String(jpegBase64),
      createdAt: Timestamp.now(),
    }),
    5000,
  );
  return pathFor(parkingId, kind);
}

/** JPEG en base64 de la foto, o null si no existe. */
export async function loadPhoto(parkingId: string, kind: PhotoKind): Promise<string | null> {
  const snap = await getDoc(photoDoc(requireUid(), parkingId, kind));
  const bytes = snap.exists() ? (snap.data()?.data as Bytes | undefined) : undefined;
  return bytes ? bytes.toBase64() : null;
}

export async function deletePhoto(parkingId: string, kind: PhotoKind) {
  await awaitOrPending(deleteDoc(photoDoc(requireUid(), parkingId, kind)), 5000);
}

/** Firestore no borra subcolecciones solo: se llama antes de borrar el estacionamiento. */
export async function deleteAllPhotos(parkingId: string) {
  for (const kind of PHOTO_KINDS) await deletePhoto(parkingId, kind);
}
