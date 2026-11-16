import { MaterialCommunityIcons } from '@expo/vector-icons';
import React, { useEffect, useState } from 'react';
import { Linking, Platform, Pressable, View } from 'react-native';
import { useAuth } from '../auth/AuthContext';
import { Button, Card, Chip, Dialog, ErrorText, Screen, SectionTitle, TextField, Txt, VehicleIcon } from '../components/ui';
import { REMINDER_OPTIONS, type Vehicle, type VehicleType, DEFAULT_REMINDER_MINUTES } from '../data/models';
import { hasActiveParking } from '../data/parkingRepository';
import { setReminderMinutes } from '../data/userRepository';
import { deleteVehicle, saveVehicle } from '../data/vehicleRepository';
import { useProfile, useVehicles } from '../hooks/useData';
import { LANGUAGES, useI18n } from '../i18n';
import {
  canPost,
  needsExactAlarmPermission,
  openExactAlarmSettings,
  requestNotificationPermission,
  scheduleTestReminder,
} from '../notifications/reminders';
import { useTheme, type ThemeMode } from '../theme/ThemeProvider';

/** Perfil: datos de la cuenta, vehículos, avisos, apariencia e idioma. */
export default function ProfileScreen() {
  const { t, language, setLanguage } = useI18n();
  const { colors, mode, setMode } = useTheme();
  const auth = useAuth();
  const profile = useProfile().value;
  const vehicles = useVehicles().value;
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Vehicle | 'new' | null>(null);
  const [notificationsOn, setNotificationsOn] = useState(true);
  const [testSent, setTestSent] = useState(false);
  const minutes = profile?.reminderMinutes ?? DEFAULT_REMINDER_MINUTES;

  useEffect(() => {
    canPost().then(setNotificationsOn);
  }, []);

  const write = async (block: () => Promise<void>) => {
    try {
      await block();
    } catch (e) {
      console.warn('Error guardando', e);
      setError(t('profile_error_save_failed', (e as Error).message ?? ''));
    }
  };

  /** Valida y guarda. Devuelve un mensaje de error para mostrar en el diálogo, o null si está bien. */
  const saveVehicleForm = (id: string, type: VehicleType, alias: string, plate: string): string | null => {
    if (alias.trim() === '') return t('profile_error_vehicle_name_required');
    const vehicle: Vehicle = { id, type, alias: alias.trim(), plate: plate.trim().toUpperCase() || null };
    write(() => saveVehicle(vehicle));
    return null;
  };

  const removeVehicle = (id: string) =>
    write(async () => {
      if (await hasActiveParking(id)) setError(t('profile_error_vehicle_active_parking'));
      else await deleteVehicle(id);
    });

  return (
    <Screen title={t('profile_title')}>
      <Card>
        <Txt variant="title">{profile?.displayName ?? auth.userName ?? t('profile_no_name')}</Txt>
        <Txt color={colors.onSurfaceVariant}>{profile?.email ?? auth.userEmail ?? ''}</Txt>
      </Card>
      <ErrorText>{error}</ErrorText>

      <SectionTitle>{t('profile_my_vehicles')}</SectionTitle>
      {vehicles.length === 0 ? <Txt color={colors.onSurfaceVariant}>{t('profile_no_vehicles')}</Txt> : null}
      {vehicles.map((v) => (
        <View key={v.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <VehicleIcon type={v.type} />
          <View style={{ flex: 1 }}>
            <Txt variant="titleMedium">{v.alias}</Txt>
            {v.plate ? <Txt variant="small" color={colors.onSurfaceVariant}>{v.plate}</Txt> : null}
          </View>
          <Button label={t('profile_edit')} variant="text" onPress={() => setEditing(v)} />
        </View>
      ))}
      <Button label={t('profile_add_vehicle')} icon="plus" variant="outlined" onPress={() => setEditing('new')} />

      <SectionTitle>{t('profile_reminder_title')}</SectionTitle>
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        {REMINDER_OPTIONS.map((m) => (
          <Chip key={m} label={t('profile_minutes', m)} selected={minutes === m} onPress={() => write(() => setReminderMinutes(m))} />
        ))}
      </View>
      {!notificationsOn && (
        <Card tone="error">
          <Txt>{t('profile_notifications_off')}</Txt>
          <Button
            label={t('profile_enable_notifications')}
            variant="tonal"
            onPress={async () => {
              const granted = await requestNotificationPermission();
              setNotificationsOn(granted);
              if (!granted) Linking.openSettings();
            }}
          />
        </Card>
      )}
      {needsExactAlarmPermission && (
        <Card>
          <Txt variant="small">{t('profile_exact_alarms_hint')}</Txt>
          <Button label={t('profile_allow_exact_alarms')} variant="text" onPress={() => openExactAlarmSettings().catch(() => Linking.openSettings())} />
        </Card>
      )}
      <Button
        label={testSent ? t('profile_test_sent') : t('profile_send_test')}
        variant="outlined"
        icon="bell-ring-outline"
        onPress={async () => {
          if (await requestNotificationPermission()) {
            await scheduleTestReminder(10);
            setTestSent(true);
          }
        }}
      />

      <SectionTitle>{t('profile_appearance')}</SectionTitle>
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        {(['system', 'light', 'dark'] as ThemeMode[]).map((m) => (
          <Chip key={m} label={t(`core_theme_${m}`)} selected={mode === m} onPress={() => setMode(m)} />
        ))}
      </View>

      <SectionTitle>{t('profile_language')}</SectionTitle>
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        {LANGUAGES.map((l) => (
          <Chip key={l.code} label={l.nativeName} selected={language === l.code} onPress={() => setLanguage(l.code)} />
        ))}
      </View>

      <Button label={t('profile_sign_out')} variant="outlined" icon="logout" onPress={auth.signOut} style={{ marginTop: 16 }} />

      {editing && (
        <VehicleDialog
          vehicle={editing === 'new' ? null : editing}
          onSave={saveVehicleForm}
          onDelete={(id) => {
            removeVehicle(id);
            setEditing(null);
          }}
          onClose={() => setEditing(null)}
        />
      )}
    </Screen>
  );
}

function VehicleDialog({
  vehicle,
  onSave,
  onDelete,
  onClose,
}: {
  vehicle: Vehicle | null;
  onSave: (id: string, type: VehicleType, alias: string, plate: string) => string | null;
  onDelete: (id: string) => void;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const [type, setType] = useState<VehicleType>(vehicle?.type ?? 'car');
  const [alias, setAlias] = useState(vehicle?.alias ?? '');
  const [plate, setPlate] = useState(vehicle?.plate ?? '');
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  return (
    <Dialog
      visible
      title={vehicle ? t('profile_edit_vehicle') : t('profile_new_vehicle')}
      confirmLabel={t('profile_save')}
      dismissLabel={t('profile_cancel')}
      onDismiss={onClose}
      onConfirm={() => {
        const problem = onSave(vehicle?.id ?? '', type, alias, plate);
        if (problem) setError(problem);
        else onClose();
      }}
    >
      <View style={{ flexDirection: 'row', gap: 8 }}>
        {(['car', 'motorcycle'] as VehicleType[]).map((v) => (
          <Chip key={v} label={t(v === 'car' ? 'core_vehicle_car' : 'core_vehicle_motorcycle')} icon={v === 'car' ? 'car' : 'motorbike'} selected={type === v} onPress={() => setType(v)} />
        ))}
      </View>
      <TextField label={t('profile_vehicle_name')} value={alias} onChangeText={setAlias} error={!!error} />
      <TextField label={t('profile_vehicle_plate')} value={plate} onChangeText={setPlate} autoCapitalize="characters" />
      <ErrorText>{error}</ErrorText>
      {vehicle && (
        <Pressable
          accessibilityRole="button"
          onPress={() => (confirmDelete ? onDelete(vehicle.id) : setConfirmDelete(true))}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}
        >
          <MaterialCommunityIcons name="delete-outline" size={18} color={colors.error} />
          <Txt variant="label" color={colors.error}>
            {confirmDelete ? t('profile_confirm_delete') : t('profile_delete_vehicle')}
          </Txt>
        </Pressable>
      )}
    </Dialog>
  );
}
