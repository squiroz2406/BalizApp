import {
  arrowRotation,
  computeGuidance,
  decideRouteAction,
  shortestRotation,
  shownDistance,
  type RouteDecisionInput,
} from '../src/screens/returnToCarLogic';
import { MIN_INTERVAL_MS, parseOsrm, type WalkingRoute } from '../src/data/routeRepository';

const car = { latitude: -34.602, longitude: -58.4 };
const me = { latitude: -34.6, longitude: -58.4 };
const route: WalkingRoute = {
  points: [me, { latitude: -34.601, longitude: -58.4 }, car],
  distanceMeters: 222,
  durationSeconds: 160,
};
const base: RouteDecisionInput = {
  mode: 'streets', car, me, current: null, lastDestination: null, forced: false, now: 100_000, lastRequestAt: 0,
};

describe('decideRouteAction', () => {
  it('en modo directo no hay ruta', () => expect(decideRouteAction({ ...base, mode: 'direct' })).toBe('idle'));
  it('sin ubicación del auto no hay ruta', () => expect(decideRouteAction({ ...base, car: null })).toBe('idle'));
  it('espera el GPS', () => expect(decideRouteAction({ ...base, me: null })).toBe('wait-position'));
  it('sin ruta previa la pide', () => expect(decideRouteAction(base)).toBe('fetch'));
  it('con ruta vigente la reutiliza', () =>
    expect(decideRouteAction({ ...base, current: route, lastDestination: car, lastRequestAt: 50_000 })).toBe('reuse'));
  it('si el pedido anterior falló hace poco espera', () =>
    expect(decideRouteAction({ ...base, lastRequestAt: base.now - 1000 })).toBe('cooldown'));
  it('pasado el intervalo vuelve a pedir', () =>
    expect(decideRouteAction({ ...base, lastRequestAt: base.now - MIN_INTERVAL_MS - 1 })).toBe('fetch'));
  it('"Recalcular" fuerza el pedido aunque haya pasado poco', () =>
    expect(decideRouteAction({ ...base, current: route, lastDestination: car, forced: true, lastRequestAt: base.now - 1000 })).toBe('fetch'));
  it('desviarse recalcula solo pasado el intervalo mínimo', () => {
    const farAway = { latitude: -34.6, longitude: -58.41 }; // ~900 m de la línea
    const input = { ...base, me: farAway, current: route, lastDestination: car };
    expect(decideRouteAction({ ...input, lastRequestAt: base.now - 1000 })).toBe('reuse');
    expect(decideRouteAction({ ...input, lastRequestAt: base.now - MIN_INTERVAL_MS })).toBe('fetch');
  });
  it('si el auto cambió de lugar pide una ruta nueva', () =>
    expect(decideRouteAction({ ...base, current: route, lastDestination: { latitude: -34.5, longitude: -58.4 }, lastRequestAt: 50_000 })).toBe('fetch'));
});

describe('computeGuidance', () => {
  it('sin posición no hay datos', () => expect(computeGuidance(car, null, 'direct', null)).toEqual({ direct: null, bearing: null, remaining: null }));
  it('modo directo: distancia y rumbo en línea recta', () => {
    const g = computeGuidance(car, me, 'direct', route);
    expect(g.direct).toBeCloseTo(222, -1);
    expect(g.bearing).toBeCloseTo(180, 0);
    expect(g.remaining).toBeNull();
  });
  it('modo calles: usa lo que falta de la ruta', () => {
    const g = computeGuidance(car, me, 'streets', route);
    expect(g.remaining).toBeCloseTo(222, -1);
    expect(shownDistance('streets', g)).toBe(g.remaining);
    expect(shownDistance('direct', g)).toBe(g.direct);
  });
  it('modo calles sin ruta muestra la distancia directa', () => {
    const g = computeGuidance(car, me, 'streets', null);
    expect(shownDistance('streets', g)).toBe(g.direct);
  });
});

describe('flecha', () => {
  it('rotación relativa al teléfono', () => {
    expect(arrowRotation(90, 30)).toBe(60);
    expect(arrowRotation(10, 350)).toBe(20);
    expect(arrowRotation(90, null)).toBe(90);
    expect(arrowRotation(null, 30)).toBeNull();
  });
  it('gira por el camino más corto', () => {
    expect(shortestRotation(350, 10)).toBe(370);
    expect(shortestRotation(10, 350)).toBe(-10);
    expect(Math.abs(shortestRotation(0, 180))).toBe(180); // media vuelta: cualquier sentido
  });
});

describe('parseOsrm', () => {
  it('interpreta la geometría [lon, lat]', () => {
    const r = parseOsrm({ code: 'Ok', routes: [{ distance: 100, duration: 70, geometry: { coordinates: [[-58.4, -34.6], [-58.41, -34.61]] } }] });
    expect(r.points[0]).toEqual({ latitude: -34.6, longitude: -58.4 });
    expect(r.distanceMeters).toBe(100);
    expect(r.durationSeconds).toBe(70);
  });
  it('un código distinto de Ok es un error', () => expect(() => parseOsrm({ code: 'NoRoute' })).toThrow(/NoRoute/));
});
