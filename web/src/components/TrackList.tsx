import { A } from "@solidjs/router";
import { createSignal, For, Show, type JSX } from "solid-js";
import type { MediumResponse, PlayedTrackResponse, PlaylistTrackResponse, TrackResponse } from "../api/responses";
import { formatTime, joinDetails } from "../format";
import { current, Playback, playback } from "../player";
import { AppRoutes, routeTo } from "../routes";
import { PlayIcon } from "./Icons";
import Menu, { MenuKind } from "./Menu";
import { AddToPlaylistItems } from "./PlaylistDialog";
import Section from "./Section";

// A release's tracks, disc by disc, each disc under its own heading when there is more than one. onPlay gets the
// track's index among all the release's tracks, in disc order.
export function MediaTrackList(props: { media: MediumResponse[]; artistId: string; onPlay: (index: number) => void }) {
  const playOnMedium = (mediumIndex: number, index: number) =>
    props.onPlay(tracksBefore(props.media, mediumIndex) + index);
  return (
    <Show
      when={props.media.length > 1}
      fallback={
        <For each={props.media}>
          {(medium, mediumIndex) => (
            <ReleaseTrackList
              tracks={medium.tracks}
              artistId={props.artistId}
              onPlay={(index) => playOnMedium(mediumIndex(), index)}
            />
          )}
        </For>
      }
    >
      <For each={props.media}>
        {(medium, mediumIndex) => (
          <Section title={joinDetails([`Disc ${medium.position}`, medium.format])}>
            <ReleaseTrackList
              tracks={medium.tracks}
              artistId={props.artistId}
              onPlay={(index) => playOnMedium(mediumIndex(), index)}
            />
          </Section>
        )}
      </For>
    </Show>
  );
}

function tracksBefore(media: MediumResponse[], mediumIndex: number): number {
  return media.slice(0, mediumIndex).reduce((count, medium) => count + medium.tracks.length, 0);
}

// One release's tracks, numbered by their position on their medium. A row names its artist only when it isn't the
// release's own (artistId), as on a compilation.
export function ReleaseTrackList(props: {
  tracks: TrackResponse[];
  artistId: string;
  onPlay: (index: number) => void;
}) {
  return (
    <ol class="tracklist">
      <For each={props.tracks}>
        {(track, index) => (
          <ReleaseTrackRow track={track} artistId={props.artistId} onPlay={() => props.onPlay(index())} />
        )}
      </For>
    </ol>
  );
}

// A playlist's tracks, numbered in the playlist's order, each naming and linking its artist and release, and offering
// to remove it from the playlist. Dragging a track onto another moves it to that track's place: onMove gets the track
// and its new position on the playlist, from 1.
export function PlaylistTrackList(props: {
  tracks: PlaylistTrackResponse[];
  onPlay: (index: number) => void;
  onRemove: (track: PlaylistTrackResponse) => void;
  onMove: (track: PlaylistTrackResponse, position: number) => void;
}) {
  // The track being dragged, and the one it's over, while a drag is under way: null otherwise.
  const [dragged, setDragged] = createSignal<PlaylistTrackResponse | null>(null);
  const [dropTarget, setDropTarget] = createSignal<PlaylistTrackResponse | null>(null);
  const endDrag = () => {
    setDragged(null);
    setDropTarget(null);
  };
  const drop = (position: number) => {
    const moved = dragged();
    endDrag();
    if (moved !== null) {
      props.onMove(moved, position);
    }
  };

  return (
    <ol class="tracklist with-context">
      <For each={props.tracks}>
        {(track, index) => (
          <PlaylistTrackRow
            track={track}
            number={index() + 1}
            drop={rowDrop(track, dragged(), dropTarget())}
            onPlay={() => props.onPlay(index())}
            onRemove={() => props.onRemove(track)}
            onDragStart={() => setDragged(track)}
            onDragOver={() => setDropTarget(track)}
            onDrop={() => drop(index() + 1)}
            onDragEnd={endDrag}
          />
        )}
      </For>
    </ol>
  );
}

// The tracks the user played, latest first, each naming and linking its artist and release. A track played again is
// listed again, so rows are told apart by their place in the list.
export function PlayedTrackList(props: { tracks: PlayedTrackResponse[]; onPlay: (index: number) => void }) {
  return (
    <ol class="tracklist with-context">
      <For each={props.tracks}>
        {(track, index) => <PlayedTrackRow track={track} number={index() + 1} onPlay={() => props.onPlay(index())} />}
      </For>
    </ol>
  );
}

function PlayedTrackRow(props: { track: PlayedTrackResponse; number: number; onPlay: () => void }) {
  const releasePath = () => routeTo(AppRoutes.RELEASE, props.track.release.releaseId);
  return (
    <TrackRow track={props.track} onPlay={props.onPlay}>
      <TrackPlayButton track={props.track} number={props.number} onPlay={props.onPlay} />
      <span class="track-main">
        <span class="track-title">{props.track.name}</span>
        <ArtistLink track={props.track} />
      </span>
      <span class="track-release">
        <A href={releasePath()}>{props.track.release.name}</A>
      </span>
      <TrackDuration track={props.track} />
      <Menu kind={MenuKind.TRACK} label={`More for ${props.track.name}`}>
        <AddToPlaylistItems trackIds={[props.track.trackId]} />
        <hr class="menu-divider" />
        <ArtistMenuItem track={props.track} />
        <A href={releasePath()} class="menu-item" role="menuitem">
          Go to release
        </A>
      </Menu>
    </TrackRow>
  );
}

