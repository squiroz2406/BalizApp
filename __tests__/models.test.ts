import { setLanguageForTests } from '../src/i18n';
import {
  isExpired,
  parkingFromData,
  summaryLine,
  userProfileFromData,
  vehicleFromData,
  vehicleTypeFrom,
} from '../src/data/models';

beforeAll(() => setLanguageForTests('ES'));

describe('modelos', () => {
  it('tipo de vehículo desconocido o nulo es auto', () => {
    expect(vehicleTypeFrom('motorcycle')).toBe('motorcycle');
    expect(vehicleTypeFrom('rocket')).toBe('car');
    expect(vehicleTypeFrom(undefined)).toBe('car');
  });
  it('vehículo desde Firestore', () => {
    expect(vehicleFromData('v1', { type: 'motorcycle', alias: 'Moto', plate: 'AB123CD' })).toEqual({
      id: 'v1', type: 'motorcycle', alias: 'Moto', plate: 'AB123CD',
    });
    expect(vehicleFromData('v2', undefined)).toEqual({ id: 'v2', type: 'car', alias: '', plate: null });
  });
  it('perfil usa 15 minutos por defecto', () => expect(userProfileFromData('u', {}).reminderMinutes).toBe(15));
  it('estacionamiento con Timestamps y GeoPoint', () => {
    const p = parkingFromData('p1', {
      vehicleId: 'v1',
      status: 'finished',
      location: { latitude: -34.6, longitude: -58.4 },
      startedAt: { toMillis: () => 1000 },
      expiresAt: { toMillis: () => 5000 },
      finishedAt: { toMillis: () => 4000 },
      placePhotoUrl: 'users/u/parkings/p1/photos/place',
      source: 'agent_sign',
    });
    expect(p).toMatchObject({
      id: 'p1', status: 'finished', startedAt: 1000, expiresAt: 5000, finishedAt: 4000,
      source: 'agent_sign', placePhotoPath: 'users/u/parkings/p1/photos/place', signPhotoPath: null,
    });
    expect(p.location).toEqual({ latitude: -34.6, longitude: -58.4 });
  });
  it('valores desconocidos de estado y origen tienen respaldo', () => {
    const p = parkingFromData('p', { status: 'raro', source: 'otro' });
    expect(p.status).toBe('active');
    expect(p.source).toBe('manual');
  });
  it('resumen del lugar: dirección, nota o piso', () => {
    expect(summaryLine({ addressText: 'Av. Mitre 100', note: 'x', levelSector: '2' })).toBe('Av. Mitre 100');
    expect(summaryLine({ addressText: ' ', note: 'Frente a la farmacia', levelSector: '2' })).toBe('Frente a la farmacia');
    expect(summaryLine({ addressText: null, note: null, levelSector: 'B2' })).toBe('Sector B2');
    expect(summaryLine({ addressText: null, note: null, levelSector: null })).toBeNull();
  });
  it('vencimiento', () => {
    expect(isExpired({ expiresAt: null }, 10)).toBe(false);
    expect(isExpired({ expiresAt: 10 }, 10)).toBe(true);
    expect(isExpired({ expiresAt: 11 }, 10)).toBe(false);
  });
});
