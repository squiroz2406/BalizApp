import DateTimePicker from '@react-native-community/datetimepicker';
import { useNavigation, useRoute } from '@react-navigation/native';
import React, { useEffect, useState } from 'react';
import { View } from 'react-native';
import { BalizMap } from '../components/BalizMap';
import { Base64Image } from '../components/Photo';
import { Button, Card, Chip, EmptyState, ErrorText, Screen, SectionTitle, TextField, Txt, VehicleIcon } from '../components/ui';
import type { PhotoKind } from '../data/photoRepository';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/ThemeProvider';
import { formatDateTime } from '../utils/time';
import { NO_LIMIT, PRESETS_MINUTES } from './formLogic';
import { useParkingForm } from './useParkingForm';

/** Alta y edición de un estacionamiento. */
export default function ParkingFormScreen() {
  const { t } = useI18n();
  const { colors } = useTheme();
  const nav = useNavigation<any>();
  const { parkingId } = (useRoute<any>().params ?? {}) as { parkingId?: string };
  const form = useParkingForm(parkingId);
  const s = form.state;
  const [pickingTime, setPickingTime] = useState(false);

  // Al guardar: al editar se vuelve atrás; al crear, el formulario se reemplaza por el detalle del nuevo registro.
  useEffect(() => {
    if (!s.savedId) return;
    if (s.isEdit) nav.goBack();
    else nav.replace('ParkingDetail', { parkingId: s.savedId });
  }, [s.savedId]); // eslint-disable-line react-hooks/exhaustive-deps

  const title = s.isEdit ? t('form_title_edit') : t('form_title_new');

  if (!s.loading && s.vehicles.length === 0 && !s.isEdit) {
    return (
      <Screen title={title} onBack={() => nav.goBack()}>
        <EmptyState icon="car-off" title={t('form_no_vehicles_title')} text={t('form_no_vehicles_body')} />
        <Button label={t('form_go_to_profile')} onPress={() => nav.navigate('Tabs', { screen: 'Profile' })} />
      </Screen>
    );
  }

  return (
    <Screen title={title} onBack={() => nav.goBack()}>
      {s.loading ? null : (
        <>
          <SectionTitle>{t('form_section_vehicle')}</SectionTitle>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {s.vehicles.map((v) => (
              <Chip key={v.id} label={v.alias} selected={s.vehicleId === v.id} onPress={() => form.setVehicle(v.id)} />
            ))}
          </View>

          <SectionTitle>{t('form_section_location')}</SectionTitle>
          <LocationSection form={form} />

          <SectionTitle>{t('form_section_photos')}</SectionTitle>
          <PhotoSlot
            kind="place"
            label={t('form_place_photo_label')}
            helper={t('form_place_photo_helper')}
            photo={s.placePhoto}
            busy={s.processingPhoto === 'place'}
            onPick={form.pickPhoto}
            onRemove={form.removePhoto}
          />
          {s.placePhoto && (
            <Button
              label={s.describingPlace ? t('form_analyzing') : t('form_describe_place_ai')}
              icon="creation"
              variant="tonal"
              loading={s.describingPlace}
              onPress={form.describePlace}
            />
          )}
          <PhotoSlot
            kind="sign"
            label={t('form_sign_photo_label')}
            helper={t('form_sign_photo_helper')}
            photo={s.signPhoto}
            busy={s.processingPhoto === 'sign'}
            onPick={form.pickPhoto}
            onRemove={form.removePhoto}
          />
          {s.signPhoto && (
            <Button
              label={s.analyzingSign ? t('form_analyzing') : t('form_read_sign_ai')}
              icon="creation"
              variant="tonal"
              loading={s.analyzingSign}
              onPress={form.analyzeSign}
            />
          )}
          {s.signAnalysis && (
            <Card tone={s.signAnalysis.verdict === 'allowed' ? 'success' : s.signAnalysis.verdict === 'forbidden' ? 'error' : 'surface'}>
              <Txt variant="titleMedium">
                {t(`form_verdict_${s.signAnalysis.verdict}`)}
              </Txt>
              {s.signAnalysis.explanation ? <Txt>{s.signAnalysis.explanation}</Txt> : null}
              <Txt variant="small" color={colors.onSurfaceVariant}>
                {t(s.signAnalysis.confidence === 'low' ? 'form_ai_review_note_low' : 'form_ai_review_note')}
              </Txt>
            </Card>
          )}

          <SectionTitle>{t('form_section_time_limit')}</SectionTitle>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            <Chip label={t('form_no_limit')} selected={s.selectedPreset === NO_LIMIT} onPress={form.setNoLimit} />
            {PRESETS_MINUTES.map((m) => (
              <Chip key={m} label={m % 60 === 0 ? `${m / 60} h` : `${m} min`} selected={s.selectedPreset === m} onPress={() => form.setPreset(m)} />
            ))}
            <Chip label={t('form_pick_time')} icon="clock-outline" selected={s.selectedPreset == null && s.expiresAt != null} onPress={() => setPickingTime(true)} />
          </View>
          <Txt variant="small" color={colors.onSurfaceVariant}>
            {s.expiresAt != null ? t('form_expires_info', formatDateTime(s.expiresAt)) : t('form_no_expiry_info')}
          </Txt>
          {pickingTime && (
            <DateTimePicker
              value={s.expiresAt ? new Date(s.expiresAt) : new Date()}
              mode="time"
              is24Hour
              onChange={(event, date) => {
                setPickingTime(false);
                if (event.type === 'set' && date) form.setExpiryTime(date.getHours(), date.getMinutes());
              }}
            />
          )}

          <TextField label={t('form_rule_label')} placeholder={t('form_rule_placeholder')} value={s.ruleSummary} onChangeText={form.setRuleSummary} />
          <TextField label={t('form_level_label')} value={s.levelSector} onChangeText={form.setLevelSector} />
          <TextField label={t('form_note_label')} placeholder={t('form_note_placeholder')} value={s.note} onChangeText={form.setNote} multiline />

          <ErrorText>{s.error}</ErrorText>
          <Button
            label={s.isEdit ? t('form_save_changes') : t('form_save_parking')}
            loading={s.saving}
            disabled={s.processingPhoto != null}
            onPress={form.save}
          />
        </>
      )}
    </Screen>
  );
}

