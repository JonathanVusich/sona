import { createSignal } from "solid-js";
import { markPlayed, type Track } from "./library";

// Playback state. The clock is simulated until the backend streams audio.

export type RepeatMode = "off" | "all" | "one";

const [queue, setQueue] = createSignal<Track[]>([]);
// Play order as indexes into the queue; shuffled when shuffle is on.
const [order, setOrder] = createSignal<number[]>([]);
// Position within the play order.
const [cursor, setCursor] = createSignal(0);
const [playing, setPlaying] = createSignal(false);
const [position, setPosition] = createSignal(0);
const [volume, setVolumeLevel] = createSignal(0.8);
const [muted, setMuted] = createSignal(false);
const [shuffle, setShuffle] = createSignal(false);
const [repeat, setRepeat] = createSignal<RepeatMode>("off");

export { playing, position, volume, muted, shuffle, repeat };

export const current = (): Track | undefined => {
  const index = order()[cursor()];
  return index === undefined ? undefined : queue()[index];
};

export function playTracks(tracks: Track[], start = 0) {
  if (tracks.length === 0) {
    return;
  }
  setQueue(tracks);
  if (shuffle()) {
    setOrder(shuffledOrder(tracks.length, start));
    setCursor(0);
  } else {
    setOrder(inOrder(tracks.length));
    setCursor(start);
  }
  startTrack();
}

export function shufflePlay(tracks: Track[]) {
  setShuffle(true);
  playTracks(tracks, Math.floor(Math.random() * tracks.length));
}

export function togglePlay() {
  if (current() !== undefined) {
    setPlaying(!playing());
  }
}

export function next() {
  advance(false);
}

export function previous() {
  // Like most players: restart the track unless it has only just begun.
  if (position() > 3 || (cursor() === 0 && repeat() === "off")) {
    setPosition(0);
    return;
  }
  setCursor(cursor() === 0 ? order().length - 1 : cursor() - 1);
  startTrack();
}

export function seek(seconds: number) {
  const track = current();
  if (track !== undefined) {
    setPosition(clamp(seconds, 0, track.duration));
  }
}

export function toggleShuffle() {
  const on = !shuffle();
  setShuffle(on);
  const playingIndex = order()[cursor()];
  if (playingIndex === undefined) {
    return;
  }
  // Keep the current track playing and reorder only what comes after it.
  if (on) {
    setOrder(shuffledOrder(queue().length, playingIndex));
    setCursor(0);
  } else {
    setOrder(inOrder(queue().length));
    setCursor(playingIndex);
  }
}

export function cycleRepeat() {
  setRepeat((mode) => (mode === "off" ? "all" : mode === "all" ? "one" : "off"));
}

export function setVolume(level: number) {
  setVolumeLevel(clamp(level, 0, 1));
  setMuted(level <= 0);
}

export function toggleMute() {
  if (muted() && volume() <= 0) {
    setVolumeLevel(0.5);
  }
  setMuted(!muted());
}

export function stop() {
  setPlaying(false);
  setQueue([]);
  setOrder([]);
  setCursor(0);
  setPosition(0);
}

function startTrack() {
  setPosition(0);
  setPlaying(true);
  const track = current();
  if (track !== undefined) {
    markPlayed(track);
  }
}

function advance(trackEnded: boolean) {
  if (trackEnded && repeat() === "one") {
    setPosition(0);
  } else if (cursor() + 1 < order().length) {
    setCursor(cursor() + 1);
    startTrack();
  } else if (repeat() !== "off") {
    setCursor(0);
    startTrack();
  } else {
    setPlaying(false);
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
  if (!playing() || track === undefined) {
    return;
  }
  const nextPosition = position() + elapsed;
  if (nextPosition >= track.duration) {
    advance(true);
  } else {
    setPosition(nextPosition);
  }
}, 250);
