# Feature Ideas for Wanda and Agro

Ranked by value for effort, grounded in what the code already has: audio embeddings, jams, drops, a
zero-knowledge vault, and a player that mixes four sources. Effort is a rough guess; none of these has
been scoped in detail. Each app's top pick is marked.

## Wanda

1. **Loudness normalisation across sources (top pick).** Local files, Navidrome, YouTube Music and
   Deezer play at very different volumes. Read ReplayGain tags where they exist and fall back to a
   gain estimate for the rest, so a mixed queue stays even. A small change in the player and an
   obvious improvement.
2. **"Sonic radio" from any track.** Wanda already computes audio embeddings and Agro has acoustic
   similarity. A button that starts an endless mix of tracks that sound like the current one is mostly
   wiring.
3. **Smart offline cache.** Pre-download the tracks you are likely to play next, based on listening
   history, on Wi-Fi and while charging only. Fits the battery-first rule.
4. **Android Auto and Wear OS controls.** A large gain in reach for a music player, but more work than
   the others.

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
