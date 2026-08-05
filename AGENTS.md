## Android application requirements

- This repository contains an Android application with one launcher `MainActivity`.
- The application ID is `com.github.panlelapin.simplercal` and the user-facing name is
  `SimplerCal`.
- The interface must be entirely in English and use Material 3.
- Use a persisted `Theme` preference with `Light`, `Dark`, and `System` options; `System` is
  the default and follows the Android light/dark setting.
- Build the default theme with `dynamicLightColorScheme` / `dynamicDarkColorScheme`. Every UI
  element must consume Material `ColorScheme` roles and their matching `on*` pairs, never a
  raw palette value. The only allowed raw values are the explicit user-selected accent palette
  in the theme factory, which must derive the Material roles used everywhere else.
- Keep the Android status bar and navigation bar visible. The activity must not be
  immersive or fullscreen.
- Handle system insets explicitly. App-bar controls and clickable content must never
  overlap system bars or gesture-navigation areas.

## Main screen

- Use a Material 3 `Scaffold` and `CenterAlignedTopAppBar`.
- The top bar contains:
  - a monochrome classic gear settings icon on the left;
  - a monochrome left-arrow icon between the settings icon and title, with no behavior yet;
  - the title `S52 31 juin` in the center, with a half-em space (`U+2002`) between `31`
    and `juin`;
  - a monochrome right-arrow icon between the title and today icon, with no behavior yet;
  - a monochrome today icon on the right. The today icon is visible but has no behavior
    yet.
- Do not force the app-bar height. Use the standard Material top-app-bar measurement and its
  status-bar inset so the week surface always starts below the complete visible top bar, with
  no overlap or clipped lower edge.
- Below the top bar, display the current calendar week as seven full-width horizontal
  containers arranged vertically from Monday through Sunday.
- Each day container contains two full-height inner containers arranged horizontally. Their
  widths are the same in all seven day containers:
  - the left inner container is only wide enough for the widest three-letter uppercase
    English day abbreviation (`MON` through `SUN`); its text is right-aligned, vertically
    centered when compact, and aligned at the top when expanded;
  - the right inner container takes the remaining width and contains the day content.
- Only the right inner container has an accent stripe. It belongs to the vertically centered
  content block and appears directly above line 1, inside that right inner container, never on
  the day-container boundary or between two parent day containers. The stripe is not part of
  the parent day container, is 12 dp high, uses `MaterialTheme.colorScheme.secondary`, and has
  fully rounded pill ends so it visually follows the rounded inner-container language.
- Compute the current calendar week with Monday as its first day.
- The right inner container displays `dolor sit amet bla bla truc bigoudi plan plan
  proutcul` on nine successive lines when expanded and one line when compact, using a
  body-medium text size. Each line starts with its one-based line number followed
  by one space and the phrase. The content is vertically centered and left-aligned. Each
  line is single-line only and is clipped at the right edge without wrapping or an ellipsis.
- Initially, Monday and Tuesday are expanded. When Monday through Friday is selected, the
  selected day and the following day are expanded and all other containers are compact.
  When Saturday or Sunday is selected, Saturday and Sunday are both expanded.
- Distribute the available safe height according to the current states:
  - every compact container occupies 6.5 percent;
  - the two expanded containers share the remaining height equally.
- The complete visible surface of every day container is clickable. Clicking a day selects
  it and applies the matching expanded-container rule above.
- For a group change caused by a simple tap, animate all affected container heights
  together over exactly 0.7 seconds of elapsed frame time, using one shared linear progression
  that is independent of the system animator duration scale. The top and bottom separators
  must move with their containers as the new two-day group expands and the former group
  compacts.
- When a compact container becomes expanded, update its right-side content before the
  height animation starts. When an expanded container becomes compact, keep its expanded
  content during the height animation and reduce it to one line only after the animation
  completes.
