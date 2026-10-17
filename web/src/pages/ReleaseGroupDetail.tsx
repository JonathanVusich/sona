import { A, useParams } from "@solidjs/router";
import { createResource, Show, type JSX } from "solid-js";
import * as api from "../api/client";
import { ResultKind, type ApiResult } from "../api/client";
import { ErrorCode, type ReleaseGroupResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import Cover, { CoverSize } from "../components/Cover";
import EditionList from "../components/EditionList";
import { MoreIcon, PlayIcon, ShuffleIcon } from "../components/Icons";
import MoreByArtist from "../components/MoreByArtist";
import { PageBody, PageHead, PageMessage } from "../components/PageLayout";
import Section from "../components/Section";
import { MediaTrackList } from "../components/TrackList";
import { formatYear, joinDetails, plural, releaseGroupTypeLabel } from "../format";
import { AppRoutes, routeTo } from "../routes";

const NOT_IN_LIBRARY = "This release group isn't in the library.";

// The album as an idea: shows its default release's tracks, lists every edition, and links the rest of the artist's
// discography.
export default function ReleaseGroupDetail() {
  const params = useParams<{ id: string }>();
  const [result] = createResource(
    () => params.id,
    (releaseGroupId) => sendAsCurrentUser((accessToken) => api.fetchReleaseGroup(accessToken, releaseGroupId)),
  );

  return (
    <Show when={result()} keyed>
      {(loaded) => loadedPage(loaded)}
    </Show>
  );
}

function loadedPage(result: ApiResult<ReleaseGroupResponse>): JSX.Element {
  switch (result.kind) {
    case ResultKind.OK:
      return <ReleaseGroupView releaseGroup={result.body} />;
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

function ReleaseGroupView(props: { releaseGroup: ReleaseGroupResponse }) {
  const editions = () => props.releaseGroup.editions;
  const details = () =>
    joinDetails([formatYear(props.releaseGroup.firstReleaseDate), plural(editions().length, "edition")]);

  return (
    <>
      <PageHead>
        <header class="detail-header">
          <Cover seed={props.releaseGroup.name} size={CoverSize.DETAIL} />
          <div class="detail-info">
            <span class="eyebrow">{releaseGroupTypeLabel(props.releaseGroup.type)}</span>
            <h1>{props.releaseGroup.name}</h1>
            <A href={routeTo(AppRoutes.ARTIST, props.releaseGroup.artist.artistId)} class="detail-artist">
              {props.releaseGroup.artist.name}
            </A>
            <p class="detail-meta">{details()}</p>
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
        <Show when={props.releaseGroup.defaultRelease} keyed>
          {(defaultRelease) => (
            <MediaTrackList media={defaultRelease.media} artistId={props.releaseGroup.artist.artistId} />
          )}
        </Show>
        <Section title={editionsTitle(editions().length)}>
          <EditionList editions={editions()} />
        </Section>
        <MoreByArtist artist={props.releaseGroup.artist} releaseGroupId={props.releaseGroup.releaseGroupId} />
      </PageBody>
    </>
  );
}

function editionsTitle(count: number): string {
  if (count === 1) {
    return "Edition";
  }
  return "Editions";
}

function refusalMessage(code: ErrorCode, reason: string): string {
  // An ID that isn't one of Sona's can't be a release group in the library either.
  switch (code) {
    case ErrorCode.RELEASE_GROUP_NOT_FOUND:
    case ErrorCode.INVALID_PARAMETER:
      return NOT_IN_LIBRARY;
    default:
      return reason;
  }
}
