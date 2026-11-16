import { Camera, GeoJSONSource, Layer, Map, Marker, UserLocation, type CameraRef } from '@maplibre/maplibre-react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import React, { useEffect, useMemo, useRef } from 'react';
import { StyleSheet, Text, View, type NativeSyntheticEvent } from 'react-native';
import type { GeoPosition, VehicleType } from '../data/models';
import { useTheme } from '../theme/ThemeProvider';
import { DEFAULT_CENTER, mapStyleFor } from './mapStyle';

/** Un marcador a dibujar. [id] se devuelve al tocarlo. */
export interface MapMarker {
  id: string;
  point: GeoPosition;
  color: string;
  alpha?: number;
  /** Si se indica, el marcador es un círculo con el ícono del vehículo; si no, un pin común. */
  vehicleType?: VehicleType;
}

interface Props {
  center: GeoPosition | null;
  zoom?: number;
  /** Para volver a centrar en el mismo punto, se incrementa. */
  centerRequest?: number;
  markers?: MapMarker[];
  interactive?: boolean;
  showMyLocation?: boolean;
  onMapClick?: (point: GeoPosition) => void;
  onMarkerClick?: (id: string) => void;
  line?: GeoPosition[];
  lineColor?: string;
  /** Punteada = línea recta aproximada; continua = recorrido real por calles. */
  lineDashed?: boolean;
  fitPoints?: GeoPosition[];
  fitRequest?: number;
}

/**
 * Mapa de OpenStreetMap con MapLibre.
 * - center: cuando cambia (o cambia centerRequest), la cámara se mueve ahí.
 * - interactive = false: mapa estático (sin gestos), para el detalle.
 * - fitPoints: encuadra todos esos puntos; se aplica cuando cambia fitRequest.
 */
export function BalizMap({
  center,
  zoom = 17,
  centerRequest = 0,
  markers = [],
  interactive = true,
  showMyLocation = false,
  onMapClick,
  onMarkerClick,
  line = [],
  lineColor = '#1E88E5',
  lineDashed = true,
  fitPoints = [],
  fitRequest = 0,
}: Props) {
  const { colors } = useTheme();
  const camera = useRef<CameraRef>(null);
  const style = useMemo(() => mapStyleFor(colors.dark), [colors.dark]);
  const start = center ?? DEFAULT_CENTER;

  // Mover la cámara cuando cambia el centro pedido.
  useEffect(() => {
    if (!center) return;
    camera.current?.easeTo({ center: [center.longitude, center.latitude], zoom, duration: 400 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center?.latitude, center?.longitude, centerRequest]);

  // Encuadrar varios puntos.
  useEffect(() => {
    if (fitPoints.length < 2) return;
    const lons = fitPoints.map((p) => p.longitude);
    const lats = fitPoints.map((p) => p.latitude);
    camera.current?.fitBounds([Math.min(...lons), Math.min(...lats), Math.max(...lons), Math.max(...lats)], {
      padding: { top: 80, right: 60, bottom: 80, left: 60 },
      duration: 400,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fitRequest]);

  const lineData = useMemo(
    () => ({
      type: 'Feature' as const,
      properties: {},
      geometry: { type: 'LineString' as const, coordinates: line.map((p) => [p.longitude, p.latitude]) },
    }),
    [line],
  );

  return (
    <Map
      style={styles.map}
      mapStyle={style}
      dragPan={interactive}
      touchZoom={interactive}
      doubleTapZoom={interactive}
      touchRotate={false}
      touchPitch={false}
      compass={false}
      logo={false}
      attribution
      onPress={(e: NativeSyntheticEvent<{ lngLat: [number, number] }>) => {
        const [longitude, latitude] = e.nativeEvent.lngLat;
        onMapClick?.({ latitude, longitude });
      }}
    >
      <Camera ref={camera} initialViewState={{ center: [start.longitude, start.latitude], zoom: center ? zoom : 12 }} />
      {showMyLocation && <UserLocation />}
      {line.length >= 2 && (
        <GeoJSONSource id="route" data={lineData}>
          <Layer
            id="route-line"
            type="line"
            layout={{ 'line-cap': 'round', 'line-join': 'round' }}
            paint={{
              'line-color': lineColor,
              'line-width': 5,
              ...(lineDashed ? { 'line-dasharray': [2, 2] } : {}),
            }}
          />
        </GeoJSONSource>
      )}
      {markers.map((m) => (
        <Marker
          key={m.id}
          id={m.id}
          lngLat={[m.point.longitude, m.point.latitude]}
          anchor={m.vehicleType ? 'center' : 'bottom'}
          onPress={() => onMarkerClick?.(m.id)}
        >
          <View style={{ opacity: m.alpha ?? 1 }}>
            {m.vehicleType ? (
              <View style={[styles.vehicleDot, { backgroundColor: m.color }]}>
                <MaterialCommunityIcons name={m.vehicleType === 'motorcycle' ? 'motorbike' : 'car'} size={20} color="#fff" />
              </View>
            ) : (
              <MaterialCommunityIcons name="map-marker" size={40} color={m.color} />
            )}
          </View>
        </Marker>
      ))}
    </Map>
  );
}

const styles = StyleSheet.create({
  map: { flex: 1 },
  vehicleDot: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#fff',
  },
});

export const Attribution = ({ color }: { color: string }) => (
  <Text style={{ color, fontSize: 10 }}>© OpenStreetMap contributors</Text>
);
