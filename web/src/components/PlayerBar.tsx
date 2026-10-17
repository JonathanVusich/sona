import { A } from "@solidjs/router";
import { createSignal, onCleanup, onMount, Show } from "solid-js";
import { formatTime } from "../format";
import { contextOf, type Track } from "../library";
import * as player from "../player";
import { Playback, RepeatMode, ShuffleMode, Sound } from "../player";
import Cover from "./Cover";
import { NextIcon, PauseIcon, PlayIcon, PreviousIcon, RepeatIcon, ShuffleIcon, VolumeIcon } from "./Icons";
import Slider, { SliderSize, SliderStatus } from "./Slider";
import { AppRoutes, routeTo } from "../routes";

export default function PlayerBar() {
  // While the scrub thumb is held, where it is, which the bar shows instead of the playback position; null otherwise.
  const [scrub, setScrub] = createSignal<number | null>(null);
  const track = player.current;
  const context = () => {
    const playingTrack = track();
    if (playingTrack === null) {
      return { release: undefined, group: undefined, artist: undefined };
    }
    return contextOf(playingTrack);
  };
  const duration = () => track()?.duration ?? 0;
  const shownPosition = () => scrub() ?? player.position();
  const shownVolume = () => {
    switch (player.sound()) {
      case Sound.ON:
        return player.volume();
      case Sound.MUTED:
        return 0;
    }
  };

  // Space toggles playback, unless focus is on something that uses Space itself.
  const onKeyDown = (event: KeyboardEvent) => {
    const target = event.target;
    const usesSpace =
      target instanceof Element && target.closest("input, textarea, select, button, a, [role=slider]") !== null;
    if (event.code !== "Space" || event.repeat || usesSpace) {
      return;
    }
    event.preventDefault();
    player.togglePlay();
  };
  onMount(() => document.addEventListener("keydown", onKeyDown));
  onCleanup(() => document.removeEventListener("keydown", onKeyDown));

  return (
    <div class="player" role="region" aria-label="Player">
      {/* Runs along the top edge of the bar; the hit area reaches above it so the thumb is easy to grab. */}
      <div class="player-scrub">
        <Slider
          size={SliderSize.LARGE}
          label="Seek"
          value={shownPosition()}
          max={duration()}
          step={5}
          status={seekStatus(track())}
          valueText={`${formatTime(shownPosition())} of ${formatTime(duration())}`}
          onInput={setScrub}
          onCommit={(seconds) => {
            player.seek(seconds);
            setScrub(null);
          }}
        />
      </div>
      <div class="player-now">
        <Show when={context().release} fallback={<div class="cover player-cover empty-cover" />}>
          {(release) => (
            <A href={routeTo(AppRoutes.RELEASE, release().id)} aria-label={`Go to ${release().title}`}>
              <Cover seed={context().group?.title ?? release().title} class="player-cover" />
            </A>
          )}
        </Show>
        <div class="player-meta">
          <div class="player-title">{track()?.title ?? "Not playing"}</div>
          <Show when={context().artist} fallback={<div class="player-artist">Pick something to play</div>}>
            {(artist) => (
              <A href={routeTo(AppRoutes.ARTIST, artist().id)} class="player-artist">
                {artist().name}
              </A>
            )}
          </Show>
        </div>
      </div>

      <div class="player-controls">
        <button
          class="icon-button toggle optional"
          classList={{ on: player.shuffleMode() === ShuffleMode.ON }}
          aria-label="Shuffle"
          aria-pressed={player.shuffleMode() === ShuffleMode.ON}
          onClick={() => player.toggleShuffle()}
        >
          <ShuffleIcon size={18} />
        </button>
        <button class="icon-button" aria-label="Previous" disabled={!track()} onClick={() => player.previous()}>
          <PreviousIcon />
        </button>
        <button
          class="play-button"
          aria-label={playButtonLabel(player.playback())}
          disabled={!track()}
          onClick={() => player.togglePlay()}
        >
          <Show when={player.playback() === Playback.PLAYING} fallback={<PlayIcon size={22} />}>
            <PauseIcon size={22} />
          </Show>
        </button>
        <button class="icon-button" aria-label="Next" disabled={!track()} onClick={() => player.next()}>
          <NextIcon />
        </button>
        <button
          class="icon-button toggle optional"
          classList={{ on: player.repeat() !== RepeatMode.OFF }}
          aria-label={`Repeat: ${player.repeat()}`}
          aria-pressed={player.repeat() !== RepeatMode.OFF}
          onClick={() => player.cycleRepeat()}
        >
          <RepeatIcon size={18} one={player.repeat() === RepeatMode.ONE} />
        </button>
      </div>

      <div class="player-side">
        <span class="player-time">
          {formatTime(shownPosition())} / {formatTime(duration())}
        </span>
        <button class="icon-button" aria-label={muteButtonLabel(player.sound())} onClick={() => player.toggleMute()}>
          <VolumeIcon size={18} muted={shownVolume() === 0} />
        </button>
        <Slider
          size={SliderSize.SMALL}
          status={SliderStatus.ENABLED}
          label="Volume"
          value={shownVolume()}
          max={1}
          step={0.05}
          valueText={`${Math.round(shownVolume() * 100)}%`}
          onInput={player.setVolume}
          onCommit={player.setVolume}
        />
      </div>
    </div>
  );
}

// There's nothing to seek through until something plays.
function seekStatus(track: Track | null): SliderStatus {
  if (track === null) {
    return SliderStatus.DISABLED;
  }
  return SliderStatus.ENABLED;
}

function playButtonLabel(playback: Playback): string {
  switch (playback) {
    case Playback.PLAYING:
      return "Pause";
    case Playback.PAUSED:
      return "Play";
  }
}

function muteButtonLabel(sound: Sound): string {
  switch (sound) {
    case Sound.ON:
      return "Mute";
    case Sound.MUTED:
      return "Unmute";
  }
}
