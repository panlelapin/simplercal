# SimplerCal — working contract

## Scope and workflow

- Android application: one launcher MainActivity, application ID
  com.github.panlelapin.simplercal, English UI, user-facing name SimplerCal.
- Read this contract before changes. Preserve unrelated user changes.
- Codex may edit and inspect source/diffs. Unless explicitly requested in the current
  user message, Codex must NOT run scripts/build-remote, scripts/check-local,
  scripts/make-remote, local compilation/tests, commit/push, Actions dispatch,
  APK retrieval, or device installation. The user runs the tracked scripts.
- A code change is IMPLEMENTED, not runtime-verified, until the relevant checks
  actually pass. Never equate an audit, a test definition, or an APK install with
  a verified UI behavior. PB1.md and PB2.md must reflect this distinction.

## Theme and windows

- Use Material 3 and dynamicLightColorScheme/dynamicDarkColorScheme by default.
  System follows Android; Light and Dark are persisted overrides.
- Custom accent seeds are confined to ThemeModels.kt. Derive the complete
  HCT SchemeTonalSpot ColorScheme, including matching on* roles. Cache schemes
  across recompositions, invalidating on seed, mode, and Android configuration.
- Components use ColorScheme roles and Material defaults. Explicit product
  exception: current/future non-isWEorBankH days have white backgrounds in Light
  and black backgrounds in Dark, with onSurface text. Do not generalize this
  exception to other components or introduce new raw UI colors.
- App-bar background means surfaceContainer. Peripheral strips use the same role.
- Status/navigation bars stay visible. No immersive/fullscreen mode.
- Use Scaffold and standard CenterAlignedTopAppBar measurement/insets, without
  fixed height or overlapping week content.
- Main Scaffold owns horizontal safeDrawing padding. Week gutters add only the
  visual margin, not the same physical inset a second time.

## update, date and navigation

- AppViewModel owns the displayed Monday, selection, settings visibility,
  reference date, calendar results and persistent preferences. Restore navigation
  through SavedStateHandle. Save transient UI state with rememberSaveable.
- Run update on first arrival, resume, displayed-week changes, calendar
  invalidations, date/time/time-zone changes, and foreground local midnight.
- First update action: compute Monday's ISO week title:
  S<week> - <Monday day><three-letter uppercase English month>.
  S, spaced hyphen, and month use 12sp small caps; numbers use normal title size.
- Left controls: gear, previous-week arrow. Right: next-week arrow, Today.
  Arrows move exactly one week. Today returns to the real current week and
  selects today, or Wednesday in Simulation (nearest allowed day in a boundary
  week if Wednesday is outside the active years). First arrival invokes Today.
- Only the real current calendar year and the following year are shown as active.
  Navigation is bounded by the Monday of the first/last intersecting week. Keep
  seven rows in boundary weeks, but gray and disable dates outside those years,
  including taps, details and drag targets. Recompute the range at local New Year.
- Past/current/future derives from complete LocalDate, never just a day index.
- Foreground midnight timer and provider work stop on ON_STOP; resume refreshes.
- Settings replaces the main composition; hidden days must not retain touch
  handlers or accessibility nodes. Preserve each screen's saveable state.

## Week layout and interactions

- Seven horizontal day rows, Monday to Sunday, each with two full-height,
  square-cornered inner containers. Only the parent row carries rounding:
  10dp normally; Saturday bottom corners square; Sunday top corners square.
- Left: two-letter uppercase day abbreviation at 12sp, immediately followed by
  day number, no dot/space/bold. Number is titleLarge minus 2sp.
  Measure the widest complete label of the displayed week; use that width in
  every row. Right side fills the rest.
- Left labels are right-aligned, vertically centered compact, top-aligned expanded.
- Exactly two expanded days: selected and next for Monday–Friday; Saturday and
  Sunday for either weekend selection. Preserve Sunday's selected identity.
