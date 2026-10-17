import { createResource, Show } from "solid-js";
import * as api from "../api/client";
import { ResultKind } from "../api/client";
import type { ArtistReleaseGroupResponse, ArtistResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import { CardLayout, DiscographyGrid } from "./Cards";
import Section from "./Section";

// The artist's other release groups, loaded from the artist's page after the page they're on. Left out while they
// load, if they fail to, and when the artist has nothing else in the library.
export default function MoreByArtist(props: { artist: ArtistResponse; releaseGroupId: string }) {
  const [result] = createResource(
    () => props.artist.artistId,
    (artistId) => sendAsCurrentUser((accessToken) => api.fetchArtist(accessToken, artistId)),
  );
  const others = (): ArtistReleaseGroupResponse[] => {
    const loaded = result();
    if (loaded === undefined || loaded.kind !== ResultKind.OK) {
      return [];
    }
    return loaded.body.releaseGroups.filter((group) => group.releaseGroupId !== props.releaseGroupId);
  };

  return (
    <Show when={others().length > 0}>
      <Section title={`More by ${props.artist.name}`}>
        <DiscographyGrid groups={others()} layout={CardLayout.ROW} />
      </Section>
    </Show>
  );
}
