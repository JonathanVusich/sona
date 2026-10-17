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

// Release groups as cards, each naming its artist.
export function ReleaseGroupGrid(props: { groups: ReleaseGroup[]; layout: CardLayout }) {
  return (
    <div class={props.layout}>
      <For each={props.groups}>{(group) => <ReleaseGroupCard group={group} subtitle={artistSubtitle(group)} />}</For>
    </div>
  );
}

// One artist's release groups as cards, each naming its year instead, since the artist is already known.
export function DiscographyGrid(props: { groups: ReleaseGroup[]; layout: CardLayout }) {
  return (
    <div class={props.layout}>
      <For each={props.groups}>{(group) => <ReleaseGroupCard group={group} subtitle={yearSubtitle(group)} />}</For>
    </div>
  );
}

function artistSubtitle(group: ReleaseGroup): string {
  const artist = artistById(group.artistId)?.name ?? "";
  return group.type === ReleaseGroupType.ALBUM ? artist : `${artist} · ${group.type}`;
}

function yearSubtitle(group: ReleaseGroup): string {
  const year = groupYear(group.id);
  return group.type === ReleaseGroupType.ALBUM ? String(year ?? "") : [year, group.type].filter(Boolean).join(" · ");
}

function ReleaseGroupCard(props: { group: ReleaseGroup; subtitle: string }) {
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
        <div class="card-subtitle">{props.subtitle}</div>
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
