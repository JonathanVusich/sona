import * as api from "../api/client";
import { sendAsCurrentUser } from "../auth";
import { ArtistGrid } from "../components/Cards";
import InfiniteScroll from "../components/InfiniteScroll";
import ListMessage from "../components/ListMessage";
import { PageBody, PageHead } from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { createPagedList } from "../paging";

// Every artist with a release group in the library, by name, loading more as the user scrolls.
export default function Artists() {
  const artists = createPagedList(
    (page) => sendAsCurrentUser((accessToken) => api.fetchArtists(accessToken, page)),
    (page) => page.artists,
  );

  return (
    <>
      <PageHead>
        <PageHeader title="Artists" />
      </PageHead>
      <PageBody>
        <ArtistGrid artists={artists.items()} />
        <ListMessage list={artists} emptyMessage="No artists in the library yet." />
        <InfiniteScroll list={artists} />
      </PageBody>
    </>
  );
}
