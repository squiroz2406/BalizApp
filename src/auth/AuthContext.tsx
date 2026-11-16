import { onAuthStateChanged, type User } from '@react-native-firebase/auth';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { auth } from '../data/firebase';
import { ensureProfile } from '../data/userRepository';
import { t } from '../i18n';
import * as repo from './authRepository';
import { authenticate } from './biometric';
import { authErrorMessage, validateEmail, validateRegister, validateSignIn } from './authValidation';

export type AuthStatus = 'loading' | 'signedOut' | 'needsVerification' | 'signedIn';

interface AuthValue {
  status: AuthStatus;
  busy: boolean;
  userName: string | null;
  userEmail: string | null;
  error: string | null;
  message: string | null;
  clearFeedback: () => void;
  signIn: (email: string, password: string) => void;
  register: (name: string, email: string, password: string, confirm: string) => void;
  signInWithGoogle: () => void;
  sendPasswordReset: (email: string) => void;
  resendVerification: () => void;
  checkVerified: () => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

function statusFor(user: User | null): AuthStatus {
  if (!user) return 'signedOut';
  const isGoogle = user.providerData.some((p) => p.providerId === 'google.com');
  return !user.emailVerified && !isGoogle ? 'needsVerification' : 'signedIn';
}

class SilentError extends Error {}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const busyRef = useRef(false);
  // Se cuenta cuántas veces cambió el usuario para forzar el redibujo tras reload() (emailVerified).
  const [, setTick] = useState(0);

  useEffect(
    () =>
      onAuthStateChanged(auth(), (u) => {
        setUser(u);
        setReady(true);
      }),
    [],
  );

  const status: AuthStatus = !ready ? 'loading' : statusFor(user);

  // Crea users/{uid} la primera vez que entra (o actualiza nombre y correo).
  useEffect(() => {
    if (status === 'signedIn') {
      ensureProfile().catch((e) => console.warn('No se pudo crear el perfil en Firestore', e));
    }
  }, [status, user?.uid]);

  const run = useCallback(async (block: () => Promise<unknown>, success: string | null = null) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await block();
      setTick((n) => n + 1);
      setUser(auth().currentUser);
      setMessage(success);
    } catch (e) {
      if (!(e instanceof SilentError) && !(e instanceof repo.GoogleCancelledError)) {
        console.warn('Error de autenticación', e);
        setError(authErrorMessage(e));
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, []);

  const fail = (msg: string) => {
    setError(msg);
    setMessage(null);
  };

  const value = useMemo<AuthValue>(
    () => ({
      status,
      busy,
      userName: user?.displayName ?? null,
      userEmail: user?.email ?? null,
      error,
      message,
      clearFeedback: () => {
        setError(null);
        setMessage(null);
      },
      signIn: (email, password) => {
        const problem = validateSignIn(email, password);
        if (problem) return fail(problem);
        run(() => repo.signIn(email.trim(), password));
      },
      register: (name, email, password, confirm) => {
        const problem = validateRegister(name, email, password, confirm);
        if (problem) return fail(problem);
        run(() => repo.register(name.trim(), email.trim(), password));
      },
      /** Ingreso con Google: primero pide la huella y, si se verifica, abre el selector de cuentas. */
      signInWithGoogle: () =>
        run(async () => {
          const result = await authenticate(t('auth_biometric_title'), t('auth_biometric_subtitle'));
          switch (result.kind) {
            case 'success':
              return repo.signInWithGoogle();
            case 'cancelled':
              throw new SilentError();
            case 'unavailable':
              throw new Error(result.message);
            case 'failed':
              throw new Error(t('auth_error_identity_failed', result.message));
          }
        }),
      sendPasswordReset: (email) => {
        const problem = validateEmail(email);
        if (problem) return fail(problem);
        run(() => repo.sendPasswordReset(email.trim()), t('auth_msg_reset_sent'));
      },
      resendVerification: () => run(() => repo.resendVerification(), t('auth_msg_verification_sent')),
      checkVerified: () =>
        run(async () => {
          if (!(await repo.reloadIsVerified())) throw new Error('verification-pending');
        }),
      signOut: () => {
        repo.signOut().then(() => {
          setError(null);
          setMessage(null);
        });
      },
    }),
    [status, busy, user, error, message, run],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth fuera de AuthProvider');
  return ctx;
}
