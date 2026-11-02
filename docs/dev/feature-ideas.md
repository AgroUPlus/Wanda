# Feature Ideas for Wanda and Agro

Ranked by value for effort, grounded in what the code already has: audio embeddings, jams, drops, a
zero-knowledge vault, and a player that mixes four sources. Effort is a rough guess; none of these has
been scoped in detail. Each app's top pick is marked.

## Wanda

1. **Loudness normalisation across sources (top pick).** Local files, Navidrome, YouTube Music and
   Deezer play at very different volumes. Read ReplayGain tags where they exist and fall back to a
   gain estimate for the rest, so a mixed queue stays even. A small change in the player and an
   obvious improvement. *Status: mostly built. Phase 1 (file tags: local, downloads, Navidrome, plus
   OpenSubsonic's reported gain) is in `core/playback/LoudnessNormaliser.kt`. Phase 2 adds YouTube
   Music: its `/player` `loudnessDb` becomes a ReplayGain 2.0 gain (`InnerTubeLoudness.kt`, handed
   over through `LoudnessHints`) and is used after tags. Still open: Deezer (its track `gain` is
   undocumented and needs calibrating against tagged copies), and an integrated-loudness estimate for
   untagged local files — the feature pass stores only a clamped RMS energy, so this needs a new
   column (a schema change) and a K-weighted, gated measurement.*
2. **"Sonic radio" from any track.** Wanda already computes audio embeddings and Agro has acoustic
   similarity. A button that starts an endless mix of tracks that sound like the current one is mostly
   wiring. *Status: built into track radio. `SonicNeighbours` pulls the library's nearest tracks by
   embedding centroid into every radio started from a track (`SmartRadioGenerator`); there is no
   separate "sonic only" button.*
3. **Smart offline cache.** Pre-download the tracks you are likely to play next, based on listening
   history, on Wi-Fi and while charging only. Fits the battery-first rule. *Status: built.
   `core/cache/predict/OfflinePredictor` ranks tracks by recency-weighted plays, time-of-day and
   weekday fit, and recent library adds; `DownloadWorker` uses it to fill spare batch room behind
   "Also download predicted songs", within the storage limit. Skips are not recorded in history, so
   skip rate is not a signal yet.*
4. **Android Auto and Wear OS controls.** A large gain in reach for a music player, but more work than
   the others. *Status: not started. `PlaybackService` is a plain `MediaSessionService`: a watch can
   mirror its notification through the system, but Android Auto lists only apps with a
   `MediaLibraryService` and a browse tree, and there is no Wear app.*

## Agro

1. **Passkeys (WebAuthn) login (top pick).** Replace passphrase plus TOTP with something
   phishing-proof. It suits the zero-knowledge and security design, but the vault's key wrapping needs
   careful thought: a passkey must not become a way to bypass the vault.
2. **ListenBrainz and Last.fm history import.** Backfilling years of listening makes stats, charts and
   recaps useful from day one.
3. **Scheduled encrypted backups with a restore check.** Automatically verify that the latest backup
   actually restores, and show the result on the dashboard.
4. **Opt-in aggregate metrics endpoint.** A Prometheus-style endpoint for operators only, with no
   per-user data, so the zero-telemetry rule still holds.

## Next step

Write a short plan for each top pick (loudness normalisation, passkeys) before building: what changes,
what could break, and how it would be tested.
