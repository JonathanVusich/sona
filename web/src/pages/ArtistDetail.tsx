import { useParams } from "@solidjs/router";
import { createResource, For, Show, type JSX } from "solid-js";
import * as api from "../api/client";
import { ResultKind, type ApiResult } from "../api/client";
import {
  ErrorCode,
  ReleaseGroupType,
  type ArtistDetailsResponse,
  type ArtistReleaseGroupResponse,
} from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import { CardLayout, DiscographyGrid } from "../components/Cards";
import { ArtistAvatar, CoverSize } from "../components/Cover";
import { ShuffleIcon } from "../components/Icons";
import { PageBody, PageHead, PageMessage } from "../components/PageLayout";
import Section from "../components/Section";
import { irregularPlural, joinDetails, plural } from "../format";

const NOT_IN_LIBRARY = "This artist isn't in the library.";

// Artist pages list albums apart from everything else: singles, EPs, and groups of other or unknown types.
const SECTIONS: { title: string; include: (group: ArtistReleaseGroupResponse) => boolean }[] = [
  { title: "Albums", include: (group) => group.type === ReleaseGroupType.ALBUM },
  { title: "Singles & EPs", include: (group) => group.type !== ReleaseGroupType.ALBUM },
];

export default function ArtistDetail() {
  const params = useParams<{ id: string }>();
  const [result] = createResource(
    () => params.id,
    (artistId) => sendAsCurrentUser((accessToken) => api.fetchArtist(accessToken, artistId)),
  );

  return (
    <Show when={result()} keyed>
      {(loaded) => loadedPage(loaded)}
    </Show>
  );
}

function loadedPage(result: ApiResult<ArtistDetailsResponse>): JSX.Element {
  switch (result.kind) {
    case ResultKind.OK:
      return <ArtistView artist={result.body} />;
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

function ArtistView(props: { artist: ArtistDetailsResponse }) {
  return (
    <>
      <PageHead>
        <header class="detail-header">
          <ArtistAvatar name={props.artist.name} size={CoverSize.DETAIL} />
          <div class="detail-info">
            <span class="eyebrow">Artist</span>
            <h1>{props.artist.name}</h1>
            <p class="detail-meta">{artistDetails(props.artist)}</p>
            {/* Nothing plays until Sona streams (docs/GAPS.md). */}
            <div class="detail-actions">
              <button class="primary">
                <ShuffleIcon size={16} /> Shuffle
              </button>
            </div>
          </div>
        </header>
      </PageHead>
      <PageBody>
        <For each={SECTIONS}>
          {({ title, include }) => {
            const ofType = () => props.artist.releaseGroups.filter(include);
            return (
              <Show when={ofType().length > 0}>
                <Section title={title}>
                  <DiscographyGrid groups={ofType()} layout={CardLayout.GRID} />
                </Section>
              </Show>
            );
          }}
        </For>
      </PageBody>
    </>
  );
}

/**
 * @returns the artist's country and how many albums and other release groups they have, leaving out any count of none
 */
function artistDetails(artist: ArtistDetailsResponse): string {
  const groups = artist.releaseGroups;
  const albumCount = groups.filter((group) => group.type === ReleaseGroupType.ALBUM).length;
  const otherCount = groups.length - albumCount;
  return joinDetails([
    artist.country,
    countDetail(albumCount, plural(albumCount, "album")),
    countDetail(otherCount, irregularPlural(otherCount, "single or EP", "singles & EPs")),
  ]);
}

function countDetail(count: number, detail: string): string | null {
  if (count === 0) {
    return null;
  }
  return detail;
}

function refusalMessage(code: ErrorCode, reason: string): string {
  // An ID that isn't one of Sona's can't be an artist in the library either.
  switch (code) {
    case ErrorCode.ARTIST_NOT_FOUND:
    case ErrorCode.INVALID_PARAMETER:
      return NOT_IN_LIBRARY;
    default:
      return reason;
  }
}
