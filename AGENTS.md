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
    day/date label among all seven days; every day uses that same width. It contains the two-letter
    day abbreviation in small caps matching the title's reduced text size, followed on the same
    line by the numeric day of the month in a slightly smaller title size. This day/date block is
    right-aligned, vertically centered when compact, and aligned at the top when expanded, without
    a separator between the abbreviation and number.
  - the right inner container takes the remaining width and contains the day content.
- Only the right inner container has an accent line. It is inside that inner container along its
  left edge, immediately before the content, never on the day-container boundary or between two
  parent day containers. The line is 3 dp wide, has square ends, uses `ColorScheme.secondary` for
  days before the current day and `ColorScheme.primary` for the current day and following days.
- The current day in the current displayed week has a border in the
  `MaterialTheme.colorScheme.primary` role around the combined pair of inner containers. This
  highlight is recalculated by the `update` action group. Other inner-container borders continue
  to use the configured Debug1 border color. Only for the highlighted day, the left inner
  container has no right corner radii and the right inner container has no left corner radii, so
  the two highlighted halves join continuously. Reserve the highlight border thickness inside
  the parent day container so this combined border remains fully visible.
- In the current displayed week, the base day state uses `surface`/`onSurface`. Weekend and
  holiday days use `surfaceContainer`/`onSurface`. Past days use
  `surfaceDim`/`onSurfaceVariant`, except past weekend and holiday days, which use
  `surfaceContainer`/`onSurface`. For validation, Monday and Tuesday are holidays and
  Tuesday is also a bank holiday (`isBankH`).
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
- On the initial arrival in the app, invoke the same action as the `Today` button so the current
  calendar day is selected automatically.
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
  day containers, including the bottom and right gesture strips. Each day-row parent has 10 dp
  corner radii, except that Saturday has square bottom corners and Sunday has square top corners.
  The two inner containers have default square corners and do not carry the row-level rounding.
  In the current displayed week, both inner-container backgrounds of days before the current day
  use `ColorScheme.surfaceDim`, except past weekend and holiday days use
  `ColorScheme.surfaceContainer`. Current/future weekend and holiday days also use
  `ColorScheme.surfaceContainer`; other current/future days use `ColorScheme.surface` for both
  inner containers.
- The Sunday container must stop immediately above a bottom band equal to 72 percent of
  the raw mandatory system-gesture inset. Its bottom separator marks the boundary with the
  system-inset area, while the app-bar-background color continues underneath to the
  bottom edge.
- Reserve a right-side strip after the day containers whose width is 22.5 percent of the
  greater of the raw mandatory system-gesture inset and 24 dp. This visible strip uses the
  app-bar-background color.
- Reserve a left-side strip before the day containers with exactly the same width as the
  right-side strip. It also uses the app-bar-background color.
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
  `App bar background` is the default and maps to the shared app-bar-background color; `Black`
  maps to `ColorScheme.onSurface`. Apply the selection immediately to every day-parent and
  inner-container border/separator.
- The next section is `Debug2`. Persist its two side-by-side Material segmented choices. The
  main day view uses the temporal background rule: both inner containers of days before the
  current day use `ColorScheme.surfaceDim`, except past weekend and holiday days use
  `ColorScheme.surfaceContainer`; current/future weekend and holiday days also use
  `ColorScheme.surfaceContainer`, and other current/future days use `ColorScheme.surface`.
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
- The normal local and GitHub Actions gate runs both `functionalCheck` and `qualityCheck`.
- Detekt runs with type resolution and all configured rule sets enabled; convention, naming,
  complexity, formatting, and style findings are part of the validation.
- KtLint, Android Lint, and ShellCheck are all executed by `check-local` and GitHub Actions.
- Any validation finding must be fixed; no cosmetic, naming, complexity, style, or lint category
  is bypassed by the normal workflow.
- GitHub Actions remains manually dispatched with `workflow_dispatch`. It runs
  `functionalCheck`, builds the release APK, verifies it, and publishes the APK with its
  SHA-256 checksum.
