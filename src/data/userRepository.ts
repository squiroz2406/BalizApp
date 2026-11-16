import { doc, getDoc, onSnapshot, serverTimestamp, setDoc, updateDoc } from '@react-native-firebase/firestore';
import { auth, db, observeUid, requireUid, type Unsubscribe } from './firebase';
import { DEFAULT_REMINDER_MINUTES, userProfileFromData, type UserProfile } from './models';

const userDoc = (uid: string) => doc(db(), 'users', uid);

/** Perfil del usuario con sesión, en tiempo real. null si no hay sesión o todavía no existe. */
export function observeProfile(cb: (profile: UserProfile | null) => void): Unsubscribe {
  let inner: Unsubscribe | null = null;
  const outer = observeUid((uid) => {
    inner?.();
    inner = null;
    if (!uid) return cb(null);
    inner = onSnapshot(
      userDoc(uid),
      (snap) => cb(snap.exists() ? userProfileFromData(uid, snap.data()) : null),
      (e) => {
        console.warn('Error leyendo el perfil', e);
        cb(null);
      },
    );
  });
  return () => {
    inner?.();
    outer();
  };
}

/** Crea users/{uid} si no existe; si existe, actualiza nombre y correo desde Auth. */
export async function ensureProfile() {
  const user = auth().currentUser;
  if (!user) return;
  const ref = userDoc(user.uid);
  const snapshot = await getDoc(ref);
  if (!snapshot.exists()) {
    await setDoc(ref, {
      displayName: user.displayName,
      email: user.email,
      reminderMinutes: DEFAULT_REMINDER_MINUTES,
      createdAt: serverTimestamp(),
    });
  } else {
    const data = snapshot.data() ?? {};
    const changes: Record<string, string> = {};
    if (user.displayName && user.displayName !== data.displayName) changes.displayName = user.displayName;
    if (user.email && user.email !== data.email) changes.email = user.email;
    if (Object.keys(changes).length) await updateDoc(ref, changes);
  }
}

export async function setReminderMinutes(minutes: number) {
  await setDoc(userDoc(requireUid()), { reminderMinutes: minutes }, { merge: true });
}