function ReleaseTrackRow(props: { track: TrackResponse; artistId: string; onPlay: () => void }) {
  return (
    <TrackRow track={props.track} onPlay={props.onPlay}>
      <TrackPlayButton track={props.track} number={props.track.position} onPlay={props.onPlay} />
      <span class="track-main">
        <span class="track-title">{props.track.name}</span>
        <Show when={props.track.artist.artistId !== props.artistId}>
          <ArtistLink track={props.track} />
        </Show>
      </span>
      <TrackDuration track={props.track} />
      <Menu kind={MenuKind.TRACK} label={`More for ${props.track.name}`}>
        <AddToPlaylistItems trackIds={[props.track.trackId]} />
        <hr class="menu-divider" />
        <ArtistMenuItem track={props.track} />
      </Menu>
    </TrackRow>
  );
}

// Whether a playlist's row is where a dragged track would land.
const RowDrop = {
  NONE: "none",
  TARGET: "target",
} as const;

type RowDrop = (typeof RowDrop)[keyof typeof RowDrop];

function rowDrop(
  track: PlaylistTrackResponse,
  dragged: PlaylistTrackResponse | null,
  dropTarget: PlaylistTrackResponse | null,
): RowDrop {
  if (dragged === null || dragged === track || dropTarget !== track) {
    return RowDrop.NONE;
  }
  return RowDrop.TARGET;
}

function PlaylistTrackRow(props: {
  track: PlaylistTrackResponse;
  number: number;
  drop: RowDrop;
  onPlay: () => void;
  onRemove: () => void;
  onDragStart: () => void;
  onDragOver: () => void;
  onDrop: () => void;
  onDragEnd: () => void;
}) {
  const releasePath = () => routeTo(AppRoutes.RELEASE, props.track.release.releaseId);
  const dragStart = (event: DragEvent) => {
    // Firefox only starts a drag that carries data.
    event.dataTransfer?.setData("text/plain", props.track.trackId);
    props.onDragStart();
  };
  // A row only takes a drop once its dragover is cancelled.
  const dragOver = (event: DragEvent) => {
    event.preventDefault();
    props.onDragOver();
  };
  const drop = (event: DragEvent) => {
    event.preventDefault();
    props.onDrop();
  };
  return (
    <li
      class="track-row"
      classList={{ current: current()?.trackId === props.track.trackId, "drop-target": props.drop === RowDrop.TARGET }}
      draggable="true"
      onClick={(event) => playOnRowClick(event, props.onPlay)}
      onDragStart={dragStart}
      onDragOver={dragOver}
      onDrop={drop}
      onDragEnd={() => props.onDragEnd()}
    >
      <TrackPlayButton track={props.track} number={props.number} onPlay={props.onPlay} />
      <span class="track-main">
        <span class="track-title">{props.track.name}</span>
        <ArtistLink track={props.track} />
      </span>
      <span class="track-release">
        <A href={releasePath()}>{props.track.release.name}</A>
      </span>
      <TrackDuration track={props.track} />
      <Menu kind={MenuKind.TRACK} label={`More for ${props.track.name}`}>
        <ArtistMenuItem track={props.track} />
        <A href={releasePath()} class="menu-item" role="menuitem">
          Go to release
        </A>
        <hr class="menu-divider" />
        <button class="menu-item danger" role="menuitem" onClick={() => props.onRemove()}>
          Remove from this playlist
        </button>
      </Menu>
    </li>
  );
}

// A track's row, which plays it on click and is marked while it's the player's.
function TrackRow(props: { track: TrackResponse; onPlay: () => void; children: JSX.Element }) {
  return (
    <li
      class="track-row"
      classList={{ current: current()?.trackId === props.track.trackId }}
      onClick={(event) => playOnRowClick(event, props.onPlay)}
    >
      {props.children}
    </li>
  );
}

// Links and buttons inside a row do their own thing; a click anywhere else in it plays the track.
function playOnRowClick(event: MouseEvent, onPlay: () => void) {
  if (!(event.target instanceof Element && event.target.closest("a, button, .menu"))) {
    onPlay();
  }
}

// The track's number, or bars while it plays, which turns into a play button on hover.
function TrackPlayButton(props: { track: TrackResponse; number: number; onPlay: () => void }) {
  const playing = () => current()?.trackId === props.track.trackId && playback() === Playback.PLAYING;
  return (
    <button class="track-play" aria-label={`Play ${props.track.name}`} onClick={() => props.onPlay()}>
      <Show when={playing()} fallback={<span class="track-number">{props.number}</span>}>
        <span class="bars" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
      </Show>
      <span class="track-play-icon">
        <PlayIcon size={14} />
      </span>
    </button>
  );
}

function TrackDuration(props: { track: TrackResponse }) {
  return <span class="track-duration">{formatTime(props.track.duration)}</span>;
}

function ArtistLink(props: { track: TrackResponse }) {
  return (
    <A href={routeTo(AppRoutes.ARTIST, props.track.artist.artistId)} class="track-sub">
      {props.track.artist.name}
    </A>
  );
}

function ArtistMenuItem(props: { track: TrackResponse }) {
  return (
    <A href={routeTo(AppRoutes.ARTIST, props.track.artist.artistId)} class="menu-item" role="menuitem">
      Go to artist
    </A>
  );
}
