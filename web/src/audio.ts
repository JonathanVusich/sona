// The one audio element the player streams through, which the browser plays and buffers, seeking with range requests.

export const StartOutcome = {
  // The audio is playing.
  STARTED: "started",
  // Another source replaced this one before it started, and plays instead.
  REPLACED: "replaced",
  // The browser wouldn't play it, such as when the user hasn't interacted with the page yet.
  REFUSED: "refused",
} as const;

export type StartOutcome = (typeof StartOutcome)[keyof typeof StartOutcome];

const audio = new Audio();
audio.preload = "auto";

/**
 * Plays from the URL, from the start.
 */
export async function startStream(url: string): Promise<StartOutcome> {
  audio.src = url;
  return resumeAudio();
}

/**
 * Plays the current stream from where it paused.
 */
export async function resumeAudio(): Promise<StartOutcome> {
  // Like the API client, this catches: play() rejects rather than answering, when the browser refuses or a new source
  // interrupts it.
  try {
    await audio.play();
    return StartOutcome.STARTED;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      return StartOutcome.REPLACED;
    }
    return StartOutcome.REFUSED;
  }
}

/**
 * @returns whether there's a stream to resume, which there isn't before the first or after clearAudio
 */
export function hasStream(): boolean {
  return audio.getAttribute("src") !== null;
}

export function pauseAudio() {
  audio.pause();
}

export function seekAudio(seconds: number) {
  audio.currentTime = seconds;
}

/**
 * @param level from 0, silent, to 1
 */
export function setAudioVolume(level: number) {
  audio.volume = level;
}

/**
 * Stops playing and drops the stream, so nothing more is downloaded.
 */
export function clearAudio() {
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
}

/**
 * Calls back with the position in seconds as the audio plays.
 */
export function onAudioProgress(listener: (seconds: number) => void) {
  audio.addEventListener("timeupdate", () => listener(audio.currentTime));
}

export function onAudioEnded(listener: () => void) {
  audio.addEventListener("ended", listener);
}

/**
 * Calls back when the stream can't be played, such as when Sona refuses it or the connection drops.
 */
export function onAudioError(listener: () => void) {
  audio.addEventListener("error", () => {
    // Clearing the source fires an error too, which isn't one.
    if (hasStream()) {
      listener();
    }
  });
}
