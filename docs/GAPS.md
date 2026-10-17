# Known gaps

Data Sona doesn't store or serve yet, and what the API does in the meantime. Fields with no stored data are left null rather
than guessed from related rows. When you close a gap, remove it from here.

## Library

| Gap | Until it's stored |
|---|---|
| **Play history.** | Nothing records plays, so there is no recently played or not-played-yet list. |
| **Every credited artist.** Only the first artist of a recording's, release's or release group's credit is kept, so "feat." and "&" credits are lost. | Each track, release and release group has one artist. |

## Pages

Data a page will show that its endpoint doesn't serve yet.

| Gap | Until it's served |
|---|---|
| **The release page's other editions and the artist's other release groups.** `GET /api/releases/{id}` serves only the release itself. | The release page's **Other editions** and **More by** sections are mocked, filled from the frontend's mocked library (`placeholderEditions`, `placeholderGroups`). It also links to its release group (`releaseGroupId`), which lists the real editions. |
| **The release group page's artist's other release groups.** `GET /api/release-groups/{id}` serves the group, its default release's tracks and its editions only. | The release group page's **More by** section is mocked, filled from the frontend's mocked library (`placeholderGroups`). |
| **Playing music.** Sona doesn't stream yet, and playlists are still mocked in the frontend. | The Play, Shuffle and More buttons on the release and release group pages, the artist page's Shuffle button, and the play button on album cards, do nothing, and the library's tracks can't be added to playlists. |
