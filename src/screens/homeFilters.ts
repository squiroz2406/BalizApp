import { isExpired, type Parking, type Vehicle } from '../data/models';

export type StatusFilter = 'all' | 'current' | 'expired' | 'finished';
export const STATUS_FILTERS: StatusFilter[] = ['all', 'current', 'expired', 'finished'];

/** Aplica los filtros de vehículo y estado de Inicio a la lista de activos y a la de historial. */
export function applyFilters(
  active: Parking[],
  history: Parking[],
  vehicleId: string | null,
  status: StatusFilter,
  now: number,
) {
  const matchesVehicle = (p: Parking) => vehicleId == null || p.vehicleId === vehicleId;
  const activeOut = active.filter(
    (p) =>
      matchesVehicle(p) &&
      (status === 'all' || (status === 'current' && !isExpired(p, now)) || (status === 'expired' && isExpired(p, now))),
  );
  const historyOut = history.filter((p) => matchesVehicle(p) && (status === 'all' || status === 'finished'));
  return { active: activeOut, history: historyOut };
}

/** El filtro de vehículo solo vale si ese vehículo todavía existe. */
export function validVehicleFilter(id: string | null, vehicles: Vehicle[]): string | null {
  return id != null && vehicles.some((v) => v.id === id) ? id : null;
}
