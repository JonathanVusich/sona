import { A, Navigate } from "@solidjs/router";
import { createSignal, For, Show } from "solid-js";
import Cover, { CoverSize } from "../../components/Cover";
import Dialog, { ConfirmDialog } from "../../components/Dialog";
import { PageBody, PageHead } from "../../components/PageLayout";
import { PageAction, PageHeader, PageMeta } from "../../components/Section";
import { Permission } from "../../api/responses";
import { hasPermission } from "../../auth";
import { plural } from "../../format";
import {
  artists,
  FORMATS,
  groups,
  releaseContext,
  releases,
  removeRelease,
  trackCount,
  tracksOfRelease,
  updateRelease,
  type Release,
} from "../../library";
import { AppRoutes } from "../../routes";

const collator = new Intl.Collator(undefined, { sensitivity: "base", numeric: true });

export default function Library() {
  const [query, setQuery] = createSignal("");
  // Null while no release is being edited or removed.
  const [editing, setEditing] = createSignal<Release | null>(null);
  const [removing, setRemoving] = createSignal<Release | null>(null);

  const rows = () => {
    const needle = query().trim().toLowerCase();
    return releases()
      .map(releaseContext)
      .filter(({ release, group, artist }) =>
        [release.title, group.title, artist.name, release.label].some((text) => text?.toLowerCase().includes(needle)),
      )
      .sort(
        (a, b) =>
          collator.compare(a.artist.name, b.artist.name) ||
          collator.compare(a.group.title, b.group.title) ||
          (a.release.year ?? 0) - (b.release.year ?? 0),
      );
  };

  return (
    <Show when={hasPermission(Permission.LIBRARY_WRITE)} fallback={<Navigate href={AppRoutes.HOME} />}>
      <>
        <PageHead>
          <PageHeader title="Library">
            <PageMeta>
              {[
                plural(artists().length, "artist"),
                plural(groups().length, "release group"),
                plural(releases().length, "release"),
                plural(trackCount(), "track"),
              ].join(" · ")}
            </PageMeta>
            <Show when={hasPermission(Permission.LIBRARY_UPLOAD)}>
              <PageAction>
                <A href={AppRoutes.ADMIN_UPLOAD} class="button primary">
                  Upload
                </A>
              </PageAction>
            </Show>
          </PageHeader>
        </PageHead>
        <PageBody>
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
                <For
                  each={rows()}
                  fallback={
                    <tr>
                      <td colspan="8" class="empty">
                        No releases match.
                      </td>
                    </tr>
                  }
                >
                  {({ release, group, artist }) => (
                    <tr>
                      <td>
                        <div class="admin-release">
                          <Cover seed={group.title} size={CoverSize.THUMBNAIL} />
                          <span>{release.title}</span>
                        </div>
                      </td>
                      <td>{artist.name}</td>
                      <td>
                        {group.title} <span class="muted">{group.type}</span>
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

          <Show when={editing()}>
            {(release) => (
              <Dialog title="Edit release" onClose={() => setEditing(null)}>
                <EditReleaseForm release={release()} onDone={() => setEditing(null)} />
              </Dialog>
            )}
          </Show>
          <Show when={removing()}>
            {(release) => (
              <ConfirmDialog
                title="Remove release?"
                message={`"${release().title}" and its tracks will be removed from the library for everyone.`}
                confirmLabel="Remove"
                onConfirm={() => removeRelease(release().id)}
                onClose={() => setRemoving(null)}
              />
            )}
          </Show>
        </PageBody>
      </>
    </Show>
  );
}

// The year field is empty for a release with no known year, and leaving it empty clears the year.
function yearText(year: number | null): string {
  if (year === null) {
    return "";
  }
  return String(year);
}

function enteredYear(text: string): number | null {
  if (text === "") {
    return null;
  }
  return Number(text);
}

// Likewise for the label.
function enteredLabel(text: string): string | null {
  const label = text.trim();
  if (label === "") {
    return null;
  }
  return label;
}

function EditReleaseForm(props: { release: Release; onDone: () => void }) {
  const [title, setTitle] = createSignal(props.release.title);
  const [year, setYear] = createSignal(yearText(props.release.year));
  const [format, setFormat] = createSignal(props.release.format);
  const [label, setLabel] = createSignal(props.release.label ?? "");

  const submit = (event: SubmitEvent) => {
    event.preventDefault();
    updateRelease(props.release.id, {
      title: title().trim(),
      year: enteredYear(year()),
      format: format(),
      label: enteredLabel(label()),
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
