import { A, useParams } from "@solidjs/router";
import { Show } from "solid-js";
import { CardLayout, DiscographyGrid } from "../components/Cards";
import Cover, { CoverSize } from "../components/Cover";
import EditionList from "../components/EditionList";
import { PlayIcon, ShuffleIcon } from "../components/Icons";
import Menu, { MenuKind } from "../components/Menu";
import { PageBody, PageHead, PageMessage } from "../components/PageLayout";
import { AddToPlaylistItems } from "../components/PlaylistDialog";
import Section from "../components/Section";
import { ReleaseTrackList } from "../components/TrackList";
import { formatTotal, plural } from "../format";
import {
  artistById,
  canonicalRelease,
  editionResponse,
  groupById,
  groupsOf,
  groupYear,
  releasesOf,
  summaryResponse,
  trackResponse,
  tracksOfRelease,
} from "../library";
import { playListedTrack, playTracks, shufflePlay } from "../player";
import { AppRoutes, routeTo } from "../routes";

// The album as an idea: shows its original edition's tracks, lists every edition, and links the rest of the
// artist's discography.
export default function ReleaseGroupDetail() {
  const params = useParams<{ id: string }>();

  return (
    <Show when={groupById(params.id)} fallback={<PageMessage message="This release isn't in the library." />}>
      {(group) => {
        const artist = () => artistById(group().artistId);
        const editions = () => releasesOf(group().id);
        const tracks = () => {
          const release = canonicalRelease(group().id);
          if (release === undefined) {
            return [];
          }
          return tracksOfRelease(release.id);
        };
        const total = () => tracks().reduce((sum, track) => sum + track.duration, 0);
        const moreByArtist = () => groupsOf(group().artistId).filter((other) => other.id !== group().id);

        return (
          <>
            <PageHead>
              <header class="detail-header">
                <Cover seed={group().title} size={CoverSize.DETAIL} />
                <div class="detail-info">
                  <span class="eyebrow">{group().type}</span>
                  <h1>{group().title}</h1>
                  <Show when={artist()}>
                    {(groupArtist) => (
                      <A href={routeTo(AppRoutes.ARTIST, groupArtist().id)} class="detail-artist">
                        {groupArtist().name}
                      </A>
                    )}
                  </Show>
                  <p class="detail-meta">
                    {[groupYear(group().id), plural(tracks().length, "song"), formatTotal(total()), plural(editions().length, "edition")]
                      .filter(Boolean)
                      .join(" · ")}
                  </p>
                  <div class="detail-actions">
                    <button class="primary" onClick={() => playTracks(tracks())}>
                      <PlayIcon size={16} /> Play
                    </button>
                    <button onClick={() => shufflePlay(tracks())}>
                      <ShuffleIcon size={16} /> Shuffle
                    </button>
                    <Menu kind={MenuKind.ACTIONS} label="More">
                      <AddToPlaylistItems trackIds={() => tracks().map((track) => track.id)} />
                    </Menu>
                  </div>
                </div>
              </header>
            </PageHead>
            <PageBody>
              <ReleaseTrackList
                tracks={tracks().map(trackResponse)}
                artistId={group().artistId}
                onPlay={(index) => playListedTrack(tracks(), index)}
              />
              <Section title={editionsTitle(editions().length)}>
                <EditionList editions={editions().map(editionResponse)} />
              </Section>
              <Show when={moreByArtist().length > 0}>
                <Section title={`More by ${artist()?.name ?? "this artist"}`}>
                  <DiscographyGrid groups={moreByArtist().map(summaryResponse)} layout={CardLayout.ROW} />
                </Section>
              </Show>
            </PageBody>
          </>
        );
      }}
    </Show>
  );
}

function editionsTitle(count: number): string {
  if (count === 1) {
    return "Edition";
  }
  return "Editions";
}
