import type { SignAnalysis } from './signParsing';

/** El vencimiento más cercano entre "máximo de minutos" y "hasta la hora del cartel"; null si no hay ninguno. */
export function suggestedExpiry(analysis: SignAnalysis, now: Date = new Date()): number | null {
  const byDuration = analysis.maxMinutes != null ? now.getTime() + analysis.maxMinutes * 60_000 : null;
  let byClock: number | null = null;
  if (analysis.allowedUntil) {
    const [h, m] = analysis.allowedUntil.split(':').map(Number);
    if (Number.isFinite(h) && Number.isFinite(m)) {
      const target = new Date(now);
      target.setHours(h, m, 0, 0);
      if (target.getTime() > now.getTime()) byClock = target.getTime();
    }
  }
  const options = [byDuration, byClock].filter((v): v is number => v != null);
  return options.length ? Math.min(...options) : null;
}

/** Vence a la hora elegida: hoy si todavía no pasó, si no mañana. */
export function expiryAtTime(hour: number, minute: number, now: Date = new Date()): number {
  const target = new Date(now);
  target.setHours(hour, minute, 0, 0);
  if (target.getTime() <= now.getTime()) target.setDate(target.getDate() + 1);
  return target.getTime();
}
