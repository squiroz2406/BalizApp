import { expiryAtTime, suggestedExpiry } from '../src/data/expiry';
import type { SignAnalysis } from '../src/data/signParsing';

const base: SignAnalysis = { ruleSummary: null, verdict: 'allowed', maxMinutes: null, allowedUntil: null, explanation: null, confidence: 'high' };
const now = new Date(2026, 5, 10, 12, 0, 0);

describe('suggestedExpiry', () => {
  it('sin datos no sugiere nada', () => expect(suggestedExpiry(base, now)).toBeNull());
  it('por duración', () => expect(suggestedExpiry({ ...base, maxMinutes: 90 }, now)).toBe(now.getTime() + 90 * 60_000));
  it('por hora del cartel', () =>
    expect(suggestedExpiry({ ...base, allowedUntil: '18:00' }, now)).toBe(new Date(2026, 5, 10, 18, 0).getTime()));
  it('elige el más cercano', () => {
    expect(suggestedExpiry({ ...base, maxMinutes: 120, allowedUntil: '18:00' }, now)).toBe(now.getTime() + 120 * 60_000);
    expect(suggestedExpiry({ ...base, maxMinutes: 600, allowedUntil: '18:00' }, now)).toBe(new Date(2026, 5, 10, 18, 0).getTime());
  });
  it('una hora que ya pasó se ignora', () => expect(suggestedExpiry({ ...base, allowedUntil: '08:00' }, now)).toBeNull());
});

describe('expiryAtTime', () => {
  it('hoy si todavía no pasó', () => expect(expiryAtTime(15, 30, now)).toBe(new Date(2026, 5, 10, 15, 30).getTime()));
  it('mañana si ya pasó', () => expect(expiryAtTime(9, 0, now)).toBe(new Date(2026, 5, 11, 9, 0).getTime()));
});
