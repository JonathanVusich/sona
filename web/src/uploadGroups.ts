import { IngestGroupKind } from "./api/requests";
import type { PickedFile } from "./audioFiles";

// Sorts picked files into the groups they're uploaded in: one per folder, and one for the loose files, which matching
// groups itself. The uploader can then move files between groups before uploading. Every function returns new groups,
// and none is left empty.

/** Files to upload together, as one ingest group. */
export interface UploadGroup {
  // Tells groups apart while they're arranged. Sona gives the ingest group its own ID when it's created.
  id: string;
  kind: IngestGroupKind;
  name: string;
  // The folder the group was made from, which files picked from it later join. Null for the loose files, and for
  // groups the uploader made.
  folder: string | null;
  files: readonly PickedFile[];
}

const LOOSE_FILES_NAME = "Loose files";
const NEW_GROUP_NAME = "New group";

const collator = new Intl.Collator(undefined, { sensitivity: "base", numeric: true });

/**
 * @returns the groups with the files picked added, each to its folder's group, leaving out files already in one
 */
export function addPickedFiles(groups: readonly UploadGroup[], picked: readonly PickedFile[]): UploadGroup[] {
  const known = new Set(groups.flatMap((group) => group.files.map((file) => file.path)));
  return picked.filter((file) => !known.has(file.path)).reduce(addPickedFile, [...groups]);
}

/**
 * @returns the groups with the file moved into the group with the target ID
 */
export function moveFile(groups: readonly UploadGroup[], file: PickedFile, targetId: string): UploadGroup[] {
  return addFiles(removeFile(groups, file), targetId, [file]);
}

/**
 * @returns the groups with the file moved into a group of its own
 */
export function moveFileToNewGroup(groups: readonly UploadGroup[], file: PickedFile): UploadGroup[] {
  return [...removeFile(groups, file), newGroup(IngestGroupKind.ALBUM, NEW_GROUP_NAME, null, [file])];
}

/**
 * @returns the groups with every file of the source group moved into the group with the target ID
 */
export function moveGroupFiles(groups: readonly UploadGroup[], source: UploadGroup, targetId: string): UploadGroup[] {
  const others = groups.filter((group) => group.id !== source.id);
  return addFiles(others, targetId, source.files);
}

export function removeFile(groups: readonly UploadGroup[], file: PickedFile): UploadGroup[] {
  return groups
    .map((group) => ({ ...group, files: group.files.filter((other) => other !== file) }))
    .filter((group) => group.files.length > 0);
}

export function renameGroup(groups: readonly UploadGroup[], renamed: UploadGroup, name: string): UploadGroup[] {
  return groups.map((group) => {
    if (group.id !== renamed.id) {
      return group;
    }
    return { ...group, name };
  });
}

function addPickedFile(groups: UploadGroup[], file: PickedFile): UploadGroup[] {
  const folder = parentFolder(file);
  const kind = groupKind(folder);
  const existing = groups.find((group) => group.kind === kind && group.folder === folder);
  if (existing === undefined) {
    return [...groups, newGroup(kind, groupName(folder), folder, [file])];
  }
  return addFiles(groups, existing.id, [file]);
}

function addFiles(groups: readonly UploadGroup[], targetId: string, files: readonly PickedFile[]): UploadGroup[] {
  return groups.map((group) => {
    if (group.id !== targetId) {
      return group;
    }
    return { ...group, files: sortFiles([...group.files, ...files]) };
  });
}

function newGroup(
  kind: IngestGroupKind,
  name: string,
  folder: string | null,
  files: readonly PickedFile[],
): UploadGroup {
  return { id: crypto.randomUUID(), kind, name, folder, files: sortFiles(files) };
}

// The folder a file was picked from, such as "Artist/Album", or null for a file picked on its own.
function parentFolder(file: PickedFile): string | null {
  const slash = file.path.lastIndexOf("/");
  if (slash === -1) {
    return null;
  }
  return file.path.slice(0, slash);
}

function groupKind(folder: string | null): IngestGroupKind {
  if (folder === null) {
    return IngestGroupKind.UNKNOWN;
  }
  return IngestGroupKind.ALBUM;
}

// A folder's group is named for the folder itself, such as "Album" for "Artist/Album".
function groupName(folder: string | null): string {
  if (folder === null) {
    return LOOSE_FILES_NAME;
  }
  return folder.slice(folder.lastIndexOf("/") + 1);
}

function sortFiles(files: readonly PickedFile[]): PickedFile[] {
  return [...files].sort((a, b) => collator.compare(a.path, b.path));
}
