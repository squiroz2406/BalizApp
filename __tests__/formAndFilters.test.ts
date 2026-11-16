import { setLanguageForTests } from '../src/i18n';
import { defaultVehicleId, trimOrNull, validateForm } from '../src/screens/formLogic';
import { applyFilters, validVehicleFilter } from '../src/screens/homeFilters';
import { newParking } from '../src/data/models';

beforeAll(() => setLanguageForTests('ES'));
const pos = { latitude: -34.6, longitude: -58.4 };

describe('validateForm', () => {
  const ok = { processingPhoto: false, vehicleId: 'v1', position: pos, expiresAt: null };
  it('un formulario completo es válido', () => expect(validateForm(ok)).toBeNull());
  it('espera a que termine la foto', () => expect(validateForm({ ...ok, processingPhoto: true })).toBe('Esperá a que termine de procesarse la foto'));
  it('exige vehículo', () => expect(validateForm({ ...ok, vehicleId: null })).toBe('Elegí un vehículo'));
  it('exige ubicación', () => expect(validateForm({ ...ok, position: null })).toBe('Marcá en el mapa dónde dejaste el vehículo'));
  it('el vencimiento debe ser futuro', () => {
    expect(validateForm({ ...ok, expiresAt: 500 }, 1000)).toBe('El vencimiento tiene que ser posterior a ahora');
    expect(validateForm({ ...ok, expiresAt: 1500 }, 1000)).toBeNull();
  });
  it('trimOrNull', () => {
    expect(trimOrNull('  hola ')).toBe('hola');
    expect(trimOrNull('   ')).toBeNull();
  });
  it('vehículo preseleccionado: el primero libre', () => {
    const list = [{ id: 'a' }, { id: 'b' }];
    expect(defaultVehicleId(list, new Set(['a']))).toBe('b');
    expect(defaultVehicleId(list, new Set(['a', 'b']))).toBe('a');
    expect(defaultVehicleId([], new Set())).toBeNull();
  });
});

describe('filtros de Inicio', () => {
  const now = 1000;
  const active = [
    newParking({ id: 'a1', vehicleId: 'v1', expiresAt: 500 }), // vencido
    newParking({ id: 'a2', vehicleId: 'v2', expiresAt: 2000 }), // vigente
    newParking({ id: 'a3', vehicleId: 'v1', expiresAt: null }), // sin límite
  ];
  const history = [newParking({ id: 'h1', vehicleId: 'v1', status: 'finished' }), newParking({ id: 'h2', vehicleId: 'v2', status: 'finished' })];
  const ids = (l: { id: string }[]) => l.map((p) => p.id);

  it('todos', () => {
    const r = applyFilters(active, history, null, 'all', now);
    expect(ids(r.active)).toEqual(['a1', 'a2', 'a3']);
    expect(ids(r.history)).toEqual(['h1', 'h2']);
  });
  it('actuales excluye vencidos y el historial', () => {
    const r = applyFilters(active, history, null, 'current', now);
    expect(ids(r.active)).toEqual(['a2', 'a3']);
    expect(r.history).toEqual([]);
  });
  it('vencidos', () => expect(ids(applyFilters(active, history, null, 'expired', now).active)).toEqual(['a1']));
  it('finalizados solo muestra historial', () => {
    const r = applyFilters(active, history, null, 'finished', now);
    expect(r.active).toEqual([]);
    expect(ids(r.history)).toEqual(['h1', 'h2']);
  });
  it('combina vehículo y estado', () => {
    const r = applyFilters(active, history, 'v1', 'all', now);
    expect(ids(r.active)).toEqual(['a1', 'a3']);
    expect(ids(r.history)).toEqual(['h1']);
  });
  it('un filtro de vehículo borrado se ignora', () => {
    expect(validVehicleFilter('x', [{ id: 'v1', type: 'car', alias: 'a', plate: null }])).toBeNull();
    expect(validVehicleFilter('v1', [{ id: 'v1', type: 'car', alias: 'a', plate: null }])).toBe('v1');
  });
});
