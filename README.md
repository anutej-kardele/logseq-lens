# Logseq Pocket

Shows up on the phone as **Logseq** (with the Logseq mark, recreated from the official `logseq/logseq` icon; this is
a personal, sideloaded build and must not be published under that name or logo). Inside the app the header says
“Logseq Pocket”, to tell it apart from the official Logseq app.

A native, **read-only**, **offline-first** Android companion for the `Activity` Logseq (DB version) graph.
It reads the JSON export produced by the separate `logseq-backup` project (`data/mobile/activity.json`), stores it in
a local Room database, and lets you read journals and pages, browse tags and classes, search everything offline,
and study your existing `#Card` flashcards. It never edits, deletes or pushes anything to Logseq or GitHub.

Kotlin · Jetpack Compose · Material 3 · Room (FTS4) · Navigation Compose · kotlinx.serialization · WorkManager.
There is no backend, no Firebase, no analytics.

---

## What works (MVP)

| Area | Status |
|---|---|
| Manual import of `activity.json` (Storage Access Framework) | ✅ |
| Validation of `schemaVersion`, structure, UUIDs, dates, depth; atomic replace; failed imports keep the previous notes | ✅ |
| Home dashboard (graph, real counts, last import, recent journals, shortcuts) | ✅ |
| Journals (grouped by month, newest first, readable dates, hide empty days) | ✅ |
| Pages (alphabetical, text filter, kind filters, hide empty) | ✅ |
| Note reader: nested outline, collapse/expand, Markdown, code blocks, `[[refs]]`, `#tags`, `((block refs))`, backlinks, selectable text | ✅ |
| Tags & classes (counts, original names, similar-spelling hints, tag view) | ✅ |
| Offline full-text search (FTS4 + substring fallback for code), highlight, jump to block | ✅ |
| Flashcards: dashboard by tag, study mode (flip, prev/next, shuffle, Know / Review again, review-again pass, restart, summary) | ✅ |
| Local review progress that survives re-imports | ✅ |
| Settings: dataset info, re-import, clear notes / progress (confirmed), theme, reading size | ✅ |
| Private GitHub sync (paste-once read-only token, ETag, offline fallback) | ✅ implemented and tested against a local mock server; **not yet run against your real repo** (needs your token on the phone) |
| Optional background check (WorkManager, off by default) | ✅ (timing is up to Android) |
| **Scratchpad**: one local note mixing text and checkboxes, on a Home card | ✅ |
| Scratchpad home-screen widget (tick boxes in place, quick-capture sheet, expand to full note) | ✅ |
| Launcher shortcuts (long-press icon → Scratchpad / New to-do) | ✅ |

