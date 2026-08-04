## Android application requirements

- This repository contains an Android application with one launcher `MainActivity`.
- The application ID is `com.github.panlelapin.simplercal` and the user-facing name is
  `SimplerCal`.
- The interface must be entirely in English and use Material 3.
- Follow the system light/dark theme automatically.
- Keep the Android status bar and navigation bar visible. The activity must not be
  immersive or fullscreen.
- Handle system insets explicitly. App-bar controls and clickable content must never
  overlap system bars or gesture-navigation areas.

## Main screen

- Use a Material 3 `Scaffold` and `CenterAlignedTopAppBar`.
- The top bar contains:
  - a monochrome classic gear settings icon on the left;
  - the title `Hello` in the center;
  - a monochrome today icon on the right. The today icon is visible but has no behavior
    yet.
- Below the top bar, display the current calendar week as seven full-width horizontal
  containers arranged vertically from Monday through Sunday.
- Each container displays on its left side:
  - the three-letter English day abbreviation (`Mon` through `Sun`);
  - the day-of-month number directly below the abbreviation.
- Compute the displayed dates from the current week, with Monday as its first day.
- All seven day containers use the same structure and each contains nine logical content
  rows. Every container has one of two display states:
  - expanded: display all nine content rows;
  - compact: display only the date row and hide the other eight rows.
- The date row is the existing day-and-date block: the three-letter day abbreviation is
  shown with the day-of-month number directly below it.
- Exactly three consecutive containers must be expanded and the other four must be
  compact at all times. Initially, Monday, Tuesday, and Wednesday are expanded.
- Distribute the available safe height according to the current states:
  - each of the three expanded containers occupies 21 percent;
  - the four compact containers each occupy 9.25 percent.
- The complete visible surface of every day container is clickable. Clicking a compact
  container moves the expanded three-day group as follows:
  - clicking Monday or Tuesday expands Monday, Tuesday, and Wednesday;
  - clicking Wednesday, Thursday, or Friday expands the clicked day, the previous day,
    and the following day;
  - clicking Saturday or Sunday expands Friday, Saturday, and Sunday.
- After each state change, every container outside the selected three-day group becomes
  compact automatically, preserving exactly three expanded and four compact containers.
  Clicking a container that is already expanded does not change the state.
- Use a visible one-dp separator around every day container. Use the themed `onSurface`
  color so separators remain dark in the light theme and visible in the dark theme.
- Saturday and Sunday use a distinct neutral background:
  - `#E4E4E4` in the light theme;
  - `#202020` in the dark theme.
- Other day containers and the area below the week use:
  - `#FFFFFF` in the light theme;
  - `#000000` in the dark theme.
- The Sunday container must stop immediately above the bottom mandatory system-gesture
  inset. Its bottom separator marks the boundary with the system-inset area, while the
  neutral screen background continues underneath to the bottom edge. Read the raw bottom
  value from `WindowInsets.mandatorySystemGestures.asPaddingValues()` so the required
  gesture area is reserved even on devices whose navigation-bar inset is zero.
- Keep every full click target above the system inset, use Material interaction feedback,
  and expose each day container as one accessible semantic element.

## Settings screen

- Show the settings screen over the main screen with a Material 3 top bar and back action.
- The first section selects the Android calendar to use:
  - without calendar permission, show a button requesting permission;
  - after permission is granted, replace it with the calendar-selection button;
  - persist the selected calendar.
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
