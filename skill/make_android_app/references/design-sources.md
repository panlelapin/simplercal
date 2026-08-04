# Offline design reference

This file is the offline index and compact working reference for Android design questions.
Read it before using web sources.

## Canonical official sources

- Android Mobile UI design: https://developer.android.com/design/ui/mobile
- Android UI kit: https://goo.gle/android-ui-kit
  - Current public Figma file: https://www.figma.com/community/file/1478523627015571873
- Android Design Figma community: https://www.figma.com/@androiddesign
- Material Design 3: https://m3.material.io/
- Material 3 theme builder: https://m3.material.io/theme-builder/

## Working rules

- Use Android Mobile as the entry point for Android-specific foundations, system bars,
  layouts, adaptive behavior, settings, accessibility, and quality guidance.
- Use Material 3 for component anatomy, states, tokens, typography, color, shape, motion,
  navigation, and canonical layouts.
- Prefer the official Android UI kit and the M3 Figma kit for visual specifications when
  a design file is available locally or supplied by the user.
- Treat the Android Design Figma community as a collection of official templates and kits,
  not as one immutable offline document.
- Keep system bars visible by default, honor safe areas and insets, and use standard
  Material app-bar spacing and touch targets.
- Prefer adaptive layouts based on compact, medium, and expanded window sizes. Do not lock
  a phone app to portrait without a product reason.
- Use dp for dimensions and sp for text. Favor 4/8 dp-aligned spacing where applicable,
  while respecting the component's own Material specifications.
- Keep labels concise, use meaningful icon content descriptions, and provide accessible
  contrast and dynamic light/dark theme behavior.

## Offline limitation

The Android UI kit, Android Design community, and M3 design kit are Figma resources. Their
official `.fig` binaries are not embedded here because Figma serves them through its own
download flow and may require the user's authenticated Figma session. If a user exports a
kit, store it beside this reference and update this section with its exact local path and
export date; until then, use this file offline and the canonical links online.
