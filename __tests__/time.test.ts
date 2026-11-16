import { setLanguageForTests } from '../src/i18n';
import { formatDateTime, formatDistance, formatDuration, formatRemaining } from '../src/utils/time';

beforeAll(() => setLanguageForTests('ES'));

describe('formatDuration', () => {
  it('menos de una hora muestra solo minutos', () => expect(formatDuration(45 * 60_000)).toBe('45 min'));
  it('hora exacta omite los minutos', () => expect(formatDuration(2 * 60 * 60_000)).toBe('2 h'));
  it('hora y minutos rellena con cero', () => expect(formatDuration(65 * 60_000)).toBe('1 h 05 min'));
  it('duración negativa se muestra en valor absoluto', () => expect(formatDuration(-10 * 60_000)).toBe('10 min'));
  it('cero es 0 min', () => expect(formatDuration(0)).toBe('0 min'));
});

describe('formatRemaining', () => {
  const now = 1_000_000_000_000;
  it('quedan horas y minutos', () => expect(formatRemaining(now + 65 * 60_000, now)).toBe('Quedan 1 h 05 min'));
  it('menos de un minuto', () => expect(formatRemaining(now + 30_000, now)).toBe('Queda menos de 1 min'));
  it('vencido', () => expect(formatRemaining(now - 10 * 60_000, now)).toBe('Venció hace 10 min'));
});

describe('formatDateTime', () => {
  const noon = new Date(2026, 5, 10, 12, 0).getTime();
  it('hoy muestra solo la hora', () => expect(formatDateTime(new Date(2026, 5, 10, 16, 5).getTime(), noon)).toBe('16:05'));
  it('mañana', () => expect(formatDateTime(new Date(2026, 5, 11, 9, 30).getTime(), noon)).toBe('mañana 09:30'));
  it('ayer', () => expect(formatDateTime(new Date(2026, 5, 9, 9, 30).getTime(), noon)).toBe('ayer 09:30'));
});

describe('formatDistance', () => {
  it('metros', () => expect(formatDistance(84.6)).toBe('85 m'));
  it('kilómetros con coma', () => expect(formatDistance(1234)).toBe('1,2 km'));
});
