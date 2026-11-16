import { useNavigation, useRoute } from '@react-navigation/native';
import React, { useEffect, useState } from 'react';
import { View } from 'react-native';
import { Base64Image } from '../components/Photo';
import { Button, Card, Dialog, EmptyState, ErrorText, Screen, Txt, VehicleIcon } from '../components/ui';
import { BalizMap } from '../components/BalizMap';
import { deleteParking, finishParking } from '../data/parkingRepository';
import { loadPhoto, type PhotoKind } from '../data/photoRepository';
import { isExpired, type Parking } from '../data/models';
import { useNow, useParking, useVehicles } from '../hooks/useData';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/ThemeProvider';
import { formatDateTime, formatDuration, formatRemaining } from '../utils/time';

/** Detalle de un estacionamiento, en tiempo real. */
export default function ParkingDetailScreen() {
  const { t } = useI18n();
  const { colors } = useTheme();
  const nav = useNavigation<any>();
  const { parkingId } = useRoute<any>().params as { parkingId: string };
  const { value: parking, loading } = useParking(parkingId);
  const vehicles = useVehicles().value;
  const now = useNow(1000);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmFinish, setConfirmFinish] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [photos, setPhotos] = useState<Partial<Record<PhotoKind, string>>>({});

  // Las fotos se cargan aparte, y de nuevo solo si cambian sus rutas (por ejemplo, al editar).
  const placePath = parking?.placePhotoPath ?? null;
  const signPath = parking?.signPhotoPath ?? null;
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const next: Partial<Record<PhotoKind, string>> = {};
      for (const [kind, path] of [['place', placePath], ['sign', signPath]] as const) {
        if (!path) continue;
        try {
          const data = await loadPhoto(parkingId, kind);
          if (data) next[kind] = data;
        } catch (e) {
          console.warn(`No se pudo cargar la foto ${kind}`, e);
        }
      }
      if (!cancelled) setPhotos(next);
    })();
    return () => {
      cancelled = true;
    };
  }, [parkingId, placePath, signPath]);

  const perform = async (message: string, block: () => Promise<void>) => {
    if (working) return;
    setWorking(true);
    setError(null);
    try {
      await block();
    } catch (e) {
      console.warn(message, e);
      setError(`${message}: ${(e as Error).message}`);
    } finally {
      setWorking(false);
    }
  };

  const vehicle = parking ? vehicles.find((v) => v.id === parking.vehicleId) : undefined;

  return (
    <Screen title={t('detail_title')} onBack={() => nav.goBack()}>
      {loading ? null : !parking ? (
        <EmptyState icon="information-outline" title={t('detail_not_found_title')} text={t('detail_not_found_text')} />
      ) : (
        <>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            {vehicle && <VehicleIcon type={vehicle.type} size={28} />}
            <Txt variant="title">{vehicle?.alias ?? t('detail_vehicle_default')}</Txt>
          </View>
          <TimeCard parking={parking} now={now} />
          {parking.location && (
            <View style={{ height: 180, borderRadius: 16, overflow: 'hidden' }}>
              <BalizMap
                center={parking.location}
                interactive={false}
                markers={[{ id: 'car', point: parking.location, color: '#E65100', vehicleType: vehicle?.type }]}
              />
            </View>
          )}
          {Object.keys(photos).length > 0 && (
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {(['place', 'sign'] as const).map((kind) =>
                photos[kind] ? (
                  <View key={kind} style={{ flex: 1, gap: 4 }}>
                    <Base64Image data={photos[kind]!} style={{ height: 140, borderRadius: 12 }} />
                    <Txt variant="small" color={colors.onSurfaceVariant}>
                      {t(kind === 'place' ? 'detail_photo_place' : 'detail_photo_sign')}
                    </Txt>
                  </View>
                ) : null,
              )}
            </View>
          )}
          <View style={{ gap: 8 }}>
            {parking.addressText && <InfoRow label={t('detail_label_address')} value={parking.addressText} />}
            <InfoRow label={t('detail_label_since')} value={formatDateTime(parking.startedAt, now)} />
            {parking.finishedAt != null && <InfoRow label={t('detail_label_finished')} value={formatDateTime(parking.finishedAt, now)} />}
            {parking.levelSector && <InfoRow label={t('detail_label_level_sector')} value={parking.levelSector} />}
            {parking.note && <InfoRow label={t('detail_label_note')} value={parking.note} />}
            {parking.ruleSummary && <InfoRow label={t('detail_label_rule')} value={parking.ruleSummary} />}
            {vehicle?.plate && <InfoRow label={t('detail_label_plate')} value={vehicle.plate} />}
          </View>
          <ErrorText>{error}</ErrorText>
          {parking.status === 'active' ? (
            <>
              <Button label={t('detail_return_to_car')} icon="compass" onPress={() => nav.navigate('ReturnToCar', { parkingId })} />
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <Button style={{ flex: 1 }} label={t('detail_edit')} variant="outlined" onPress={() => nav.navigate('ParkingForm', { parkingId })} />
                <Button style={{ flex: 1 }} label={t('detail_finish')} variant="tonal" disabled={working} onPress={() => setConfirmFinish(true)} />
              </View>
            </>
          ) : (
            <Button label={t('detail_delete_history')} variant="outlined" disabled={working} onPress={() => setConfirmDelete(true)} />
          )}
        </>
      )}

      <Dialog
        visible={confirmFinish}
        title={t('detail_confirm_finish_title')}
        text={t('detail_confirm_finish_text')}
        confirmLabel={t('detail_confirm_finish_ok')}
        dismissLabel={t('detail_cancel')}
        onDismiss={() => setConfirmFinish(false)}
        onConfirm={() => {
          setConfirmFinish(false);
          perform(t('detail_error_finish'), () => finishParking(parkingId));
        }}
      />
      <Dialog
        visible={confirmDelete}
        destructive
        title={t('detail_confirm_delete_title')}
        text={t('detail_confirm_delete_text')}
        confirmLabel={t('detail_confirm_delete_ok')}
        dismissLabel={t('detail_cancel')}
        onDismiss={() => setConfirmDelete(false)}
        onConfirm={() => {
          setConfirmDelete(false);
          perform(t('detail_error_delete'), async () => {
            await deleteParking(parkingId);
            nav.goBack();
          });
        }}
      />
    </Screen>
  );
}

function TimeCard({ parking, now }: { parking: Parking; now: number }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  let title: string;
  let subtitle: string | null = null;
  let tone: 'surface' | 'primary' | 'error' = 'primary';
  if (parking.status === 'finished') {
    title = t('detail_time_lasted', formatDuration((parking.finishedAt ?? now) - parking.startedAt));
    tone = 'surface';
  } else if (parking.expiresAt == null) {
    title = t('detail_time_no_limit');
    subtitle = t('detail_time_parked_ago', formatDuration(now - parking.startedAt));
  } else {
    title = formatRemaining(parking.expiresAt, now);
    subtitle = t('detail_time_expires', formatDateTime(parking.expiresAt, now));
    if (isExpired(parking, now)) tone = 'error';
  }
  return (
    <Card tone={tone}>
      <Txt variant="title" color={tone === 'error' ? colors.onErrorContainer : undefined}>{title}</Txt>
      {subtitle ? <Txt>{subtitle}</Txt> : null}
    </Card>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  const { colors } = useTheme();
  return (
    <View>
      <Txt variant="small" color={colors.onSurfaceVariant}>{label}</Txt>
      <Txt>{value}</Txt>
    </View>
  );
}
