import { A, useParams } from "@solidjs/router";
import { Show } from "solid-js";
import { ReleaseGroupGrid } from "../components/Cards";
import Cover from "../components/Cover";
import EditionList from "../components/EditionList";
import { MoreIcon, PlayIcon, ShuffleIcon } from "../components/Icons";
import Menu from "../components/Menu";
import PageLayout from "../components/PageLayout";
import { AddToPlaylistItems } from "../components/PlaylistDialog";
import Section from "../components/Section";
import TrackList from "../components/TrackList";
import { formatTotal, plural } from "../format";
import { artistById, canonicalRelease, groupById, groupsOf, groupYear, releasesOf, tracksOfRelease } from "../library";
import { playTracks, shufflePlay } from "../player";

// The album as an idea: shows its original edition's tracks, lists every edition, and links the rest of the
// artist's discography.
export default function ReleaseGroupDetail() {
  const params = useParams<{ id: string }>();

  return (
    <Show when={groupById(params.id)} fallback={<PageLayout header={<p class="empty">This release isn't in the library.</p>} />}>
      {(group) => {
        const artist = () => artistById(group().artistId);
        const editions = () => releasesOf(group().id);
        const tracks = () => {
          const release = canonicalRelease(group().id);
          return release === undefined ? [] : tracksOfRelease(release.id);
        };
        const total = () => tracks().reduce((sum, track) => sum + track.duration, 0);
        const moreByArtist = () => groupsOf(group().artistId).filter((other) => other.id !== group().id);

        return (
          <PageLayout
            header={
              <header class="detail-header">
                <Cover seed={group().title} class="detail-cover" />
                <div class="detail-info">
                  <span class="eyebrow">{group().type}</span>
                  <h1>{group().title}</h1>
                  <Show when={artist()}>
                    {(groupArtist) => (
                      <A href={`/artists/${groupArtist().id}`} class="detail-artist">
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
                    <Menu label="More" icon={<MoreIcon size={18} />}>
                      <AddToPlaylistItems trackIds={() => tracks().map((track) => track.id)} />
                    </Menu>
                  </div>
                </div>
              </header>
            }
          >
            <TrackList tracks={tracks()} showContext={false} onPlay={(index) => playTracks(tracks(), index)} />
            <Section title={editions().length === 1 ? "Edition" : "Editions"}>
              <EditionList releases={editions()} />
            </Section>
            <Show when={moreByArtist().length > 0}>
              <Section title={`More by ${artist()?.name ?? "this artist"}`}>
                <ReleaseGroupGrid groups={moreByArtist()} layout="row" subtitle="year" />
              </Section>
            </Show>
          </PageLayout>
        );
      }}
    </Show>
  );
}
