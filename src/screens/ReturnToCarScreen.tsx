import { useNavigation, useRoute } from '@react-navigation/native';
import { useKeepAwake } from 'expo-keep-awake';
import React, { useEffect, useRef } from 'react';
import { Animated, Easing, Linking, Platform, Pressable, View } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import { BalizMap } from '../components/BalizMap';
import { Button, Card, EmptyState, ErrorText, Screen, Segmented, Txt } from '../components/ui';
import { cardinalKey } from '../data/routeMath';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/ThemeProvider';
import { formatDistance } from '../utils/time';
import { ARRIVED_METERS, shortestRotation, type GuidanceMode } from './returnToCarLogic';
import { useReturnToCar } from './useReturnToCar';

const CAR_COLOR = '#E65100';
const ARRIVED_COLOR = '#2E7D32';

/**
 * Volver al auto: flecha de brújula que apunta al vehículo, distancia en vivo y mapa con tu posición, la del
 * auto y una línea entre ambos. Útil en interiores y playas grandes, donde el mapa solo no alcanza para orientarse.
 */
export default function ReturnToCarScreen() {
  const { t } = useI18n();
  const nav = useNavigation<any>();
  const { parkingId } = useRoute<any>().params as { parkingId: string };
  const rc = useReturnToCar(parkingId);
  useKeepAwake(); // la pantalla no se apaga mientras se camina hacia el auto

  useEffect(() => {
    if (!rc.loading && !rc.hasLocationPermission) rc.requestPermission();
  }, [rc.loading]); // eslint-disable-line react-hooks/exhaustive-deps

  // "Ya lo retiré": vuelve a Inicio, donde el registro ya aparece en el historial.
  useEffect(() => {
    if (rc.finished) nav.popToTop();
  }, [rc.finished]); // eslint-disable-line react-hooks/exhaustive-deps

  const car = rc.parking?.location ?? null;

  return (
    <Screen title={t('nav_title')} onBack={() => nav.goBack()} scroll={false}>
      {rc.loading ? null : !rc.parking || !car ? (
        <EmptyState icon="information-outline" title={t('nav_not_found_title')} text={t('nav_not_found_text')} />
      ) : (
        <>
          <Segmented<GuidanceMode>
            value={rc.mode}
            onChange={rc.setMode}
            options={[
              { value: 'direct', label: t('nav_mode_direct') },
              { value: 'streets', label: t('nav_mode_streets') },
            ]}
          />
          <GuidanceCard rc={rc} />
          <View style={{ flex: 1, borderRadius: 16, overflow: 'hidden' }}>
            <ReturnMap rc={rc} />
          </View>
          {rc.mode === 'streets' && (
            <Pressable accessibilityRole="link" onPress={() => Linking.openURL('https://www.openstreetmap.org/fixthemap')}>
              <Txt variant="small">{t('nav_attribution')}</Txt>
            </Pressable>
          )}
          <PlaceInfo rc={rc} />
          <ErrorText>{rc.error}</ErrorText>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button
              style={{ flex: 1 }}
              label={t('nav_open_maps')}
              variant="outlined"
              onPress={() => {
                const url = `https://www.google.com/maps/dir/?api=1&destination=${car.latitude},${car.longitude}&travelmode=walking`;
                Linking.openURL(url).catch(() =>
                  Linking.openURL(
                    Platform.OS === 'ios'
                      ? `maps:?q=${car.latitude},${car.longitude}`
                      : `geo:${car.latitude},${car.longitude}?q=${car.latitude},${car.longitude}`,
                  ),
                );
              }}
            />
            <Button style={{ flex: 1 }} label={t('nav_finish')} disabled={rc.working} onPress={rc.finish} />
          </View>
        </>
      )}
    </Screen>
  );
}

type RC = ReturnType<typeof useReturnToCar>;

/** Flecha + distancia + indicaciones. */
function GuidanceCard({ rc }: { rc: RC }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const showArrow = rc.mode === 'direct'; // por calles se sigue la línea del mapa: no hay brújula
  const distance = rc.shownDistance;
  const accuracy = rc.myPosition?.accuracyMeters;
  return (
    <Card tone={rc.arrived ? 'success' : 'primary'}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 16 }}>
        {showArrow && <CompassArrow rotation={rc.arrowRotation} color={rc.arrived ? ARRIVED_COLOR : CAR_COLOR} />}
        <View style={{ flex: 1, gap: 4 }}>
          <Txt variant="headline">
            {!rc.hasLocationPermission
              ? t('nav_no_location')
              : distance == null
                ? t('nav_searching_position')
                : rc.arrived
                  ? t('nav_arrived')
                  : formatDistance(distance)}
          </Txt>
          {rc.mode === 'streets' && !rc.arrived && <RouteSummary rc={rc} />}
          <Txt>{guidanceText(rc, t)}</Txt>
          {accuracy != null && accuracy > 30 && (
            <Txt variant="small" color={colors.error}>{t('nav_gps_low_accuracy', Math.round(accuracy))}</Txt>
          )}
          {!rc.hasLocationPermission && <Button label={t('nav_allow_location')} variant="outlined" onPress={rc.requestPermission} />}
        </View>
      </View>
    </Card>
  );
}

