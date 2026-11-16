import { StatusBar } from 'expo-status-bar';
import React, { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from './src/auth/AuthContext';
import { AuthGate } from './src/auth/AuthScreens';
import { I18nProvider } from './src/i18n';
import AppNavigator from './src/navigation/AppNavigator';
import { setupNotifications } from './src/notifications/reminders';
import { ThemeProvider, useTheme } from './src/theme/ThemeProvider';

function Root() {
  const { colors } = useTheme();
  useEffect(() => {
    setupNotifications().catch((e) => console.warn('No se pudo crear el canal de avisos', e));
  }, []);
  return (
    <>
      <StatusBar style={colors.dark ? 'light' : 'dark'} />
      <AuthGate>
        <AppNavigator />
      </AuthGate>
    </>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <I18nProvider>
        <ThemeProvider>
          <AuthProvider>
            <Root />
          </AuthProvider>
        </ThemeProvider>
      </I18nProvider>
    </SafeAreaProvider>
  );
}
