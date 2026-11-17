import * as ImagePicker from 'expo-image-picker';
import { useCallback, useEffect, useRef, useState } from 'react';
import { precacheTilesAround } from '../components/offlineTiles';
import { expiryAtTime } from '../data/expiry';
import { compressImage } from '../data/imageCompressor';
import { addressFor, currentPosition, hasLocationPermission, requestLocationPermission } from '../data/locationService';
import { newParking, type GeoPosition, type Parking, type Vehicle } from '../data/models';
import {
  ActiveParkingExistsError,
  createParking,
  getParking,
  hasActiveParking,
  observeActive,
  setPhotoPaths,
  updateParking,
} from '../data/parkingRepository';
import { deletePhoto, loadPhoto, savePhoto, type PhotoKind } from '../data/photoRepository';
import { observeVehicles } from '../data/vehicleRepository';
import { t } from '../i18n';
import { NO_LIMIT, defaultVehicleId, trimOrNull, validateForm } from './formLogic';

export type PositionSource = 'none' | 'gps' | 'manual' | 'saved';

export interface FormState {
  loading: boolean;
  isEdit: boolean;
  vehicles: Vehicle[];
  vehicleId: string | null;
  note: string;
  levelSector: string;
  ruleSummary: string;
  position: GeoPosition | null;
  positionSource: PositionSource;
  addressText: string | null;
  locating: boolean;
  hasLocationPermission: boolean;
  /** Fotos en base64; changed = hay que guardar (o borrar) la foto al confirmar. */
  placePhoto: string | null;
  signPhoto: string | null;
  placePhotoChanged: boolean;
  signPhotoChanged: boolean;
  processingPhoto: PhotoKind | null;
  expiresAt: number | null;
  /** Chip de límite marcado: minutos del preset, NO_LIMIT o null si se eligió una hora. */
  selectedPreset: number | null;
  saving: boolean;
  error: string | null;
  savedId: string | null;
}

const initial = (isEdit: boolean): FormState => ({
  loading: isEdit,
  isEdit,
  vehicles: [],
  vehicleId: null,
  note: '',
  levelSector: '',
  ruleSummary: '',
  position: null,
  positionSource: 'none',
  addressText: null,
  locating: false,
  hasLocationPermission: false,
  placePhoto: null,
  signPhoto: null,
  placePhotoChanged: false,
  signPhotoChanged: false,
  processingPhoto: null,
  expiresAt: null,
  selectedPreset: NO_LIMIT,
  saving: false,
  error: null,
  savedId: null,
});

