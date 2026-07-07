# Obsidian Git MD

An Android app for reading and syncing an [Obsidian](https://obsidian.md) vault of Markdown
notes through a GitHub repository. Built with Kotlin Multiplatform, Jetpack Compose
(Compose Multiplatform), and coroutines.

## Problem

Obsidian keeps your notes as plain Markdown files, and Git is a natural way to sync a vault
across devices. But putting that on a phone runs into friction:

- **Storage bloat.** Naively cloning a repo keeps the full history on the device; for a
  long-lived vault (especially with binary attachments) that grows without bound.
- **No mobile-friendly sync.** Doing `git pull/commit/push` by hand on a phone is painful,
  and merge conflicts in notes need a clear, non-destructive resolution.
- **Authentication.** Pasting a personal access token by hand is clumsy and error-prone.

## Solution

A focused Android client that treats a GitHub repo as the vault backend:

- **Read Markdown.** Browse the `.md` files in the vault and render a selected note.
- **Git sync engine (JGit).** Clone / pull / commit / push a single branch. Conflict policy:
  non-`.md` files take the server version; `.md` conflicts prompt you to keep the **local** or
  **server** version (no silent data loss).
- **GitHub login (OAuth Device Flow).** Sign in by entering a short code in the browser — no
  manual token. The access token is stored encrypted (`EncryptedSharedPreferences`).

Planned next: repository settings screen, background auto-sync (WorkManager), in-app editing
and search, and an AI mode (OpenRouter) for searching/adding/editing notes.

## How to start

**Prerequisites**
- JDK 17
- Android SDK (with an emulator or a device)
- A [registered GitHub OAuth app](https://github.com/settings/developers) (Device Flow
  enabled) to obtain a `client_id`

**Configure `local.properties`** (in the repo root — it is git-ignored, keep secrets here):

```properties
sdk.dir=/absolute/path/to/Android/sdk
github.clientId=<your GitHub OAuth app client id>
sync.remoteUrl=https://github.com/<user>/<vault-repo>.git
```

**Build and run**

```bash
./gradlew :composeApp:assembleDebug          # build the debug APK
./gradlew :composeApp:testDebugUnitTest      # run the unit tests
```

Install the APK from `composeApp/build/outputs/apk/debug/` on an emulator or device (or run
from Android Studio). On first launch, sign in via GitHub, then press **Синхронизировать** to
clone the vault and browse your notes.
