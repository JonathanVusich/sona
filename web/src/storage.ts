// The browser's local storage, which keeps the viewer's settings between visits. Browsers throw when a user blocks
// site data, so this is the one place that catches: a setting that can't be kept lasts for this page load only.

export function readSetting(key: string): string | null {
  const storage = localStorageIfAllowed();
  return storage === null ? null : storage.getItem(key);
}

export function writeSetting(key: string, value: string) {
  localStorageIfAllowed()?.setItem(key, value);
}

function localStorageIfAllowed(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}
