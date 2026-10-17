import * as api from "../api/client";
import { sendAsCurrentUser } from "../auth";
import { CardLayout, ReleaseGroupGrid } from "../components/Cards";
import InfiniteScroll from "../components/InfiniteScroll";
import ListMessage from "../components/ListMessage";
import { PageBody, PageHead } from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { createPagedList } from "../paging";

// Everything in the library, singles and EPs included, by name, loading more as the user scrolls.
export default function Albums() {
  const albums = createPagedList(
    (page) => sendAsCurrentUser((accessToken) => api.fetchReleaseGroups(accessToken, page)),
    (page) => page.releaseGroups,
  );

  return (
    <>
      <PageHead>
        <PageHeader title="Albums" />
      </PageHead>
      <PageBody>
        <ReleaseGroupGrid groups={albums.items()} layout={CardLayout.GRID} />
        <ListMessage list={albums} emptyMessage="No albums in the library yet." />
        <InfiniteScroll list={albums} />
      </PageBody>
    </>
  );
}
