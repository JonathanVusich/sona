import { A } from "@solidjs/router";
import { For } from "solid-js";
import type { EditionResponse } from "../api/responses";
import { formatLabels, formatMedia, formatYear, joinDetails, plural } from "../format";
import { AppRoutes, routeTo } from "../routes";

// The releases of a release group, one row each, linking to each release's page.
export default function EditionList(props: { editions: EditionResponse[] }) {
  return (
    <ul class="edition-list">
      <For each={props.editions}>
        {(edition) => (
          <li>
            <A href={routeTo(AppRoutes.RELEASE, edition.releaseId)} class="edition">
              <span class="edition-title">{edition.name}</span>
              <span class="edition-details">{editionDetails(edition)}</span>
            </A>
          </li>
        )}
      </For>
    </ul>
  );
}

function editionDetails(edition: EditionResponse): string {
  return joinDetails([
    formatYear(edition.date),
    formatMedia(edition.formats),
    formatLabels(edition.labels),
    edition.country,
    plural(edition.trackCount, "song"),
  ]);
}
