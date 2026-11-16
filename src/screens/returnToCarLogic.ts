import type { GeoPosition } from '../data/models';
import { MIN_INTERVAL_MS, type WalkingRoute } from '../data/routeRepository';
import { bearing as bearingBetween, closestPoint, distance, nextTarget, normalizeDegrees, remainingMeters } from '../data/routeMath';

export type GuidanceMode = 'direct' | 'streets';

/** Distancia a la que se considera que el usuario llegó. */
export const ARRIVED_METERS = 15;
/** Si el usuario se aleja más que esto de la línea, se recalcula la ruta. */
export const OFF_ROUTE_METERS = 40;
/** Más lejos que esto del trazado guardado, el recorrido ya no sirve de guía. */
export const SAVED_ROUTE_MAX_DISTANCE_METERS = 150;

export type RouteAction =
  | 'idle' // no corresponde ruta (modo directo o sin ubicación del auto)
  | 'wait-position' // en modo calles, esperando el GPS
  | 'reuse' // la ruta actual sirve
  | 'cooldown' // falló hace poco: no insistir hasta que pase el intervalo
  | 'fetch'; // hay que pedir una ruta nueva

export interface RouteDecisionInput {
  mode: GuidanceMode;
  car: GeoPosition | null;
  me: GeoPosition | null;
  current: WalkingRoute | null;
  lastDestination: GeoPosition | null;
  /** El usuario tocó "Recalcular". */
  forced: boolean;
  now: number;
  lastRequestAt: number;
}

/**
 * Decide qué hacer con la ruta por calles: pedirla al activar el modo y volver a pedirla si el usuario se desvía
 * más de OFF_ROUTE_METERS de la línea o toca "Recalcular", respetando el intervalo mínimo entre pedidos.
 */
export function decideRouteAction(i: RouteDecisionInput): RouteAction {
  if (i.mode !== 'streets' || !i.car) return 'idle';
  if (!i.me) return 'wait-position';
  const { current } = i;
  const offRoute = current != null && closestPoint(current.points, i.me).distance > OFF_ROUTE_METERS;
  const destinationChanged = i.lastDestination == null || distance(i.lastDestination, i.car) > 0.5;
  const needsNew =
    current == null || i.forced || destinationChanged || (offRoute && i.now - i.lastRequestAt >= MIN_INTERVAL_MS);
  if (!needsNew) return 'reuse';
  if (!i.forced && current == null && i.lastRequestAt !== 0 && i.now - i.lastRequestAt < MIN_INTERVAL_MS) return 'cooldown';
  return 'fetch';
}

export interface Guidance {
  /** Distancia en línea recta hasta el vehículo, en metros. */
  direct: number | null;
  /** Rumbo hacia donde apunta la flecha (auto o próximo tramo de la ruta), respecto del norte geográfico. */
  bearing: number | null;
  /** Lo que falta por calles (solo en modo calles con ruta calculada). */
  remaining: number | null;
}

export function computeGuidance(
  car: GeoPosition | null,
  me: GeoPosition | null,
  mode: GuidanceMode,
  route: WalkingRoute | null,
): Guidance {
  if (!car || !me) return { direct: null, bearing: null, remaining: null };
  const direct = distance(me, car);
  let bearing = bearingBetween(me, car);
  let remaining: number | null = null;
  if (mode === 'streets' && route && route.points.length >= 2) {
    remaining = remainingMeters(route.points, me);
    // La flecha apunta al próximo tramo de la ruta, no al auto en línea recta.
    const target = nextTarget(route.points, me);
    if (target) bearing = bearingBetween(me, target);
  }
  return { direct, bearing, remaining };
}

/** Giro de la flecha: con brújula, relativo al teléfono; sin brújula, relativo al norte. */
export function arrowRotation(bearing: number | null, heading: number | null): number | null {
  return bearing == null ? null : normalizeDegrees(bearing - (heading ?? 0));
}

/** Distancia que se muestra en grande: por calles si hay ruta, si no en línea recta. */
export function shownDistance(mode: GuidanceMode, g: Guidance): number | null {
  return mode === 'streets' ? g.remaining ?? g.direct : g.direct;
}

/** Giro por el camino más corto (de 350° a 10° gira 20°, no 340°). */
export function shortestRotation(accumulated: number, target: number): number {
  const current = normalizeDegrees(accumulated);
  const delta = ((target - current + 540) % 360) - 180;
  return accumulated + delta;
}
