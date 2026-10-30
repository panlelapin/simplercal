# SimplerCal — agent entrypoint

Before inspecting or changing this project, read [codex/README.md](codex/README.md)
and the complete [working contract](codex/CONTRACT.md). Those repository documents
are required instructions, not optional background.

- Preserve unrelated user changes.
- Unless explicitly delegated in the current user request, do not run validation,
  compilation/tests, signing setup, commit/push, CI dispatch, APK retrieval or installation.
- When delegated, use the tracked scripts. `scripts/build-remote` chains the local
  gate and delivery; never bypass a failed gate or assemble an APK locally.
- Never commit secrets, change repository visibility, uninstall the app, delete
  remote artifacts or change retention without the required explicit authorization.
- Distinguish implementation, passing automated checks, APK installation and manually
  verified UI behavior. Record evidence and remaining limitations in the project docs.

Read [architecture](codex/ARCHITECTURE.md), [development and delivery](codex/DEVELOPMENT.md)
and [validation and handoff](codex/VALIDATION.md) for the task at hand.
The product contract is codex/CONTRACT.md; PB1.md/PB2.md are historical audit records.
The tracked skill snapshot lives in skill/make_android_app/; it does not change the
agent's globally installed skill. The user's explicit instructions take precedence.
