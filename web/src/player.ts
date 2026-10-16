import { createSignal } from "solid-js";
import { markPlayed, type Track } from "./library";

// Playback state. The clock is simulated until the backend streams audio.

export const RepeatMode = {
  OFF: "off",
  ALL: "all",
  ONE: "one",
} as const;

export type RepeatMode = (typeof RepeatMode)[keyof typeof RepeatMode];

export const Playback = {
  PLAYING: "playing",
  PAUSED: "paused",
} as const;

export type Playback = (typeof Playback)[keyof typeof Playback];

export const Sound = {
  ON: "on",
  MUTED: "muted",
} as const;

export type Sound = (typeof Sound)[keyof typeof Sound];

export const ShuffleMode = {
  OFF: "off",
  ON: "on",
} as const;

export type ShuffleMode = (typeof ShuffleMode)[keyof typeof ShuffleMode];

// Why the player moves on to another track.
const Advance = {
  SKIPPED: "skipped",
  ENDED: "ended",
} as const;

type Advance = (typeof Advance)[keyof typeof Advance];

const [queue, setQueue] = createSignal<Track[]>([]);
// Play order as indexes into the queue; shuffled when shuffle is on.
const [order, setOrder] = createSignal<number[]>([]);
// Position within the play order.
const [cursor, setCursor] = createSignal(0);
const [playback, setPlayback] = createSignal<Playback>(Playback.PAUSED);
const [position, setPosition] = createSignal(0);
const [volume, setVolumeLevel] = createSignal(0.8);
const [sound, setSound] = createSignal<Sound>(Sound.ON);
const [shuffleMode, setShuffleMode] = createSignal<ShuffleMode>(ShuffleMode.OFF);
const [repeat, setRepeat] = createSignal<RepeatMode>(RepeatMode.OFF);

export { playback, position, volume, sound, shuffleMode, repeat };

/**
 * @returns the track the player is on, or null when nothing is queued
 */
export function current(): Track | null {
  const index = order()[cursor()];
  if (index === undefined) {
    return null;
  }
  return queue()[index] ?? null;
}

export function playTracks(tracks: Track[], start = 0) {
  if (tracks.length === 0) {
    return;
  }
  setQueue(tracks);
  switch (shuffleMode()) {
    case ShuffleMode.ON:
      setOrder(shuffledOrder(tracks.length, start));
      setCursor(0);
      break;
    case ShuffleMode.OFF:
      setOrder(inOrder(tracks.length));
      setCursor(start);
      break;
  }
  startTrack();
}

export function shufflePlay(tracks: Track[]) {
  setShuffleMode(ShuffleMode.ON);
  playTracks(tracks, Math.floor(Math.random() * tracks.length));
}

export function togglePlay() {
  if (current() !== null) {
    setPlayback(toggledPlayback(playback()));
  }
}

function toggledPlayback(current: Playback): Playback {
  switch (current) {
    case Playback.PLAYING:
      return Playback.PAUSED;
    case Playback.PAUSED:
      return Playback.PLAYING;
  }
}

export function next() {
  advance(Advance.SKIPPED);
}

export function previous() {
  // Like most players: restart the track unless it has only just begun.
  if (position() > 3 || (cursor() === 0 && repeat() === RepeatMode.OFF)) {
    setPosition(0);
    return;
  }
  setCursor(previousCursor());
  startTrack();
}

// The previous track wraps around to the last.
function previousCursor(): number {
  if (cursor() === 0) {
    return order().length - 1;
  }
  return cursor() - 1;
}

export function seek(seconds: number) {
  const track = current();
  if (track !== null) {
    setPosition(clamp(seconds, 0, track.duration));
  }
}

export function toggleShuffle() {
  const mode = toggledShuffleMode(shuffleMode());
  setShuffleMode(mode);
  const playingIndex = order()[cursor()];
  if (playingIndex === undefined) {
    return;
  }
  // Keep the current track playing and reorder only what comes after it.
  switch (mode) {
    case ShuffleMode.ON:
      setOrder(shuffledOrder(queue().length, playingIndex));
      setCursor(0);
      break;
    case ShuffleMode.OFF:
      setOrder(inOrder(queue().length));
      setCursor(playingIndex);
      break;
  }
}

function toggledShuffleMode(mode: ShuffleMode): ShuffleMode {
  switch (mode) {
    case ShuffleMode.ON:
      return ShuffleMode.OFF;
    case ShuffleMode.OFF:
      return ShuffleMode.ON;
  }
}

export function cycleRepeat() {
  setRepeat(nextRepeatMode);
}

function nextRepeatMode(mode: RepeatMode): RepeatMode {
  switch (mode) {
    case RepeatMode.OFF:
      return RepeatMode.ALL;
    case RepeatMode.ALL:
      return RepeatMode.ONE;
    case RepeatMode.ONE:
      return RepeatMode.OFF;
  }
}

export function setVolume(level: number) {
  setVolumeLevel(clamp(level, 0, 1));
  setSound(soundAtLevel(level));
}

function soundAtLevel(level: number): Sound {
  if (level <= 0) {
    return Sound.MUTED;
  }
  return Sound.ON;
}

export function toggleMute() {
  switch (sound()) {
    case Sound.ON:
      setSound(Sound.MUTED);
      break;
    case Sound.MUTED:
      // Unmuting at no volume would stay silent.
      if (volume() <= 0) {
        setVolumeLevel(0.5);
      }
      setSound(Sound.ON);
      break;
  }
}

export function stop() {
  setPlayback(Playback.PAUSED);
  setQueue([]);
  setOrder([]);
  setCursor(0);
  setPosition(0);
}

function startTrack() {
  setPosition(0);
  setPlayback(Playback.PLAYING);
  const track = current();
  if (track !== null) {
    markPlayed(track);
  }
}

function advance(cause: Advance) {
  if (cause === Advance.ENDED && repeat() === RepeatMode.ONE) {
    setPosition(0);
  } else if (cursor() + 1 < order().length) {
    setCursor(cursor() + 1);
    startTrack();
  } else if (repeat() !== RepeatMode.OFF) {
    setCursor(0);
    startTrack();
  } else {
    setPlayback(Playback.PAUSED);
    setPosition(0);
  }
}

function inOrder(length: number): number[] {
  return Array.from({ length }, (_, i) => i);
}

function shuffledOrder(length: number, first: number): number[] {
  const rest = inOrder(length).filter((i) => i !== first);
  for (let i = rest.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [rest[i], rest[j]] = [rest[j], rest[i]];
  }
  return [first, ...rest];
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

let lastTick = performance.now();
setInterval(() => {
  const now = performance.now();
  const elapsed = (now - lastTick) / 1000;
  lastTick = now;
  const track = current();
  if (playback() === Playback.PAUSED || track === null) {
    return;
  }
  const nextPosition = position() + elapsed;
  if (nextPosition >= track.duration) {
    advance(Advance.ENDED);
  } else {
    setPosition(nextPosition);
  }
}, 250);
