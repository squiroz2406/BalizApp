import AsyncStorage from '@react-native-async-storage/async-storage';
import { loadRoute, saveRoute } from '../src/data/routeCache';

const destination = { latitude: -34.6, longitude: -58.4 };
const route = {
  points: [{ latitude: -34.598, longitude: -58.4 }, destination],
  distanceMeters: 222,
  durationSeconds: 160,
};

beforeEach(() => AsyncStorage.clear());

describe('routeCache', () => {
  it('guardar y cargar devuelve la misma ruta', async () => {
    await saveRoute('p1', destination, route);
    expect(await loadRoute('p1', destination)).toEqual(route);
  });
  it('sin datos guardados devuelve null', async () => expect(await loadRoute('nunca', destination)).toBeNull());
  it('si el vehículo se movió más de 10 m descarta la ruta', async () => {
    await saveRoute('p1', destination, route);
    expect(await loadRoute('p1', { latitude: -34.601, longitude: -58.4 })).toBeNull();
  });
  it('si el vehículo se movió menos de 10 m la conserva', async () => {
    await saveRoute('p1', destination, route);
    expect(await loadRoute('p1', { latitude: -34.60003, longitude: -58.4 })).not.toBeNull();
  });
  it('datos corruptos devuelven null en vez de fallar', async () => {
    await AsyncStorage.setItem('route.p1', '{no es json');
    expect(await loadRoute('p1', destination)).toBeNull();
  });
  it('borra los recorridos de más de 30 días al guardar uno nuevo', async () => {
    const day = 24 * 60 * 60 * 1000;
    const now = Date.now();
    await saveRoute('viejo', destination, route, now - 31 * day);
    await saveRoute('nuevo', destination, route, now);
    expect(await AsyncStorage.getItem('route.viejo')).toBeNull();
    expect(await loadRoute('nuevo', destination)).not.toBeNull();
  });
});
