import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';
import { darkPalette, lightPalette, type Palette } from './colors';

/** Claro, oscuro o el del sistema (se elige en Perfil). */
export type ThemeMode = 'system' | 'light' | 'dark';
const STORAGE_KEY = 'settings.themeMode';

interface ThemeValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  colors: Palette;
}

const ThemeContext = createContext<ThemeValue>({ mode: 'system', setMode: () => undefined, colors: lightPalette });

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const system = useColorScheme();
  const [mode, setModeState] = useState<ThemeMode>('system');

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then((saved) => {
        if (saved === 'light' || saved === 'dark' || saved === 'system') setModeState(saved);
      })
      .catch(() => undefined);
  }, []);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    AsyncStorage.setItem(STORAGE_KEY, next).catch(() => undefined);
  }, []);

  const dark = mode === 'system' ? system === 'dark' : mode === 'dark';
  const value = useMemo(() => ({ mode, setMode, colors: dark ? darkPalette : lightPalette }), [mode, setMode, dark]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);