function guidanceText(rc: RC, t: (k: string, ...a: (string | number)[]) => string): string {
  const bearing = rc.guidance.bearing;
  if (!rc.hasLocationPermission) return t('nav_guide_no_permission');
  if (rc.arrived) return t('nav_guide_arrived', ARRIVED_METERS);
  if (bearing == null) return t('nav_guide_waiting_gps');
  if (rc.mode === 'streets' && rc.routeUi.route) return t('nav_guide_follow_blue_line');
  if (rc.mode === 'streets' && rc.routeUi.loading) return t('nav_calculating_route');
  if (rc.mode === 'streets') return t('nav_guide_follow_line_straight');
  if (!rc.compassAvailable) return t('nav_guide_no_compass', t(cardinalKey(bearing)));
  if (rc.compassUnreliable) return t('nav_guide_calibrate');
  return t('nav_guide_compass', t(cardinalKey(bearing)));
}

/** Distancia y tiempo por calles, estado del cálculo y botón para recalcular. */
function RouteSummary({ rc }: { rc: RC }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const { route, loading, error } = rc.routeUi;
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
      <View style={{ flex: 1 }}>
        {route ? (
          <>
            <Txt variant="label">{t('nav_route_summary', Math.max(1, Math.round(route.durationSeconds / 60)))}</Txt>
            {rc.guidance.direct != null && <Txt variant="small">{t('nav_straight_distance', formatDistance(rc.guidance.direct))}</Txt>}
          </>
        ) : loading ? (
          <Txt variant="small">{t('nav_calculating_route')}</Txt>
        ) : null}
        {loading && route ? <Txt variant="small">{t('nav_updating_route')}</Txt> : null}
        {error ? <Txt variant="small" color={colors.error}>{error}</Txt> : null}
      </View>
      {!loading && rc.myPosition ? <Button label={t('nav_recalculate')} variant="text" onPress={rc.recalculate} /> : null}
    </View>
  );
}

/** Flecha dibujada con SVG. La rotación se anima siempre por el camino más corto (de 350° a 10° gira 20°, no 340°). */
function CompassArrow({ rotation, color }: { rotation: number | null; color: string }) {
  const { colors } = useTheme();
  const accumulated = useRef(rotation ?? 0);
  const animated = useRef(new Animated.Value(rotation ?? 0)).current;
  useEffect(() => {
    if (rotation == null) return;
    accumulated.current = shortestRotation(accumulated.current, rotation);
    Animated.timing(animated, { toValue: accumulated.current, duration: 250, easing: Easing.out(Easing.quad), useNativeDriver: true }).start();
  }, [rotation, animated]);
  const spin = animated.interpolate({ inputRange: [-3600, 3600], outputRange: ['-3600deg', '3600deg'] });
  const fill = rotation != null ? color : colors.outline;
  return (
    <Animated.View style={{ width: 120, height: 120, transform: [{ rotate: rotation != null ? spin : '0deg' }] }}>
      <Svg width={120} height={120} viewBox="0 0 100 100">
        <Circle cx={50} cy={50} r={50} fill={colors.surface} />
        <Path d="M50 12 L78 70 L50 58 L22 70 Z" fill={fill} />
        <Circle cx={50} cy={86} r={4} fill={fill} />
      </Svg>
    </Animated.View>
  );
}

/** Mapa con el auto (naranja), tu posición y la ruta por calles (continua) o la línea directa (punteada). */
function ReturnMap({ rc }: { rc: RC }) {
  const car = rc.parking!.location!;
  const me = rc.myPosition;
  // Se encuadran ambos puntos una vez, cuando llega la primera posición.
  const [fitRequest, setFitRequest] = React.useState(0);
  const fitted = useRef(false);
  useEffect(() => {
    if (me && !fitted.current) {
      fitted.current = true;
      setFitRequest((n) => n + 1);
    }
  }, [me != null]); // eslint-disable-line react-hooks/exhaustive-deps
  const routePoints = rc.routePoints;
  // Cuando llega una ruta nueva, se encuadra el recorrido completo.
  useEffect(() => {
    if (routePoints.length >= 2) setFitRequest((n) => n + 1);
  }, [routePoints]);
  const showRoute = routePoints.length >= 2;
  const line = rc.arrived ? [] : showRoute ? routePoints : me ? [me, car] : [];
  const fitPoints = showRoute ? [...routePoints, car] : me ? [me, car] : [];
  return (
    <BalizMap
      center={car}
      markers={[{ id: 'car', point: car, color: CAR_COLOR, vehicleType: rc.vehicle?.type }]}
      showMyLocation={rc.hasLocationPermission}
      line={line}
      lineDashed={!showRoute}
      fitPoints={fitPoints}
      fitRequest={fitRequest}
    />
  );
}

/** Datos para encontrar el auto: piso/sector bien visible (playas de estacionamiento), dirección y nota. */
function PlaceInfo({ rc }: { rc: RC }) {
  const { t } = useI18n();
  const { colors } = useTheme();
  const parking = rc.parking!;
  const title = [rc.vehicle?.alias, rc.vehicle?.plate].filter(Boolean).join(' · ') || t('nav_vehicle_default');
  const details = [parking.addressText, parking.note].filter(Boolean).join(' · ');
  return (
    <View style={{ gap: 2 }}>
      <Txt variant="titleMedium">{title}</Txt>
      {parking.levelSector ? <Txt variant="title" color={colors.primary}>{t('nav_level_sector', parking.levelSector)}</Txt> : null}
      {details ? <Txt color={colors.onSurfaceVariant}>{details}</Txt> : null}
    </View>
  );
}
