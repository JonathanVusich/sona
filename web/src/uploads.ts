import { createSignal } from "solid-js";
import * as api from "./api/client";
import { ResultKind, type ApiResult } from "./api/client";
import { ErrorCode, IngestState, type IngestGroupResponse, type TrackIngestResponse } from "./api/responses";
import type { PickedFile } from "./audioFiles";
import { sendAsCurrentUser } from "./auth";
import { forgetIngestStates } from "./ingestProgress";
import type { UploadGroup } from "./uploadGroups";

// Uploads groups of files to Sona: each group is created with all of its files, then the files are sent one request
// each, a few at a time. Uploads live here rather than in the page, so they carry on while the uploader browses
// elsewhere.

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
  // The ingest Sona made for the file when it created the group, or null until it has.
  ingest: TrackIngestResponse | null;
}

// Browsers open about six connections to one host, so this leaves room for the rest of the app's requests.
const CONCURRENT_UPLOADS = 4;

const [uploads, setUploads] = createSignal<readonly FileUpload[]>([]);
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
  await Promise.all(groups.map(createGroup));
  await startUploaders();
}

/**
 * Sends a file whose upload failed again. If Sona never created its group, creates it again first, with the rest of
 * its files.
 */
export async function retryUpload(upload: FileUpload): Promise<void> {
  if (upload.ingest === null) {
    await retryGroupCreation(upload.group);
  } else {
    replaceUpload(upload, (current) => ({ ...current, state: UploadState.WAITING, sent: 0, reason: null }));
  }
  await startUploaders();
}

/**
 * Forgets the uploads that have finished, once none is in progress.
 */
export function clearUploads() {
  setUploads([]);
  forgetIngestStates();
}

async function retryGroupCreation(group: UploadGroup): Promise<void> {
  for (const member of groupMembers(group)) {
    updateUpload(member, UploadState.WAITING, null);
  }
  await createGroup(group);
}

// The files of the group that Sona has made no ingest for yet.
function groupMembers(group: UploadGroup): FileUpload[] {
  return uploads().filter((upload) => upload.group.id === group.id && upload.ingest === null);
}

// Creates the group with every file that has no ingest yet, and gives each file the ingest Sona made for it.
async function createGroup(group: UploadGroup): Promise<void> {
  const members = groupMembers(group);
  const request = {
    kind: group.kind,
    name: group.name.trim(),
    filenames: members.map((member) => member.file.file.name),
  };
  const result = await sendAsCurrentUser((accessToken) => api.createIngestGroup(accessToken, request));
  updateCreationResult(members, result);
}

function updateCreationResult(members: readonly FileUpload[], result: ApiResult<IngestGroupResponse>) {
  switch (result.kind) {
    case ResultKind.OK:
      return members.forEach((member, index) => setIngest(member, result.body.tracks[index] ?? null));
    case ResultKind.REFUSED:
      return members.forEach((member) => updateUpload(member, UploadState.REFUSED, result.reason));
    case ResultKind.FAILED:
      return members.forEach((member) => updateUpload(member, UploadState.FAILED, result.reason));
    default: {
      const unhandled: never = result;
      return unhandled;
    }
  }
}

function setIngest(upload: FileUpload, ingest: TrackIngestResponse | null) {
  replaceUpload(upload, (current) => ({ ...current, ingest }));
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

// Sends the waiting files one after another, until none is left. A file waits for its group to be created.
async function uploadWaitingFiles(): Promise<void> {
  const next = uploads().find((upload) => upload.state === UploadState.WAITING && upload.ingest !== null);
  if (next === undefined || next.ingest === null) {
    return;
  }
  updateUpload(next, UploadState.UPLOADING, null);
  const result = await sendUpload(next, next.ingest);
  updateUploadResult(next, next.ingest, result);
  await uploadWaitingFiles();
}

async function sendUpload(upload: FileUpload, ingest: TrackIngestResponse): Promise<ApiResult<TrackIngestResponse>> {
  const file = upload.file.file;
  return sendAsCurrentUser((accessToken) =>
    api.uploadTrack(accessToken, ingest.groupIngestId, ingest.trackIngestId, file, (sent) => updateSent(upload, sent)),
  );
}

function updateUploadResult(upload: FileUpload, ingest: TrackIngestResponse, result: ApiResult<TrackIngestResponse>) {
  switch (result.kind) {
    case ResultKind.OK:
      return markUploaded(upload, result.body);
    case ResultKind.REFUSED:
      if (result.code === ErrorCode.INGEST_ALREADY_UPLOADED) {
        // Sona stored the file, but the connection broke before it answered.
        return markUploaded(upload, { ...ingest, state: IngestState.UPLOADED });
      }
      return updateUpload(upload, UploadState.REFUSED, result.reason);
    case ResultKind.FAILED:
      return updateUpload(upload, UploadState.FAILED, result.reason);
    default: {
      const unhandled: never = result;
      return unhandled;
    }
  }
}

function markUploaded(upload: FileUpload, ingest: TrackIngestResponse) {
  replaceUpload(upload, (current) => ({ ...current, state: UploadState.UPLOADED, reason: null, ingest }));
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
