/*
 * Modelos del dominio. Son objetos planos (tiempos en milisegundos, ubicación como lat/lng) para
 * poder probarlos sin Firebase; la conversión a Timestamp/GeoPoint vive en los repositorios.
 * Los nombres de campo y los valores guardados ("car", "active", ...) son los mismos que usa la app Kotlin,
 * así que ambas apps leen y escriben los mismos documentos de Firestore.
 */
import { t } from '../i18n';

export type Raw = Record<string, any> | undefined;

export const DEFAULT_REMINDER_MINUTES = 15;
export const REMINDER_OPTIONS = [5, 10, 15, 30];

export interface GeoPosition {
  latitude: number;
  longitude: number;
  accuracyMeters?: number | null;
}

/** Firestore Timestamp (o cualquier cosa con toMillis) -> milisegundos. */
export function toMillis(value: any): number | null {
  if (value == null) return null;
  if (typeof value === 'number') return value;
  if (typeof value.toMillis === 'function') return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return null;
}

/** users/{uid} */
export interface UserProfile {
  uid: string;
  displayName: string | null;
  email: string | null;
  reminderMinutes: number;
}

export function userProfileFromData(uid: string, data: Raw): UserProfile {
  return {
    uid,
    displayName: data?.displayName ?? null,
    email: data?.email ?? null,
    reminderMinutes: typeof data?.reminderMinutes === 'number' ? data.reminderMinutes : DEFAULT_REMINDER_MINUTES,
  };
}

export type VehicleType = 'car' | 'motorcycle';

export function vehicleTypeFrom(value: unknown): VehicleType {
  return value === 'motorcycle' ? 'motorcycle' : 'car';
}

/** users/{uid}/vehicles/{vehicleId} */
export interface Vehicle {
  id: string;
  type: VehicleType;
  alias: string;
  plate: string | null;
}

export function vehicleFromData(id: string, data: Raw): Vehicle {
  return {
    id,
    type: vehicleTypeFrom(data?.type),
    alias: data?.alias ?? '',
    plate: data?.plate ?? null,
  };
}

export function vehicleToData(v: Omit<Vehicle, 'id'>) {
  return { type: v.type, alias: v.alias, plate: v.plate };
}

export type ParkingStatus = 'active' | 'finished';
export type ParkingSource = 'manual' | 'agent_sign' | 'agent_text';

export function parkingStatusFrom(value: unknown): ParkingStatus {
  return value === 'finished' ? 'finished' : 'active';
}

export function parkingSourceFrom(value: unknown): ParkingSource {
  return value === 'agent_sign' || value === 'agent_text' ? value : 'manual';
}

/** users/{uid}/parkings/{parkingId} */
export interface Parking {
  id: string;
  vehicleId: string;
  status: ParkingStatus;
  location: GeoPosition | null;
  addressText: string | null;
  note: string | null;
  levelSector: string | null;
  /** Ruta lógica de la foto (users/UID/parkings/ID/photos/place); la foto está en la subcolección photos. */
  placePhotoPath: string | null;
  signPhotoPath: string | null;
  startedAt: number;
  expiresAt: number | null;
  ruleSummary: string | null;
  source: ParkingSource;
  agentConfidence: string | null;
  finishedAt: number | null;
}

export function parkingFromData(id: string, data: Raw, now: number = Date.now()): Parking {
  const loc = data?.location;
  return {
    id,
    vehicleId: data?.vehicleId ?? '',
    status: parkingStatusFrom(data?.status),
    location: loc ? { latitude: loc.latitude, longitude: loc.longitude } : null,
    addressText: data?.addressText ?? null,
    note: data?.note ?? null,
    levelSector: data?.levelSector ?? null,
    placePhotoPath: data?.placePhotoUrl ?? null,
    signPhotoPath: data?.signPhotoUrl ?? null,
    startedAt: toMillis(data?.startedAt) ?? now,
    expiresAt: toMillis(data?.expiresAt),
    ruleSummary: data?.ruleSummary ?? null,
    source: parkingSourceFrom(data?.source),
    agentConfidence: data?.agentConfidence ?? null,
    finishedAt: toMillis(data?.finishedAt),
  };
}

export function newParking(partial: Partial<Parking> & Pick<Parking, 'vehicleId'>): Parking {
  return {
    id: '',
    status: 'active',
    location: null,
    addressText: null,
    note: null,
    levelSector: null,
    placePhotoPath: null,
    signPhotoPath: null,
    startedAt: Date.now(),
    expiresAt: null,
    ruleSummary: null,
    source: 'manual',
    agentConfidence: null,
    finishedAt: null,
    ...partial,
  };
}

/** Texto corto para identificar el lugar: dirección, nota o piso/sector. */
export function summaryLine(p: Pick<Parking, 'addressText' | 'note' | 'levelSector'>): string | null {
  const candidates = [p.addressText, p.note, p.levelSector ? t('core_sector', p.levelSector) : null];
  return candidates.find((c) => c != null && c.trim() !== '') ?? null;
}

export function isExpired(p: Pick<Parking, 'expiresAt'>, now: number): boolean {
  return p.expiresAt != null && p.expiresAt <= now;
}
