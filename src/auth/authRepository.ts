import { GoogleSignin } from '@react-native-google-signin/google-signin';
import {
  GoogleAuthProvider,
  createUserWithEmailAndPassword,
  reload,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithCredential,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  updateProfile,
} from '@react-native-firebase/auth';
import { googleWebClientId } from '../config/firebaseWeb';
import { auth } from '../data/firebase';
import { t } from '../i18n';
import { cancelAllReminders } from '../notifications/reminders';

export async function signIn(email: string, password: string) {
  return (await signInWithEmailAndPassword(auth(), email, password)).user;
}

export async function register(name: string, email: string, password: string) {
  const user = (await createUserWithEmailAndPassword(auth(), email, password)).user;
  await updateProfile(user, { displayName: name });
  await sendEmailVerification(user);
  return user;
}

export class GoogleCancelledError extends Error {}

/** Inicia sesión (o registra, si es la primera vez) con una cuenta de Google. */
export async function signInWithGoogle() {
  if (!googleWebClientId) throw new Error(t('auth_error_missing_google_config'));
  GoogleSignin.configure({ webClientId: googleWebClientId });
  await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
  const response = await GoogleSignin.signIn();
  if (response.type !== 'success') throw new GoogleCancelledError();
  const idToken = response.data.idToken;
  if (!idToken) throw new Error(t('auth_error_unsupported_credential'));
  return (await signInWithCredential(auth(), GoogleAuthProvider.credential(idToken))).user;
}

export async function sendPasswordReset(email: string) {
  await sendPasswordResetEmail(auth(), email);
}

export async function resendVerification() {
  const user = auth().currentUser;
  if (user) await sendEmailVerification(user);
}

/** Refresca el usuario desde el servidor; devuelve true si el email ya fue verificado. */
export async function reloadIsVerified(): Promise<boolean> {
  const user = auth().currentUser;
  if (!user) return false;
  await reload(user);
  return auth().currentUser?.emailVerified === true;
}

export async function signOut() {
  await cancelAllReminders().catch(() => undefined);
  await firebaseSignOut(auth());
  try {
    await GoogleSignin.signOut();
  } catch {
    // no había sesión de Google
  }
}
