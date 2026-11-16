import AsyncStorage from '@react-native-async-storage/async-storage';
import * as IntentLauncher from 'expo-intent-launcher';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { t } from '../i18n';

/*
 * Avisos de vencimiento con notificaciones locales programadas (sin push ni backend: suenan aunque no haya
 * internet ni la app esté abierta). Cada estacionamiento activo con vencimiento tiene dos:
 *  - WARNING: N minutos antes (N = reminderMinutes del perfil).
 *  - EXPIRED: en el momento del vencimiento.
 * Lo programado se guarda en AsyncStorage para saber qué cambió; expo-notifications vuelve a registrar las
 * alarmas cuando el teléfono se reinicia.
 */
const CHANNEL_ID = 'vencimientos';
const STORAGE_KEY = 'reminders.specs';
export const TEST_ID = 'prueba';

export interface ReminderSpec {
  parkingId: string;
  expiresAt: number;
  reminderMinutes: number;
  vehicleName: string;
  place: string | null;
  /** Idioma con el que se redactó el texto; si cambia, se vuelve a programar. */
  lang: string;
}

type Kind = 'WARNING' | 'EXPIRED';
const idFor = (parkingId: string, kind: Kind) => `${parkingId}/${kind}`;

export function specsEqual(a: ReminderSpec, b: ReminderSpec): boolean {
  return (
    a.parkingId === b.parkingId &&
    a.expiresAt === b.expiresAt &&
    a.reminderMinutes === b.reminderMinutes &&
    a.vehicleName === b.vehicleName &&
    a.place === b.place &&
    a.lang === b.lang
  );
}

/** Qué hay que cancelar y qué hay que (re)programar para pasar de [previous] a [next]. */
export function diffSpecs(previous: ReminderSpec[], next: ReminderSpec[]) {
  const prev = new Map(previous.map((s) => [s.parkingId, s]));
  const nxt = new Map(next.map((s) => [s.parkingId, s]));
  const cancel = [...prev.keys()].filter((id) => !nxt.has(id));
  const schedule = next.filter((s) => {
    const old = prev.get(s.parkingId);
    return !old || !specsEqual(old, s);
  });
  return { cancel, schedule };
}

/** Qué avisos corresponden a un vencimiento: el previo solo si todavía no pasó su hora. */
export function plannedTimes(spec: Pick<ReminderSpec, 'expiresAt' | 'reminderMinutes'>, now: number) {
  if (spec.expiresAt <= now) return [];
  const warnAt = spec.expiresAt - spec.reminderMinutes * 60_000;
  const out: { kind: Kind; at: number }[] = [];
  // Si al guardar ya queda menos tiempo que el aviso previo, solo se avisa al vencer.
  if (warnAt > now) out.push({ kind: 'WARNING', at: warnAt });
  out.push({ kind: 'EXPIRED', at: spec.expiresAt });
  return out;
}

export function setupNotifications() {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
  return ensureChannel();
}

/** El canal se crea una vez; el usuario puede ajustarlo en los Ajustes del teléfono. */
export async function ensureChannel() {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
    name: t('notif_channel_name'),
    description: t('notif_channel_description'),
    importance: Notifications.AndroidImportance.HIGH,
  });
}

/** true si la app puede mostrar notificaciones (Android 13+ pide permiso explícito). */
export async function canPost(): Promise<boolean> {
  const { status } = await Notifications.getPermissionsAsync();
  return status === 'granted';
}

export async function requestNotificationPermission(): Promise<boolean> {
  const current = await Notifications.getPermissionsAsync();
  if (current.status === 'granted') return true;
  const asked = await Notifications.requestPermissionsAsync();
  return asked.status === 'granted';
}

/** Android 12+ pide un permiso especial para alarmas exactas; sin él, el aviso puede demorarse. */
export const needsExactAlarmPermission = Platform.OS === 'android' && Number(Platform.Version) >= 31;

export async function openExactAlarmSettings(packageName = 'com.example.balizapp') {
  if (Platform.OS !== 'android') return;
  await IntentLauncher.startActivityAsync('android.settings.REQUEST_SCHEDULE_EXACT_ALARM', {
    data: `package:${packageName}`,
  });
}

async function loadStored(): Promise<ReminderSpec[]> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as ReminderSpec[]) : [];
  } catch {
    return [];
  }
}

async function store(specs: ReminderSpec[]) {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(specs));
}

async function cancelAlarms(parkingId: string) {
  await Notifications.cancelScheduledNotificationAsync(idFor(parkingId, 'WARNING')).catch(() => undefined);
  await Notifications.cancelScheduledNotificationAsync(idFor(parkingId, 'EXPIRED')).catch(() => undefined);
}

async function setAlarms(spec: ReminderSpec, now: number = Date.now()) {
  await cancelAlarms(spec.parkingId);
  for (const { kind, at } of plannedTimes(spec, now)) {
    const expired = kind === 'EXPIRED';
    const title = expired
      ? t('notif_title_expired', spec.vehicleName)
      : t('notif_title_warning', spec.vehicleName, spec.reminderMinutes);
    const body = [spec.place, t('notif_tap_to_return')].filter(Boolean).join(' · ');
    await Notifications.scheduleNotificationAsync({
      identifier: idFor(spec.parkingId, kind),
      content: {
        title,
        body,
        data: { parkingId: spec.parkingId },
        sound: true,
        priority: expired ? Notifications.AndroidNotificationPriority.HIGH : Notifications.AndroidNotificationPriority.DEFAULT,
        categoryIdentifier: undefined,
      },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: new Date(at), channelId: CHANNEL_ID },
    });
  }
}

let queue: Promise<unknown> = Promise.resolve();

/**
 * Deja programados exactamente los avisos de [specs]: agrega o actualiza los que cambiaron y cancela los
 * de estacionamientos que ya no están (retirados, borrados u otra sesión).
 */
export function syncReminders(specs: ReminderSpec[]): Promise<void> {
  const run = async () => {
    const previous = await loadStored();
    const { cancel, schedule } = diffSpecs(previous, specs);
    for (const id of cancel) await cancelAlarms(id);
    for (const spec of schedule) await setAlarms(spec);
    await store(specs);
  };
  queue = queue.then(run, run);
  return queue as Promise<void>;
}

/** Al cerrar sesión: no deben sonar avisos de la cuenta anterior. */
export function cancelAllReminders(): Promise<void> {
  const run = async () => {
    const previous = await loadStored();
    for (const s of previous) await cancelAlarms(s.parkingId);
    await store([]);
  };
  queue = queue.then(run, run);
  return queue as Promise<void>;
}

/** Aviso de prueba en [seconds] segundos, para verificar permisos y la demo. */
export async function scheduleTestReminder(seconds = 10) {
  await ensureChannel();
  await Notifications.scheduleNotificationAsync({
    identifier: TEST_ID,
    content: {
      title: t('notif_title_warning', t('notif_test_vehicle'), 15),
      body: [t('notif_test_place'), t('notif_tap_to_return')].join(' · '),
      data: { parkingId: TEST_ID },
      sound: true,
    },
    trigger: { type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds, channelId: CHANNEL_ID },
  });
}
