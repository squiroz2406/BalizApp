/** ¿Se puede estacionar ahora según el cartel? */
export type ParkingVerdict = 'allowed' | 'forbidden' | 'unknown';

/** Lo que Gemini entendió de la foto del cartel. Es una propuesta: el usuario la revisa. */
export interface SignAnalysis {
  /** Regla resumida, por ejemplo "Medido, máx. 2 h, lun a vie de 8 a 18". */
  ruleSummary: string | null;
  verdict: ParkingVerdict;
  /** Tiempo máximo que se puede quedar a partir de ahora, en minutos. */
  maxMinutes: number | null;
  /** Hora (HH:mm) hasta la que se puede estar, si el cartel indica un horario. */
  allowedUntil: string | null;
  explanation: string | null;
  /** "high", "medium" o "low". */
  confidence: string;
}

function optString(value: unknown): string | null {
  if (value == null) return null;
  const text = String(value).trim();
  return text === '' ? null : text;
}

/** Interpreta el JSON que devuelve Gemini para el cartel. Devuelve null si la foto no tenía un cartel legible. */
export function parseSignAnalysis(text: string): SignAnalysis | null {
  const json = JSON.parse(text);
  if (json.readable !== true) return null;
  const verdict: ParkingVerdict =
    json.verdict === 'allowed' ? 'allowed' : json.verdict === 'forbidden' ? 'forbidden' : 'unknown';
  const max = json.maxMinutes == null ? null : Math.trunc(Number(json.maxMinutes));
  const until = optString(json.allowedUntil);
  return {
    ruleSummary: optString(json.ruleSummary),
    verdict,
    maxMinutes: max != null && Number.isFinite(max) && max > 0 ? max : null,
    allowedUntil: until && /^\d{1,2}:\d{2}$/.test(until) ? until : null,
    explanation: optString(json.explanation),
    confidence: typeof json.confidence === 'string' ? json.confidence : 'low',
  };
}

/** Limpia la frase que devuelve Gemini para el lugar: sin espacios ni comillas en los bordes; null si queda vacía. */
export function cleanPlaceDescription(text: string | null | undefined): string | null {
  if (text == null) return null;
  const cleaned = text.trim().replace(/^"+|"+$/g, '').trim();
  return cleaned === '' ? null : cleaned;
}