### Not in this version (future)
Bookmarks/reading history, search filters by tag/date, a calendar view, rich media attachments, biometric lock,
a spaced-repetition schedule (Know / Review again is implemented; Logseq's own FSRS state is shown read-only),
rendering Logseq live queries (deck/query blocks show the query and link to the tag instead).

---

## Requirements

- macOS with Android Studio (tested with 2026.2) and its SDK at `~/Library/Android/sdk`
- JDK 17+ for Gradle (Android Studio's bundled JBR works; the commands below use JBR 21)
- An Android 8.0+ device (minSdk 26). Developed against an API 35 emulator.

| Setting | Value | Why |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | Every Samsung phone in use; native `java.time`; above Room 2.8's minimum (23) |
| `targetSdk` | 36 | Current platform behaviour (edge-to-edge, predictive back) |
| `compileSdk` | 37 | Required by current AndroidX releases (core 1.19, Compose 1.12) |

Toolchain: AGP 9.4.1 (built-in Kotlin), Gradle 9.8.2 wrapper, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00,
Room 2.8.5, kotlinx.serialization 1.12.0, OkHttp 5.5.0, WorkManager 2.12.0. See `gradle/libs.versions.toml`.

## Build

```bash
cd ~/Code/Git/logseq-reader-android
```

`local.properties` (git-ignored) must point at the SDK. Android Studio creates it; on the command line:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

Then:

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home"
```

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` (application id
`com.anutej.logseqpocket.debug`, so it can sit next to a release build).

Instrumented smoke test (needs a running emulator or connected device):

```bash
./gradlew connectedDebugAndroidTest
```

### Release APK

Create a signing key once, outside the repository (never commit it):

```bash
keytool -genkeypair -v -keystore ~/keys/logseq-pocket.jks -alias pocket -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root (git-ignored):

```properties
storeFile=/Users/<you>/keys/logseq-pocket.jks
storePassword=…
keyAlias=pocket
keyPassword=…
```

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (minified with R8, ~2 MB). Without `keystore.properties`
Gradle produces `app-release-unsigned.apk`, which Android will not install. Keep the keystore: updates must be
signed with the same key.

## Install on a Samsung phone

**USB (recommended)**
1. Settings → About phone → Software information → tap **Build number** 7 times to enable Developer options.
2. Settings → Developer options → enable **USB debugging**; connect the phone and accept the prompt.
3. Install:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Without a cable:** copy the APK to the phone (e.g. Downloads), open it in *My Files*, and allow
*Install unknown apps* for My Files when prompted. Samsung may run an Auto Blocker check; allow the install or
temporarily disable Auto Blocker (Settings → Security and privacy).

## Use

1. On the Mac, run `logpush` as usual (this app does not change that pipeline).
2. **Manual import:** get `data/mobile/activity.json` onto the phone (USB, Quick Share, cloud drive…), then
   **Settings → Import JSON** (or **Import notes** on the first-run screen). Importing the same file again reports
   “Already up to date”.
3. **GitHub sync:** see below. Afterwards use the sync icon on Home or **Settings → Sync now**.
4. Everything you have imported works offline (verified on the emulator in airplane mode).

### Scratchpad (local only)

A single note for thoughts and to-dos. It's deliberately **not** part of Logseq: it is never synced to GitHub or
Logseq, and imports or “Clear local notes” never touch it.

- **Quick capture without opening the editor:**
  - Type straight into the field on the Home card.
  - Tap **Type something…** on the widget, or long-press the app icon → **New Scratchpad item**. A small sheet
    slides up over whatever you're doing; each Enter/Send adds a line (checkbox toggle on the left), and Done
    returns you to where you were.
- **Open the full note:** the expand icon (⤢) on the widget or Home card, the Home card itself, or long-press the
  app icon → **Scratchpad**.
- **Checkboxes:**
  - The **Checkbox** button toggles the current line between text and a checkbox. **New item** adds a checkbox.
  - Enter on a checkbox line starts another one; Enter on an empty checkbox line goes back to text.
  - Backspace at the start of a checkbox removes the box; at the start of a text line it joins with the line above.
- **Menu (⋮):** Clear checked items · Add widget to home screen · Share as text (Markdown `- [ ]` / `- [x]`, so it
  pastes cleanly into Logseq).
- **Widget:** add it from the Scratchpad menu or the launcher's widget picker (“Logseq → Scratchpad”, resizable).
  - Checkboxes can be ticked right on the widget. Android widgets can't host text fields, so **Type something…**
    opens the quick-capture sheet; the expand icon or tapping an existing text line opens the full editor.
  - App and widget stay in sync both ways.
- **Storage:** `scratchpad.json` in app storage (DataStore, atomic writes). Cloud backup is disabled for privacy,
  so **uninstalling the app deletes the Scratchpad**; use *Share as text* to keep a copy.

### GitHub sync setup (once)

Logseq Pocket reads exactly one file from the private repo, fixed in code:
`anutej-kardele/logseq-backup`, branch `master`, path `data/mobile/activity.json`.

1. On GitHub: **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. Resource owner: your account. Expiration: your choice (e.g. 90 days).
3. **Repository access → Only select repositories → `logseq-backup`.**
4. **Permissions → Repository permissions → Contents: Read-only.** (Metadata: Read-only is added automatically.)
   Grant nothing else.
5. Generate, copy the `github_pat_…` token, and paste it once into the app (Home sync icon or Settings → Add token).

How the token is handled:
- It is encrypted with an AES-256-GCM key held in the Android Keystore; only ciphertext is stored, and app data is
  excluded from Google/Samsung cloud backup and device transfer.
- It's never logged or shown again. **Replace token** and **Remove token** are in Settings. Revoke it on GitHub
  when no longer needed.
- Requests: one authenticated `GET /repos/anutej-kardele/logseq-backup/contents/data/mobile/activity.json?ref=master`
  with `Accept: application/vnd.github.raw+json` and `If-None-Match`. A `304` costs nothing and skips the rebuild.
  No write endpoints, no clone, no `raw.githubusercontent.com`, no HTTP cache.
- Expired or revoked tokens (401), missing access (403/404), rate limits, malformed downloads and offline
  errors all leave the local notes untouched and show a message.

---

## How the export is interpreted

The app was built against the real export format (schemaVersion 1, `source: logseq-cli-edn-build`, Logseq DB
schema 65.22) and verified locally against the real `activity.json`. All 31 pages, 156 blocks and 21 flashcards
(16 leetcode, 5 algo-pattern) were read with no warnings and no text lost. The real file is never committed or
bundled.

- **Pages**: `kind` is `journal` (with `journalDay` `YYYYMMDD`) or `tag` (user classes). Journal dates fall back
  to an ISO title. Unknown kinds are shown as-is.
- **Blocks** are recursive `children`. They are flattened depth-first into Room (`depth`, `parentUuid`,
  `siblingIndex`, `preorder`), so a whole page loads in one ordered query and renders as a lazy list.
  Siblings are sorted by `order` when every sibling has one, otherwise array order is kept.
- **Tags** are metadata objects `{entityId, uuid, name}` (bare strings are also accepted). Built-in classes such as
  `Card`/`Cards` have no page in the export, so tags are also collected from block metadata.
- **References**: the CLI export leaves `references` empty, so `[[Page]]`, `[[uuid]]`, `#tag`, `#[[uuid]]` and
  `((uuid))` are parsed from the text. Missing targets are shown muted, never invented.
- **Timestamps** are null throughout the CLI export, so “Recently updated” is hidden. It appears automatically if
  a future export includes them.
- **Unknown fields** are ignored; an unsupported `schemaVersion` is rejected with a clear message and the old data
  is kept.

### Flashcards

Logseq DB marks a card by tagging a block with the built-in `Card` class. The block's content is the **question**;
its nested child blocks (the whole subtree, in order, including code) are the **answer**. A card with no children
shows “No answer in the export” and nothing is generated. `{{cloze …}}` is supported (masked on the front).

Categories come from tag intersections: every other tag on a card block becomes a deck (`Card + leetcode`,
`Card + algo-pattern`, and any future tag). A card with two category tags is in both decks. Cards with only `Card`
go to **Other**. Deck titles come from your Logseq `#Cards` query blocks (`(tags [[…]])`) when present, otherwise
the tag name. Tags are not inherited from parent blocks, matching Logseq's `(tags …)` query.

**Card identity across re-imports.** CLI-derived block UUIDs encode the tree position (see `logseq-backup`
`docs/EDN_EXPORT.md`), so inserting a block above a card changes its UUID. Progress is therefore keyed by
`sha256(page key + normalised question text)`:
- Identical questions on one page get `#2`, `#3`… suffixes, so distinct cards are never merged.
- If a key disappears but a new card has the **same UUID and identical answer**, the progress moves over (you edited
  the question in place).
- Progress for removed cards is kept as an orphan (it re-attaches if the card returns) and can be pruned in Settings.
- Limitation: editing a question **and** moving it in the same export resets that card's progress (the safe
  failure mode).

Review progress lives in separate Room tables that imports never clear, and is never sent anywhere.

### Possible exporter enhancement (not done — needs your separate approval)

If `@logseq/cli`'s Build EDN export can include block timestamps and original UUIDs, `edn_to_json.py` could pass
them through and also fill `references`. That is backward-compatible (same keys, populated values). The app
already uses them when present: “Recently updated” would appear, and card identity could use real UUIDs. Nothing
is required for flashcards today.

---

## Architecture

Single `:app` module, MVVM + repositories, manual dependency injection (`AppContainer`).

```
data/json      DTOs, streaming parser (SHA-256, size and nesting limits), ImportError
data/importer  GraphMapper (validate + flatten + tags/refs/cards), ImportService (one transaction), ImportCoordinator
data/db        Room entities, DAOs, FTS4 index, PocketDatabase (schema JSON in app/schemas)
data/repo      Notes, Search, Study, Settings (DataStore) repositories
data/sync      GitHubSync (OkHttp), KeystoreTokenStore, SyncWorker
data/scratchpad ScratchpadStore (DataStore JSON file, separate from the Room database)
widget         Glance home-screen widget + tick action
domain         Markdown/Logseq inline parser, RefIndex, PropertyFormatter, CardIdentity, StudySession, PadEditor
ui             Compose screens (home, journals, pages, tags, reader, search, cards, settings, sync), theme tokens
```

Security and privacy: the only permissions are `INTERNET` and `ACCESS_NETWORK_STATE`, used solely for sync.
Traffic is HTTPS-only with system CAs only. `allowBackup=false` plus data-extraction rules keep data out of cloud
backup. Logs contain counts only, never note text or tokens. The emoji2 font-provider lookup is disabled. HTML in
notes is shown as text and never executed, and only `http(s)` links open.

## Tests

- 97 JVM tests (`./gradlew testDebugUnitTest`), including Scratchpad editing rules and storage (persistence,
  concurrent widget/editor writes, corrupt-file fallback), plus: parser, validation, unsupported schema, hierarchy and order,
  journal dates, tags/refs, Markdown, flashcard classification and answers, card identity, Room import and
  rollback, FTS search, offline persistence (reopen from disk), progress preservation, study sessions, and GitHub
  sync (MockWebServer: 200/304/401/403/404/429/500, truncated body, network down, no token).
- 1 instrumented Compose smoke test (`connectedDebugAndroidTest`): import → journals → reader → study → search.
- All test data is the synthetic fixture `app/src/test/resources/fixtures/synthetic-activity.json`.

## Repository hygiene

`local-data/`, `local.properties`, APK/AAB files, keystores and `keystore.properties` are git-ignored. Nothing in
this repository talks to or modifies `~/Code/Git/logseq-backup` or the live graph.
