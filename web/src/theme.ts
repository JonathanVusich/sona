import { readSetting, writeSetting } from "./storage";

export const Theme = {
  SYSTEM: "system",
  LIGHT: "light",
  DARK: "dark",
} as const;

export type Theme = (typeof Theme)[keyof typeof Theme];

const STORAGE_KEY = "sona.theme";

export function loadTheme(): Theme {
  const stored = readSetting(STORAGE_KEY);
  if (stored === null) {
    return Theme.SYSTEM;
  }
  return parseTheme(stored);
}

/**
 * @returns the theme the value names, or the system's when it names none
 */
export function parseTheme(value: string): Theme {
  return Object.values(Theme).find((theme) => theme === value) ?? Theme.SYSTEM;
}

export function applyTheme(theme: Theme) {
  if (theme === Theme.SYSTEM) {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = theme;
  }
  writeSetting(STORAGE_KEY, theme);
}
