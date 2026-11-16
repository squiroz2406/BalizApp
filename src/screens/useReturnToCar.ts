import { useIsFocused } from '@react-navigation/native';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { isCompassAvailable, watchCompass, type CompassReading } from '../data/compass';
import { hasLocationPermission, requestLocationPermission, watchPosition } from '../data/locationService';
import type { GeoPosition, Parking, Vehicle } from '../data/models';
import { finishParking, observeParking } from '../data/parkingRepository';
import { loadRoute, saveRoute } from '../data/routeCache';
import { closestPoint } from '../data/routeMath';
import { walkingRoute, type WalkingRoute } from '../data/routeRepository';
import { observeVehicles } from '../data/vehicleRepository';
import { t } from '../i18n';
import {
  ARRIVED_METERS,
  SAVED_ROUTE_MAX_DISTANCE_METERS,
  arrowRotation,
  computeGuidance,
  decideRouteAction,
  shownDistance,
  type GuidanceMode,
} from './returnToCarLogic';

export interface RouteUi {
  loading: boolean;
  route: WalkingRoute | null;
  error: string | null;
}

/**
 * Volver al auto: combina el estacionamiento, el GPS en tiempo real, la brújula y, en modo "Por calles", la ruta
 * a pie calculada con OSRM. GPS, sensores y ruta solo están activos mientras la pantalla está visible.
 */
export function useReturnToCar(parkingId: string) {
  const focused = useIsFocused();
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setAppActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const live = focused && appActive;

  const [loading, setLoading] = useState(true);
  const [parking, setParking] = useState<Parking | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [permission, setPermission] = useState(false);
  const [me, setMe] = useState<GeoPosition | null>(null);
  const [compassAvailable, setCompassAvailable] = useState(false);
  const [compass, setCompass] = useState<CompassReading | null>(null);
  const [mode, setMode] = useState<GuidanceMode>('direct');
  const [recalc, setRecalc] = useState(0);
  const [routeUi, setRouteUi] = useState<RouteUi>({ loading: false, route: null, error: null });
  const [working, setWorking] = useState(false);
  const [finished, setFinished] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(
    () =>
      observeParking(parkingId, (p) => {
        setParking(p);
        setLoading(false);
      }),
    [parkingId],
  );
  useEffect(() => observeVehicles(setVehicles), []);
  useEffect(() => {
    hasLocationPermission().then(setPermission);
    isCompassAvailable().then(setCompassAvailable);
  }, []);

  // GPS y brújula solo mientras la pantalla está visible.
  useEffect(() => {
    if (!live || !permission) return;
    return watchPosition(setMe);
  }, [live, permission]);
  useEffect(() => {
    if (!live || !permission || !compassAvailable) return;
    return watchCompass(setCompass);
  }, [live, permission, compassAvailable]);

  const car: GeoPosition | null = parking?.location ?? null;

  // --- Ruta por calles ---
  const lastRoute = useRef<WalkingRoute | null>(null);
  const lastDestination = useRef<GeoPosition | null>(null);
  const lastRequestAt = useRef(0);
  const handledRecalc = useRef(0);
  const inFlight = useRef(false);
  const [rerun, setRerun] = useState(0);

  useEffect(() => {
    if (!live) return;
    const action = decideRouteAction({
      mode,
      car,
      me,
      current: lastRoute.current,
      lastDestination: lastDestination.current,
      forced: recalc !== handledRecalc.current,
      now: Date.now(),
      lastRequestAt: lastRequestAt.current,
    });
    if (action === 'idle') return setRouteUi((u) => (u.loading || u.route || u.error ? { loading: false, route: null, error: null } : u));
    if (action === 'wait-position') return setRouteUi({ loading: true, route: null, error: null });
    if (action === 'reuse') return setRouteUi({ loading: false, route: lastRoute.current, error: null });
    if (inFlight.current) return;

    const savedRouteFor = async () => {
      if (!car || !me) return null;
      const saved = await loadRoute(parkingId, car);
      return saved && closestPoint(saved.points, me).distance <= SAVED_ROUTE_MAX_DISTANCE_METERS ? saved : null;
    };

    if (action === 'cooldown') {
      // Falló hace poco: no insistir hasta que pase el intervalo.
      savedRouteFor().then((saved) =>
        setRouteUi(
          saved
            ? { loading: false, route: saved, error: t('nav_route_saved_offline') }
            : { loading: false, route: null, error: t('nav_route_error_retry') },
        ),
      );
      return;
    }

    // action === 'fetch'
    handledRecalc.current = recalc;
    lastRequestAt.current = Date.now();
    inFlight.current = true;
    setRouteUi({ loading: true, route: lastRoute.current, error: null });
    (async () => {
      try {
        const route = await walkingRoute(me!, car!.latitude, car!.longitude);
        lastRoute.current = route;
        lastDestination.current = car;
        saveRoute(parkingId, car!, route);
        setRouteUi({ loading: false, route, error: null });
      } catch (e) {
        console.warn('No se pudo calcular la ruta', e);
        const saved = lastRoute.current ?? (await savedRouteFor());
        setRouteUi(
          saved
            ? { loading: false, route: saved, error: t('nav_route_saved_offline') }
            : { loading: false, route: null, error: t('nav_route_error_offline') },
        );
      } finally {
        inFlight.current = false;
        setRerun((n) => n + 1);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [live, mode, me, car?.latitude, car?.longitude, recalc, rerun]);

  const guidance = useMemo(
    () => computeGuidance(car, me, mode, routeUi.route),
    [car, me, mode, routeUi.route],
  );
  const heading = compass ? compass.headingDegrees : null;

  const requestPermission = useCallback(async () => {
    setPermission(await requestLocationPermission());
  }, []);

  const recalculate = useCallback(() => {
    if (Date.now() - lastRequestAt.current < 3000) return; // evita toques repetidos
    setRecalc((n) => n + 1);
  }, []);

  /** "Ya lo retiré" desde esta pantalla. */
  const finish = useCallback(async () => {
    if (working || !parking || parking.status !== 'active') return;
    setWorking(true);
    setError(null);
    try {
      await finishParking(parkingId);
      setFinished(true);
    } catch (e) {
      console.warn('No se pudo marcar como retirado', e);
      setError(t('nav_finish_error', (e as Error).message ?? ''));
    } finally {
      setWorking(false);
    }
  }, [working, parking, parkingId]);

  return {
    loading,
    parking,
    vehicle: parking ? vehicles.find((v) => v.id === parking.vehicleId) ?? null : null,
    hasLocationPermission: permission,
    myPosition: me,
    mode,
    setMode,
    guidance,
    shownDistance: shownDistance(mode, guidance),
    arrowRotation: arrowRotation(guidance.bearing, heading),
    arrived: (guidance.direct ?? Number.MAX_VALUE) <= ARRIVED_METERS,
    routeUi,
    routePoints: mode === 'streets' ? routeUi.route?.points ?? [] : [],
    compassAvailable,
    compassUnreliable: compass?.unreliable === true,
    working,
    finished,
    error,
    requestPermission,
    recalculate,
    finish,
  };
}
