import { createSignal } from "solid-js";
import * as api from "./api/client";
import { ResultKind, type ApiResult } from "./api/client";
import type { IngestGroupResponse, TrackIngestResponse } from "./api/responses";
import type { PickedFile } from "./audioFiles";
import { sendAsCurrentUser } from "./auth";
import { forgetIngestStates } from "./ingestProgress";
import type { UploadGroup } from "./uploadGroups";

// Uploads groups of files to Sona: each group is created, then its files are sent one request each, a few at a time.
// Uploads live here rather than in the page, so they carry on while the uploader browses elsewhere.

export const UploadState = {
  WAITING: "waiting",
  UPLOADING: "uploading",
  UPLOADED: "uploaded",
  // Sona refused the file, such as for a format it can't ingest, so sending it again won't help.
  REFUSED: "refused",
  // The connection broke, or Sona failed to answer, so the file can be sent again.
  FAILED: "failed",
} as const;

export type UploadState = (typeof UploadState)[keyof typeof UploadState];

/** One file being uploaded, and how far it has got. */
export interface FileUpload {
  id: string;
  file: PickedFile;
  group: UploadGroup;
  state: UploadState;
  // How much of the file has been sent, from 0 to 1.
  sent: number;
  // Why the file was refused or failed, or null while it hasn't.
  reason: string | null;
  // The ingest Sona stored the file as, or null until it's uploaded.
  ingest: TrackIngestResponse | null;
}

// Browsers open about six connections to one host, so this leaves room for the rest of the app's requests.
const CONCURRENT_UPLOADS = 4;

const [uploads, setUploads] = createSignal<readonly FileUpload[]>([]);
// Each group's creation, sent once however many of its files wait for it, and answered with the ID Sona gave it.
const groupCreations = new Map<string, Promise<ApiResult<IngestGroupResponse>>>();
let runningUploaders = 0;

export function fileUploads(): readonly FileUpload[] {
  return uploads();
}

/**
 * @returns whether any file is still waiting or being sent
 */
export function uploadsInProgress(): boolean {
  return uploads().some((upload) => upload.state === UploadState.WAITING || upload.state === UploadState.UPLOADING);
}

export async function startUploads(groups: readonly UploadGroup[]): Promise<void> {
  const added = groups.flatMap((group) =>
    group.files.map((file) => ({
      id: crypto.randomUUID(),
      file,
      group,
      state: UploadState.WAITING,
      sent: 0,
      reason: null,
      ingest: null,
    })),
  );
  setUploads((current) => [...current, ...added]);
  await startUploaders();
}

/**
 * Sends a file whose upload failed again.
 */
export async function retryUpload(upload: FileUpload): Promise<void> {
  replaceUpload(upload, (current) => ({ ...current, state: UploadState.WAITING, sent: 0, reason: null }));
  await startUploaders();
}

/**
 * Forgets the uploads that have finished, once none is in progress.
 */
export function clearUploads() {
  setUploads([]);
  groupCreations.clear();
  forgetIngestStates();
}

async function startUploaders(): Promise<void> {
  const idle = CONCURRENT_UPLOADS - runningUploaders;
  await Promise.all(Array.from({ length: idle }, runUploader));
}

async function runUploader(): Promise<void> {
  runningUploaders += 1;
  await uploadWaitingFiles();
  runningUploaders -= 1;
}

// Sends the waiting files one after another, until none is left.
async function uploadWaitingFiles(): Promise<void> {
  const next = uploads().find((upload) => upload.state === UploadState.WAITING);
  if (next === undefined) {
    return;
  }
  updateUpload(next, UploadState.UPLOADING, null);
  const result = await sendUpload(next);
  updateUploadResult(next, result);
  await uploadWaitingFiles();
}

async function sendUpload(upload: FileUpload): Promise<ApiResult<TrackIngestResponse>> {
  const created = await createGroup(upload.group);
  if (created.kind !== ResultKind.OK) {
    return created;
  }
  const groupIngestId = created.body.groupIngestId;
  const file = upload.file.file;
  return sendAsCurrentUser((accessToken) =>
    api.uploadTrack(accessToken, groupIngestId, file, (sent) => updateSent(upload, sent)),
  );
}

// A group whose creation failed is sent again by its next file. If Sona stored it before the connection broke, that
// leaves an empty group behind, which does no harm.
async function createGroup(group: UploadGroup): Promise<ApiResult<IngestGroupResponse>> {
  const creation = groupCreations.get(group.id) ?? sendGroup(group);
  groupCreations.set(group.id, creation);
  const result = await creation;
  if (result.kind !== ResultKind.OK && groupCreations.get(group.id) === creation) {
    groupCreations.delete(group.id);
  }
  return result;
}

async function sendGroup(group: UploadGroup): Promise<ApiResult<IngestGroupResponse>> {
  const request = { kind: group.kind, name: group.name.trim(), trackCount: group.files.length };
  return sendAsCurrentUser((accessToken) => api.createIngestGroup(accessToken, request));
}

function updateUploadResult(upload: FileUpload, result: ApiResult<TrackIngestResponse>) {
  switch (result.kind) {
    case ResultKind.OK:
      return replaceUpload(upload, (current) => ({
        ...current,
        state: UploadState.UPLOADED,
        reason: null,
        ingest: result.body,
      }));
    case ResultKind.REFUSED:
      return updateUpload(upload, UploadState.REFUSED, result.reason);
    case ResultKind.FAILED:
      return updateUpload(upload, UploadState.FAILED, result.reason);
    default: {
      const unhandled: never = result;
      return unhandled;
    }
  }
}

function updateUpload(upload: FileUpload, state: UploadState, reason: string | null) {
  replaceUpload(upload, (current) => ({ ...current, state, reason }));
}

function updateSent(upload: FileUpload, sent: number) {
  replaceUpload(upload, (current) => ({ ...current, sent }));
}

function replaceUpload(upload: FileUpload, change: (current: FileUpload) => FileUpload) {
  setUploads((uploads) =>
    uploads.map((other) => {
      if (other.id !== upload.id) {
        return other;
      }
      return change(other);
    }),
  );
}
