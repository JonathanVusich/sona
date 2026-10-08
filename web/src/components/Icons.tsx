import type { JSX } from "solid-js";

type IconProps = { size?: number };

function Stroked(props: IconProps & { children: JSX.Element }) {
  return (
    <svg width={props.size ?? 20} height={props.size ?? 20} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      {props.children}
    </svg>
  );
}

function Filled(props: IconProps & { children: JSX.Element }) {
  return (
    <svg width={props.size ?? 20} height={props.size ?? 20} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      {props.children}
    </svg>
  );
}

export const PlayIcon = (props: IconProps) => (
  <Filled size={props.size}>
    <path d="M8 5.14v13.72a1 1 0 0 0 1.5.86l11.24-6.86a1 1 0 0 0 0-1.72L9.5 4.28A1 1 0 0 0 8 5.14z" />
  </Filled>
);

export const PauseIcon = (props: IconProps) => (
  <Filled size={props.size}>
    <rect x="6" y="4.5" width="4" height="15" rx="1.2" />
    <rect x="14" y="4.5" width="4" height="15" rx="1.2" />
  </Filled>
);

export const NextIcon = (props: IconProps) => (
  <Filled size={props.size}>
    <path d="M4.5 6.1v11.8a.9.9 0 0 0 1.4.75l8.6-5.9a.9.9 0 0 0 0-1.5L5.9 5.35a.9.9 0 0 0-1.4.75z" />
    <rect x="16.5" y="5" width="2.6" height="14" rx="1" />
  </Filled>
);

export const PreviousIcon = (props: IconProps) => (
  <Filled size={props.size}>
    <path d="M19.5 6.1v11.8a.9.9 0 0 1-1.4.75l-8.6-5.9a.9.9 0 0 1 0-1.5l8.6-5.9a.9.9 0 0 1 1.4.75z" />
    <rect x="4.9" y="5" width="2.6" height="14" rx="1" />
  </Filled>
);

export const ShuffleIcon = (props: IconProps) => (
  <Stroked size={props.size}>
    <path d="M16 3h5v5" />
    <path d="M4 20 21 3" />
    <path d="M21 16v5h-5" />
    <path d="m15 15 6 6" />
    <path d="m4 4 5 5" />
  </Stroked>
);

export const RepeatIcon = (props: IconProps & { one?: boolean }) => (
  <Stroked size={props.size}>
    <path d="m17 2 4 4-4 4" />
    <path d="M3 11v-1a4 4 0 0 1 4-4h14" />
    <path d="m7 22-4-4 4-4" />
    <path d="M21 13v1a4 4 0 0 1-4 4H3" />
    {props.one && <path d="M11 10.5 12.5 9.5v5" />}
  </Stroked>
);

export const VolumeIcon = (props: IconProps & { muted?: boolean }) => (
  <Stroked size={props.size}>
    <path d="M11 5 6 9H3v6h3l5 4z" />
    {props.muted ? (
      <>
        <path d="m22 9-6 6" />
        <path d="m16 9 6 6" />
      </>
    ) : (
      <>
        <path d="M15.5 8.5a5 5 0 0 1 0 7" />
        <path d="M18.5 5.5a9 9 0 0 1 0 13" />
      </>
    )}
  </Stroked>
);

export const GearIcon = (props: IconProps) => (
  <Stroked size={props.size}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </Stroked>
);

export const MoreIcon = (props: IconProps) => (
  <Filled size={props.size}>
    <circle cx="5" cy="12" r="1.8" />
    <circle cx="12" cy="12" r="1.8" />
    <circle cx="19" cy="12" r="1.8" />
  </Filled>
);
