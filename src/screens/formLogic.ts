import { t } from '../i18n';
import type { GeoPosition } from '../data/models';

export const NO_LIMIT = 0;
export const PRESETS_MINUTES = [30, 60, 120, 180, 240];

export interface FormValidationInput {
  processingPhoto: boolean;
  vehicleId: string | null;
  position: GeoPosition | null;
  expiresAt: number | null;
}

/** Valida el formulario antes de guardar; devuelve el mensaje de error o null si está bien. */
export function validateForm(s: FormValidationInput, now: number = Date.now()): string | null {
  if (s.processingPhoto) return t('form_err_wait_photo');
  if (!s.vehicleId) return t('form_err_pick_vehicle');
  if (!s.position) return t('form_err_mark_map');
  if (s.expiresAt != null && s.expiresAt <= now) return t('form_err_expiry_past');
  return null;
}

export const trimOrNull = (value: string): string | null => {
  const v = value.trim();
  return v === '' ? null : v;
};

/** Elige el vehículo preseleccionado: el primero que no esté estacionado, o el primero de la lista. */
export function defaultVehicleId(vehicles: { id: string }[], busyVehicleIds: Set<string>): string | null {
  return (vehicles.find((v) => !busyVehicleIds.has(v.id)) ?? vehicles[0])?.id ?? null;
}
