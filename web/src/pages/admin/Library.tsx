import { A, Navigate } from "@solidjs/router";
import { createSignal, For, Show } from "solid-js";
import Cover from "../../components/Cover";
import Dialog, { ConfirmDialog } from "../../components/Dialog";
import PageLayout from "../../components/PageLayout";
import { PageHeader } from "../../components/Section";
import { can } from "../../auth";
import { plural } from "../../format";
import {
  artistById,
  artists,
  FORMATS,
  groupById,
  groups,
  releases,
  removeRelease,
  trackCount,
  tracksOfRelease,
  updateRelease,
  type Release,
} from "../../library";

const collator = new Intl.Collator(undefined, { sensitivity: "base", numeric: true });

function rowFor(release: Release) {
  const group = groupById(release.groupId);
  const artist = group === undefined ? undefined : artistById(group.artistId);
  return { release, group, artist };
}

export default function Library() {
  const [query, setQuery] = createSignal("");
  const [editing, setEditing] = createSignal<Release | null>(null);
  const [removing, setRemoving] = createSignal<Release | null>(null);

  const rows = () => {
    const needle = query().trim().toLowerCase();
    return releases()
      .map(rowFor)
      .filter(({ release, group, artist }) =>
        [release.title, group?.title, artist?.name, release.label].some((text) => text?.toLowerCase().includes(needle)),
      )
      .sort(
        (a, b) =>
          collator.compare(a.artist?.name ?? "", b.artist?.name ?? "") ||
          collator.compare(a.group?.title ?? "", b.group?.title ?? "") ||
          (a.release.year ?? 0) - (b.release.year ?? 0),
      );
  };

  return (
    <Show when={can("write")} fallback={<Navigate href="/" />}>
      <PageLayout
        header={
          <PageHeader
            title="Library"
            meta={[
              plural(artists().length, "artist"),
              plural(groups().length, "release group"),
              plural(releases().length, "release"),
              plural(trackCount(), "track"),
            ].join(" · ")}
            action={
              <Show when={can("upload")}>
                <A href="/admin/upload" class="button primary">
                  Upload
                </A>
              </Show>
            }
          />
        }
      >
        <input
          class="admin-search"
          type="search"
          placeholder="Search releases, artists, labels"
          aria-label="Search the library"
          value={query()}
          onInput={(e) => setQuery(e.currentTarget.value)}
        />
        <div class="table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>Release</th>
                <th>Artist</th>
                <th>Release group</th>
                <th>Year</th>
                <th>Format</th>
                <th>Label</th>
                <th class="numeric">Tracks</th>
                <th>
                  <span class="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <For each={rows()} fallback={<tr><td colspan="8" class="empty">No releases match.</td></tr>}>
                {({ release, group, artist }) => (
                  <tr>
                    <td>
                      <div class="admin-release">
                        <Cover seed={group?.title ?? release.title} class="admin-thumb" />
                        <span>{release.title}</span>
                      </div>
                    </td>
                    <td>{artist?.name}</td>
                    <td>
                      {group?.title} <span class="muted">{group?.type}</span>
                    </td>
                    <td>{release.year ?? <span class="muted">—</span>}</td>
                    <td>{release.format}</td>
                    <td>{release.label ?? <span class="muted">—</span>}</td>
                    <td class="numeric">{tracksOfRelease(release.id).length}</td>
                    <td class="admin-actions">
                      <button class="quiet" onClick={() => setEditing(release)}>
                        Edit
                      </button>
                      <button class="quiet danger" onClick={() => setRemoving(release)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                )}
              </For>
            </tbody>
          </table>
        </div>

        <Dialog open={editing() !== null} title="Edit release" onClose={() => setEditing(null)}>
          <Show when={editing()}>{(release) => <EditReleaseForm release={release()} onDone={() => setEditing(null)} />}</Show>
        </Dialog>
        <ConfirmDialog
          open={removing() !== null}
          title="Remove release?"
          message={`"${removing()?.title}" and its tracks will be removed from the library for everyone.`}
          confirmLabel="Remove"
          onConfirm={() => removeRelease(removing()!.id)}
          onClose={() => setRemoving(null)}
        />
      </PageLayout>
    </Show>
  );
}

function EditReleaseForm(props: { release: Release; onDone: () => void }) {
  const [title, setTitle] = createSignal(props.release.title);
  const [year, setYear] = createSignal(props.release.year === null ? "" : String(props.release.year));
  const [format, setFormat] = createSignal(props.release.format);
  const [label, setLabel] = createSignal(props.release.label ?? "");

  const submit = (event: SubmitEvent) => {
    event.preventDefault();
    updateRelease(props.release.id, {
      title: title().trim(),
      year: year() === "" ? null : Number(year()),
      format: format(),
      label: label().trim() === "" ? null : label().trim(),
    });
    props.onDone();
  };

  return (
    <form class="dialog-form" onSubmit={submit}>
      <label>
        Title
        <input value={title()} onInput={(e) => setTitle(e.currentTarget.value)} required autofocus />
      </label>
      <div class="dialog-row">
        <label>
          Year
          <input type="number" min="1900" max="2100" value={year()} onInput={(e) => setYear(e.currentTarget.value)} />
        </label>
        <label>
          Format
          <select value={format()} onChange={(e) => setFormat(e.currentTarget.value)}>
            <For each={FORMATS}>{(option) => <option value={option}>{option}</option>}</For>
          </select>
        </label>
      </div>
      <label>
        Label
        <input value={label()} onInput={(e) => setLabel(e.currentTarget.value)} placeholder="Optional" />
      </label>
      <div class="dialog-actions">
        <button type="button" onClick={() => props.onDone()}>
          Cancel
        </button>
        <button type="submit" class="primary" disabled={title().trim() === ""}>
          Save
        </button>
      </div>
    </form>
  );
}
