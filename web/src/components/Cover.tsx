import { For, Show } from "solid-js";

// Stands in for album art until the backend serves pictures: a muted gradient seeded from the album title.
function gradient(seed: string): string {
  let hash = 0;
  for (const char of seed) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  const hue = Math.abs(hash) % 360;
  return `linear-gradient(145deg, hsl(${hue} 30% 58%), hsl(${(hue + 35) % 360} 34% 26%))`;
}

export default function Cover(props: { seed: string; class?: string }) {
  return <div class={`cover ${props.class ?? ""}`} style={{ background: gradient(props.seed) }} />;
}

// Four covers in a grid for playlists; a single cover when there aren't four albums to show.
export function Mosaic(props: { seeds: string[]; class?: string }) {
  return (
    <Show when={props.seeds.length >= 4} fallback={<Cover seed={props.seeds[0] ?? ""} class={props.class} />}>
      <div class={`cover mosaic ${props.class ?? ""}`}>
        <For each={props.seeds.slice(0, 4)}>{(seed) => <div style={{ background: gradient(seed) }} />}</For>
      </div>
    </Show>
  );
}

// Round placeholder portrait with the artist's initials.
export function ArtistAvatar(props: { name: string; class?: string }) {
  const initials = () =>
    props.name
      .split(/\s+/)
      .filter((word) => /^\p{L}/u.test(word))
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join("");
  return (
    <div class={`cover avatar ${props.class ?? ""}`} style={{ background: gradient(props.name) }}>
      <span>{initials()}</span>
    </div>
  );
}
