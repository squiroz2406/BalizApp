import { MaterialCommunityIcons } from '@expo/vector-icons';
import { BottomTabBar, createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { DarkTheme, DefaultTheme, NavigationContainer, createNavigationContainerRef } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import * as Notifications from 'expo-notifications';
import React, { useEffect } from 'react';
import { View } from 'react-native';
import { ConnectionBanner } from '../components/ui';
import { summaryLine } from '../data/models';
import { useActiveParkings, useProfile, useVehicles } from '../hooks/useData';
import { useI18n } from '../i18n';
import { syncReminders, TEST_ID, type ReminderSpec } from '../notifications/reminders';
import HomeScreen from '../screens/HomeScreen';
import MapScreen from '../screens/MapScreen';
import ParkingDetailScreen from '../screens/ParkingDetailScreen';
import ParkingFormScreen from '../screens/ParkingFormScreen';
import ProfileScreen from '../screens/ProfileScreen';
import ReturnToCarScreen from '../screens/ReturnToCarScreen';
import { DEFAULT_REMINDER_MINUTES } from '../data/models';
import { useTheme } from '../theme/ThemeProvider';

/*
 * Rutas tipadas. Tres destinos en la barra inferior y tres pantallas secundarias que se apilan encima.
 * El login no está acá: AuthGate solo muestra este navegador cuando el usuario está autenticado y verificado,
 * así que funciona como guardia de todas las pantallas.
 */
export type TabParamList = { Home: undefined; Map: undefined; Profile: undefined };
export type RootStackParamList = {
  Tabs: undefined;
  /** Alta (parkingId = undefined) o edición de un estacionamiento. */
  ParkingForm: { parkingId?: string } | undefined;
  ParkingDetail: { parkingId: string };
  ReturnToCar: { parkingId: string };
};

const Tab = createBottomTabNavigator<TabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();
export const navigationRef = createNavigationContainerRef<RootStackParamList>();

const TAB_ICONS: Record<keyof TabParamList, React.ComponentProps<typeof MaterialCommunityIcons>['name']> = {
  Home: 'home',
  Map: 'map-marker',
  Profile: 'account',
};

function Tabs() {
  const { t } = useI18n();
  const { colors } = useTheme();
  return (
    <Tab.Navigator
      // Franja de conexión sobre la barra inferior: cada pantalla ya maneja el espacio de la barra de estado.
      tabBar={(props) => (
        <View>
          <ConnectionBanner />
          <BottomTabBar {...props} />
        </View>
      )}
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.onSecondaryContainer,
        tabBarInactiveTintColor: colors.onSurfaceVariant,
        tabBarStyle: { backgroundColor: colors.surfaceVariant },
        tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name={TAB_ICONS[route.name]} size={size} color={color} />,
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} options={{ title: t('core_nav_home') }} />
      <Tab.Screen name="Map" component={MapScreen} options={{ title: t('core_nav_map') }} />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: t('core_nav_profile') }} />
    </Tab.Navigator>
  );
}

/**
 * Mantiene sincronizados los avisos de vencimiento con los estacionamientos activos: cualquier cambio (alta,
 * edición, "Ya lo retiré", minutos de aviso, idioma, otro dispositivo) vuelve a calcular las notificaciones.
 */
function RemindersSync() {
  const { t, language } = useI18n();
  const active = useActiveParkings().value;
  const vehicles = useVehicles().value;
  const profile = useProfile();
  const minutes = profile.value?.reminderMinutes ?? DEFAULT_REMINDER_MINUTES;

  useEffect(() => {
    if (profile.loading) return;
    const names = new Map(vehicles.map((v) => [v.id, v.alias]));
    const specs: ReminderSpec[] = active
      .filter((p) => p.expiresAt != null)
      .map((p) => ({
        parkingId: p.id,
        expiresAt: p.expiresAt!,
        reminderMinutes: minutes,
        vehicleName: names.get(p.vehicleId) ?? t('core_your_vehicle'),
        place: summaryLine(p),
        lang: language,
      }));
    syncReminders(specs).catch((e) => console.warn('No se pudieron programar los avisos', e));
  }, [active, vehicles, minutes, language, profile.loading]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}

/** Tocar una notificación de vencimiento abre "Volver al auto" (también con la app cerrada). */
function NotificationRouter() {
  const response = Notifications.useLastNotificationResponse();
  useEffect(() => {
    const parkingId = response?.notification.request.content.data?.parkingId as string | undefined;
    if (!parkingId || parkingId === TEST_ID) return;
    const open = () => navigationRef.navigate('ReturnToCar', { parkingId });
    if (navigationRef.isReady()) open();
    else {
      const timer = setTimeout(open, 500);
      return () => clearTimeout(timer);
    }
  }, [response]);
  return null;
}

export default function AppNavigator() {
  const { colors } = useTheme();
  const base = colors.dark ? DarkTheme : DefaultTheme;
  return (
    <NavigationContainer
      ref={navigationRef}
      theme={{
        ...base,
        colors: {
          ...base.colors,
          primary: colors.primary,
          background: colors.background,
          card: colors.surface,
          text: colors.onSurface,
          border: colors.outline,
        },
      }}
    >
      <RemindersSync />
      <NotificationRouter />
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Tabs" component={Tabs} />
        <Stack.Screen name="ParkingForm" component={ParkingFormScreen} />
        <Stack.Screen name="ParkingDetail" component={ParkingDetailScreen} />
        <Stack.Screen name="ReturnToCar" component={ReturnToCarScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
