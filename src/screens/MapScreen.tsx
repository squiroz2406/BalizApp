import { useIsFocused, useNavigation } from '@react-navigation/native';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { BalizMap, type MapMarker } from '../components/BalizMap';
import { Button, Card, Screen, Txt } from '../components/ui';
import { currentPosition, hasLocationPermission, requestLocationPermission } from '../data/locationService';
import type { GeoPosition, Parking } from '../data/models';
import { useActiveParkings, useHistory, useVehicles } from '../hooks/useData';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/ThemeProvider';
import { formatDateTime } from '../utils/time';

const ACTIVE_COLOR = '#E65100';
const HISTORY_COLOR = '#757575';

/** Mapa general: ubicación actual y marcadores de los estacionamientos guardados. */
export default function MapScreen() {
  const { t } = useI18n();
  const { colors } = useTheme();
  const nav = useNavigation<any>();
  const focused = useIsFocused();
  const active = useActiveParkings().value.filter((p) => p.location);
  const history = useHistory().value.filter((p) => p.location);
  const vehicles = useVehicles().value;
  const [permission, setPermission] = useState(false);
  const [me, setMe] = useState<GeoPosition | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [centerRequest, setCenterRequest] = useState(0);

  const refreshMyPosition = useCallback(async () => {
    if (!(await hasLocationPermission())) return;
    setPermission(true);
    const position = await currentPosition();
    if (position) setMe((prev) => position ?? prev);
  }, []);

  useEffect(() => {
    if (focused) refreshMyPosition();
  }, [focused, refreshMyPosition]);

  const requestPermission = async () => {
    const granted = await requestLocationPermission();
    setPermission(granted);
    if (granted) refreshMyPosition();
  };

  const all = useMemo(() => [...active.map((p) => ({ p, isActive: true })), ...history.map((p) => ({ p, isActive: false }))], [active, history]);
  const markers: MapMarker[] = all.map(({ p, isActive }) => ({
    id: p.id,
    point: p.location!,
    color: isActive ? ACTIVE_COLOR : HISTORY_COLOR,
    alpha: isActive ? 1 : 0.55,
    vehicleType: vehicles.find((v) => v.id === p.vehicleId)?.type,
  }));
  const selected = all.find(({ p }) => p.id === selectedId);
  const center = me ?? active[0]?.location ?? null;

  const goToMe = () => {
    setCenterRequest((n) => n + 1);
    refreshMyPosition();
  };

  return (
    <Screen title={t('map_title')} scroll={false} padded={false}>
      <View style={{ flex: 1 }}>
        <BalizMap
          center={center}
          centerRequest={centerRequest}
          markers={markers}
          showMyLocation={permission}
          onMarkerClick={setSelectedId}
          onMapClick={() => setSelectedId(null)}
        />
        {permission ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={t('map_center_on_me')}
            onPress={goToMe}
            style={{ position: 'absolute', right: 16, bottom: selected ? 190 : 24, backgroundColor: colors.primaryContainer, padding: 12, borderRadius: 16, elevation: 4 }}
          >
            <MaterialCommunityIcons name="crosshairs-gps" size={24} color={colors.onPrimaryContainer} />
          </Pressable>
        ) : (
          <Card style={{ position: 'absolute', top: 12, left: 12, right: 12, gap: 8 }}>
            <Txt>{t('map_allow_location_prompt')}</Txt>
            <Button label={t('map_allow_location')} onPress={requestPermission} />
          </Card>
        )}
        {selected && (
          <SelectedCard
            parking={selected.p}
            active={selected.isActive}
            vehicleName={vehicles.find((v) => v.id === selected.p.vehicleId)?.alias}
            onClose={() => setSelectedId(null)}
            onOpen={() => nav.navigate('ParkingDetail', { parkingId: selected.p.id })}
          />
        )}
      </View>
    </Screen>
  );
}

function SelectedCard({
  parking,
  active,
  vehicleName,
  onClose,
  onOpen,
}: {
  parking: Parking;
  active: boolean;
  vehicleName?: string;
  onClose: () => void;
  onOpen: () => void;
}) {
  const { t } = useI18n();
  const { colors } = useTheme();
  return (
    <Card style={{ position: 'absolute', left: 12, right: 12, bottom: 12, gap: 8 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <View style={{ flex: 1 }}>
          <Txt variant="titleMedium">{vehicleName ?? t('map_parking_default')}</Txt>
          <Txt variant="small" color={colors.onSurfaceVariant}>
            {active ? t('map_parked_now') : formatDateTime(parking.startedAt)}
          </Txt>
          {parking.addressText ? <Txt>{parking.addressText}</Txt> : null}
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={t('map_close')} onPress={onClose} hitSlop={12}>
          <MaterialCommunityIcons name="close" size={24} color={colors.onSurface} />
        </Pressable>
      </View>
      <Button label={t('map_view_detail')} onPress={onOpen} />
    </Card>
  );
}
