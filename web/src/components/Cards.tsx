import { A } from "@solidjs/router";
import { For } from "solid-js";
import {
  ReleaseGroupType,
  type ArtistReleaseGroupResponse,
  type ArtistSummaryResponse,
  type ReleaseGroupSummaryResponse,
} from "../api/responses";
import { formatYear, joinDetails, plural, releaseGroupTypeLabel } from "../format";
import { canonicalRelease, tracksOfRelease } from "../library";
import { playTracks } from "../player";
import Cover, { ArtistAvatar, CoverSize } from "./Cover";
import { PlayIcon } from "./Icons";
import { AppRoutes, routeTo } from "../routes";

// How cards are laid out, as the class that lays them out.
export const CardLayout = {
  GRID: "card-grid",
  ROW: "card-row",
} as const;

export type CardLayout = (typeof CardLayout)[keyof typeof CardLayout];

// Release groups as cards, each naming its artist.
export function ReleaseGroupGrid(props: { groups: ReleaseGroupSummaryResponse[]; layout: CardLayout }) {
  return (
    <div class={props.layout}>
      <For each={props.groups}>{(group) => <ReleaseGroupCard group={group} subtitle={artistSubtitle(group)} />}</For>
    </div>
  );
}

// One artist's release groups as cards, each naming its year instead, since the artist is already known.
export function DiscographyGrid(props: { groups: ArtistReleaseGroupResponse[]; layout: CardLayout }) {
  return (
    <div class={props.layout}>
      <For each={props.groups}>{(group) => <ReleaseGroupCard group={group} subtitle={yearSubtitle(group)} />}</For>
    </div>
  );
}

function ReleaseGroupCard(props: { group: ArtistReleaseGroupResponse; subtitle: string }) {
  // Only the mocked library plays; nothing plays from Sona until it streams (docs/GAPS.md).
  const play = () => {
    const release = canonicalRelease(props.group.releaseGroupId);
    if (release !== undefined) {
      playTracks(tracksOfRelease(release.id));
    }
  };

  return (
    <div class="card">
      <A href={routeTo(AppRoutes.RELEASE_GROUP, props.group.releaseGroupId)} class="card-link">
        <Cover seed={props.group.name} size={CoverSize.CARD} />
        <div class="card-title">{props.group.name}</div>
        <div class="card-subtitle">{props.subtitle}</div>
      </A>
      <button class="card-play" aria-label={`Play ${props.group.name}`} onClick={play}>
        <PlayIcon size={18} />
      </button>
    </div>
  );
}

function artistSubtitle(group: ReleaseGroupSummaryResponse): string {
  return joinDetails([group.artist.name, cardTypeLabel(group.type)]);
}

function yearSubtitle(group: ArtistReleaseGroupResponse): string {
  return joinDetails([formatYear(group.firstReleaseDate), cardTypeLabel(group.type)]);
}

/**
 * @returns the type the card names, or null for an album, which most of the library is, or a group of unknown type
 */
function cardTypeLabel(type: ReleaseGroupType | null): string | null {
  if (type === ReleaseGroupType.ALBUM || type === null) {
    return null;
  }
  return releaseGroupTypeLabel(type);
}

export function ArtistGrid(props: { artists: ArtistSummaryResponse[] }) {
  return (
    <div class="card-grid">
      <For each={props.artists}>
        {(artist) => (
          <A href={routeTo(AppRoutes.ARTIST, artist.artistId)} class="card-link artist-card">
            <ArtistAvatar name={artist.name} size={CoverSize.CARD} />
            <div class="card-title">{artist.name}</div>
            <div class="card-subtitle">{plural(artist.releaseGroupCount, "release")}</div>
          </A>
        )}
      </For>
    </div>
  );
}
