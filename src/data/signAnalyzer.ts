import { getApp, getApps, initializeApp } from 'firebase/app';
import { GoogleAIBackend, Schema, getAI, getGenerativeModel, type GenerativeModel } from 'firebase/ai';
import { firebaseWebConfig } from '../config/firebaseWeb';
import { currentLanguage, t } from '../i18n';
import { cleanPlaceDescription, parseSignAnalysis, type SignAnalysis } from './signParsing';

const MODEL = 'gemini-2.5-flash';

/** Error de configuración: falta la clave web de Firebase. */
export class AiNotConfiguredError extends Error {
  constructor() {
    super('Falta EXPO_PUBLIC_FIREBASE_WEB_API_KEY / EXPO_PUBLIC_FIREBASE_WEB_APP_ID (ver .env.example)');
  }
}

function webApp() {
  if (!firebaseWebConfig.apiKey || !firebaseWebConfig.appId) throw new AiNotConfiguredError();
  // Nombre propio para no chocar con la app nativa de @react-native-firebase.
  return getApps().find((a) => a.name === 'gemini') ?? initializeApp(firebaseWebConfig, 'gemini');
}

let signModel: GenerativeModel | null = null;
let placeModel: GenerativeModel | null = null;

function ai() {
  return getAI(webApp(), { backend: new GoogleAIBackend() });
}

function getSignModel() {
  signModel ??= getGenerativeModel(ai(), {
    model: MODEL,
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: Schema.object({
        properties: {
          readable: Schema.boolean(),
          ruleSummary: Schema.string({ nullable: true }),
          verdict: Schema.enumString({ enum: ['allowed', 'forbidden', 'unknown'] }),
          maxMinutes: Schema.integer({ nullable: true }),
          allowedUntil: Schema.string({ nullable: true }),
          explanation: Schema.string({ nullable: true }),
          confidence: Schema.enumString({ enum: ['high', 'medium', 'low'] }),
        },
      }),
    },
  });
  return signModel;
}

function getPlaceModel() {
  placeModel ??= getGenerativeModel(ai(), { model: MODEL });
  return placeModel;
}

function pad(n: number) {
  return String(n).padStart(2, '0');
}

/**
 * Lee el cartel de [jpegBase64] con Gemini. Devuelve null si la foto no tiene un cartel legible.
 * Es una propuesta: el usuario la revisa antes de guardar.
 */
export async function analyzeSign(jpegBase64: string, now: Date = new Date()): Promise<SignAnalysis | null> {
  const day = now.toLocaleDateString('es-AR', { weekday: 'long' });
  const stamp = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
  const language = currentLanguage().nativeName;
  const prompt = `
Sos un asistente que lee carteles de estacionamiento de calles de Argentina.
Ahora es ${day} ${stamp} (hora local).
Mirá la foto del cartel y respondé en JSON:
- readable: false si en la foto no hay un cartel de estacionamiento que se pueda leer.
- ruleSummary: la regla en una frase corta (por ejemplo "Medido, máx. 2 h, lun a vie de 8 a 18").
- verdict: "allowed" si ahora se puede estacionar, "forbidden" si ahora está prohibido o
  hay riesgo de grúa o multa, "unknown" si no alcanza la información.
- maxMinutes: minutos que se puede quedar a partir de ahora (solo si el cartel fija un máximo), si no null.
- allowedUntil: hora HH:mm (24 h) a partir de la cual deja de poder estar o empieza una prohibición HOY, si no null.
- explanation: una frase corta de por qué sí o por qué no.
- confidence: qué tan seguro estás de haber leído bien.
No inventes: si no estás seguro de algo, usá null o "unknown" y confidence "low".
Escribí ruleSummary y explanation en ${language}.`.trim();
  const response = await getSignModel().generateContent([
    { type: 'inlineData' as const, inlineData: { mimeType: 'image/jpeg', data: jpegBase64 } },
    { type: 'text' as const, text: prompt },
  ]);
  const text = response.response.text();
  if (!text) throw new Error(t('form_err_ai_no_response'));
  return parseSignAnalysis(text);
}

/** Descripción corta del lugar para recordarlo ("Frente a la farmacia, junto a un árbol"). */
export async function describePlace(jpegBase64: string, address: string | null): Promise<string | null> {
  const language = currentLanguage().nativeName;
  const prompt = `
Esta foto muestra dónde alguien dejó su vehículo${address ? ` (dirección: ${address})` : ''}.
Escribí en ${language} UNA frase corta (máximo 15 palabras) que ayude a reconocer el lugar al volver:
referencias fijas como comercios, carteles, árboles, esquinas o columnas. No describas el vehículo ni
inventes lo que no se ve. Respondé solo la frase, sin comillas.`.trim();
  const response = await getPlaceModel().generateContent([
    { type: 'inlineData' as const, inlineData: { mimeType: 'image/jpeg', data: jpegBase64 } },
    { type: 'text' as const, text: prompt },
  ]);
  return cleanPlaceDescription(response.response.text());
}

/** Mensaje corto para el usuario; los errores de cuota se explican sin mostrar el texto crudo de Google. */
export function aiErrorMessage(e: unknown, fallback: string): string {
  const msg = e instanceof Error ? e.message : String(e);
  if (/quota|rate limit|429/i.test(msg)) return t('form_err_ai_quota');
  if (e instanceof AiNotConfiguredError) return t('form_err_ai_generic', fallback, msg.slice(0, 150));
  return t('form_err_ai_generic', fallback, msg.slice(0, 150));
}
