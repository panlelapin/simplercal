---
name: make-android-app
description: Bootstrap and develop simple Kotlin Android applications with one launcher MainActivity, minSdk 34 (Android 14), strict local and CI Kotlin quality gates, one manually dispatched GitHub Actions release build, and verified APK delivery. Use when Codex must initialize or modify a small Android app, establish or verify its GitHub origin, create the first activity-based app and remotely compiled APK, or continue app development without assembling APKs locally.
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
`qualityCheck` is the single local/CI gate: KtLint, `:app:detektRelease`, and
`:app:lintRelease` with warnings treated as errors.

Keep `gradle/verification-metadata.xml`, the Gradle wrapper, strict Detekt/KtLint
configuration, minSdk 34, targetSdk 36, release shrinking, and non-debuggable
development signing. Local quality may compile Kotlin for type resolution; it must not
assemble, sign, or claim an APK.

## Remote build workflow

Copy `scripts/check-github-stuff`, `scripts/check-local`, `scripts/make-remote`, and
`scripts/verify-apk` unchanged except for documented application configuration, preserve
executable bits, and copy `references/github-actions-template.yml` to
`.github/workflows/android-app.yml`. The workflow must use only `workflow_dispatch`, run
`qualityCheck`, then `:app:assembleRelease`, inspect one APK with `verify-apk`, and upload
the APK plus its SHA-256 checksum.

Delegate every commit, push, dispatch, poll, artifact download, checksum verification,
and optional ADB installation to `scripts/make-remote --bootstrap`; never reproduce that
sequence ad hoc. A successful remote build proves CI compilation, APK metadata/signature,
and artifact integrity. ADB verification proves installation of the exact artifact, not
activity rendering or complete runtime behavior.

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
contracts, run the skill `quick_validate.py`, review the final file list, and report
remote compilation or device validation only when those exact checks succeeded.
