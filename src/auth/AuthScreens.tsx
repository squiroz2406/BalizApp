import React, { useEffect, useState } from 'react';
import { View } from 'react-native';
import { Button, ErrorText, Screen, TextField, Txt } from '../components/ui';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/ThemeProvider';
import { useAuth } from './AuthContext';

type Page = 'login' | 'register' | 'reset';

/**
 * Guardia de la app: sin sesión válida muestra login/registro/recuperar contraseña; con sesión sin verificar,
 * la pantalla de verificación. Solo muestra [children] cuando el usuario está autenticado y verificado.
 */
export function AuthGate({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const [page, setPage] = useState<Page>('login');

  // Los mensajes de una pantalla no deben quedar al pasar a otra.
  useEffect(() => auth.clearFeedback(), [page]); // eslint-disable-line react-hooks/exhaustive-deps

  if (auth.status === 'loading') return <Screen><View /></Screen>;
  if (auth.status === 'signedIn') return <>{children}</>;
  if (auth.status === 'needsVerification') return <VerifyScreen />;
  if (page === 'register') return <RegisterScreen onNavigate={setPage} />;
  if (page === 'reset') return <ResetScreen onNavigate={setPage} />;
  return <LoginScreen onNavigate={setPage} />;
}

function Feedback() {
  const { error, message } = useAuth();
  const { colors } = useTheme();
  return (
    <>
      <ErrorText>{error}</ErrorText>
      {message ? <Txt color={colors.success}>{message}</Txt> : null}
    </>
  );
}

function Divider() {
  const { t } = useI18n();
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <View style={{ flex: 1, height: 1, backgroundColor: colors.outline }} />
      <Txt variant="small" color={colors.onSurfaceVariant}>{t('auth_or')}</Txt>
      <View style={{ flex: 1, height: 1, backgroundColor: colors.outline }} />
    </View>
  );
}

function LoginScreen({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const { t } = useI18n();
  const auth = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  return (
    <Screen title={t('auth_title_login')}>
      <TextField label={t('auth_email')} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" autoComplete="email" />
      <TextField label={t('auth_password')} value={password} onChangeText={setPassword} secureTextEntry autoCapitalize="none" autoComplete="password" />
      <Feedback />
      <Button label={t('auth_login_button')} loading={auth.busy} onPress={() => auth.signIn(email, password)} />
      <Button label={t('auth_forgot_link')} variant="text" onPress={() => onNavigate('reset')} />
      <Divider />
      <Button label={t('auth_continue_google')} variant="outlined" icon="google" disabled={auth.busy} onPress={auth.signInWithGoogle} />
      <Button label={t('auth_register_link')} variant="text" onPress={() => onNavigate('register')} />
    </Screen>
  );
}

function RegisterScreen({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const { t } = useI18n();
  const auth = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  return (
    <Screen title={t('auth_title_register')}>
      <TextField label={t('auth_name')} value={name} onChangeText={setName} autoCapitalize="words" autoComplete="name" />
      <TextField label={t('auth_email')} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" autoComplete="email" />
      <TextField label={t('auth_password')} value={password} onChangeText={setPassword} secureTextEntry autoCapitalize="none" />
      <TextField label={t('auth_repeat_password')} value={confirm} onChangeText={setConfirm} secureTextEntry autoCapitalize="none" />
      <Feedback />
      <Button label={t('auth_register_button')} loading={auth.busy} onPress={() => auth.register(name, email, password, confirm)} />
      <Divider />
      <Button label={t('auth_register_google')} variant="outlined" icon="google" disabled={auth.busy} onPress={auth.signInWithGoogle} />
      <Button label={t('auth_have_account_link')} variant="text" onPress={() => onNavigate('login')} />
    </Screen>
  );
}

function ResetScreen({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const { t } = useI18n();
  const auth = useAuth();
  const { colors } = useTheme();
  const [email, setEmail] = useState('');
  return (
    <Screen title={t('auth_title_reset')} onBack={() => onNavigate('login')}>
      <Txt color={colors.onSurfaceVariant}>{t('auth_reset_info')}</Txt>
      <TextField label={t('auth_email')} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" />
      <Feedback />
      <Button label={t('auth_send_link')} loading={auth.busy} onPress={() => auth.sendPasswordReset(email)} />
      <Button label={t('auth_back')} variant="text" onPress={() => onNavigate('login')} />
    </Screen>
  );
}

function VerifyScreen() {
  const { t } = useI18n();
  const auth = useAuth();
  const { colors } = useTheme();
  return (
    <Screen title={t('auth_title_verify')}>
      <Txt color={colors.onSurfaceVariant}>{t('auth_verify_info', auth.userEmail ?? '')}</Txt>
      <Feedback />
      <Button label={t('auth_verified_button')} loading={auth.busy} onPress={auth.checkVerified} />
      <Button label={t('auth_resend')} variant="outlined" disabled={auth.busy} onPress={auth.resendVerification} />
      <Button label={t('auth_sign_out')} variant="text" onPress={auth.signOut} />
    </Screen>
  );
}
