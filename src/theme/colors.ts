/* Paleta de BalizApp: ámbar de baliza sobre azul noche (misma que la app Kotlin). */
export interface Palette {
  dark: boolean;
  primary: string;
  onPrimary: string;
  primaryContainer: string;
  onPrimaryContainer: string;
  secondary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;
  tertiary: string;
  background: string;
  surface: string;
  surfaceVariant: string;
  onSurface: string;
  onSurfaceVariant: string;
  outline: string;
  error: string;
  onError: string;
  errorContainer: string;
  onErrorContainer: string;
  success: string;
  successContainer: string;
}

export const lightPalette: Palette = {
  dark: false,
  primary: '#8A5100',
  onPrimary: '#FFFFFF',
  primaryContainer: '#FFDCBE',
  onPrimaryContainer: '#2C1600',
  secondary: '#3A5BA0',
  secondaryContainer: '#D9E2FF',
  onSecondaryContainer: '#001944',
  tertiary: '#006A62',
  background: '#FFF8F4',
  surface: '#FFF8F4',
  surfaceVariant: '#F2E0D0',
  onSurface: '#211A14',
  onSurfaceVariant: '#52443A',
  outline: '#85746A',
  error: '#BA1A1A',
  onError: '#FFFFFF',
  errorContainer: '#FFDAD6',
  onErrorContainer: '#410002',
  success: '#1B6E3A',
  successContainer: '#C8F0D2',
};

export const darkPalette: Palette = {
  dark: true,
  primary: '#FFB870',
  onPrimary: '#4A2800',
  primaryContainer: '#693C00',
  onPrimaryContainer: '#FFDCBE',
  secondary: '#B0C6FF',
  secondaryContainer: '#1F4385',
  onSecondaryContainer: '#D9E2FF',
  tertiary: '#6FD8CB',
  background: '#12131A',
  surface: '#12131A',
  surfaceVariant: '#2B2C36',
  onSurface: '#E3E1EC',
  onSurfaceVariant: '#C6C5D0',
  outline: '#90909A',
  error: '#FFB4AB',
  onError: '#690005',
  errorContainer: '#93000A',
  onErrorContainer: '#FFDAD6',
  success: '#7FDB9A',
  successContainer: '#0F3D20',
};
