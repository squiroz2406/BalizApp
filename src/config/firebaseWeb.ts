import googleServices from '../../google-services.json';

/** Cliente web de Google para el ingreso con Google (client_type 3 del google-services.json). */
export const googleWebClientId: string | undefined = (() => {
  for (const client of googleServices.client as any[]) {
    const found = (client.oauth_client ?? []).find((o: any) => o.client_type === 3);
    if (found) return found.client_id as string;
  }
  return undefined;
})();

export const mapTilerKey = process.env.EXPO_PUBLIC_MAPTILER_KEY ?? '';
