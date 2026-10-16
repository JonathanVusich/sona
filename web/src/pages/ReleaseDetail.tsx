import { A, useParams } from "@solidjs/router";
import { createResource, For, Show } from "solid-js";
import * as api from "../api/client";
import type { ApiFailure } from "../api/client";
import { ErrorCode, type MediumResponse, type ReleaseResponse, type TrackResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import Cover from "../components/Cover";
import { MoreIcon, PlayIcon, ShuffleIcon } from "../components/Icons";
import PageLayout from "../components/PageLayout";
import Section from "../components/Section";
import TrackList, { type TrackRow } from "../components/TrackList";
import { formatTotal, plural } from "../format";
import { AppRoutes, routeTo } from "../routes";

const NOT_IN_LIBRARY = "This release isn't in the library.";

// One concrete edition: its details and its tracks, disc by disc. Its other editions are on its release group's page.
export default function ReleaseDetail() {
  const params = useParams<{ id: string }>();
  const [result] = createResource(
    () => params.id,
    (releaseId) => sendAsCurrentUser((accessToken) => api.fetchRelease(accessToken, releaseId)),
  );

  return (
    <Show when={result()} keyed>
      {(loaded) =>
        loaded.ok ? (
          <ReleaseView release={loaded.body} />
        ) : (
          <PageLayout header={<p class="empty">{messageFor(loaded.failure)}</p>} />
        )
      }
    </Show>
  );
}

function ReleaseView(props: { release: ReleaseResponse }) {
  const year = () => props.release.date?.slice(0, 4);
  const formats = () => [...new Set(props.release.media.flatMap((medium) => medium.format ?? []))].join(", ");
  const labels = () => props.release.labels.map((label) => label.name).join(", ");
  const total = () =>
    props.release.media.flatMap((medium) => medium.tracks).reduce((sum, track) => sum + track.duration, 0);

  return (
    <PageLayout
      header={
        <header class="detail-header">
          <Cover seed={props.release.name} class="detail-cover" />
          <div class="detail-info">
            <span class="eyebrow">Release</span>
            <h1>{props.release.name}</h1>
            <A href={routeTo(AppRoutes.ARTIST, props.release.artist.artistId)} class="detail-artist">
              {props.release.artist.name}
            </A>
            <p class="detail-meta">
              {[year(), formats(), labels(), props.release.country, plural(props.release.trackCount, "song"), formatTotal(total())]
                .filter(Boolean)
                .join(" · ")}
            </p>
            <p class="detail-meta">
              <A href={routeTo(AppRoutes.RELEASE_GROUP, props.release.releaseGroupId)}>All editions</A>
            </p>
            {/* Nothing plays until Sona streams (docs/GAPS.md). */}
            <div class="detail-actions">
              <button class="primary">
                <PlayIcon size={16} /> Play
              </button>
              <button>
                <ShuffleIcon size={16} /> Shuffle
              </button>
              <button class="icon-button" aria-label="More">
                <MoreIcon size={18} />
              </button>
            </div>
          </div>
        </header>
      }
    >
      <Show
        when={props.release.media.length > 1}
        fallback={
          <For each={props.release.media}>{(medium) => <MediumTracks medium={medium} release={props.release} />}</For>
        }
      >
        <For each={props.release.media}>
          {(medium) => (
            <Section title={[`Disc ${medium.position}`, medium.format].filter(Boolean).join(" · ")}>
              <MediumTracks medium={medium} release={props.release} />
            </Section>
          )}
        </For>
      </Show>
    </PageLayout>
  );
}

function MediumTracks(props: { medium: MediumResponse; release: ReleaseResponse }) {
  return (
    <TrackList
      tracks={props.medium.tracks.map(trackRowOf)}
      showContext={false}
      artistId={props.release.artist.artistId}
      canAddToPlaylist={false}
      onPlay={() => {}}
    />
  );
}

function trackRowOf(track: TrackResponse): TrackRow {
  return {
    id: track.trackId,
    number: track.position,
    title: track.name,
    duration: track.duration,
    artist: { id: track.artist.artistId, name: track.artist.name },
    release: undefined,
  };
}

function messageFor(failure: ApiFailure): string {
  // An ID that isn't one of Sona's can't be a release in the library either.
  return failure.code === ErrorCode.RELEASE_NOT_FOUND || failure.code === ErrorCode.INVALID_PARAMETER
    ? NOT_IN_LIBRARY
    : failure.reason;
}
