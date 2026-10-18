import { A } from "@solidjs/router";
import { For, Show, type JSX } from "solid-js";
import type { MediumResponse, TrackResponse } from "../api/responses";
import { formatTime, joinDetails } from "../format";
import { current, Playback, playback } from "../player";
import { removeFromPlaylist, type PlaylistTrack } from "../playlists";
import { AppRoutes, routeTo } from "../routes";
import { PlayIcon } from "./Icons";
import Menu, { MenuKind } from "./Menu";
import { AddToPlaylistItems } from "./PlaylistDialog";
import Section from "./Section";

// A release's tracks, disc by disc, each disc under its own heading when the release has more than one. Discs none of
// whose tracks are in the library are left out. Nothing plays until Sona streams (docs/GAPS.md).
export function MediaTrackList(props: { media: MediumResponse[]; artistId: string }) {
  const withTracks = () => props.media.filter((medium) => medium.tracks.length > 0);
  return (
    <Show
      when={props.media.length > 1}
      fallback={
        <For each={withTracks()}>
          {(medium) => <ReleaseTrackList tracks={medium.tracks} artistId={props.artistId} onPlay={() => {}} />}
        </For>
      }
    >
      <For each={withTracks()}>
        {(medium) => (
          <Section title={joinDetails([`Disc ${medium.position}`, medium.format])}>
            <ReleaseTrackList tracks={medium.tracks} artistId={props.artistId} onPlay={() => {}} />
          </Section>
        )}
      </For>
    </Show>
  );
}

// One release's tracks, numbered by their position on their medium. A row names its artist only when it isn't the
// release's own (artistId), as on a compilation. Playlists are still mocked, so these tracks can't go on them.
export function ReleaseTrackList(props: { tracks: TrackResponse[]; artistId: string; onPlay: (index: number) => void }) {
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
// to remove it from the playlist.
export function PlaylistTrackList(props: {
  tracks: PlaylistTrack[];
  playlistId: string;
  onPlay: (index: number) => void;
}) {
  return (
    <ol class="tracklist with-context">
      <For each={props.tracks}>
        {(track, index) => (
          <PlaylistTrackRow
            track={track}
            number={index() + 1}
            playlistId={props.playlistId}
            onPlay={() => props.onPlay(index())}
          />
        )}
      </For>
    </ol>
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
        <ArtistMenuItem track={props.track} />
      </Menu>
    </TrackRow>
  );
}

function PlaylistTrackRow(props: { track: PlaylistTrack; number: number; playlistId: string; onPlay: () => void }) {
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
        <AddToPlaylistItems trackIds={() => [props.track.trackId]} />
        <hr class="menu-divider" />
        <ArtistMenuItem track={props.track} />
        <A href={releasePath()} class="menu-item" role="menuitem">
          Go to release
        </A>
        <hr class="menu-divider" />
        <button
          class="menu-item danger"
          role="menuitem"
          onClick={() => removeFromPlaylist(props.playlistId, props.track.trackId)}
        >
          Remove from this playlist
        </button>
      </Menu>
    </TrackRow>
  );
}

// A track's row, which plays it on click and is marked while it's the player's.
function TrackRow(props: { track: TrackResponse; onPlay: () => void; children: JSX.Element }) {
  // Links and buttons inside the row do their own thing.
  const onRowClick = (event: MouseEvent) => {
    if (!(event.target instanceof Element && event.target.closest("a, button, .menu"))) {
      props.onPlay();
    }
  };
  return (
    <li class="track-row" classList={{ current: current()?.id === props.track.trackId }} onClick={onRowClick}>
      {props.children}
    </li>
  );
}

// The track's number, or bars while it plays, which turns into a play button on hover.
function TrackPlayButton(props: { track: TrackResponse; number: number; onPlay: () => void }) {
  const playing = () => current()?.id === props.track.trackId && playback() === Playback.PLAYING;
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
