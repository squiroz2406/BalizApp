import { getApp } from '@react-native-firebase/app';
import { getAuth, onAuthStateChanged, type User } from '@react-native-firebase/auth';
import { getFirestore } from '@react-native-firebase/firestore';
import { t } from '../i18n';

/*
 * Acceso único a Firebase. @react-native-firebase usa los SDK nativos, así que Firestore guarda su cache
 * persistente en disco (igual que la app Kotlin) y la app funciona sin conexión.
 */
export const auth = () => getAuth(getApp());
export const db = () => getFirestore(getApp());

export type Unsubscribe = () => void;

export function requireUid(): string {
  const uid = auth().currentUser?.uid;
  if (!uid) throw new Error(t('form_err_no_session'));
  return uid;
}

/** uid del usuario con sesión, o null. Avisa de nuevo cada vez que cambia la sesión. */
export function observeUid(cb: (uid: string | null) => void): Unsubscribe {
  let last: string | null | undefined;
  return onAuthStateChanged(auth(), (user: User | null) => {
    const uid = user?.uid ?? null;
    if (uid !== last) {
      last = uid;
      cb(uid);
    }
  });
}

/**
 * Firestore guarda primero en el cache local y después sincroniza. Sin conexión, la promesa recién termina
 * cuando vuelve la red; para no congelar la pantalla se espera un rato y, si no hubo respuesta, se da por
 * buena la escritura (queda pendiente de sincronizar). Un error real (por ejemplo, las reglas) sí se propaga.
 */
export function awaitOrPending<T>(promise: Promise<T>, ms = 3000): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => resolve(undefined), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}

/** Igual que awaitOrPending pero para lecturas: si el servidor tarda, devuelve undefined para usar el cache. */
export const withTimeout = awaitOrPending;
