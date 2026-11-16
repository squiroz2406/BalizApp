import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import es from './es.json';
import en from './en.json';
import it from './it.json';
import pt from './pt.json';
import de from './de.json';

/** Idiomas de la app. El primero es el predeterminado. */
export type LanguageCode = 'ES' | 'EN' | 'IT' | 'PT' | 'DE';

export const LANGUAGES: { code: LanguageCode; tag: string; nativeName: string }[] = [
  { code: 'ES', tag: 'es-AR', nativeName: 'Español' },
  { code: 'EN', tag: 'en', nativeName: 'English' },
  { code: 'IT', tag: 'it', nativeName: 'Italiano' },
  { code: 'PT', tag: 'pt', nativeName: 'Português' },
  { code: 'DE', tag: 'de', nativeName: 'Deutsch' },
];

type Dict = Record<string, string>;
const dictionaries: Record<LanguageCode, Dict> = {
  ES: es.strings,
  EN: en.strings,
  IT: it.strings,
  PT: pt.strings,
  DE: de.strings,
};

const STORAGE_KEY = 'settings.language';

let current: LanguageCode = 'ES';
const listeners = new Set<() => void>();

export function currentLanguage() {
  return LANGUAGES.find((l) => l.code === current)!;
}

export function currentLocaleTag(): string {
  return currentLanguage().tag;
}

/** Reemplaza {1}, {2}... por los argumentos (equivale a %1$s / %2$d de Android). */
export function format(template: string, args: (string | number)[]): string {
  return template.replace(/\{(\d+)\}/g, (_, n) => String(args[Number(n) - 1] ?? ''));
}

/** Texto traducido. Si falta en el idioma elegido, cae al español. */
export function t(key: string, ...args: (string | number)[]): string {
  const template = dictionaries[current][key] ?? dictionaries.ES[key] ?? key;
  return format(template, args);
}

export function setLanguageForTests(code: LanguageCode) {
  current = code;
}

export async function setLanguage(code: LanguageCode) {
  current = code;
  try {
    await AsyncStorage.setItem(STORAGE_KEY, code);
  } catch {
    // Si no se puede guardar, igual se aplica en esta sesión.
  }
  listeners.forEach((l) => l());
}

export async function loadSavedLanguage() {
  try {
    const saved = (await AsyncStorage.getItem(STORAGE_KEY)) as LanguageCode | null;
    if (saved && saved in dictionaries) current = saved;
  } catch {
    // se queda en español
  }
}

interface I18nValue {
  language: LanguageCode;
  setLanguage: (code: LanguageCode) => Promise<void>;
  t: typeof t;
}

const I18nContext = createContext<I18nValue>({ language: 'ES', setLanguage, t });

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [language, setLang] = useState<LanguageCode>(current);
  useEffect(() => {
    const listener = () => setLang(current);
    listeners.add(listener);
    loadSavedLanguage().then(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);
  // `t` cambia de identidad con el idioma para que los componentes memoizados se vuelvan a dibujar.
  const translate = useCallback((key: string, ...args: (string | number)[]) => t(key, ...args), [language]);
  const value = useMemo(() => ({ language, setLanguage, t: translate }), [language, translate]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export const useI18n = () => useContext(I18nContext);
