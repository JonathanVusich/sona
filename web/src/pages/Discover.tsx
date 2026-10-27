import * as api from "../api/client";
import { sendAsCurrentUser } from "../auth";
import { CardLayout, ReleaseGroupGrid } from "../components/Cards";
import InfiniteScroll from "../components/InfiniteScroll";
import ListMessage from "../components/ListMessage";
import { PageBody, PageHead } from "../components/PageLayout";
import Section, { PageHeader } from "../components/Section";
import { PlayedTrackList } from "../components/TrackList";
import { recentlyAdded, summaryResponse } from "../library";
import { createPagedList } from "../paging";
import { playListedTrack } from "../player";

// Recently played comes last, as its list of tracks goes on as long as the user scrolls.
export default function Discover() {
  const plays = createPagedList(
    (page) => sendAsCurrentUser((accessToken) => api.fetchPlays(accessToken, page)),
    (page) => page.plays,
  );
  const tracks = () => plays.items().map((play) => play.track);

  return (
    <>
      <PageHead>
        <PageHeader title="Discover" />
      </PageHead>
      <PageBody>
        <Section title="Recently added">
          <ReleaseGroupGrid groups={recentlyAdded().slice(0, 12).map(summaryResponse)} layout={CardLayout.ROW} />
        </Section>
        <Section title="Recently played">
          <PlayedTrackList tracks={tracks()} onPlay={(index) => playListedTrack(tracks(), index)} />
          <ListMessage list={plays} emptyMessage="Songs you play show up here." />
          <InfiniteScroll list={plays} />
        </Section>
      </PageBody>
    </>
  );
}
