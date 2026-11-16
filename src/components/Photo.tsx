import React from 'react';
import { Image, type StyleProp, type ImageStyle } from 'react-native';

/** Foto guardada como JPEG en base64. */
export function Base64Image({ data, style }: { data: string; style?: StyleProp<ImageStyle> }) {
  return <Image source={{ uri: `data:image/jpeg;base64,${data}` }} style={style} resizeMode="cover" />;
}
