import { A } from "@solidjs/router";
import { createSignal, onCleanup, onMount, Show } from "solid-js";
import { formatTime } from "../format";
import { contextOf } from "../library";
import * as player from "../player";
import Cover from "./Cover";
import { NextIcon, PauseIcon, PlayIcon, PreviousIcon, RepeatIcon, ShuffleIcon, VolumeIcon } from "./Icons";
import Slider from "./Slider";

export default function PlayerBar() {
  // While the scrub thumb is held, show where it is instead of the playback position.
  const [scrub, setScrub] = createSignal<number | null>(null);
  const track = player.current;
  const context = () => {
    const playingTrack = track();
    return playingTrack === undefined ? { release: undefined, group: undefined, artist: undefined } : contextOf(playingTrack);
  };
  const duration = () => track()?.duration ?? 0;
  const shownPosition = () => scrub() ?? player.position();
  const shownVolume = () => (player.muted() ? 0 : player.volume());

  // Space toggles playback, unless focus is on something that uses Space itself.
  const onKeyDown = (event: KeyboardEvent) => {
    const target = event.target as HTMLElement;
    if (event.code !== "Space" || event.repeat || target.closest("input, textarea, select, button, a, [role=slider]")) {
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
          size="large"
          label="Seek"
          value={shownPosition()}
          max={duration()}
          step={5}
          disabled={!track()}
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
            <A href={`/releases/${release().id}`} aria-label={`Go to ${release().title}`}>
              <Cover seed={context().group?.title ?? release().title} class="player-cover" />
            </A>
          )}
        </Show>
        <div class="player-meta">
          <div class="player-title">{track()?.title ?? "Not playing"}</div>
          <Show when={context().artist} fallback={<div class="player-artist">Pick something to play</div>}>
            {(artist) => (
              <A href={`/artists/${artist().id}`} class="player-artist">
                {artist().name}
              </A>
            )}
          </Show>
        </div>
      </div>

      <div class="player-controls">
        <button
          class="icon-button toggle optional"
          classList={{ on: player.shuffle() }}
          aria-label="Shuffle"
          aria-pressed={player.shuffle()}
          onClick={() => player.toggleShuffle()}
        >
          <ShuffleIcon size={18} />
        </button>
        <button class="icon-button" aria-label="Previous" disabled={!track()} onClick={() => player.previous()}>
          <PreviousIcon />
        </button>
        <button
          class="play-button"
          aria-label={player.playing() ? "Pause" : "Play"}
          disabled={!track()}
          onClick={() => player.togglePlay()}
        >
          <Show when={player.playing()} fallback={<PlayIcon size={22} />}>
            <PauseIcon size={22} />
          </Show>
        </button>
        <button class="icon-button" aria-label="Next" disabled={!track()} onClick={() => player.next()}>
          <NextIcon />
        </button>
        <button
          class="icon-button toggle optional"
          classList={{ on: player.repeat() !== "off" }}
          aria-label={`Repeat: ${player.repeat()}`}
          aria-pressed={player.repeat() !== "off"}
          onClick={() => player.cycleRepeat()}
        >
          <RepeatIcon size={18} one={player.repeat() === "one"} />
        </button>
      </div>

      <div class="player-side">
        <span class="player-time">
          {formatTime(shownPosition())} / {formatTime(duration())}
        </span>
        <button class="icon-button" aria-label={player.muted() ? "Unmute" : "Mute"} onClick={() => player.toggleMute()}>
          <VolumeIcon size={18} muted={shownVolume() === 0} />
        </button>
        <Slider
          size="small"
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
