import googleServices from '../../google-services.json';

/**
 * Configuración de Firebase para el SDK de JavaScript, que solo se usa para Gemini (Firebase AI Logic):
 * @react-native-firebase todavía no incluye AI Logic. La clave del google-services.json es de la app Android
 * (restringida por paquete y huella), así que desde JavaScript hace falta la clave de una app Web del mismo
 * proyecto. Ver .env.example.
 */
export const firebaseWebConfig = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_WEB_API_KEY ?? '',
  appId: process.env.EXPO_PUBLIC_FIREBASE_WEB_APP_ID ?? '',
  projectId: googleServices.project_info.project_id,
  authDomain: `${googleServices.project_info.project_id}.firebaseapp.com`,
};

/** Cliente web de Google para el ingreso con Google (client_type 3 del google-services.json). */
export const googleWebClientId: string | undefined = (() => {
  for (const client of googleServices.client as any[]) {
    const found = (client.oauth_client ?? []).find((o: any) => o.client_type === 3);
    if (found) return found.client_id as string;
  }
  return undefined;
})();

export const mapTilerKey = process.env.EXPO_PUBLIC_MAPTILER_KEY ?? '';
