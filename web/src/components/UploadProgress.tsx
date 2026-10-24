import { For, Show } from "solid-js";
import { IngestState } from "../api/responses";
import { plural } from "../format";
import { ingestState } from "../ingestProgress";
import type { UploadGroup } from "../uploadGroups";
import { clearUploads, fileUploads, retryUpload, uploadsInProgress, UploadState, type FileUpload } from "../uploads";

/**
 * The files being uploaded, group by group, with how far each has got: how much of it has been sent, then how far
 * Sona has got adding it to the library. A file whose connection broke can be sent again; one Sona refused can't.
 */
export default function UploadProgress() {
  const uploaded = () => fileUploads().filter((upload) => upload.state === UploadState.UPLOADED).length;
  const added = () => fileUploads().filter((upload) => uploadIngestState(upload) === IngestState.COMPLETED).length;
  return (
    <>
      <div class="pending-header">
        <span class="upload-summary">
          {uploaded()} of {plural(fileUploads().length, "file")} uploaded
        </span>
        <span class="ingest-summary">{added()} added to the library</span>
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
      <Show
        when={props.upload.state === UploadState.UPLOADING}
        fallback={
          <span class="upload-status" classList={{ error: isFailed(props.upload) }}>
            {uploadStatus(props.upload)}
          </span>
        }
      >
        <UploadRing upload={props.upload} />
      </Show>
      <Show when={props.upload.state === UploadState.FAILED}>
        <button class="link" aria-label={`Retry ${props.upload.file.path}`} onClick={() => retryUpload(props.upload)}>
          Retry
        </button>
      </Show>
    </li>
  );
}

const RING_RADIUS = 7;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

// How much of a file has been sent, as a ring that fills clockwise from the top.
function UploadRing(props: { upload: FileUpload }) {
  return (
    <svg
      class="upload-ring"
      viewBox="0 0 18 18"
      role="progressbar"
      aria-label={`Uploading ${props.upload.file.path}`}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(props.upload.sent * 100)}
    >
      <circle class="upload-ring-track" cx="9" cy="9" r={RING_RADIUS} />
      <circle
        class="upload-ring-sent"
        cx="9"
        cy="9"
        r={RING_RADIUS}
        stroke-dasharray={`${RING_CIRCUMFERENCE}`}
        stroke-dashoffset={RING_CIRCUMFERENCE * (1 - props.upload.sent)}
      />
    </svg>
  );
}

// The groups of the uploads, each once, in the order they were picked.
function uploadGroups(uploads: readonly FileUpload[]): UploadGroup[] {
  return [...new Map(uploads.map((upload) => [upload.group.id, upload.group])).values()];
}

// How far Sona has got with an uploaded file: the latest state it sent, or the one it answered the upload with.
function uploadIngestState(upload: FileUpload): IngestState | null {
  if (upload.ingest === null) {
    return null;
  }
  return ingestState(upload.ingest.trackIngestId) ?? upload.ingest.state;
}

function isFailed(upload: FileUpload): boolean {
  switch (upload.state) {
    case UploadState.REFUSED:
    case UploadState.FAILED:
      return true;
    case UploadState.WAITING:
    case UploadState.UPLOADING:
      return false;
    case UploadState.UPLOADED: {
      const state = uploadIngestState(upload);
      return state === IngestState.FAILED || state === IngestState.MOVE_FAILED;
    }
    default: {
      const unhandled: never = upload.state;
      return unhandled;
    }
  }
}

function uploadStatus(upload: FileUpload): string {
  switch (upload.state) {
    case UploadState.WAITING:
      return "Waiting";
    case UploadState.UPLOADING:
      return "Uploading";
    case UploadState.UPLOADED:
      return uploadedStatus(upload);
    case UploadState.REFUSED:
    case UploadState.FAILED:
      return upload.reason ?? "Upload failed";
    default: {
      const unhandled: never = upload.state;
      return unhandled;
    }
  }
}

function uploadedStatus(upload: FileUpload): string {
  const state = uploadIngestState(upload);
  if (state === null) {
    return "Uploaded";
  }
  return ingestStatus(state);
}

function ingestStatus(state: IngestState): string {
  switch (state) {
    case IngestState.PENDING:
      return "Waiting to be matched";
    case IngestState.INPUT_REQUIRED:
      return "No match found";
    case IngestState.MOVE_FAILED:
      return "Couldn't be moved into the library";
    case IngestState.FAILED:
      return "Couldn't be added";
    case IngestState.COMPLETED:
      return "Added to the library";
    default: {
      const unhandled: never = state;
      return unhandled;
    }
  }
}
