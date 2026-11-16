import {
  bearing,
  cardinalKey,
  closestPoint,
  distance,
  nextTarget,
  normalizeDegrees,
  remainingMeters,
} from '../src/data/routeMath';

// Puntos sobre un mismo meridiano: 0.001° de latitud ≈ 111 m.
const a = { latitude: -34.6, longitude: -58.4 };
const b = { latitude: -34.601, longitude: -58.4 };
const c = { latitude: -34.602, longitude: -58.4 };
const route = [a, b, c];

describe('routeMath', () => {
  it('distancia entre puntos cercanos', () => expect(distance(a, b)).toBeCloseTo(111, -1));
  it('rumbo hacia el sur es 180', () => expect(bearing(a, b)).toBeCloseTo(180, 0));
  it('rumbo hacia el norte es 0 y nunca negativo', () => {
    const r = bearing(b, a);
    expect(r).toBeGreaterThanOrEqual(0);
    expect(r < 1 || r > 359).toBe(true);
  });
  it('rumbo hacia el este es 90', () => {
    expect(bearing({ latitude: 0, longitude: 0 }, { latitude: 0, longitude: 1 })).toBeCloseTo(90, 0);
  });
  it('punto más cercano de la ruta', () => {
    const r = closestPoint(route, { latitude: -34.6011, longitude: -58.4 });
    expect(r.index).toBe(1);
    expect(r.distance).toBeLessThan(20);
  });
  it('metros restantes desde el inicio es toda la ruta', () => expect(remainingMeters(route, a)).toBeCloseTo(222, -1));
  it('metros restantes de una ruta vacía es cero', () => expect(remainingMeters([], a)).toBe(0));
  it('siguiente objetivo respeta la distancia de anticipación', () => expect(nextTarget(route, a, 150)).toBe(c));
  it('si nada supera la anticipación apunta al final', () => expect(nextTarget(route, a, 10_000)).toBe(c));
  it('ruta vacía no tiene objetivo', () => expect(nextTarget([], a)).toBeNull());
  it('normalizeDegrees', () => {
    expect(normalizeDegrees(-10)).toBe(350);
    expect(normalizeDegrees(370)).toBe(10);
    expect(normalizeDegrees(0)).toBe(0);
  });
  it('puntos cardinales', () => {
    expect(cardinalKey(0)).toBe('nav_cardinal_n');
    expect(cardinalKey(359)).toBe('nav_cardinal_n');
    expect(cardinalKey(90)).toBe('nav_cardinal_e');
    expect(cardinalKey(225)).toBe('nav_cardinal_sw');
    expect(cardinalKey(315)).toBe('nav_cardinal_nw');
  });
});
