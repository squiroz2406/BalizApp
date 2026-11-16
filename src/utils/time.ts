import { currentLocaleTag, t } from '../i18n';

const MINUTE = 60_000;

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "16:00" si es hoy, "mañana 16:00", "ayer 16:00" o "mar 30 sept 16:00". */
export function formatDateTime(millis: number, now: number = Date.now()): string {
  const date = new Date(millis);
  const today = new Date(now);
  const tomorrow = new Date(now + 24 * 60 * MINUTE);
  const yesterday = new Date(now - 24 * 60 * MINUTE);
  const locale = currentLocaleTag();
  const time = date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit', hour12: false });
  if (sameDay(date, today)) return time;
  if (sameDay(date, tomorrow)) return t('time_tomorrow', time);
  if (sameDay(date, yesterday)) return t('time_yesterday', time);
  const day = date.toLocaleDateString(locale, { weekday: 'short', day: 'numeric', month: 'short' });
  return `${day} ${time}`;
}

/** Duración legible: "45 min", "1 h 05 min", "2 h". */
export function formatDuration(millis: number): string {
  const totalMinutes = Math.floor(Math.abs(millis) / MINUTE);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) return `${minutes} min`;
  if (minutes === 0) return `${hours} h`;
  return `${hours} h ${String(minutes).padStart(2, '0')} min`;
}

/** Cuenta regresiva hasta [expiresAt]: "Quedan 1 h 05 min" o "Venció hace 10 min". */
export function formatRemaining(expiresAt: number, now: number): string {
  const diff = expiresAt - now;
  if (diff > MINUTE) return t('time_remaining', formatDuration(diff));
  if (diff > 0) return t('time_less_than_minute');
  return t('time_expired_ago', formatDuration(diff));
}

/** Distancia legible: "85 m" o "1,2 km". */
export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(1).replace('.', ',')} km`;
}
