// Collects audio files from a file picker or a drag and drop, descending into dropped folders.

const AUDIO_EXTENSIONS = new Set([
  "flac",
  "mp3",
  "mp2",
  "mp1",
  "ogg",
  "opus",
  "m4a",
  "aac",
  "wav",
  "aif",
  "aiff",
  "wma",
]);

export interface PickedFile {
  file: File;
  // Path relative to whatever was picked or dropped, e.g. "Artist/Album/01 Track.flac".
  path: string;
}

export function isAudio(name: string): boolean {
  const extension = fileExtension(name);
  return extension !== null && AUDIO_EXTENSIONS.has(extension);
}

/**
 * @returns the extension of a file's name, lowercase and without the dot, or null when it has none
 */
export function fileExtension(name: string): string | null {
  const dot = name.lastIndexOf(".");
  if (dot === -1) {
    return null;
  }
  return name.slice(dot + 1).toLowerCase();
}

export function fromFileList(list: FileList): PickedFile[] {
  return Array.from(list)
    .filter((file) => isAudio(file.name))
    .map((file) => ({ file, path: file.webkitRelativePath || file.name }));
}

export async function fromDataTransfer(transfer: DataTransfer): Promise<PickedFile[]> {
  // Entries must be taken synchronously, before the drop event's data store is cleared.
  const entries = Array.from(transfer.items)
    .map((item) => item.webkitGetAsEntry())
    .filter((entry): entry is FileSystemEntry => entry !== null);
  if (entries.length === 0) {
    // No entry API for this drop, so folders can't be walked; take the loose files.
    return fromFileList(transfer.files);
  }
  const nested = await Promise.all(entries.map(walk));
  return nested.flat();
}

async function walk(entry: FileSystemEntry): Promise<PickedFile[]> {
  if (entry instanceof FileSystemFileEntry) {
    const file = await new Promise<File>((resolve, reject) => entry.file(resolve, reject));
    if (!isAudio(file.name)) {
      return [];
    }
    return [{ file, path: entry.fullPath.replace(/^\//, "") }];
  }
  if (entry instanceof FileSystemDirectoryEntry) {
    const children = await readAll(entry.createReader());
    const nested = await Promise.all(children.map(walk));
    return nested.flat();
  }
  return [];
}

// readEntries returns entries in batches, then an empty batch once the directory is exhausted.
async function readAll(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  const batch = await new Promise<FileSystemEntry[]>((resolve, reject) => reader.readEntries(resolve, reject));
  if (batch.length === 0) {
    return [];
  }
  return [...batch, ...(await readAll(reader))];
}
