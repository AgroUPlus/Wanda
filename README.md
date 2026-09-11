<p align="center">
  <img src="docs/wanda-logo.png" width="96" height="96" alt="Wanda logo" />
</p>

<h1 align="center">Wanda</h1>

<p align="center">
  A clean, open-source Android music client — built by the community under AGPL-3.0
</p>

<p align="center">
  <a href="https://sonarcloud.io/summary/new_code?id=AgroUPlus_Wanda"><img src="https://sonarcloud.io/api/project_badges/measure?project=AgroUPlus_Wanda&metric=security_rating" alt="Security Rating"/></a>
  <a href="https://sonarcloud.io/summary/new_code?id=AgroUPlus_Wanda"><img src="https://sonarcloud.io/api/project_badges/measure?project=AgroUPlus_Wanda&metric=reliability_rating" alt="Reliability Rating"/></a>
  <a href="https://sonarcloud.io/summary/new_code?id=AgroUPlus_Wanda"><img src="https://sonarcloud.io/api/project_badges/measure?project=AgroUPlus_Wanda&metric=alert_status" alt="Quality Gate"/></a>
</p>

---

Wanda unifies **Navidrome**, **local files**, **YouTube Music**, and the **Internet Archive** behind a single library, queue, and player. Material 3 Expressive throughout. Battery-first, privacy-first, no telemetry. Pairs with [Agro](https://github.com/AgroUPlus/Agro) for playback handoff, listen-along, and cross-device sync.

<p align="center">
  <img width="1600" height="1000" alt="image" src="https://github.com/user-attachments/assets/9f226e44-bf8b-4800-a497-593527fc0ade" />
</p>

---

## Sources

Each backend implements one interface (`IMusicSource`) and declares what it supports (`SourceCapabilities`) — the UI hides actions a source doesn't offer rather than silently failing.

| Source | Search | Albums | Playlists | Likes | Scrobble | Radio | Lyrics |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Navidrome / Subsonic | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| On this device | ✅ | ✅ | — | — | — | ✅ | — |
| YouTube Music | ✅ | ✅ | ✅ | ✅ | — | ✅ | — |
| Internet Archive | ✅ | ✅ | ✅ | — | — | ✅ | — |

- **Navidrome** — Subsonic 1.16, salted-token auth (password never crosses the wire), starring, scrobbling, similar-songs radio, server-synced lyrics.
- **Local files** — MediaStore scan persisted in Room, incremental via `DATE_MODIFIED` watermark.
- **YouTube Music** — InnerTube. Sign in via in-app WebView or cookie paste; search and playback work signed out. Direct Opus (itag 251) streams.
- **Internet Archive** — anonymous. Prefers lossless (FLAC → m4a → ogg → opus → mp3). Collections: All Audio, Live Music, Netlabels, 78rpm.

---

## Features

**Lyrics** — source-native first (Navidrome structured lyrics), then LRCLIB. Synced lines highlight as you listen and are tappable to seek.

**Smart mixes** — Endless Radio, Forgotten Favourites, Never Played, and Internet Archive Gems, built from your own listening history. A mix with no tracks isn't shown.

**Incognito mode** — stops play counts and scrobbles at the source level.

**Offline mode** — restricts playback to what's already on the device.

**Auto-update check** — Settings → About → Check for update compares against the latest GitHub release. The app never downloads or installs anything on its own.

---

## Battery & Privacy

**Battery**
- No polling — playback state arrives via `Player.Listener`; position ticks only while playing *and* the screen is visible.
- Audio offload on by default — DSP plays while CPU sleeps.
- Wake lock is `NETWORK` during streaming, unset for local files.
- Downloads via WorkManager with unmetered + charging + battery-not-low constraints.

**Privacy**
1. No trackers — no Firebase, no Play Services, no Crashlytics, no analytics.
2. Keystore-backed secrets — every credential in `EncryptedSharedPreferences` (AES-256-GCM); never in Room, logs, or backups.
3. No cleartext — `usesCleartextTraffic=false`; HTTP-only servers need an explicit per-domain exception.
4. No backup — `allowBackup=false` plus explicit data-extraction rules.

---

## Architecture

Single `:app` module, package root `com.wander.android`, Hilt for DI.

```
core/
  playback/   PlaybackService owns the ExoPlayer; PlayerConnection is the UI's MediaController
  cache/      SimpleCache + WorkManager downloader
  database/   Room — the offline source of truth
  network/    Ktor over a shared OkHttp client
  security/   SecureStorage (Android Keystore)
  permissions/
data/
  model/      UnifiedTrack, UnifiedAlbum, SmartMix, LyricsData
  sources/    navidrome · local · ytmusic · archive
  repository/ MusicRepository, LyricsRepository, SmartMixRepository
di/           One Hilt module per concern
ui/
  theme/      MaterialExpressiveTheme, Monet dynamic colour, true-black OLED
  navigation/ Four tabs + Now Playing, Queue, login routes
  components/ Artwork, TrackRow, MiniPlayer, EmptyState, SourceFilterChips
  screens/    home · library · search · settings · player · queue · login
```

Conventions are in [CLAUDE.md](CLAUDE.md): 300-line file cap, no speculative fallbacks, no dead code, Room as source of truth, Media3 as owner of playback state.

---

## Building

Requires **JDK 17** and **Android SDK 37** (compileSdk 37, minSdk 26, AGP 9, Gradle 9.5).

```bash
./gradlew :app:assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:installDebug         # install on connected device
```

Set `org.gradle.java.home` in `gradle.properties` if your JDK isn't on `PATH`.

**Release signing** — configured via `local.properties` (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`). Without those keys the release build is left unsigned. A GitHub Actions workflow (`.github/workflows/release-apk.yml`) builds and attaches a signed APK on every GitHub Release using the same four values stored as repository secrets.

---

## Licence

**AGPL-3.0.** This project links `zemer-cipher` (GPL-3.0); GPLv3 §13 explicitly permits combining GPLv3 with an AGPLv3 work, which is why Wanda can be licensed AGPL-3.0 rather than GPL-3.0.

Contributions require agreement to [`CLA.md`](CLA.md) — see [`CONTRIBUTING.md`](CONTRIBUTING.md).
