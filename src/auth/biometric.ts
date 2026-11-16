import * as LocalAuthentication from 'expo-local-authentication';
import { t } from '../i18n';

export type BiometricResult =
  | { kind: 'success' }
  | { kind: 'cancelled' }
  | { kind: 'unavailable'; message: string }
  | { kind: 'failed'; message: string };

/**
 * Pide la huella (o el rostro) antes de continuar. Si el usuario no puede usarla, el sistema ofrece el PIN
 * o patrón del teléfono como respaldo.
 */
export async function authenticate(title: string, subtitle: string): Promise<BiometricResult> {
  const hasHardware = await LocalAuthentication.hasHardwareAsync();
  const enrolledLevel = await LocalAuthentication.getEnrolledLevelAsync();
  if (!hasHardware && enrolledLevel === LocalAuthentication.SecurityLevel.NONE) {
    return { kind: 'unavailable', message: t('auth_biometric_unavailable') };
  }
  if (enrolledLevel === LocalAuthentication.SecurityLevel.NONE) {
    return { kind: 'unavailable', message: t('auth_biometric_none_enrolled') };
  }
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: title,
    promptSubtitle: subtitle,
    cancelLabel: t('profile_cancel'),
    disableDeviceFallback: false,
  });
  if (result.success) return { kind: 'success' };
  switch (result.error) {
    case 'user_cancel':
    case 'system_cancel':
    case 'app_cancel':
      return { kind: 'cancelled' };
    case 'not_enrolled':
      return { kind: 'unavailable', message: t('auth_biometric_none_enrolled') };
    case 'not_available':
    case 'passcode_not_set':
      return { kind: 'unavailable', message: t('auth_biometric_unavailable') };
    default:
      return { kind: 'failed', message: String(result.error) };
  }
}
