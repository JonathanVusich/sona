import { A } from "@solidjs/router";
import { For } from "solid-js";
import { plural } from "../format";
import { tracksOfRelease, type Release } from "../library";

// The releases of a release group, one row each. The current release, if any, is marked and not linked.
export default function EditionList(props: { releases: Release[]; currentId?: string }) {
  return (
    <ul class="edition-list">
      <For each={props.releases}>
        {(release) => {
          const details = () =>
            [release.year, release.format, release.label, release.country, plural(tracksOfRelease(release.id).length, "track")]
              .filter(Boolean)
              .join(" · ");
          return (
            <li>
              {release.id === props.currentId ? (
                <div class="edition current" aria-current="page">
                  <span class="edition-title">{release.title}</span>
                  <span class="edition-details">{details()}</span>
                </div>
              ) : (
                <A href={`/releases/${release.id}`} class="edition">
                  <span class="edition-title">{release.title}</span>
                  <span class="edition-details">{details()}</span>
                </A>
              )}
            </li>
          );
        }}
      </For>
    </ul>
  );
}
