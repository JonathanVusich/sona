// Collects audio files from a file picker or a drag and drop, descending into dropped folders.

const AUDIO_EXTENSIONS = new Set(["flac", "mp3", "ogg", "opus", "m4a", "aac", "wav", "aif", "aiff", "wma"]);

export interface PickedFile {
  file: File;
  // Path relative to whatever was picked or dropped, e.g. "Artist/Album/01 Track.flac".
  path: string;
}

export function isAudio(name: string): boolean {
  const dot = name.lastIndexOf(".");
  return dot !== -1 && AUDIO_EXTENSIONS.has(name.slice(dot + 1).toLowerCase());
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
  if (entry.isFile) {
    const file = await new Promise<File>((resolve, reject) => (entry as FileSystemFileEntry).file(resolve, reject));
    return isAudio(file.name) ? [{ file, path: entry.fullPath.replace(/^\//, "") }] : [];
  }
  if (entry.isDirectory) {
    const children = await readAll((entry as FileSystemDirectoryEntry).createReader());
    const nested = await Promise.all(children.map(walk));
    return nested.flat();
  }
  return [];
}

// readEntries returns entries in batches, then an empty batch once the directory is exhausted.
async function readAll(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  const batch = await new Promise<FileSystemEntry[]>((resolve, reject) => reader.readEntries(resolve, reject));
  return batch.length === 0 ? [] : [...batch, ...(await readAll(reader))];
}