- Compact rows occupy 6.5% each; expanded rows split the remainder (33.75% each).
- When safe height cannot support compact 48dp targets or expanded text at the
  user's font scale, use a vertically scrollable week with a sufficient virtual
  height. Preserve proportions, tap behavior and selection; ordinary scrolling
  replaces the magnification gesture in this accessibility fallback.
- Right containers show real selected-calendar event titles, clipped single-line,
  left-aligned and vertically centered. Expanded: up to nine rows; compact: one.
  Beyond nine events, show eight plus an All events button opening the full list.
  That list includes complete titles and times. Days have no long-press action.
- Entire day surface has Material interaction feedback and one merged semantic
  node with full English date including year, selected/expanded state and event count.
  All events keeps its touch action and is exposed as a custom accessibility action
  on that day node, without a second clickable accessibility node.
- Simple taps work on every day in either scroll mode. Only the child handles
  taps; the parent arbitrates drags. Crossing device touchSlop cancels taps even
  where a Discrete drag is not allowed.
- Tap group change: one shared linear withFrameNanos progression, exactly 500ms
  of elapsed frame time, independent of animator duration scale.
  Expanding content changes before animation; collapsing content stays expanded
  until completion. ViewModel selection feedback must not replay the tap.
- Discrete: drag can start only on an initially expanded day and waits for that
  touched row's halfway point, in addition to touchSlop.
- Linear: drag can start anywhere in the week, including peripheral strips;
  after touchSlop, it starts immediately from the current expanded group.
  Upward page-like motion advances groups; downward motion reverses them.
- Drag uses pixel distance and actual current weights, including an interrupted
  animation. No timed drag animation. Discard overscroll so reversal responds
  immediately. Include final up-event displacement. Release settles immediately
  to the nearest group; cancellation safely leaves drag state.
- In a settled layout, one group transition equals expanded minus compact height.
  The shared expanded row retains height; boundaries follow the pixel movement.
- Left/right visual strips have equal width: 22.5% of max(24dp, mandatory right
  gesture inset), outside the day rows and inside Scaffold's safe horizontal area.
- Bottom band is max(72% of raw mandatory gesture inset, navigation-bar bottom,
  safeDrawing bottom). Safety takes precedence over the requested decorative
  percentage: Sunday and all click targets stop above this band.
- One draw owner on the parent row handles rounded contours after children draw;
  reserve stroke thickness inside its bounds. Current day has one combined primary
  contour. Ordinary borders use Debug1, and seams have a single owner.
  No outer horizontal delimiter above Monday/below Sunday and no Saturday/Sunday
  ordinary horizontal seam. A current-day contour remains complete even on the
  weekend. Square inner containers have no independent outlines.

## Special-day rules

- isWEorBankH combines weekend and a date-specific bank-holiday marker.
  isHolidays is a separate vacation marker, never inferred from event title.
- Days have no long-press action or manual Vacation/Bank holiday editor.
  Weekends are automatically isWEorBankH. Do not invent bank holidays or vacations
  from a locale, region, or calendar name. Bank-holiday and school-holiday source
  events mark every overlapping allowed date, independently of event titles.
  Disabled sources contribute no titles or markers; weekends remain automatic.
  Old manual markers are no longer read for day appearance.
- Simulation is persisted, default Off. In every displayed simulated week:
  Wednesday is today; Monday/Saturday/Sunday are isWEorBankH; Monday–Thursday
  only are isHolidays. Real markers are ignored, and edits are disabled.
- Only isHolidays days show the 3dp square-ended vertical line inside the LEFT
  edge of the RIGHT inner container. Past: secondary; today/future: primary.
- Current day alone has a combined primary outline, recomputed by update.
- isWEorBankH: surface/onSurface, with right-side text in true font Italic
  (FontSynthesis.Style allowed; no extra geometric skew).
- Other past days: surfaceContainer/onSurfaceVariant.
- Other current/future days: explicit white/black product background, onSurface.
- These rules apply to both inner backgrounds. There are no weekend outer stripes.

