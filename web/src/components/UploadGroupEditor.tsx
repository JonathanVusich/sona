import { For, Show } from "solid-js";
import { IngestGroupKind } from "../api/requests";
import type { PickedFile } from "../audioFiles";
import { formatFileSize, plural } from "../format";
import {
  moveFile,
  moveFileToNewGroup,
  moveGroupFiles,
  removeFile,
  renameGroup,
  type UploadGroup,
} from "../uploadGroups";

// The option of a file's "Move to" menu that moves it into a group of its own. Group IDs are UUIDs, so none is this.
const NEW_GROUP = "new-group";

interface GroupProps {
  group: UploadGroup;
  // Every group of the upload, which the menus offer to move files into.
  groups: readonly UploadGroup[];
  onChange: (groups: UploadGroup[]) => void;
}

/**
 * One group of files to upload: its name, which the uploader may change, and its files, which they may move to
 * another group or leave out.
 */
export default function UploadGroupEditor(props: GroupProps) {
  return (
    <section class="upload-group" aria-label={props.group.name}>
      <div class="upload-group-head">
        <input
          class="upload-group-name"
          aria-label="Group name"
          value={props.group.name}
          onInput={(e) => props.onChange(renameGroup(props.groups, props.group, e.currentTarget.value))}
        />
        <span class="badge">{groupKindLabel(props.group.kind)}</span>
        <span class="muted">{plural(props.group.files.length, "file")}</span>
        <Show when={props.groups.length > 1}>
          <GroupMergeMenu {...props} />
        </Show>
      </div>
      <Show when={props.group.kind === IngestGroupKind.UNKNOWN}>
        <p class="hint">Files that aren't in a folder. Sona works out which go together as it matches them.</p>
      </Show>
      <ul class="pending-list">
        <For each={props.group.files}>{(file) => <GroupFile {...props} file={file} />}</For>
      </ul>
    </section>
  );
}

function GroupFile(props: GroupProps & { file: PickedFile }) {
  return (
    <li>
      <span class="pending-path">{props.file.path}</span>
      <span class="muted">{formatFileSize(props.file.file.size)}</span>
      <FileMoveMenu {...props} />
      <button
        class="link"
        aria-label={`Remove ${props.file.path}`}
        onClick={() => props.onChange(removeFile(props.groups, props.file))}
      >
        Remove
      </button>
    </li>
  );
}

function FileMoveMenu(props: GroupProps & { file: PickedFile }) {
  const onPick = (event: Event & { currentTarget: HTMLSelectElement }) => {
    const target = event.currentTarget.value;
    if (target === NEW_GROUP) {
      props.onChange(moveFileToNewGroup(props.groups, props.file));
    } else {
      props.onChange(moveFile(props.groups, props.file, target));
    }
  };
  return (
    <select aria-label={`Move ${props.file.path} to`} onChange={onPick}>
      <option disabled selected>
        Move to…
      </option>
      <OtherGroupOptions {...props} />
      <option value={NEW_GROUP}>New group</option>
    </select>
  );
}

function GroupMergeMenu(props: GroupProps) {
  const onPick = (event: Event & { currentTarget: HTMLSelectElement }) => {
    const select = event.currentTarget;
    props.onChange(moveGroupFiles(props.groups, props.group, select.value));
    // The editor stays for whichever group takes this one's place in the list, so its menu starts over.
    select.selectedIndex = 0;
  };
  return (
    <select class="upload-group-merge" aria-label={`Move all files in ${props.group.name} to`} onChange={onPick}>
      <option disabled selected>
        Move all to…
      </option>
      <OtherGroupOptions {...props} />
    </select>
  );
}

function OtherGroupOptions(props: GroupProps) {
  const others = () => props.groups.filter((group) => group.id !== props.group.id);
  return <For each={others()}>{(group) => <option value={group.id}>{group.name}</option>}</For>;
}

function groupKindLabel(kind: IngestGroupKind): string {
  switch (kind) {
    case IngestGroupKind.ALBUM:
      return "Album";
    case IngestGroupKind.UNKNOWN:
      return "Unsorted";
    default: {
      const unhandled: never = kind;
      return unhandled;
    }
  }
}
