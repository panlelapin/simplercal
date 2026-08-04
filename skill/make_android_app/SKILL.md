---
name: make-android-app
description: Bootstrap and develop simple Kotlin Android applications with one launcher MainActivity, minSdk 34 (Android 14), non-cosmetic local and CI validation, one manually dispatched GitHub Actions release build, and verified APK delivery. Use when Codex must initialize or modify a small Android app, establish or verify its GitHub origin, create the first activity-based app and remotely compiled APK, or continue app development without assembling Android APKs locally.
---

# Make Android App

Build a conventional, activity-based Kotlin Android application from the bundled
starter. Keep one launcher `MainActivity`, use GitHub Actions as the only APK build
environment, and keep local quality checks identical to CI.

## Bootstrap contract

Before changing a new repository, collect:

- a lowercase reverse-DNS application ID, such as `com.example.simpleapp`;
- a non-empty user-facing application name;
- confirmation that the app should use the single `MainActivity` starter;
- only when strict ABI restriction matters, whether the user accepts a pure-Kotlin APK
  being ABI-neutral. `abiFilters` alone cannot make an APK arm64-only without native
  `.so` libraries.

Inspect the checkout and `AGENTS.md` first. Verify or establish the GitHub repository
with `scripts/check-github-stuff` before copying project files. Present one complete
bootstrap confirmation, including the application ID/name, development signing, local
quality check, remote build, checksum verification, and APK download. After acceptance,
do not ask again for routine steps.

Use `assets/starter-project-base` for an empty project and copy exactly
`assets/starter-app-with-activity/src/main` into `app/src/main`. Do not copy widget
receivers, Glance dependencies, app-widget metadata, or widget preview resources. Replace
`com.example.app` in Kotlin, Gradle, workflow, and scripts; move Kotlin files into the
matching package directory; set `app_name` and the activity text from the user's name.
The starter must expose exactly one launcher activity named `MainActivity`.

## Quality and permissions

Run `scripts/check-local` after changes to Kotlin, resources, manifests, or Gradle files,
and before `scripts/make-remote`. If source was created or changed because of `AGENTS.md`,
ask before launching that check. Never use a baseline, lenient dependency verification,
`ignoreFailures`, broad suppressions, or local APK assembly to turn failures into success.
`functionalCheck` is the local/CI Kotlin gate. It runs only Detekt's `potential-bugs`
rules with type resolution; all convention-oriented Detekt rule sets are disabled.

Keep KtLint, Android Lint, the optional `qualityCheck` task, and the ShellCheck helper
available for explicit manual use, but do not invoke them from `scripts/check-local`,
`scripts/make-remote`, or GitHub Actions. Cosmetic formatting, naming, complexity, and
style findings must not block the normal local or remote workflow.

Always invoke the tracked `scripts/check-local` and `scripts/make-remote` entry points
directly when their responsibilities are needed. Do not manually reproduce any operation
that belongs inside them, including their Git staging/commit/push flow, GitHub Actions
dispatch/polling, artifact download/checksum flow, APK inspection, or ADB installation.
Use separate commands only for read-only diagnosis, and keep the scripts as the source of
truth for the complete local and remote procedures.

Make the `check-local` worktree attestation depend only on current file paths, contents,
executable modes, and symbolic-link targets. Never include `HEAD`, index state, or commit
identity in that digest: `make-remote` must accept the same validated content after it has
staged or committed it, including when resuming after a partial remote-build failure.

Keep `gradle/verification-metadata.xml`, the Gradle wrapper, the functional Detekt
configuration, minSdk 34, targetSdk 36, release shrinking, and non-debuggable development
signing. Local validation may compile Kotlin for type resolution; it must not assemble,
sign, or claim an APK.

## Window and Material layout defaults

Activities must not be immersive or full-screen by default: never hide the Android status
bar or navigation bar. The status bar must remain visible, and the first app content must
start at the normal Material spacing below the system bar rather than being glued to the
top edge. On Android 15+ edge-to-edge may be enforced by the target SDK, so handle
`WindowInsets`/Compose insets correctly instead of assuming that disabling edge-to-edge
is possible. Keep tappable app-bar content out of system-bar overlap.

Use Material 3 layout primitives where applicable, especially `Scaffold` with a
`TopAppBar`/`CenterAlignedTopAppBar` and the scaffold content padding. Keep the title
between the navigation icon and right-side actions, preserve standard touch targets, and
use monochrome icons with meaningful content descriptions. Do not add custom top padding
that duplicates the app bar's system-bar insets.

## Design references

For every design question, read `references/design-sources.md` first. Treat it as the
offline design reference for this skill, then consult the linked official sources when
the question depends on current guidance or a resource update:

- Android Mobile design guidance: `https://developer.android.com/design/ui/mobile`
- Official Android UI kit: `https://goo.gle/android-ui-kit`
- Official Android Design Figma community: `https://www.figma.com/@androiddesign`
- Material Design 3: `https://m3.material.io/`

The Figma links are official online design resources, not self-contained local HTML
archives. Do not claim that a `.fig` file is available offline unless it has actually
been exported and stored in the skill resources. Use the local reference for offline
work and the canonical links for current component specifications, kits, and updates.

## Remote build workflow

Copy `scripts/check-github-stuff`, `scripts/check-local`, `scripts/make-remote`, and
`scripts/verify-apk` unchanged except for documented application configuration, preserve
executable bits, and copy `references/github-actions-template.yml` to
`.github/workflows/android-app.yml`. The workflow must use only `workflow_dispatch`, run
`functionalCheck`, then `:app:assembleRelease`, inspect one APK with `verify-apk`, and upload
the APK plus its SHA-256 checksum.

Delegate every commit, push, dispatch, poll, artifact download, checksum verification,
and optional ADB installation to `scripts/make-remote --bootstrap`; never reproduce that
sequence ad hoc. A successful remote build proves CI compilation, APK metadata/signature,
and artifact integrity. ADB verification proves installation of the exact artifact, not
activity rendering or complete runtime behavior.

At the beginning of `make-remote`, run the ADB preflight. Use `ADB_SERIAL` when supplied;
otherwise inspect `adb devices` and accept only authorized entries in `device` state. Print
the selected accessible device before continuing. If ADB is unavailable, no authorized
device is connected, or several devices are present without a selection, say so clearly;
continue the remote build but skip or defer device installation as appropriate. After the
APK is downloaded, install it on the selected device and verify package, version metadata,
and the pulled APK SHA-256 against the remote artifact. Never report device validation when
the preflight or the post-install hash check did not succeed.

## Keep this skill synchronized

When this skill or one of its resources changes, copy the complete skill directory into
the active repository at `skill/make_android_app/` before validation. Repository
`scripts/check-local` must enforce that snapshot and byte-for-byte equality of reusable
scripts. Keep scripts product-neutral and infer the package from `app/build.gradle.kts`.

## Feature growth

Ask feature questions only after the first APK gate succeeds. Add JVM tests when business
logic appears, persistence only when state is required, permissions only when justified,
and device tests when runtime behavior matters. Keep the one-activity starter minimal and
prefer AndroidX/Compose dependencies already pinned by the base project.

## Final evidence

Before reporting completion, run `scripts/check-local`, validate XML and workflow
contracts, and run the generic `skill-creator/scripts/quick_validate.py` validator with
this skill directory as its argument. The validator belongs to `skill-creator`, not to
this skill's `scripts/` directory. Review the final file list, and report remote
compilation or device validation only when those exact checks succeeded.