/** Alta y edición de un estacionamiento. */
export function useParkingForm(parkingId: string | undefined) {
  const [state, setState] = useState<FormState>(() => initial(parkingId != null));
  const original = useRef<Parking | null>(null);
  const positionRef = useRef<GeoPosition | null>(null);
  const update = useCallback((patch: Partial<FormState> | ((s: FormState) => Partial<FormState>)) => {
    setState((s) => ({ ...s, ...(typeof patch === 'function' ? patch(s) : patch) }));
  }, []);
  const fail = useCallback((message: string) => update({ error: message }), [update]);

  const resolveAddress = useCallback(
    async (position: GeoPosition) => {
      const address = await addressFor(position.latitude, position.longitude);
      // Solo se aplica si la ubicación no cambió mientras se resolvía.
      if (positionRef.current === position) update({ addressText: address });
    },
    [update],
  );

  const locate = useCallback(async () => {
    update((s) => (s.locating ? {} : { locating: true, error: null }));
    const position = await currentPosition();
    if (!position) return update({ locating: false, error: t('form_err_location') });
    positionRef.current = position;
    update({ locating: false, position, positionSource: 'gps', addressText: null });
    resolveAddress(position);
  }, [update, resolveAddress]);

  // Carga inicial: permiso, vehículos y (al editar) el registro existente.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const granted = await hasLocationPermission();
      if (cancelled) return;
      update({ hasLocationPermission: granted });
      if (parkingId) {
        try {
          const parking = await getParking(parkingId);
          if (!parking) throw new Error(t('form_err_not_exists'));
          original.current = parking;
          positionRef.current = parking.location;
          if (cancelled) return;
          update({
            loading: false,
            vehicleId: parking.vehicleId,
            note: parking.note ?? '',
            levelSector: parking.levelSector ?? '',
            ruleSummary: parking.ruleSummary ?? '',
            position: parking.location,
            positionSource: parking.location ? 'saved' : 'none',
            addressText: parking.addressText,
            expiresAt: parking.expiresAt,
            selectedPreset: parking.expiresAt == null ? NO_LIMIT : null,
          });
          for (const kind of ['place', 'sign'] as const) {
            if (!(kind === 'place' ? parking.placePhotoPath : parking.signPhotoPath)) continue;
            update({ processingPhoto: kind });
            const photo = await loadPhoto(parkingId, kind).catch(() => null);
            if (!cancelled) update(photoPatch(kind, photo, false));
            update({ processingPhoto: null });
          }
        } catch (e) {
          console.warn(`No se pudo cargar ${parkingId}`, e);
          update({ loading: false, error: (e as Error).message || t('form_err_load_failed') });
        }
      } else if (granted) {
        locate();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [parkingId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Vehículos en tiempo real; en un alta se preselecciona el primero que no esté estacionado.
  useEffect(() => {
    let vehicles: Vehicle[] = [];
    let busy = new Set<string>();
    const apply = () =>
      setState((s) => {
        const keep = s.vehicleId != null && vehicles.some((v) => v.id === s.vehicleId);
        return {
          ...s,
          vehicles,
          vehicleId: keep || s.isEdit ? s.vehicleId : defaultVehicleId(vehicles, busy),
        };
      });
    const offVehicles = observeVehicles((list) => {
      vehicles = list;
      apply();
    });
    const offActive = observeActive((list) => {
      busy = new Set(list.map((p) => p.vehicleId));
      apply();
    });
    return () => {
      offVehicles();
      offActive();
    };
  }, []);

  const requestPermission = useCallback(async () => {
    const granted = await requestLocationPermission();
    update({ hasLocationPermission: granted });
    if (granted && !parkingId) locate();
  }, [parkingId, locate, update]);

  /** Plan B sin GPS o para corregir: el usuario toca el mapa donde dejó el vehículo. */
  const setManualPosition = useCallback(
    (latitude: number, longitude: number) => {
      const position = { latitude, longitude };
      positionRef.current = position;
      update({ position, positionSource: 'manual', addressText: null, error: null });
      resolveAddress(position);
    },
    [update, resolveAddress],
  );

  const pickPhoto = useCallback(
    async (kind: PhotoKind, source: 'camera' | 'gallery') => {
      try {
        const permission =
          source === 'camera'
            ? await ImagePicker.requestCameraPermissionsAsync()
            : await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (!permission.granted) return;
        const result =
          source === 'camera'
            ? await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 1 })
            : await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 1 });
        const asset = result.canceled ? null : result.assets[0];
        if (!asset) return;
        update({ processingPhoto: kind, error: null });
        const jpeg = await compressImage(asset.uri, asset.width, asset.height);
        update({ ...photoPatch(kind, jpeg, true), processingPhoto: null });
      } catch (e) {
        console.warn('Error procesando la foto', e);
        update({ processingPhoto: null, error: t('form_err_photo_use', (e as Error).message ?? '') });
      }
    },
    [update],
  );

  const removePhoto = useCallback(
    (kind: PhotoKind) => update(photoPatch(kind, null, true)),
    [update],
  );

  const setNoLimit = () => update({ expiresAt: null, selectedPreset: NO_LIMIT, error: null });
  const setPreset = (minutes: number) =>
    update({ expiresAt: Date.now() + minutes * 60_000, selectedPreset: minutes, error: null });
  const setExpiryTime = (hour: number, minute: number) =>
    update({ expiresAt: expiryAtTime(hour, minute), selectedPreset: null, error: null });

  const save = useCallback(async () => {
    const s = state;
    if (s.saving) return;
    const problem = validateForm({
      processingPhoto: s.processingPhoto != null,
      vehicleId: s.vehicleId,
      position: s.position,
      expiresAt: s.expiresAt,
    });
    if (problem) return fail(problem);
    const vehicleId = s.vehicleId!;
    const position = s.position!;
    update({ saving: true, error: null });
    try {
      const base = original.current;
      const fields = {
        vehicleId,
        location: { latitude: position.latitude, longitude: position.longitude },
        addressText: s.addressText,
        note: trimOrNull(s.note),
        levelSector: trimOrNull(s.levelSector),
        ruleSummary: trimOrNull(s.ruleSummary),
        expiresAt: s.expiresAt,
      };
      let id: string;
      if (!base) {
        id = await createParking(
          newParking({
            ...fields,
          }),
        );
      } else {
        if (vehicleId !== base.vehicleId && (await hasActiveParking(vehicleId))) throw new ActiveParkingExistsError();
        await updateParking({ ...base, ...fields });
        id = base.id;
      }
      await savePhotos(id, s, base);
      precacheTilesAround(position.latitude, position.longitude);
      update({ saving: false, savedId: id });
    } catch (e) {
      console.warn('Error guardando', e);
      update({
        saving: false,
        error: e instanceof ActiveParkingExistsError ? e.message : t('form_err_save', (e as Error).message ?? ''),
      });
    }
  }, [state, update, fail]);

  return {
    state,
    setVehicle: (id: string) => update({ vehicleId: id, error: null }),
    setNote: (note: string) => update({ note }),
    setLevelSector: (levelSector: string) => update({ levelSector }),
    setRuleSummary: (ruleSummary: string) => update({ ruleSummary }),
    setNoLimit,
    setPreset,
    setExpiryTime,
    locate,
    requestPermission,
    setManualPosition,
    pickPhoto,
    removePhoto,
    save,
  };
}

function photoPatch(kind: PhotoKind, photo: string | null, changed: boolean): Partial<FormState> {
  if (kind === 'place') return { placePhoto: photo, ...(changed ? { placePhotoChanged: true } : {}) };
  return { signPhoto: photo, ...(changed ? { signPhotoChanged: true } : {}) };
}

/** Guarda o borra cada foto que cambió y actualiza las rutas del estacionamiento. */
async function savePhotos(parkingId: string, s: FormState, base: Parking | null) {
  if (!s.placePhotoChanged && !s.signPhotoChanged) return;
  const resolve = async (kind: PhotoKind, photo: string | null, changed: boolean, existing: string | null) => {
    if (!changed) return existing;
    if (photo) return savePhoto(parkingId, kind, photo);
    if (existing) await deletePhoto(parkingId, kind);
    return null;
  };
  const place = await resolve('place', s.placePhoto, s.placePhotoChanged, base?.placePhotoPath ?? null);
  const sign = await resolve('sign', s.signPhoto, s.signPhotoChanged, base?.signPhotoPath ?? null);
  await setPhotoPaths(parkingId, place, sign);
}
