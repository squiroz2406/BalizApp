/** Vence a la hora elegida: hoy si todavía no pasó, si no mañana. */
export function expiryAtTime(hour: number, minute: number, now: Date = new Date()): number {
  const target = new Date(now);
  target.setHours(hour, minute, 0, 0);
  if (target.getTime() <= now.getTime()) target.setDate(target.getDate() + 1);
  return target.getTime();
}
