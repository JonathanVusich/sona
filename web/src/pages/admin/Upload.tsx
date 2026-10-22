import { Navigate } from "@solidjs/router";
import { createSignal, Index, Show } from "solid-js";
import { fileExtension, fromDataTransfer, fromFileList, type PickedFile } from "../../audioFiles";
import { PageBody, PageHead } from "../../components/PageLayout";
import { PageHeader } from "../../components/Section";
import UploadGroupEditor from "../../components/UploadGroupEditor";
import UploadProgress from "../../components/UploadProgress";
import { Permission } from "../../api/responses";
import { currentUser, hasPermission } from "../../auth";
import { formatFileSize, plural } from "../../format";
import { AppRoutes } from "../../routes";
import { addPickedFiles, type UploadGroup } from "../../uploadGroups";
import { fileUploads, startUploads } from "../../uploads";

// Whether files are being dragged over the drop zone.
const DropZone = {
  IDLE: "idle",
  DRAGGED_OVER: "dragged-over",
} as const;

type DropZone = (typeof DropZone)[keyof typeof DropZone];

export default function Upload() {
  return (
    <Show when={hasPermission(Permission.LIBRARY_UPLOAD)} fallback={<Navigate href={AppRoutes.HOME} />}>
      <>
        <PageHead>
          <PageHeader title="Upload music" />
        </PageHead>
        <PageBody>
          <Show when={fileUploads().length > 0} fallback={<UploadSelection />}>
            <UploadProgress />
          </Show>
        </PageBody>
      </>
    </Show>
  );
}

// Picks the files to upload, sorted into a group per folder, which the uploader can then rearrange.
function UploadSelection() {
  const [groups, setGroups] = createSignal<readonly UploadGroup[]>([]);
  const [dropZone, setDropZone] = createSignal<DropZone>(DropZone.IDLE);
  // The files of the last pick that were left out, as Sona can't ingest their format, or they're too large for it.
  const [unsupported, setUnsupported] = createSignal<readonly PickedFile[]>([]);
  const [tooLarge, setTooLarge] = createSignal<readonly PickedFile[]>([]);
  let filesInput: HTMLInputElement | undefined;
  let folderInput: HTMLInputElement | undefined;

  const add = (picked: PickedFile[]) => {
    const supported = picked.filter(isSupported);
    setUnsupported(picked.filter((file) => !isSupported(file)));
    setTooLarge(supported.filter(isTooLarge));
    const accepted = supported.filter((file) => !isTooLarge(file));
    setGroups((current) => addPickedFiles(current, accepted));
  };
  const clear = () => {
    setGroups([]);
    setUnsupported([]);
    setTooLarge([]);
  };
  const fileCount = () => groups().reduce((count, group) => count + group.files.length, 0);
  const unnamed = () => groups().some((group) => group.name.trim() === "");

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
    setDropZone(DropZone.IDLE);
    if (event.dataTransfer !== null) {
      add(await fromDataTransfer(event.dataTransfer));
    }
  };

  return (
    <>
      <div
        class="dropzone"
        classList={{ dragging: dropZone() === DropZone.DRAGGED_OVER }}
        onDragOver={(e) => {
          e.preventDefault();
          setDropZone(DropZone.DRAGGED_OVER);
        }}
        onDragLeave={() => setDropZone(DropZone.IDLE)}
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

      <Show when={unsupported().length > 0}>
        <p class="error">
          Left out {plural(unsupported().length, "file")} Sona can't ingest yet: {filePaths(unsupported())}
        </p>
      </Show>
      <Show when={currentUser()}>
        {(user) => (
          <Show when={tooLarge().length > 0}>
            <p class="error">
              Left out {plural(tooLarge().length, "file")} larger than {formatFileSize(user().maxUploadSize)}:{" "}
              {filePaths(tooLarge())}
            </p>
          </Show>
        )}
      </Show>

      <Show when={groups().length > 0} fallback={<p class="muted">No files selected. Only audio files are kept.</p>}>
        <div class="pending-header">
          <span>
            {plural(fileCount(), "file")} in {plural(groups().length, "group")}
          </span>
          <div class="dropzone-actions">
            <button onClick={clear}>Clear</button>
            <button class="primary" disabled={unnamed()} onClick={() => startUploads(groups())}>
              Upload
            </button>
          </div>
        </div>
        <Show when={unnamed()}>
          <p class="error">Give every group a name.</p>
        </Show>
        <p class="muted">
          Each folder is a group of files that belong together, such as one album. Move files between groups to fix them
          before uploading.
        </p>
        {/* Index rather than For, so renaming a group, which replaces it, keeps its name field focused. */}
        <Index each={groups()}>
          {(group) => <UploadGroupEditor group={group()} groups={groups()} onChange={setGroups} />}
        </Index>
      </Show>
    </>
  );
}

// Whether Sona can ingest the file's format. One it can't would be refused, so it's better left out than sent.
function isSupported(file: PickedFile): boolean {
  const user = currentUser();
  const extension = fileExtension(file.file.name);
  return user !== null && extension !== null && user.uploadExtensions.includes(extension);
}

// Whether Sona would refuse the file for its size, so it's better left out than sent.
function isTooLarge(file: PickedFile): boolean {
  const user = currentUser();
  return user !== null && file.file.size > user.maxUploadSize;
}

function filePaths(files: readonly PickedFile[]): string {
  return files.map((file) => file.path).join(", ");
}
