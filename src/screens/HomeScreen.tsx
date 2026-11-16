import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import React, { useMemo, useState } from 'react';
import { FlatList, Pressable, ScrollView, View } from 'react-native';
import { Button, Card, Chip, EmptyState, Screen, SectionTitle, Txt, VehicleIcon } from '../components/ui';
import { isExpired, summaryLine, type Parking, type Vehicle } from '../data/models';
import { useActiveParkings, useHistory, useNow, useVehicles } from '../hooks/useData';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/ThemeProvider';
import { formatDateTime, formatRemaining } from '../utils/time';
import { STATUS_FILTERS, applyFilters, validVehicleFilter, type StatusFilter } from './homeFilters';

/** Inicio: estacionamientos activos, historial y filtros por vehículo y estado. */
export default function HomeScreen() {
  const { t } = useI18n();
  const { colors } = useTheme();
  const nav = useNavigation<any>();
  const active = useActiveParkings();
  const history = useHistory();
  const vehicleList = useVehicles();
  const now = useNow(1000);
  const [vehicleFilter, setVehicleFilter] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');

  const vehicles = vehicleList.value;
  const byId = useMemo(() => new Map(vehicles.map((v) => [v.id, v])), [vehicles]);
  const selectedVehicle = validVehicleFilter(vehicleFilter, vehicles);
  const filtered = applyFilters(active.value, history.value, selectedVehicle, statusFilter, now);
  const loading = active.loading || history.loading || vehicleList.loading;
  const nothingSaved = active.value.length === 0 && history.value.length === 0;
  const open = (id: string) => nav.navigate('ParkingDetail', { parkingId: id });

  return (
    <Screen title="BalizApp" scroll={false} padded={false}>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }}>
        <Button label={t('home_park_here')} icon="map-marker-plus" onPress={() => nav.navigate('ParkingForm', {})} />
        {loading ? null : nothingSaved ? (
          <EmptyState icon="car-clock" title={t('home_empty_title')} text={t('home_empty_text')} />
        ) : (
          <>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {vehicles.length > 1 && (
                <>
                  <Chip label={t('home_filter_all_vehicles')} selected={selectedVehicle == null} onPress={() => setVehicleFilter(null)} />
                  {vehicles.map((v) => (
                    <Chip key={v.id} label={v.alias} selected={selectedVehicle === v.id} onPress={() => setVehicleFilter(v.id)} />
                  ))}
                </>
              )}
            </ScrollView>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {STATUS_FILTERS.map((f) => (
                <Chip key={f} label={t(`home_filter_status_${f}`)} selected={statusFilter === f} onPress={() => setStatusFilter(f)} />
              ))}
            </ScrollView>

            {filtered.active.length === 0 && filtered.history.length === 0 ? (
              <Txt color={colors.onSurfaceVariant}>{t('home_no_results')}</Txt>
            ) : null}

            {filtered.active.length > 0 && <SectionTitle>{t('home_section_active')}</SectionTitle>}
            {filtered.active.map((p) => (
              <ActiveCard key={p.id} parking={p} vehicle={byId.get(p.vehicleId)} now={now} onPress={() => open(p.id)} />
            ))}

            {(statusFilter === 'all' || statusFilter === 'finished') && (
              <>
                <SectionTitle>{t('home_section_history')}</SectionTitle>
                {filtered.history.length === 0 ? (
                  <Txt color={colors.onSurfaceVariant}>{t('home_history_empty')}</Txt>
                ) : (
                  <FlatList
                    scrollEnabled={false}
                    data={filtered.history}
                    keyExtractor={(p) => p.id}
                    renderItem={({ item }) => <HistoryItem parking={item} vehicle={byId.get(item.vehicleId)} onPress={() => open(item.id)} />}
                  />
                )}
              </>
            )}
          </>
        )}
      </ScrollView>
    </Screen>
  );
}

function ActiveCard({ parking, vehicle, now, onPress }: { parking: Parking; vehicle?: Vehicle; now: number; onPress: () => void }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const expired = isExpired(parking, now);
  const summary = summaryLine(parking);
  return (
    <Card tone={expired ? 'error' : 'primary'} onPress={onPress}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        {vehicle && <VehicleIcon type={vehicle.type} size={32} />}
        <View style={{ flex: 1 }}>
          <Txt variant="titleMedium">{vehicle?.alias ?? t('core_vehicle_default')}</Txt>
          {summary ? <Txt>{summary}</Txt> : null}
          <Txt variant="small">{t('home_since', formatDateTime(parking.startedAt, now))}</Txt>
        </View>
        {parking.expiresAt != null && (
          <Txt variant="label" color={expired ? colors.error : colors.onPrimaryContainer} style={{ maxWidth: 110, textAlign: 'right' }}>
            {formatRemaining(parking.expiresAt, now)}
          </Txt>
        )}
      </View>
    </Card>
  );
}

function HistoryItem({ parking, vehicle, onPress }: { parking: Parking; vehicle?: Vehicle; onPress: () => void }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const subtitle = [vehicle?.alias, formatDateTime(parking.startedAt)].filter(Boolean).join(' · ');
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10 }}>
      {vehicle ? <VehicleIcon type={vehicle.type} /> : <MaterialCommunityIcons name="car" size={24} color={colors.outline} />}
      <View style={{ flex: 1 }}>
        <Txt variant="titleMedium">{summaryLine(parking) ?? vehicle?.alias ?? t('core_parking_default')}</Txt>
        <Txt variant="small" color={colors.onSurfaceVariant}>{subtitle}</Txt>
      </View>
    </Pressable>
  );
}
