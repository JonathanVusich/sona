import { createSignal } from "solid-js";
import * as api from "./api/client";
import { ResultKind } from "./api/client";
import type {
  MediumResponse,
  ReleaseGroupResponse,
  ReleaseResponse,
  TrackReleaseResponse,
  TrackResponse,
} from "./api/responses";
import {
  clearAudio,
  hasStream,
  onAudioEnded,
  onAudioError,
  onAudioProgress,
  pauseAudio,
  resumeAudio,
  seekAudio,
  setAudioVolume,
  StartOutcome,
  startStream,
} from "./audio";
import { sendAsCurrentUser } from "./auth";
import { toast } from "./components/Toast";

// Playback state, which the audio element follows as Sona streams each track.

/** A track the player can queue: one from Sona, with the release it's on, which the player links to. */
export interface QueuedTrack extends TrackResponse {
  release: TrackReleaseResponse;
}

/**
 * @returns the release's tracks, disc by disc, as the player queues them
 */
export function releaseTracks(release: ReleaseResponse): QueuedTrack[] {
  return queuedMedia({ releaseId: release.releaseId, name: release.name }, release.media);
}

/**
 * @returns the tracks of the release that stands for the group, disc by disc, as the player queues them
 */
export function defaultReleaseTracks(releaseGroup: ReleaseGroupResponse): QueuedTrack[] {
  const defaultRelease = releaseGroup.defaultRelease;
  const edition = releaseGroup.editions.find((listed) => listed.releaseId === defaultRelease.releaseId);
  if (edition === undefined) {
    throw new Error("A release group's default release is one of its editions.");
  }
  return queuedMedia({ releaseId: edition.releaseId, name: edition.name }, defaultRelease.media);
}

function queuedMedia(release: QueuedTrack["release"], media: MediumResponse[]): QueuedTrack[] {
  return media.flatMap((medium) => medium.tracks).map((track) => ({ ...track, release }));
}

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

const [queue, setQueue] = createSignal<QueuedTrack[]>([]);
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
export function current(): QueuedTrack | null {
  const index = order()[cursor()];
  if (index === undefined) {
    return null;
  }
  return queue()[index] ?? null;
}

export async function playTracks(tracks: QueuedTrack[]) {
  await playListedTrack(tracks, 0);
}

/**
 * Plays the track at the index, queueing the rest of the list around it.
 */
export async function playListedTrack(tracks: QueuedTrack[], start: number) {
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
  await startTrack();
}

export async function shufflePlay(tracks: QueuedTrack[]) {
  setShuffleMode(ShuffleMode.ON);
  await playListedTrack(tracks, Math.floor(Math.random() * tracks.length));
}

export async function togglePlay() {
  if (current() === null) {
    return;
  }
  const toggled = toggledPlayback(playback());
  setPlayback(toggled);
  switch (toggled) {
    case Playback.PLAYING:
      await resumePlayback();
      break;
    case Playback.PAUSED:
      pauseAudio();
      break;
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

export async function next() {
  await advance(Advance.SKIPPED);
}

export async function previous() {
  // Like most players: restart the track unless it has only just begun.
  if (position() > 3 || (cursor() === 0 && repeat() === RepeatMode.OFF)) {
    restartTrack();
    return;
  }
  setCursor(previousCursor());
  await startTrack();
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
    const clamped = clamp(seconds, 0, track.duration);
    setPosition(clamped);
    seekAudio(clamped);
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
  setAudioVolume(audibleVolume());
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
  setAudioVolume(audibleVolume());
}

// Muting silences the audio but keeps the volume to unmute to.
function audibleVolume(): number {
  switch (sound()) {
    case Sound.ON:
      return volume();
    case Sound.MUTED:
      return 0;
  }
}

export function stop() {
  setPlayback(Playback.PAUSED);
  setQueue([]);
  setOrder([]);
  setCursor(0);
  setPosition(0);
  clearAudio();
}

async function startTrack() {
  setPosition(0);
  setPlayback(Playback.PLAYING);
  const track = current();
  if (track === null) {
    return;
  }
  // The track before stops while this one's token is on its way.
  pauseAudio();
  // The audio element can't send the access token, so each track is streamed with a token for it alone.
  const result = await sendAsCurrentUser((accessToken) => api.fetchStreamToken(accessToken, track.trackId));
  // The user moved on while the token was on its way.
  if (current() !== track) {
    return;
  }
  if (result.kind !== ResultKind.OK) {
    failPlayback(track);
    return;
  }
  const outcome = await startStream(api.trackStreamUrl(track.trackId, result.body.token));
  followStart(outcome);
}

async function resumePlayback() {
  // A track that failed to stream has no stream to resume, so it's asked for again.
  if (!hasStream()) {
    await startTrack();
    return;
  }
  const outcome = await resumeAudio();
  followStart(outcome);
}

function followStart(outcome: StartOutcome) {
  switch (outcome) {
    case StartOutcome.STARTED:
    case StartOutcome.REPLACED:
      break;
    case StartOutcome.REFUSED:
      setPlayback(Playback.PAUSED);
      break;
  }
}

function restartTrack() {
  setPosition(0);
  seekAudio(0);
}

function failPlayback(track: QueuedTrack) {
  setPlayback(Playback.PAUSED);
  // Otherwise playing again would resume the track before.
  clearAudio();
  toast(`Couldn't play ${track.name}.`);
}

async function advance(cause: Advance) {
  if (cause === Advance.ENDED && repeat() === RepeatMode.ONE) {
    restartTrack();
    await resumePlayback();
  } else if (cursor() + 1 < order().length) {
    setCursor(cursor() + 1);
    await startTrack();
  } else if (repeat() !== RepeatMode.OFF) {
    setCursor(0);
    await startTrack();
  } else {
    setPlayback(Playback.PAUSED);
    restartTrack();
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

setAudioVolume(audibleVolume());
onAudioProgress(setPosition);
onAudioEnded(() => advance(Advance.ENDED));
onAudioError(() => {
  const track = current();
  if (track !== null) {
    failPlayback(track);
  }
});