## Calendar provider and persistence

- Three independent persisted roles: main events, bank holidays, school holidays.
  Each chooses an Android calendar, a local iCalendar document or an HTTPS feed.
  All are combined on the same week view. Only main events supply displayed titles;
  the two other roles supply their respective day flags. Each role has an enabled
  switch that preserves its configured source and last successful cache.
- Android + READ_CALENDAR: query CalendarContract.Instances for the
  displayed Monday–Sunday week on Dispatchers.IO, with one captured time zone.
  Preserve stable event/instance IDs, sort by begin/end/event ID, and split
  multi-day events correctly. All-day dates use UTC and exclusive end dates.
- Null cursor is a provider FAILURE, never a successful empty result. Preserve
  prior successful data on transient failure, and do not display one week's
  events in a different week or from a different selected calendar.
- Validate calendar existence independently of VISIBLE. A hidden calendar can
  stay selected; a removed/inaccessible calendar shows an explicit error and
  lets the user reselect, without silently deleting preferences.
- Superseded/debounced reads cancel through CancellationSignal, including cursor
  iteration. Reject stale responses by revision, calendar, Monday and zone.
- Permission denial/revocation updates hasCalendarPermission=false and cancels
  pending work. Do not swallow CancellationException.
- Observe Events and Calendars URIs with descendant notifications only while
  composition is started and permission/selection are valid. Invalidation calls
  update and reloads calendars/events. Unregister on disposal/stop/selection change.
  Retry transient registration failure with bounded backoff and surface an error.
- Keep selected numeric calendar ID in device-local calendar_device preferences,
  excluded from cloud backup/device transfer. Migrate existing same-device values.
  AppBackupAgent also removes legacy restored IDs after restoration: another
  installation must explicitly select its own calendars/documents. Provider IDs and
  document URI grants for all roles remain in calendar_device; AppBackupAgent clears
  the entire device-local preference file after restoration. Other settings are backed up.
- Android synchronization from the Internet belongs to another program populating
  the system provider. The app does not synchronize those Android calendars itself.
- iCalendar is read on explicit source choice or its own Refresh button only. Never
  reread a document or fetch HTTPS automatically at startup/resume/week navigation.
  Reparse the private successful snapshot as needed for year/zone changes. Show the
  last successful refresh timestamp per source, including while disabled. A failure
  preserves the last successful copy and timestamp and shows an explicit error.
- Local documents use OpenDocument with a persisted read grant. HTTPS accepts only
  HTTPS, including redirects. Import/download/parsing work runs on Dispatchers.IO
  and stops/cancels at ON_STOP. A document choice returned while stopped queues its
  initial import for resume; that is not a general startup refresh.
- Successful raw snapshots are atomically replaced in private files/ical_sources,
  excluded from cloud/device backup. Associate cached and displayed data with the
  exact source identity; never reuse one source's events for another.
- biweekly expands VEVENT recurrences/timezones, exceptions and cancellations in the
  two active years. Reject malformed/unsupported imports rather than accepting a
  partial successful calendar. Current explicit limits: 20 MiB input, 100,000 retained
  instances, 200,000 iterations per series, event duration <= 3660 days. RANGE
  recurrence overrides and RDATE period values are explicitly rejected. See ARCHITECTURE.md.

## Settings

- One Material settings screen, scrollable, with back action.
- Ordered sections: Calendar, Schedules, Theme, Accent color, Scroll mode,
  Simulation mode, Debug1, version/project link.
- Calendar: three role sections, each with its own switch and Android/file/HTTPS
  source controls. Android selection requests permission first, then a scrollable
  list with account/owner details. iCalendar requires no READ_CALENDAR permission.
  Source refresh errors/timestamps are independent; file Refresh never reloads the
  other file sources. The file picker and HTTPS dialog are saveable transient UI.
