import { For, Show } from "solid-js";
import { plural } from "../format";
import type { UploadGroup } from "../uploadGroups";
import { clearUploads, fileUploads, retryUpload, uploadsInProgress, UploadState, type FileUpload } from "../uploads";

/**
 * The files being uploaded, group by group, with how far each has got. A file whose connection broke can be sent
 * again; one Sona refused can't.
 */
export default function UploadProgress() {
  const uploaded = () => fileUploads().filter((upload) => upload.state === UploadState.UPLOADED).length;
  return (
    <>
      <div class="pending-header">
        <span class="upload-summary">
          {uploaded()} of {plural(fileUploads().length, "file")} uploaded
        </span>
        <Show when={!uploadsInProgress()}>
          <button class="primary" onClick={clearUploads}>
            Upload more
          </button>
        </Show>
      </div>
      <For each={uploadGroups(fileUploads())}>{(group) => <GroupProgress group={group} />}</For>
    </>
  );
}

function GroupProgress(props: { group: UploadGroup }) {
  const groupUploads = () => fileUploads().filter((upload) => upload.group.id === props.group.id);
  return (
    <section class="upload-group" aria-label={props.group.name}>
      <h2 class="upload-group-title">{props.group.name}</h2>
      <ul class="pending-list">
        <For each={groupUploads()}>{(upload) => <FileProgress upload={upload} />}</For>
      </ul>
    </section>
  );
}

function FileProgress(props: { upload: FileUpload }) {
  return (
    <li>
      <span class="pending-path">{props.upload.file.path}</span>
      <span
        class="upload-status"
        classList={{
          error: props.upload.state === UploadState.REFUSED || props.upload.state === UploadState.FAILED,
        }}
      >
        {uploadStatus(props.upload)}
      </span>
      <Show when={props.upload.state === UploadState.FAILED}>
        <button class="link" aria-label={`Retry ${props.upload.file.path}`} onClick={() => retryUpload(props.upload)}>
          Retry
        </button>
      </Show>
    </li>
  );
}

// The groups of the uploads, each once, in the order they were picked.
function uploadGroups(uploads: readonly FileUpload[]): UploadGroup[] {
  return [...new Map(uploads.map((upload) => [upload.group.id, upload.group])).values()];
}

function uploadStatus(upload: FileUpload): string {
  switch (upload.state) {
    case UploadState.WAITING:
      return "Waiting";
    case UploadState.UPLOADING:
      return "Uploading";
    case UploadState.UPLOADED:
      return "Uploaded";
    case UploadState.REFUSED:
    case UploadState.FAILED:
      return upload.reason ?? "Upload failed";
    default: {
      const unhandled: never = upload.state;
      return unhandled;
    }
  }
}
