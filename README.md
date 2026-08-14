# Wanda 🎵

One Android music player for **Navidrome**, **local files**, **YouTube Music** and the
**Internet Archive** — a single library, a single queue, a single player.
Material 3 Expressive throughout. Battery-first, privacy-first, no telemetry.

Inspired by the multi-source aggregation of Symfonium and the visualizer soul of
[Wander](https://github.com/Kolbxyz/Wander), the Linux Rust TUI music player this is the Android sibling of.

---

## Features

**Sources.** Each backend implements one interface (`IMusicSource`) and declares what it can
actually do (`SourceCapabilities`); the UI hides actions a source does not support rather than
offering ones that quietly do nothing.

| Source | Search | Albums | Playlists | Likes | Scrobble | Radio | Lyrics |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Navidrome / Subsonic | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| On this device | ✅ | ✅ | — | — | — | ✅ | — |
| YouTube Music | ✅ | ✅ | ✅ | ✅ | — | ✅ | — |
| Internet Archive | ✅ | ✅ | ✅ | — | — | ✅ | — |

- **Navidrome** — Subsonic 1.16 client, salted-token auth (the password never crosses the wire),
  starring, scrobbling, similar-songs radio and server-side synced lyrics. Credentials are
  validated with a `ping` before they are stored.
- **On this device** — MediaStore scan persisted into Room, incremental after the first run via a
  `DATE_MODIFIED` watermark.
- **YouTube Music** — InnerTube. Sign in through an in-app WebView (or paste a cookie); search and
  playback work signed out. Direct Opus (itag 251) streams.
- **Internet Archive** — anonymous. Search expands items into their actual songs, preferring
  lossless (FLAC → m4a → ogg → opus → mp3). Collections: All Audio, Live Music, Netlabels, 78rpm.

**Lyrics.** Source-native first (Navidrome's structured lyrics), then LRCLIB. Synced lines
highlight as you listen and are tappable to seek.

**Visualizers.** Five Compose Canvas renderers fed by a real PCM tap on the audio pipeline —
Aurora Ribbon, Embers, Bloom Rings, Oscilloscope, Spectrogram Waterfall. Off by default, because
a visualizer requires decoded PCM and therefore disables audio offload (see below).

**Smart mixes.** Endless Radio, Forgotten Favourites, Never Played, and Internet Archive Gems,
built from your own listening history. A mix with no tracks is not shown.

---

## Battery

- **No polling.** Playback state arrives via `Player.Listener`; the position ticks only while
  something is playing *and* the screen is showing it (`repeatOnLifecycle`).
- **Audio offload** is on by default, so the DSP plays while the CPU sleeps. Enabling a
  visualizer turns it off for the duration, because offloaded audio never reaches the processor
  chain — that trade-off is explicit, not hidden.
- Wake mode is `NETWORK` while streaming and unset for local files.
- Downloads run under WorkManager with unmetered + charging + battery-not-low constraints.
- FFT work stops the moment Now Playing leaves the screen.

## Privacy and security

1. **No trackers.** No Firebase, no Play Services, no Crashlytics, no analytics of any kind.
2. **Keystore-backed secrets.** Every credential lives in `EncryptedSharedPreferences`
   (AES-256-GCM) and nowhere else — never in Room, never in logs, never in a backup.
3. **No cleartext.** `usesCleartextTraffic=false`; a self-hosted HTTP-only server needs a
   deliberate per-domain exception.
4. **No backup, no transfer.** `allowBackup=false` plus explicit data-extraction rules.
5. **Incognito mode** stops play counts and scrobbles at the source.
6. **Offline mode** restricts playback to what is already on the device.

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

Conventions are documented in [CLAUDE.md](CLAUDE.md) — 300-line file cap, no speculative
fallbacks, no dead code, Room as the source of truth, Media3 as the owner of playback state.

---

## Building

Requires **JDK 17** and **Android SDK 37** (compileSdk 37, minSdk 26, AGP 9, Gradle 9.5).
Set `org.gradle.java.home` in `gradle.properties` if your JDK is not on `PATH`.

```bash
./gradlew :app:assembleDebug        # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:installDebug         # install on a connected device
```

Release builds are signed from `local.properties` (`releaseStoreFile`, `releaseStorePassword`,
`releaseKeyAlias`, `releaseKeyPassword`). Without those keys the release build is left unsigned
rather than falling back to the debug key.

## License

MIT