- Schedules: exactly five rows, Case 1–Case 5 and Material time selector;
  independently persisted minutes in 0..1439, or unset. Validate stored values.
  These are stored values only; no alarm or notification is scheduled.
  Use Material numeric time input in low windows and allow picker content to scroll.
- Theme segmented control: System (default), Light, Dark.
- Accent list is defined once by AccentTheme.entries: System (default), Royal blue,
  Indigo, Teal, Material violet, Plum, Raspberry, Mandarin, Emerald green, second
  Teal. Preserve this supplied current specification; the old conversation's larger
  Seed color list is not the current contract. Scroll the full list.
- Scroll segmented control: Discrete (default), Linear.
- Simulation segmented control: Off (default), Simulation.
- Debug1 segmented control: App bar background (default, surfaceContainer) and
  Black (onSurface, not a literal RGB black). No Debug2.
- Footer: SimplerCal v<official release version>, or --- for unofficial builds;
  clickable project link. Try ACTION_VIEW directly and report missing browser
  rather than using resolveActivity as a package-visibility preflight.

## Local validation, CI and installation

- `scripts/build-remote` is the project-specific single entrypoint after cloning:
  it runs check-local then delegates delivery to make-remote. It has the same
  explicit-delegation requirement as those scripts. Prerequisites and --resume
  semantics are documented in DEVELOPMENT.md.

- User-owned scripts/check-local runs shell parsing, toolchain discovery,
  functionalCheck, XML/YAML and project/script contract checks. Detekt enables
  only potential-bugs with type resolution; functionalCheck ALSO runs all release
  JVM unit tests, compiles the Android test sources and runs isolated Python script tests. KtLint, Android Lint, qualityCheck and ShellCheck stay optional.
- check-local must propagate every failed contract assertion, flush its tee log
  before exit, and reject changes made during the check.
- Freshness digest represents actual file paths, contents, executable modes and
  symlink targets, not staging/commit identity. Deletion must have the same digest
  before and after staging. Successful digest is checked by make-remote.
- Four reusable scripts remain byte-identical between scripts/ and the tracked
  skill/make_android_app/scripts snapshot.
- CI: workflow_dispatch only; functionalCheck, stable-signed arm64 release build,
  Compose/device tests on Android 14 and 16 x86_64, a minified smoke startup on
  both APIs, APK verification, SHA-256 and upload. All device/smoke checks must pass
  before release artifact publication. Always retain test reports and emulator
  diagnostics, including on failure, without signing material or personal data.
- The smoke build type is a disposable CI fixture with application ID suffix
  .smoke, x86_64 ABI filter, release shrinking and non-debuggable settings, and an
  explicitly assigned test signing key. It is never the delivered production APK.
  Production release signing remains mandatory with no debug fallback.
- Stable signing: user manually runs scripts/configure-signing once, approving
  creation/reuse of private ignored signing/ material and four repository secrets.
  Codex may run this setup and backup when explicitly requested by the user.
  The script verifies a private key/credentials backup outside Git before uploading
  secrets. Default backup root: ~/.local/share/simplercal/signing-backups/.
  A same-machine copy is not an off-device disaster-recovery backup.
  No debug signing fallback for release.
  Never log secret passwords or check signing material into Git.
- make-remote performs ADB preflight; missing/unauthorized device is reported.
  Installation is adb install -r, NEVER automatic uninstall. Signing mismatch
  stops with data-preservation guidance; any one-time debug-to-release migration
  needs explicit user action and backup.
- --resume requires clean source and matching successful run SHA; do not claim
  an old APK represents a changed worktree.
- Cleanup is separate and manual: artifact deletion and retention edits each
  require explicit confirmation; failed inventory/deletion/retention returns failure.
- Codex leaves all validation/signing setup/build/deployment operations above
  to the user, except operations explicitly delegated in the current request.
  Updating the tracked skill snapshot does not update the globally
  installed skill outside this repository.