function LocationSection({ form }: { form: ReturnType<typeof useParkingForm> }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const s = form.state;
  const sourceText =
    s.positionSource === 'gps'
      ? s.position?.accuracyMeters != null
        ? t('form_source_gps_accuracy', Math.round(s.position.accuracyMeters))
        : t('form_source_gps')
      : s.positionSource === 'manual'
        ? t('form_source_manual')
        : s.positionSource === 'saved'
          ? t('form_source_saved')
          : null;
  return (
    <View style={{ gap: 8 }}>
      <View style={{ height: 220, borderRadius: 16, overflow: 'hidden' }}>
        <BalizMap
          center={s.position}
          zoom={17}
          showMyLocation={s.hasLocationPermission}
          onMapClick={(p) => form.setManualPosition(p.latitude, p.longitude)}
          markers={s.position ? [{ id: 'car', point: s.position, color: '#E65100' }] : []}
        />
      </View>
      {s.locating ? (
        <Txt color={colors.onSurfaceVariant}>{t('form_locating')}</Txt>
      ) : s.position == null ? (
        <Txt color={colors.onSurfaceVariant}>{s.hasLocationPermission ? t('form_tap_map_hint') : t('form_no_permission_hint')}</Txt>
      ) : (
        <View>
          {sourceText ? <Txt variant="small" color={colors.onSurfaceVariant}>{sourceText}</Txt> : null}
          <Txt>{s.addressText ?? t('form_address_unavailable')}</Txt>
        </View>
      )}
      <View style={{ flexDirection: 'row', gap: 8 }}>
        {s.hasLocationPermission ? (
          <Button label={t('form_use_gps')} icon="crosshairs-gps" variant="outlined" loading={s.locating} onPress={form.locate} />
        ) : (
          <Button label={t('form_allow')} variant="outlined" onPress={form.requestPermission} />
        )}
      </View>
    </View>
  );
}

function PhotoSlot({
  kind,
  label,
  helper,
  photo,
  busy,
  onPick,
  onRemove,
}: {
  kind: PhotoKind;
  label: string;
  helper: string;
  photo: string | null;
  busy: boolean;
  onPick: (kind: PhotoKind, source: 'camera' | 'gallery') => void;
  onRemove: (kind: PhotoKind) => void;
}) {
  const { t } = useI18n();
  const { colors } = useTheme();
  return (
    <View style={{ gap: 8 }}>
      <Txt variant="titleMedium">{label}</Txt>
      {photo ? (
        <>
          <Base64Image data={photo} style={{ height: 180, borderRadius: 12 }} />
          <Button label={t('form_remove')} variant="text" icon="delete-outline" onPress={() => onRemove(kind)} />
        </>
      ) : (
        <>
          <Txt variant="small" color={colors.onSurfaceVariant}>{helper}</Txt>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button style={{ flex: 1 }} label={t('form_camera')} icon="camera" variant="outlined" loading={busy} onPress={() => onPick(kind, 'camera')} />
            <Button style={{ flex: 1 }} label={t('form_gallery')} icon="image" variant="outlined" disabled={busy} onPress={() => onPick(kind, 'gallery')} />
          </View>
        </>
      )}
    </View>
  );
}
