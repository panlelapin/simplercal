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
- Define an `update` action group. Run it when the app first becomes visible, whenever the
  activity resumes after returning to the foreground, and whenever the displayed week changes.
  The first update action recomputes the app-bar title from the Monday of the displayed week:
  `S<ISO week number> - <Monday day of month><first three uppercase letters of the English
  month name>`. The `S`, ` - `, and month use a significantly smaller small-caps treatment than
  the normal-sized numbers. For example, a Monday in June is rendered as `S23 - 31JUN`, with
  the `S`, ` - `, and `JUN` visibly smaller.
- The top bar contains:
  - a monochrome classic gear settings icon on the left;
  - a monochrome left-arrow icon between the settings icon and title, which changes the
    displayed week to the preceding week;
  - the dynamically calculated `update` title in the center;
  - a monochrome right-arrow icon between the title and today icon, which changes the displayed
    week to the following week;
  - a monochrome today icon on the right. It returns to the current calendar week and selects
    the current day, applying the normal two-expanded-day rule.
- Do not force the app-bar height. Use the standard Material top-app-bar measurement and its
  status-bar inset so the week surface always starts below the complete visible top bar, with
  no overlap or clipped lower edge.
- Below the top bar, display the current calendar week as seven full-width horizontal
  containers arranged vertically from Monday through Sunday.
- Each day container contains two full-height inner containers arranged horizontally. Their
  widths are the same in all seven day containers:
  - the left inner container width is automatically calculated from the widest complete
    day/date label among all seven days (`MON. 1` through `SUN. 31`); every day uses that same
    width. It contains the accent stripe at its top,
    then the three-letter day abbreviation in small caps matching the title's reduced text size,
    followed on the same line by the numeric day of the month in the normal title size.
    This day/date block is right-aligned, vertically centered when compact, and aligned at the
    top below the stripe when expanded. Include a period and a space between the day and number,
    for example `WED.31`.
  - the right inner container takes the remaining width and contains the day content.
- Only the left inner container has an accent stripe. It is inside that inner container at its
  top edge, never on the day-container boundary or between two parent day containers. The stripe
  is 6 dp high, uses `MaterialTheme.colorScheme.secondary`, and has fully rounded pill ends so
  it visually follows the rounded inner-container language.
- The current day in the current displayed week has a border in the
  `MaterialTheme.colorScheme.primary` role around both inner containers. This highlight is
  recalculated by the `update` action group. Other inner-container borders continue to use the
  configured Debug1 border color. The border for the current day is recalculated by the `update`
  action group. Only for the highlighted day, the left inner container has no right corner radii
  and the right inner container has no left corner radii, so the two highlighted halves join
  continuously.
- There is no vertical peripheral gap or horizontal delimiter between Saturday and Sunday; their
  vertical side borders and other inner-container borders remain visible.
- There is no outer delimiter above the first day container or below the last day container.
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
  together over exactly 0.5 seconds of elapsed frame time, using one shared linear progression
  that is independent of the system animator duration scale. The top and bottom separators
  must move with their containers as the new two-day group expands and the former group
  compacts.
- When a compact container becomes expanded, update its right-side content before the
  height animation starts. When an expanded container becomes compact, keep its expanded
  content during the height animation and reduce it to one line only after the animation
  completes.
- A simple tap remains available on every compact or expanded day container regardless of the
  selected scroll mode. Persist a `Scroll mode` setting with `Discrete` as its default:
  - `Discrete` starts a vertical drag only when the initial touch is on an expanded container.
    A new day becomes active only after the finger crosses the current container's halfway
    point.
  - `Linear` starts a vertical drag anywhere in the week area, including compact containers,
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
- Define `app bar background` as the `surfaceContainer` role used explicitly by the top app
  bars. Use that same color for every left inner container and all peripheral areas around the
  day containers, including the bottom and right gesture strips. Each left and right inner
  container has all four corners rounded by 10 dp, except where the Saturday and Sunday
  containers meet: Saturday has no bottom-left or bottom-right corner radius, and Sunday has no
  top-left or top-right corner radius, for both horizontal inner containers.
- The Sunday container must stop immediately above a bottom band equal to 72 percent of
  the raw mandatory system-gesture inset. Its bottom separator marks the boundary with the
  system-inset area, while the app-bar-background color continues underneath to the
  bottom edge.
- Reserve a right-side strip after the day containers whose width is 45 percent of the
  greater of the raw mandatory system-gesture inset and 24 dp. This visible strip uses the
  app-bar-background color.
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
  (`#005AC1`), `Indigo` (`#3F51B5`), `Teal` (`#006B5F`), `Material violet` (`#6750A4`),
  `Plum` (`#7D3C98`), `Raspberry` (`#A7355C`), `Mandarin` (`#F57C00`), `Emerald green`
  (`#2E7D32`), and a second `Teal` (`#006B5F`). The selector therefore contains ten rows:
  `System` plus the nine remaining colour entries. Its list must scroll so every row remains
  reachable on compact screens.
- The next section selects the persisted `Scroll mode`. Display the `Discrete` and `Linear`
  choices side by side as a single-choice Material segmented control, not in a menu.
- The next section is `Debug1`. Persist its two side-by-side Material segmented choices:
  `Black` is the default and maps to `ColorScheme.onSurface`; `App bar background` maps to the
  shared app-bar-background color. Apply the selection immediately to every day-parent and
  inner-container border/separator.
- The next section is `Debug2`. Persist its two side-by-side Material segmented choices:
  `Surface` is the default and maps to `ColorScheme.surface`; `App bar background` maps to the
  shared app-bar-background color. Apply the selection immediately to every right inner
  container background.
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