- A simple tap remains available on every compact or expanded day container regardless of the
  selected scroll mode. Persist a `Scroll mode` setting with `Smooth` as its default:
  - `Smooth` starts a vertical drag only when the initial touch is on an expanded container.
    A new day becomes active only after the finger crosses the current container's halfway
    point.
  - `Per day` starts a vertical drag anywhere in the week area, including compact containers,
    the right-side strip, and the bottom strip. It starts from the currently expanded group,
    not from the touch-down container. Do not apply touch slop or wait for the pointer to
    reach any position: the very first non-zero vertical pointer delta starts the change.
    Transfer that exact pixel delta from the expanded container at the leading edge of the
    drag to the compact container entering at the opposite edge. The shared expanded
    container keeps its height, so its boundaries move by exactly the same number of pixels
    as the finger. Cross consecutive groups one after another as drag distance accumulates;
    one complete group transition equals the expanded-height minus compact-height difference.
  While dragging, interpolate neighboring expanded layouts directly from the accumulated
  pixel distance. Do not use a timed animation: the growth and compaction speed follows the
  finger. Settle immediately on the nearest group when the finger is released, producing a
  dock-style magnification movement without scaling content.
- Use the semantic `outlineVariant` role for the 1.5 dp separators around every day
  container and its two inner containers. Each left and right inner container has all four
  corners rounded by 24 dp. Their shared background is `surfaceContainer`, so no gap is
  visible between them or under their rounded corners. Use `surfaceContainer` for the
  fill of each left inner container and for the area below the day containers, including
  the system-gesture inset area. Use `surface` for the day parents and right inner
  containers.
- The Sunday container must stop immediately above a bottom band equal to 60 percent of
  the raw mandatory system-gesture inset. Its bottom separator marks the boundary with the
  system-inset area, while the `surfaceContainer` background continues underneath to the
  bottom edge.
- Reserve a right-side strip after the day containers whose width is 60 percent of the
  greater of the raw mandatory system-gesture inset and 24 dp. This visible strip uses
  `surfaceContainer`.
- Keep every full click target above the system inset, use Material interaction feedback,
  and expose each day container as one accessible semantic element.

## Settings screen

- Show the settings screen over the main screen with a Material 3 top bar and back action.
- The first section selects the Android calendar to use:
  - without calendar permission, show a button requesting permission;
  - after permission is granted, replace it with the calendar-selection button;
  - persist the selected calendar.
- The next section is `Theme`, with persisted `Light`, `Dark`, and `System` options; `System`
  is the default. Display all three choices side by side as a single-choice Material segmented
  control, not in a menu.
- The next section is `Accent color`. Persist the selection and use it to derive the complete
  Material `ColorScheme`, so it is reflected throughout the app. Its first option is `System`,
  which leaves the dynamic Android scheme intact. The remaining options are `Royal blue`
  (`#005AC1`), `Indigo` (`#3F51B5`), `Ocean blue` (`#00639B`), `Teal` (`#006B5F`),
  `Material violet` (`#6750A4`), `Plum` (`#7D3C98`), `Raspberry` (`#A7355C`), `Coral`
  (`#B4472D`), `Emerald green` (`#2E7D32`), a second `Teal` (`#006B5F`), and `Olive green`
  (`#627000`). The selector therefore contains twelve rows: `System` plus the eleven supplied
  colour entries. Its list must scroll so every row remains reachable on compact screens.
- The next section selects the persisted `Scroll mode`. Display the `Smooth` and `Per day`
  choices side by side as a single-choice Material segmented control, not in a menu.
- The final section is smaller and horizontally centered. It displays:
  - `SimplerCal v<release version>`;
  - the GitHub project URL as a clickable web link.
- If the installed build is not an official GitHub release, display `---` as the release
  version.

## Validation and remote build policy

- Codex must not run `scripts/check-local` or `scripts/make-remote`. The user runs both
  scripts manually.
- Codex may inspect files and diffs but must leave local validation, commit, push, GitHub
  Actions dispatch, APK retrieval, and device installation to the user-owned scripts.
- The local-check freshness digest must represent current file paths, contents, executable
  modes, and symbolic-link targets. It must remain unchanged when identical content is
  staged or committed so `make-remote` can safely resume after a partial failure.
- The normal local and GitHub Actions gate is `functionalCheck`.
- `functionalCheck` runs Detekt with type resolution and only the `potential-bugs` rule
  set. Detekt convention-oriented rules are disabled.
- KtLint, Android Lint, the optional `qualityCheck` task, and the ShellCheck helper remain
  available for explicit manual use but are bypassed by `check-local`, `make-remote`, and
  GitHub Actions.
- Cosmetic formatting, naming, complexity, and style findings must not block the normal
  workflow.
- GitHub Actions remains manually dispatched with `workflow_dispatch`. It runs
  `functionalCheck`, builds the release APK, verifies it, and publishes the APK with its
  SHA-256 checksum.
