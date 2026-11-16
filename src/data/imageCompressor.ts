import * as ImageManipulator from 'expo-image-manipulator';
import { t } from '../i18n';

/*
 * Prepara una foto para guardarla en Firestore: la achica (lado mayor MAX_SIDE_PX), la endereza según la
 * orientación de la cámara y la comprime en JPEG hasta que pese menos de MAX_BYTES (un documento de
 * Firestore admite hasta 1 MiB). Devuelve el JPEG en base64.
 */
export const MAX_SIDE_PX = 2048;
export const MAX_BYTES = 700 * 1024;

export function base64Bytes(base64: string): number {
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0;
  return Math.floor((base64.length * 3) / 4) - padding;
}

export async function compressImage(uri: string, width: number, height: number): Promise<string> {
  if (!width || !height) throw new Error(t('form_err_image_read'));
  const scale = Math.min(1, MAX_SIDE_PX / Math.max(width, height));
  const actions: ImageManipulator.Action[] =
    scale < 1 ? [{ resize: width >= height ? { width: Math.round(width * scale) } : { height: Math.round(height * scale) } }] : [];

  let quality = 0.8;
  let base64 = '';
  do {
    const result = await ImageManipulator.manipulateAsync(uri, actions, {
      compress: quality,
      format: ImageManipulator.SaveFormat.JPEG,
      base64: true,
    });
    base64 = result.base64 ?? '';
    quality -= 0.1;
  } while (base64Bytes(base64) > MAX_BYTES && quality >= 0.3);

  if (!base64) throw new Error(t('form_err_image_read'));
  if (base64Bytes(base64) > MAX_BYTES) throw new Error(t('form_err_image_big'));
  return base64;
}
