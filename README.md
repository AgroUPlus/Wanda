<p align="center">
  <img src="docs/wanda-logo.png" width="96" height="96" alt="Wanda logo" />
</p>

<h1 align="center">Wanda</h1>

<p align="center">
  A clean, battery-first, sovereign Android music player built under EUPL-1.2.
</p>

<p align="center">
  <a href="https://github.com/AgroUPlus/Wanda/actions/workflows/ci.yml"><img src="https://github.com/AgroUPlus/Wanda/actions/workflows/ci.yml/badge.svg" alt="CI Status"/></a>
  <a href="https://github.com/AgroUPlus/Wanda/releases"><img src="https://img.shields.io/github/v/release/AgroUPlus/Wanda?include_prereleases&label=release" alt="Latest Release"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-EUPL--1.2-blue" alt="License"/></a>
  <img src="https://img.shields.io/badge/made%20in-%F0%9F%87%AB%F0%9F%87%B7%20France-002395" alt="Made in France"/>
  <a href="https://sonarcloud.io/summary/new_code?id=AgroUPlus_Wanda"><img src="https://sonarcloud.io/api/project_badges/measure?project=AgroUPlus_Wanda&metric=alert_status" alt="Quality Gate"/></a>
</p>

<p align="center">
  <a href="https://github.com/AgroUPlus/Agro">Agro Server</a> ·
  <a href="https://github.com/AgroUPlus/Wander">Wander Desktop</a> ·
  <a href="SECURITY.md">Security</a> ·
  <a href="CONTRIBUTING.md">Contributing</a> ·
  <a href="CODE_OF_CONDUCT.md">Code of Conduct</a>
</p>

---

Wanda unifies **Navidrome / Subsonic**, **local device files**, **YouTube Music**, and **Deezer** into a single cohesive library, queue, and playback engine. 

Designed for digital sovereignty and hardened operating systems like GrapheneOS: zero telemetry, zero analytics, Keystore-backed secrets, application-layer HTTPS enforcement, and battery-first background audio.

