import { A, useParams } from "@solidjs/router";
import { createResource, Show, type JSX } from "solid-js";
import * as api from "../api/client";
import { ResultKind, type ApiResult } from "../api/client";
import { ErrorCode, type EditionResponse, type ReleaseResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import Cover, { CoverSize } from "../components/Cover";
import EditionList from "../components/EditionList";
import { MoreIcon, PlayIcon, ShuffleIcon } from "../components/Icons";
import MoreByArtist from "../components/MoreByArtist";
import { PageBody, PageHead, PageMessage } from "../components/PageLayout";
import Section from "../components/Section";
import { MediaTrackList } from "../components/TrackList";
import { formatLabels, formatMedia, formatTotal, formatYear, joinDetails, plural } from "../format";
import { AppRoutes, routeTo } from "../routes";

const NOT_IN_LIBRARY = "This release isn't in the library.";

// One concrete edition: its details and its tracks, disc by disc, then its other editions and the rest of the artist's
// discography.
export default function ReleaseDetail() {
  const params = useParams<{ id: string }>();
  const [result] = createResource(
    () => params.id,
    (releaseId) => sendAsCurrentUser((accessToken) => api.fetchRelease(accessToken, releaseId)),
  );

  return (
    <Show when={result()} keyed>
      {(loaded) => loadedPage(loaded)}
    </Show>
  );
}

function loadedPage(result: ApiResult<ReleaseResponse>): JSX.Element {
  switch (result.kind) {
    case ResultKind.OK:
      return <ReleaseView release={result.body} />;
    case ResultKind.REFUSED:
      return <PageMessage message={refusalMessage(result.code, result.reason)} />;
    case ResultKind.FAILED:
      return <PageMessage message={result.reason} />;
    default: {
      const unknown: never = result;
      throw new Error(`Unknown result: ${JSON.stringify(unknown)}`);
    }
  }
}

function ReleaseView(props: { release: ReleaseResponse }) {
  const total = () =>
    props.release.media.flatMap((medium) => medium.tracks).reduce((sum, track) => sum + track.duration, 0);
  const details = () =>
    joinDetails([
      formatYear(props.release.date),
      formatMedia(props.release.media.map((medium) => medium.format)),
      formatLabels(props.release.labels),
      props.release.country,
      plural(props.release.trackCount, "song"),
      formatTotal(total()),
    ]);

  return (
    <>
      <PageHead>
        <header class="detail-header">
          <Cover seed={props.release.name} size={CoverSize.DETAIL} />
          <div class="detail-info">
            <span class="eyebrow">Release</span>
            <h1>{props.release.name}</h1>
            <A href={routeTo(AppRoutes.ARTIST, props.release.artist.artistId)} class="detail-artist">
              {props.release.artist.name}
            </A>
            <p class="detail-meta">{details()}</p>
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
      </PageHead>
      <PageBody>
        <MediaTrackList media={props.release.media} artistId={props.release.artist.artistId} />
        <OtherEditions release={props.release} />
        <MoreByArtist artist={props.release.artist} releaseGroupId={props.release.releaseGroupId} />
      </PageBody>
    </>
  );
}

// The release group's other releases, loaded from the release group's page after this one. Left out while they load,
// if they fail to, and when the release is the group's only one.
function OtherEditions(props: { release: ReleaseResponse }) {
  const [result] = createResource(
    () => props.release.releaseGroupId,
    (releaseGroupId) => sendAsCurrentUser((accessToken) => api.fetchReleaseGroup(accessToken, releaseGroupId)),
  );
  const others = (): EditionResponse[] => {
    const loaded = result();
    if (loaded === undefined || loaded.kind !== ResultKind.OK) {
      return [];
    }
    return loaded.body.editions.filter((edition) => edition.releaseId !== props.release.releaseId);
  };

  return (
    <Show when={others().length > 0}>
      <Section title="Other editions">
        <EditionList editions={others()} />
      </Section>
    </Show>
  );
}

function refusalMessage(code: ErrorCode, reason: string): string {
  // An ID that isn't one of Sona's can't be a release in the library either.
  switch (code) {
    case ErrorCode.RELEASE_NOT_FOUND:
    case ErrorCode.INVALID_PARAMETER:
      return NOT_IN_LIBRARY;
    default:
      return reason;
  }
}
