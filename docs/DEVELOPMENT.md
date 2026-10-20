# Logseq Lens — development notes

Technical details for building, testing and understanding the app. For installing and using it, see the
[README](../README.md).

## Stack

Kotlin · Jetpack Compose · Material 3 · Room (FTS4) · Navigation Compose · kotlinx.serialization · DataStore ·
Glance (widget) · WorkManager · OkHttp. No backend, no Firebase, no analytics.

| Setting | Value | Why |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | Covers practically all phones in use; native `java.time` |
| `targetSdk` | 36 | Current platform behaviour (edge-to-edge, predictive back) |
| `compileSdk` | 37 | Required by current AndroidX releases |

Toolchain: AGP 9.4.1 (built-in Kotlin), Gradle 9.8.2 wrapper, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00,
Room 2.8.5. All versions are in `gradle/libs.versions.toml`.

## Build

Requirements: Android Studio (or the Android SDK) and JDK 17+.

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # git-ignored; Android Studio creates it too
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/logseq-lens-v<version>-debug.apk`. Its application id is
`com.anutej.logseqpocket.debug`, so it installs next to a release build.

Instrumented smoke test (needs an emulator or a connected device):

```bash
./gradlew connectedDebugAndroidTest
```

### Release APK

Create a signing key once and keep it **outside** the repository. Every future update must be signed with the same
key, so back it up.

```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/logseq-lens.jks -alias logseq-lens -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root (git-ignored):

```properties
storeFile=/Users/<you>/keys/logseq-lens.jks
storePassword=…
keyAlias=logseq-lens
keyPassword=…
```

```bash
./gradlew assembleRelease
```

The output is `app/build/outputs/apk/release/logseq-lens-v<version>-release.apk`, minified with R8 (about 3 MB).
Without `keystore.properties` you get an unsigned APK that Android won't install. The version is set by
`APP_VERSION` in `app/build.gradle.kts`; bump `versionCode` for every release.

## Architecture

Single `:app` module, MVVM + repositories, manual dependency injection (`AppContainer`).

```
data/json        DTOs, streaming parser (SHA-256, size and nesting limits), ImportError
data/importer    GraphMapper (validate + flatten + tags/refs/cards), ImportService (one transaction), ImportCoordinator
data/db          Room entities, DAOs, FTS4 index, PocketDatabase (schema JSON in app/schemas)
data/repo        Notes, Search, Study, Settings (DataStore) repositories
data/sync        GitHubSource (config + link parser), GitHubSync (OkHttp, GET only), KeystoreTokenStore, SyncWorker
data/scratchpad  ScratchpadStore (DataStore JSON file, separate from the Room database)
widget           Glance home-screen widget + tick action
domain           Markdown/Logseq inline parser, RefIndex, PropertyFormatter, CardIdentity, StudySession, PadEditor
ui               Compose screens (home, journals, pages, tags, reader, search, cards, settings, sync, scratchpad)
```

**Imports are atomic.** The whole file is parsed, validated and mapped in memory, then replaced in a single Room
transaction. Any failure (malformed JSON, unsupported `schemaVersion`, network error) leaves the previous notes
untouched. Review progress and the Scratchpad live outside the content tables and are never cleared by an import.

## How the export is interpreted

Input: the `schemaVersion 1` JSON written by the export pipeline (`data/mobile/activity.json`).

- **Pages**: `kind` is `journal` (with `journalDay` `YYYYMMDD`) or `tag` (user classes). Journal dates fall back
  to an ISO title.
- **Blocks** are recursive `children`. They are flattened depth-first (`depth`, `parentUuid`, `siblingIndex`,
  `preorder`), so a page loads in one ordered query and renders as a lazy list.
- **Tags** are `{entityId, uuid, name}` objects (bare strings are accepted too). Built-in classes like `Card` /
  `Cards` have no page, so tags are also collected from block metadata.
- **References**: `[[Page]]`, `[[uuid]]`, `#tag`, `#[[uuid]]` and `((uuid))` are parsed from the text. Missing
  targets are shown muted, never invented.
- **Timestamps**: "Recently updated" appears only if the export includes them.
- **Unknown fields** are ignored. An unsupported `schemaVersion` is rejected with a clear message.

### Flashcards

A block tagged with Logseq's built-in `Card` class is a card. Its content is the **question** and its nested child
blocks are the **answer**. Every other tag on the card becomes a deck (for example `leetcode`, `algo-pattern`); cards
with only `Card` go to **Other**. Deck titles come from `#Cards` query blocks (`(tags [[…]])`) when present.

Card identity across re-imports: block UUIDs from the CLI export encode tree position, so progress is keyed by
`sha256(page key + normalised question)`. Duplicates on one page get `#2`, `#3`… An edited question keeps its
progress when the block UUID and the answer are unchanged. Progress for removed cards is kept as an orphan and
re-attaches if the card returns.

## GitHub access

All requests are `GET` with the user's fine-grained token (Contents: Read-only):

| Purpose | Endpoint |
|---|---|
| Download the export | `/repos/{o}/{r}/contents/{path}?ref={branch}` (raw media type, `If-None-Match`; `304` skips the rebuild) |
| Default branch | `/repos/{o}/{r}` |
| Branch dropdown | `/repos/{o}/{r}/branches` |
| Graph switcher | `/repos/{o}/{r}/contents/{folder}` |
| Find exports from a repo-only link | `/repos/{o}/{r}/git/trees/{branch}?recursive=1` |

`manifest.json`, `config*.json`, `*.example.json` and similar files are never offered as graphs
(`isGraphExportCandidate`). A new source becomes active only after its file downloads and validates.

## Security & privacy

- Read-only: no write endpoints, no clone, no HTTP cache.
- The token is encrypted with an AES-256-GCM key in the Android Keystore and never shown or logged.
- The only permissions are `INTERNET` and `ACCESS_NETWORK_STATE`. Traffic is HTTPS-only and trusts system CAs only.
- `allowBackup=false` plus data-extraction rules keep notes out of cloud backup.
- Logs contain counts only. HTML in notes is shown as text, and only `http(s)` links open.

## Tests

- JVM + Robolectric (`./gradlew testDebugUnitTest`) covering:
  - parser, validation and schema checks; hierarchy and order; dates; tags and refs; Markdown
  - flashcards and card identity; Room import and rollback; FTS search; offline persistence
  - study sessions; Scratchpad editing and storage
  - GitHub sync against MockWebServer (status codes, ETags, discovery, source switching)
- One instrumented Compose smoke test (`connectedDebugAndroidTest`).
- Test data is synthetic only: `app/src/test/resources/fixtures/synthetic-activity.json` and
  `docs/demo/demo-graph.json`.