Seamlessly pairs with [Agro](https://github.com/AgroUPlus/Agro) for E2EE listen-along sessions, Jam rooms, off-grid local mesh playback, and real-time handoff with [Wander](https://github.com/AgroUPlus/Wander) on desktop.

<p align="center">
  <img width="1600" height="1000" alt="Wanda Interface" src="https://github.com/user-attachments/assets/9f226e44-bf8b-4800-a497-593527fc0ade" />
</p>

---

## Welcome to Wanda! (FAQ for Newcomers)

<details>
<summary><b>I already use a major streaming app. Why should I use Wanda?</b></summary>
<br>
Because your music shouldn't be trapped in silos. Wanda is a premium Android player that lets you seamlessly mix your local files, your private Navidrome/Subsonic server, Deezer, and YouTube Music into <em>one single library</em>. Plus, it has zero tracking, true offline support, and social features that don't rely on a corporate server.
</details>

<details>
<summary><b>How does it actually combine those sources? Do I just flip between a "YouTube tab" and a "Local tab"?</b></summary>
<br>
No, and this is where Wanda gets really smart. Wanda actually analyzes the audio on your device. If you have a lossless FLAC of a song on your home server, and that same song exists on YouTube Music, Wanda’s engine figures out they are the exact same recording. It links them together invisibly, so you only see <em>one</em> beautiful, unified track in your library.
</details>

<details>
<summary><b>I care a lot about audio quality. If it merges tracks, what version does it actually play?</b></summary>
<br>
Wanda always prioritizes the highest <b>Quality</b> available with the lowest latency. If you hit play, and Wanda knows you have the lossless local file or a high-res stream on your private server, it plays that directly to the hardware decoder. It only falls back to a compressed stream if you don't have the high-quality version. 
</details>

<details>
<summary><b>I keep seeing "Agro" mentioned everywhere. What is that? Do I need to host a server?</b></summary>
<br>
You don't need a server! <a href="https://github.com/AgroUPlus/Agro">Agro</a> is our custom peer-to-peer (P2P) ecosystem built right into the app. Instead of relying on a centralized cloud, Agro connects your devices directly to each other. It’s what powers our syncing and makes our social features possible entirely off-the-grid.
</details>

<details>
<summary><b>Wait, social features? Can I listen with my friends?</b></summary>
<br>
Yes! Through Agro, Wanda has a native <b>Friends System</b> and <b>Jam Rooms</b>. You can see what your friends are listening to, share your presence, and collaboratively build a queue in real-time. 
</details>

<details>
<summary><b>Is it safe to share my listening activity with friends over P2P?</b></summary>
<br>
Incredibly safe. Agro provides <b>End-to-End Encryption (E2EE)</b> for absolutely everything. It also uses a <b>Privacy Relay</b> system to mask network metadata. This means your communication, your social graph, and your listening habits are completely untraceable—even to your ISP.
</details>

<details>
<summary><b>A lot of open-source music players look a bit... dated. How is the design?</b></summary>
<br>
We take design just as seriously as the engineering. Wanda is built with <b>Material 3 Expressive</b>. It features a true edge-to-edge layout, dynamic theming, and bespoke, fluid motion (like the bouncy, tactile animations when you open your queue). It is designed to feel incredibly premium and native to Android. 
</details>

<details>
<summary><b>What do you mean by "Offline-First" and "Battery-First"?</b></summary>
<br>
Most apps freeze or show spinners when your signal drops. Wanda doesn't. Everything you see in the app is loaded instantly from a highly optimized local database on your phone. When you connect to the internet, Wanda quietly updates the database in the background. This makes the app blindingly fast, completely usable in airplane mode, and incredibly gentle on your battery.
</details>

<details>
<summary><b>Is it tracking me? What is the privacy policy?</b></summary>
<br>
Our threat mitigation philosophy is simple: <b>Zero Telemetry and Zero Trust.</b> We do not track you. We strictly enforce application-layer HTTPS, and we never store your backend passwords in plaintext. Even looking up lyrics requires your explicit consent. Your data belongs entirely to you.
</details>

<br>

---

## Unified Sources

Every backend implements a single interface (`IMusicSource`) declaring explicit capabilities (`SourceCapabilities`). The UI adapts dynamically to what each source supports instead of failing or faking missing endpoints:

| Source | Search | Albums | Artists | Playlists | Likes | Scrobble | Radio | Lossless |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Navidrome / Subsonic** | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| **On This Device** | Yes | Yes | Yes | Local | N/A | N/A | Yes | Yes |
| **YouTube Music** | Yes | Yes | Yes | Yes | Yes | N/A | Yes | N/A |
| **Deezer** | Yes | Yes | Yes | Yes | Yes | N/A | Yes | Yes |

* **Navidrome / Subsonic**: Full Subsonic API support, salted token authentication (passwords never touch the network), remote playlists, star ratings, scrobbling, and similar-artist radio.
* **Local Files**: MediaStore and SAF indexing persisted directly in Room with incremental `DATE_MODIFIED` watermarks.
* **YouTube Music**: Direct InnerTube integration with Opus (itag 251) stream extraction, artist discovery, and personalized mixes. Works completely signed out or with optional in-app sign-in.
* **Deezer**: High-fidelity FLAC and 320kbps MP3 streaming with on-the-fly decryption, artist catalogs, search, and personal library integration.

---

## Key Capabilities

* **Agro E2EE Listen-Along & Jam Rooms**: Host or join synchronized listening sessions with friends over local Wi-Fi or secure Agro relays. Encrypted end-to-end, with zero tracking of your listening history.
* **Off-Grid Local Mesh**: Discover nearby listeners and trade audio fingerprints peer-to-peer over local network transports without Internet connectivity.
* **Synced Lyrics**: Native source lyrics prioritized first (Navidrome structured formats), followed by optional, user-consented lookups via LRCLIB. Time-synced lines highlight smoothly and allow tap-to-seek.
* **Acoustic Fingerprinting**: Embedded Chromaprint/fpcalc engine tracks audio signatures for cross-backend deduplication and seamless library matching.
* **Smart Mixes & Infinite Radio**: Endless Radio, Forgotten Favorites, and Deep Cuts computed locally on-device from your listening graph.
* **Incognito Mode**: Instantly halts play history logging, scrobbles, and cache retention at the source level.
* **Material 3 Expressive UI**: Fluid spring animations, edge-to-edge window insets, predictive back gestures, cover-art dynamic color tinting, and pure OLED black theming.

<p align="center">
  <img width="1600" height="1000" alt="Wanda Details" src="https://github.com/user-attachments/assets/9d40cd8e-4cb6-4e2a-a7ee-5ca7a8be6c39" />
</p>

---

## Battery & Privacy Discipline

### Battery First
* **Zero Polling Loops**: Playback updates stream via native event callbacks; progress indicators tick only while audio is playing and the screen is actively visible.
* **Direct Audio Offload**: Hardware DSP audio offload is enabled by default so the main application processor can sleep during playback.
* **Smart Wake Locks**: Network wake locks are held exclusively during remote stream buffering, and completely released during local playback.
* **Constrained Sync**: Background cache downloads run through WorkManager under strict unmetered network, battery-not-low, and charging conditions.

### Hardened Privacy
1. **Zero Telemetry**: No Firebase, no Google Play Services, no Crashlytics, no tracking SDKs, and no analytics of any kind.
2. **Keystore-Backed Secrets**: Authentication tokens, vault encryption keys, and credentials live in encrypted storage backed by the hardware Android Keystore (AES-256-GCM). Nothing sensitive is stored in plain text, database rows, or logs.
3. **Application-Layer HTTPS**: Network calls reject cleartext HTTP on wide-area networks; non-HTTPS connections are permitted only on private subnets, loopback, or `.local` domains.
4. **Complete Session Purge**: Signing out clears all session cookies, WebStorage caches, and cryptographic tokens immediately.
5. **Backup Resistant**: Data extraction and cloud backups are blocked (`allowBackup=false`).

---

## Architectural Principles

The codebase enforces strict, automated engineering constraints documented in [AGENTS.md](AGENTS.md):

* **300 Lines Max Per File**: Target under 250 lines per file with a single clear responsibility per component.
* **Room as Single Source of Truth**: Remote API responses are stored in Room and observed as reactive Kotlin Flows.
* **Media3 Playback Ownership**: `PlaybackService` exclusively controls `ExoPlayer`; UI components interact through `PlayerConnection` and `MediaController`.
* **Explicit Window Insets**: Fully manual edge-to-edge container padding for safe navigation, IME keyboards, and system gestures.

```
app/src/main/java/com/wander/android/
├── core/
│   ├── audio/        Chromaprint acoustic fingerprinting
│   ├── cache/        Media cache and WorkManager download engine
│   ├── database/     Room entities, DAOs, and migrations
│   ├── network/      Shared OkHttp and Ktor engines with HTTPS guards
│   ├── p2p/          Off-grid local link management
│   ├── playback/     Media3 PlaybackService, queue management, and audio offload
│   └── security/     Android Keystore, AgroVault, and SecureStorage
├── data/
│   ├── model/        Unified audio domain models
│   ├── repository/   Room-backed reactive repositories
│   └── sources/      Navidrome, Local, YouTube Music, Deezer, Agro
└── ui/
    ├── components/   M3 Expressive player sheet, mini-player, and bars
    ├── navigation/   Deep-link routing and Compose destination graphs
    ├── screens/      Home, Library, Search, Social, Jam, Settings
    └── theme/        Material 3 Expressive typography, tokens, and palettes
```

---

## Installation & Updates

* **Obtainium**: Add `AgroUPlus/Wanda` to [Obtainium](https://github.com/ImranR98/Obtainium) for direct, automated updates straight from GitHub releases.
* **GitHub Releases**: Download pre-built, signed APKs from the [Releases](https://github.com/AgroUPlus/Wanda/releases) tab.
* **In-App Update Check**: Navigate to Settings > About > Check for update to verify against GitHub release tags. Wanda never installs code silently in the background.

---

## Building from Source

Requires **JDK 17** and **Android SDK 37** (compileSdk 37, minSdk 26).

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :app:testDebugUnitTest

# Install to connected device
./gradlew :app:installDebug
```

Release builds are signed using properties specified in `local.properties` (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`).

---

## Governance & Code of Conduct

Participation and contributions are governed by our [Meritocratic Code of Conduct](CODE_OF_CONDUCT.md). Collaboration focuses purely on technical excellence, empirical quality, battery performance, and software maintainability.

All contributions require agreement to our [Contributor License Agreement](CLA.md). Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull requests.

---

## License

* **Source Code**: Licensed under the **EUPL-1.2 (European Union Public Licence v1.2)**, ensuring reciprocal software freedom under European civil law.
* **Compiled Binaries**: Because compiled binaries link `zemer-cipher` (GPL-3.0) for YouTube cipher deobfuscation, the binary distribution is conveyed under the **GNU General Public License v3.0 (GPL-3.0)**, as authorized by EUPL-1.2 Article 5.
