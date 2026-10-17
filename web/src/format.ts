import { ReleaseGroupType, type LabelResponse } from "./api/responses";

export function formatTime(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}

export function formatTotal(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min`;
  }
  return `${Math.floor(minutes / 60)} hr ${minutes % 60} min`;
}

export function plural(count: number, word: string): string {
  return irregularPlural(count, word, `${word}s`);
}

// For a word whose plural isn't the word with an s.
export function irregularPlural(count: number, word: string, pluralWord: string): string {
  if (count === 1) {
    return `${count} ${word}`;
  }
  return `${count} ${pluralWord}`;
}

// Only the year: MusicBrainz stores a partial date as the first of what it knows, so "2002" is 2002-01-01, and Sona
// doesn't keep how much of a date is known.
const YEAR_FORMAT = new Intl.DateTimeFormat(undefined, { year: "numeric", timeZone: "UTC" });

/**
 * @returns the year of an ISO date, or null when the date isn't known
 */
export function formatYear(isoDate: string | null): string | null {
  if (isoDate === null) {
    return null;
  }
  // A date-only ISO string is read as UTC midnight, which the format reads back in UTC.
  return YEAR_FORMAT.format(new Date(isoDate));
}

/**
 * @returns the media's formats as MusicBrainz writes them, like "2×CD" or "CD + DVD", or null when none is known
 */
export function formatMedia(formats: (string | null)[]): string | null {
  const known = formats.filter((format) => format !== null);
  if (known.length === 0) {
    return null;
  }
  return [...new Set(known)].map((format) => countedFormat(known, format)).join(" + ");
}

function countedFormat(formats: string[], format: string): string {
  const count = formats.filter((other) => other === format).length;
  if (count === 1) {
    return format;
  }
  return `${count}×${format}`;
}

/**
 * @returns the labels by name, or null when there are none
 */
export function formatLabels(labels: LabelResponse[]): string | null {
  if (labels.length === 0) {
    return null;
  }
  return labels.map((label) => label.name).join(", ");
}

/**
 * @returns the details on one line, leaving out those that aren't known
 */
export function joinDetails(details: (string | null)[]): string {
  return details.filter((detail) => detail !== null).join(" · ");
}

/**
 * @returns what a page calls the release group type
 */
export function releaseGroupTypeLabel(type: ReleaseGroupType | null): string {
  switch (type) {
    case ReleaseGroupType.ALBUM:
      return "Album";
    case ReleaseGroupType.SINGLE:
      return "Single";
    case ReleaseGroupType.EP:
      return "EP";
    case ReleaseGroupType.BROADCAST:
      return "Broadcast";
    case ReleaseGroupType.OTHER:
    case null:
      return "Release group";
  }
}
