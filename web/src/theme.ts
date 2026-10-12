import { readSetting, writeSetting } from "./storage";

export type Theme = "system" | "light" | "dark";

const THEMES: readonly Theme[] = ["system", "light", "dark"];
const STORAGE_KEY = "sona.theme";

export function loadTheme(): Theme {
  return parseTheme(readSetting(STORAGE_KEY));
}

/**
 * @returns the theme the value names, or "system" when it names none
 */
export function parseTheme(value: string | null): Theme {
  return THEMES.find((theme) => theme === value) ?? "system";
}

export function applyTheme(theme: Theme) {
  if (theme === "system") {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = theme;
  }
  writeSetting(STORAGE_KEY, theme);
}
