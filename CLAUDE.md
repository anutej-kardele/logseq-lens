# CLAUDE.md — Logseq Pocket conventions

Native Android, read-only, offline-first reader for the `Activity` Logseq graph export (`activity.json` from the
separate `logseq-backup` repo). See README.md for features, setup and the data-model notes.

## Hard rules
- **Read-only.** Never add code that writes to Logseq, GitHub, or any remote. GitHub access is a single GET of one
  fixed file (`data/sync/GitHubSync.kt`). Never request write scopes.
- **Never touch** `~/Code/Git/logseq-backup` or `~/logseq/graphs/Activity`. Don't change the exporter from here.
- **Private data.** `local-data/activity.json` is real notes: never commit it, copy it into resources/assets, upload
  it, or print its content. Committed tests use only `app/src/test/resources/fixtures/synthetic-activity.json`.
  A local check against the real file may print counts only and must not be committed.
- **No secrets in source/APK.** The GitHub token is user-entered and Keystore-encrypted (`KeystoreTokenStore`).
- **Logging:** counts and exception class names only — never note text, titles, tokens or URLs with tokens.
- No analytics, crash SDKs, Firebase, or new network services. Justify any new dependency (maintained, needed).
- Imports must stay atomic: parse + validate + map fully in memory, then replace content in one Room transaction.
  A failure must leave the previous dataset intact. `review_progress` / `review_sessions` are never cleared by imports.
- Preserve note text exactly. The Markdown renderer must not drop or rewrite characters; unknown syntax and HTML
  render literally. Only `http(s)` links may open.

## Build & test
```
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home"
./gradlew testDebugUnitTest assembleDebug        # JVM + Robolectric tests, debug APK
./gradlew connectedDebugAndroidTest              # needs emulator (AVD Medium_Phone_API_35) or device
./gradlew assembleRelease                        # R8; signed only if keystore.properties exists
```
Never claim a build/test passed without running it. Robolectric needs the `--add-exports/--add-opens` JVM args
already configured in `app/build.gradle.kts`.

## Code conventions
- Kotlin, Compose + Material 3, MVVM. ViewModels expose `StateFlow`; create them with `pocketViewModel { … }`
  (manual DI via `AppContainer`, no Hilt).
- Packages: `data/json` (wire DTOs, all fields optional), `data/importer` (validation/mapping), `data/db` (Room),
  `data/repo`, `data/sync`, `domain` (pure Kotlin, unit-testable), `ui/<feature>`.
- Pure logic goes in `domain` with JVM tests; DB behaviour gets Robolectric tests with an in-memory Room DB.
- Colors/shapes/typography come from `ui/theme` tokens (`PocketTheme.colors` for extras). Support light and dark.
- Room schema changes: bump `PocketDatabase.version`, add an explicit `Migration` (never destructive — progress
  tables must survive), and commit the exported schema JSON in `app/schemas/`.
- Export format changes: add the new `schemaVersion` to `GraphMapper.SUPPORTED_SCHEMA_VERSIONS` only after handling
  it; keep DTO fields optional.
- Flashcard identity lives in `domain/cards/CardIdentity.kt`; changing it orphans existing progress — avoid.

## Commits
Small commits per verified milestone; don't push without the owner's approval.
