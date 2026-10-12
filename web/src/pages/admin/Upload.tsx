import { Navigate, useNavigate } from "@solidjs/router";
import { createSignal, For, Show } from "solid-js";
import { fromDataTransfer, fromFileList, type PickedFile } from "../../audioFiles";
import PageLayout from "../../components/PageLayout";
import { PageHeader } from "../../components/Section";
import { Permission } from "../../api/responses";
import { hasPermission } from "../../auth";
import { importFiles } from "../../library";
import { AppRoutes } from "../../routes";

export default function Upload() {
  const [pending, setPending] = createSignal<PickedFile[]>([]);
  const [dragging, setDragging] = createSignal(false);
  const navigate = useNavigate();
  let filesInput: HTMLInputElement | undefined;
  let folderInput: HTMLInputElement | undefined;

  const add = (picked: PickedFile[]) => {
    setPending((current) => {
      const known = new Set(current.map((p) => p.path));
      return [...current, ...picked.filter((p) => !known.has(p.path))];
    });
  };

  const onPick = (event: Event & { currentTarget: HTMLInputElement }) => {
    const input = event.currentTarget;
    if (input.files !== null) {
      add(fromFileList(input.files));
    }
    // Clear so picking the same selection again still fires a change.
    input.value = "";
  };

  const onDrop = async (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    if (event.dataTransfer !== null) {
      add(await fromDataTransfer(event.dataTransfer));
    }
  };

  const submit = () => {
    importFiles(pending());
    navigate(AppRoutes.ADMIN_LIBRARY);
  };

  return (
    <Show when={hasPermission(Permission.LIBRARY_UPLOAD)} fallback={<Navigate href={AppRoutes.HOME} />}>
      <PageLayout header={<PageHeader title="Upload music" />}>
        <div
          class="dropzone"
          classList={{ dragging: dragging() }}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <p>Drag and drop audio files or folders here</p>
          <div class="dropzone-actions">
            <button onClick={() => filesInput?.click()}>Choose files</button>
            <button onClick={() => folderInput?.click()}>Choose folder</button>
          </div>
          <input ref={filesInput} type="file" accept="audio/*,.flac" multiple hidden onChange={onPick} />
          <input
            ref={(el) => {
              folderInput = el;
              el.webkitdirectory = true;
            }}
            type="file"
            multiple
            hidden
            onChange={onPick}
          />
        </div>

        <Show when={pending().length > 0} fallback={<p class="muted">No files selected. Only audio files are kept.</p>}>
          <div class="pending-header">
            <span>
              {pending().length} file{pending().length === 1 ? "" : "s"} ready
            </span>
            <div class="dropzone-actions">
              <button onClick={() => setPending([])}>Clear</button>
              <button class="primary" onClick={submit}>
                Add to library
              </button>
            </div>
          </div>
          <ul class="pending-list">
            <For each={pending()}>
              {(picked) => (
                <li>
                  <span class="pending-path">{picked.path}</span>
                  <span class="muted">{(picked.file.size / (1024 * 1024)).toFixed(1)} MB</span>
                  <button
                    class="link"
                    aria-label={`Remove ${picked.path}`}
                    onClick={() => setPending((current) => current.filter((p) => p !== picked))}
                  >
                    Remove
                  </button>
                </li>
              )}
            </For>
          </ul>
        </Show>
      </PageLayout>
    </Show>
  );
}
