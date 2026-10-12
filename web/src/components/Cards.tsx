import { A } from "@solidjs/router";
import { For } from "solid-js";
import { plural } from "../format";
import {
  artistById,
  canonicalRelease,
  groupsOf,
  groupYear,
  ReleaseGroupType,
  tracksOfRelease,
  type Artist,
  type ReleaseGroup,
} from "../library";
import { playTracks } from "../player";
import Cover, { ArtistAvatar } from "./Cover";
import { PlayIcon } from "./Icons";
import { AppRoutes, routeTo } from "../routes";

// How cards are laid out, as the class that lays them out.
export const CardLayout = {
  GRID: "card-grid",
  ROW: "card-row",
} as const;

export type CardLayout = (typeof CardLayout)[keyof typeof CardLayout];

// What a card's subtitle names: the artist, or the year and type when the artist is already known.
export const CardSubtitle = {
  ARTIST: "artist",
  YEAR: "year",
} as const;

export type CardSubtitle = (typeof CardSubtitle)[keyof typeof CardSubtitle];

// Release groups as cards.
export function ReleaseGroupGrid(props: { groups: ReleaseGroup[]; layout: CardLayout; subtitle: CardSubtitle }) {
  return (
    <div class={props.layout}>
      <For each={props.groups}>{(group) => <ReleaseGroupCard group={group} subtitle={props.subtitle} />}</For>
    </div>
  );
}

function ReleaseGroupCard(props: { group: ReleaseGroup; subtitle: CardSubtitle }) {
  const subtitle = () => {
    if (props.subtitle === CardSubtitle.ARTIST) {
      const artist = artistById(props.group.artistId)?.name ?? "";
      return props.group.type === ReleaseGroupType.ALBUM ? artist : `${artist} · ${props.group.type}`;
    }
    const year = groupYear(props.group.id);
    return props.group.type === ReleaseGroupType.ALBUM ? String(year ?? "") : [year, props.group.type].filter(Boolean).join(" · ");
  };
  const play = () => {
    const release = canonicalRelease(props.group.id);
    if (release !== undefined) {
      playTracks(tracksOfRelease(release.id));
    }
  };

  return (
    <div class="card">
      <A href={routeTo(AppRoutes.RELEASE_GROUP, props.group.id)} class="card-link">
        <Cover seed={props.group.title} />
        <div class="card-title">{props.group.title}</div>
        <div class="card-subtitle">{subtitle()}</div>
      </A>
      <button class="card-play" aria-label={`Play ${props.group.title}`} onClick={play}>
        <PlayIcon size={18} />
      </button>
    </div>
  );
}

export function ArtistGrid(props: { artists: Artist[] }) {
  return (
    <div class="card-grid">
      <For each={props.artists}>
        {(artist) => (
          <A href={routeTo(AppRoutes.ARTIST, artist.id)} class="card-link artist-card">
            <ArtistAvatar name={artist.name} />
            <div class="card-title">{artist.name}</div>
            <div class="card-subtitle">{plural(groupsOf(artist.id).length, "release")}</div>
          </A>
        )}
      </For>
    </div>
  );
}
