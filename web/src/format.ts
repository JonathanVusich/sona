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
