# sona design language

**The music is the colour.** The interface stays quiet so album art and the music carry the personality. Every
screen does one job, and anything that isn't needed for that job isn't on the screen.

## Principles

1. **Clarity over features.** One primary action per screen. Secondary actions are quiet text buttons. If a control
   is rarely used, it lives in Settings, not on the page.
2. **Monochrome chrome, colourful content.** Surfaces, text and controls use a neutral graphite scale. The single
   accent (cobalt) only marks state: what is playing, which toggle is on, where keyboard focus is. Never decoration.
3. **Fast by default.** System font stack, no UI library, inline SVG icons, no images for chrome. Motion is limited to
   `opacity` and `transform`, around 150ms, and is switched off under `prefers-reduced-motion`.
4. **Generous targets.** Anything dragged or clicked often is big. The scrub thumb is 18px on a 32px hit area and
   grows while dragging.
5. **Same thing, same shape.** One album card, one track row, one page header, one section header, used everywhere.

## Tokens

All colours are CSS custom properties in `src/styles.css`, defined for light and dark.

| Token | Use |
|---|---|
| `--bg` | Page background |
| `--surface` | Cards, menus, panels |
| `--surface-2` | Hover fills, secondary buttons, active nav pill |
| `--text` / `--text-2` / `--text-3` | Primary text, secondary text, tertiary (timestamps, counts) |
| `--border` | Hairlines only; prefer spacing over borders |
| `--accent` / `--accent-soft` | State only: now playing, toggles on, focus ring |
| `--danger` | Destructive text actions |

Primary buttons are the inverse of the page (`--text` on `--bg`), not the accent.

**Type scale:** 12 / 13 / 14 (body) / 16 / 20 / 28 / 36. Headings are 600 weight with slight negative tracking.
Times and counts use tabular numerals.

**Spacing:** multiples of 4px. **Radius:** 6px (controls), 10px (covers, cards), 16px (floating surfaces).

## Building blocks

- **One shell.** Every page, library management included, sits in the same top bar, settings menu and player.
  Managing the library lives under `/admin` for admins only, reached from the settings menu; its Library page links
  to Upload.
- **Top bar:** brand left, section nav centred (Albums, Artists, Playlists, Discover), settings menu right.
- **Fixed page head.** The window never scrolls. Every page's header (title, cover, actions) stays put and only the
  content below it scrolls.
- **Page header:** title, one line of meta, at most one action.
- **Detail header:** cover, eyebrow (Artist, Album, Release, Playlist), title, linked artist, one meta line, then
  Play, Shuffle and a ⋯ menu for anything else.
- **Card:** square cover (round for artists), title, one subtitle line. A play button appears on hover.
- **Track row:** number (play button on hover, playing indicator when current), title, optional artist and release
  links, duration, and a ⋯ menu that appears on hover.
- **Discography links.** Every page links outward: artist to release groups, release group to its editions and
  the artist's other work, release to its sibling editions and the artist's other work.
- **Player bar:** one row floating at the bottom of the screen. The scrubber runs along its top edge with a large
  thumb whose hit area reaches above the bar. Now playing on the left, transport in the centre, time and volume on
  the right. On narrow screens time, volume, shuffle and repeat are hidden.
