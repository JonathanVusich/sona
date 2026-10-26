# Known gaps

Data Sona doesn't store or serve yet, and what the API does in the meantime. Fields with no stored data are left null rather
than guessed from related rows. When you close a gap, remove it from here.

## Library

| Gap | Until it's stored |
|---|---|
| **Not-played-yet list.** Sona records each user's plays, but no endpoint serves what they haven't played. | The Discover page's not-played-yet section stays filled from the mocked library. |
| **Every credited artist.** Only the first artist of a recording's, release's or release group's credit is kept, so "feat." and "&" credits are lost. | Each track, release and release group has one artist. |

## Pages

Data a page will show that its endpoint doesn't serve yet.

| Gap | Until it's served |
|---|---|
| **An artist's tracks.** No endpoint serves every track of an artist. | The artist page's Shuffle button does nothing. |
| **Release actions.** | The More buttons on the release and release group pages do nothing. |
