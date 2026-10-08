import { A } from "@solidjs/router";
import { For } from "solid-js";
import { plural } from "../format";
import { artistById, canonicalRelease, groupsOf, groupYear, tracksOfRelease, type Artist, type ReleaseGroup } from "../library";
import { playTracks } from "../player";
import Cover, { ArtistAvatar } from "./Cover";
import { PlayIcon } from "./Icons";

// Release groups as cards. The subtitle names the artist, or the year and type when the artist is already known.
export function ReleaseGroupGrid(props: { groups: ReleaseGroup[]; layout: "grid" | "row"; subtitle: "artist" | "year" }) {
  return (
    <div class={props.layout === "row" ? "card-row" : "card-grid"}>
      <For each={props.groups}>{(group) => <ReleaseGroupCard group={group} subtitle={props.subtitle} />}</For>
    </div>
  );
}

function ReleaseGroupCard(props: { group: ReleaseGroup; subtitle: "artist" | "year" }) {
  const subtitle = () => {
    if (props.subtitle === "artist") {
      const artist = artistById(props.group.artistId)?.name ?? "";
      return props.group.type === "Album" ? artist : `${artist} · ${props.group.type}`;
    }
    const year = groupYear(props.group.id);
    return props.group.type === "Album" ? String(year ?? "") : [year, props.group.type].filter(Boolean).join(" · ");
  };
  const play = () => {
    const release = canonicalRelease(props.group.id);
    if (release !== undefined) {
      playTracks(tracksOfRelease(release.id));
    }
  };

  return (
    <div class="card">
      <A href={`/release-groups/${props.group.id}`} class="card-link">
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
          <A href={`/artists/${artist.id}`} class="card-link artist-card">
            <ArtistAvatar name={artist.name} />
            <div class="card-title">{artist.name}</div>
            <div class="card-subtitle">{plural(groupsOf(artist.id).length, "release")}</div>
          </A>
        )}
      </For>
    </div>
  );
}
