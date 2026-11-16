import { t } from '../i18n';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(email: string): string | null {
  if (email.trim() === '') return t('auth_error_email_required');
  if (!EMAIL.test(email.trim())) return t('auth_error_email_invalid');
  return null;
}

/** Valida el formulario de ingreso; devuelve el mensaje de error o null. */
export function validateSignIn(email: string, password: string): string | null {
  return validateEmail(email) ?? (password === '' ? t('auth_error_password_required') : null);
}

/** Valida el formulario de registro; devuelve el mensaje de error o null. */
export function validateRegister(name: string, email: string, password: string, confirm: string): string | null {
  if (name.trim() === '') return t('auth_error_name_required');
  const emailError = validateEmail(email);
  if (emailError) return emailError;
  if (password.length < 6) return t('auth_error_password_short');
  if (password !== confirm) return t('auth_error_password_mismatch');
  return null;
}

/** Traduce los códigos de error de Firebase Auth a un texto para el usuario. */
export function authErrorMessage(e: unknown): string {
  const err = e as { code?: string; message?: string } | undefined;
  const code = err?.code ?? '';
  switch (code) {
    case 'auth/weak-password':
      return t('auth_error_weak_password');
    case 'auth/email-already-in-use':
      return t('auth_error_account_exists');
    case 'auth/user-not-found':
      return t('auth_error_no_account');
    case 'auth/wrong-password':
    case 'auth/invalid-credential':
    case 'auth/invalid-login-credentials':
      return t('auth_error_wrong_credentials');
    case 'auth/invalid-email':
      return t('auth_error_email_invalid');
    case 'auth/network-request-failed':
      return t('auth_error_network');
  }
  if (err?.message === 'verification-pending') return t('auth_error_verification_pending');
  if (code.startsWith('auth/')) return t('auth_error_auth_code', code.replace('auth/', ''));
  return err?.message || t('auth_error_unexpected');
}
