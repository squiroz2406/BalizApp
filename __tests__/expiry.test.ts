import { expiryAtTime } from '../src/data/expiry';

const now = new Date(2026, 5, 10, 12, 0, 0);

describe('expiryAtTime', () => {
  it('hoy si todavía no pasó', () => expect(expiryAtTime(15, 30, now)).toBe(new Date(2026, 5, 10, 15, 30).getTime()));
  it('mañana si ya pasó', () => expect(expiryAtTime(9, 0, now)).toBe(new Date(2026, 5, 11, 9, 0).getTime()));
});
