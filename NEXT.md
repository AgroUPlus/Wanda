# Where things stand

Written at the end of a long session so tomorrow does not start by re-deriving
what today already worked out. Branch:
`feat/recording-identity-and-expressive-pass`.

---

## The one idea behind most of it

Everything the user owns was keyed to a **source**. A like written against
`ytm:IGQH1FS89jE` left the Navidrome copy of the same song unliked; an artist
page keyed on a name could not tell `yuri` from `Yuri`.

The fix is that **identity travels with the data** instead of being re-derived
downstream. That is the thread running through the parsing fixes, the artist
route, and the recording work.

---

## Status

### Done, verified

- **Recording identity** — likes, artist pages, parsing. Shipped earlier.
- **`Split`** — pin two rows apart so they never merge. Verified on the Pixel against 3000 real
  rows: pin honoured by likes, unpinned groups still merge, undo works, library restored.
- **Play counts and history, per recording** — the last half of the recording model. See below for
  why it is not the migration this file used to describe.
- **Share chain** — custom domain → Agro server → backend, with speed/pitch on track links.
  `SHARE_LINKS.md` in the Agro repo is the normative spec; three implementations conform.
- **Licensing** — Agro is AGPL-3.0 with a LICENSE file; both repos have a CLA. `AGRO_PREMIUM.md`
  records the reasoning: one repo, sell hosting, do not split into open-core.
- **Security** — two XSS holes fixed (`share.rs`, `listen.rs`), an open redirect closed on
  frwd.top, Agro's database and its `-wal`/`-shm` chmod 0600, Wander's `config.toml` 0600.
- **Incognito** — account-wide and server-owned when Agro is paired, device-local otherwise.
- **Release notifications** — a daily WorkManager check, off by default, one notification per
  version.

### P2P: why every track "arrived corrupted"

Three independent faults, none of them corruption.

**1. The message was wrong, which hid the rest.** `MediaStoreWriter` returned `null` for *any*
failure and the caller called all of them "arrived corrupted". A transfer that delivered **zero
bytes** was reported as a damaged file. It now distinguishes `Empty`, `HashMismatch` and `Failed`,
and says which.

**2. `relay.rs` tore the session down on a five-second timer.** `receive_relay` spawned a task that
slept 5s, recorded the holding, and dropped the session — regardless of whether anything had
transferred. Dropping the session releases the last `Arc`, and with it the sender's half of the
channel, so a sender that had not connected within five seconds found its session gone and the
receiver's stream simply ended: **HTTP 200, no bytes**. The client hashed zero bytes, got a
mismatch, and said "corrupted". The same timer also recorded the receiver as *holding* a track it
never got. Cleanup now happens when the stream ends, and the holding is recorded only if bytes
actually moved.

**3. `send_relay` answered before reading the request body.** It spawned the pump and returned 200
immediately. HTTP does not promise the body survives a completed response — the connection may be
reused or closed. Now pumped inline, with `tx` dropped afterwards to signal completion.

**Where it actually stops, traced on the device (15:19):**

```
LAN P2P  192.168.1.141:8701     connect timeout after 3s
relay    POST /api/v1/relay/open   HTTP 200, session created
relay    GET  /relay/{id}/receive  hangs — no headers, ever
```

So the relay session opens fine and the receiver never gets a byte.

**The LAN leg: Tailscale is routing this machine's own LAN over the tailnet.**

```
$ ip route get 192.168.1.128
192.168.1.128 dev tailscale0 table 52 src 100.120.165.35
```

Some node advertises `192.168.1.0/24` as a subnet route, this machine runs with `RouteAll: true`
(`--accept-routes`), and the policy rule `5270: from all lookup 52` outranks
`32766: from all lookup main`. So packets to the local LAN leave through `tailscale0` carrying a
`100.x` source address.

Inbound connections arrive on `enp6s0` perfectly well — `INPUT` policy is `accept` and the only
`DROP` is for `100.64.0.0/10` arriving off-tailnet. It is the **replies** that go the wrong way, so
every peer sees a connect timeout. Host-to-phone `ping` appeared to work only because it detoured
through the tailnet in both directions.

Not a firewall, not AP isolation, not GrapheneOS — all three were wrong guesses along the way,
including two of mine about `ufw`.

```bash
sudo tailscale up --accept-routes=false
ip route get 192.168.1.128        # should now say: dev enp6s0
```

Better still, stop advertising `192.168.1.0/24` from whichever node does: a subnet route for a LAN
this machine is *already on* can only do harm here.

**The relay leg most likely dies at the reverse proxy.** `agro.kolbxyz.xyz` resolves to a public
address fronted by **openresty**, and the relay is a long-lived duplex stream — exactly what nginx
buffering breaks. Two directives, for the relay paths only:

The front end is **Nginx Proxy Manager**: edit the `agro.kolbxyz.xyz` proxy host → **Advanced** tab
→ Custom Nginx Configuration:

```nginx
location /api/v1/relay/ {
    proxy_pass http://192.168.1.16:1674;
    proxy_http_version 1.1;
    proxy_buffering off;          # or the receiver gets no headers until a buffer fills
    proxy_request_buffering off;  # or the sender's whole file is buffered before Agro sees it
    proxy_read_timeout 1h;
    proxy_send_timeout 1h;
    client_max_body_size 0;       # a relayed track is one request, not chunked like an upload
}
```

Unconfirmed — that host was not reachable from here. It is a hypothesis with good evidence, not a
finding. The sender is healthy: Wander holds two live connections to Agro (one is the WebSocket),
its `local_index.json` is populated, and it does implement `RELAY_REQUEST`.

**The fetch no longer hangs forever.** `AgroUploader` used `readTimeout(0)` — infinite — for the
relay, so a relay that delivered nothing showed "Syncing…" indefinitely, which is
indistinguishable from working. Relay calls now use their own client with a 45-second stall
timeout; uploads keep the infinite one, because a library upload legitimately takes hours.

**Untested end to end.** The relay fixes are verified by reasoning, a clean build and 201 passing
tests — there is no integration test that stands up a session and streams through it.

### Left to do

1. **Re-pair the phone with Agro.** The old client/server skew is resolved (the server now takes
   both `ipAddress` and `lanAddress`), but the earlier pairing attempts all aborted, so the device
   is unpaired. Scan the QR again.
2. **`TrackDeduplicator.deduplicate` is still not split-aware** — the display filter behind search
   and library lists. A pinned pair still collapses to one row *in a list*, while staying apart
   everywhere identity is written or counted. `distinctRecordings` now exists and is split-aware;
   wiring the list path to it is the remaining piece.
3. **Agro playlist sync** — designed, not started. See below.

### Not done, on purpose

**The row-deleting merge migration.** This file used to describe folding renditions together by
summing play counts onto one survivor and deleting the rest, and called it the half with no way
back. That shape was wrong, and it is why it kept being deferred:

- Deleting a rendition also deletes a source `RenditionFinder` can offer — including, potentially,
  the only copy that plays offline.
- It decides once, at migration time, and is wrong forever afterwards for anything pinned apart
  later.
- A wrong merge destroys a recording the user owns, silently.

**Totalling on the way out costs one grouping pass and has none of those failure modes.**
`RecordingPlayCounts` sums a recording's plays across its copies when the list is built, and
`TrackDeduplicator.distinctRecordings` collapses history the same way. Both honour `SplitSet` and
duration. Nothing is written, so there is nothing to undo — and the user-visible outcome is the one
the migration was for: a song held twice counts once, with the right number.

The merge preview stays as it is. It is still the place to audit what the matcher thinks, and it is
where pins are made and undone.

### 2. Agro playlist sync

Design approved, not started:
<https://claude.ai/code/artifact/f3921156-d603-445e-95c0-1e6e4778857f>

The hard part is already built three times over and must not be written a
fourth: `agro/src/norm.rs` (`recording_key`), `TrackDeduplicator.RecordingKey`,
`PlaylistImportRepository.findBestMatch`, `ListenAlongResolver`. Sync is a
transport, a schema, and one shared resolver — not a new matcher.

Two questions still open from the design:

- Does this replace `local_playlists`, or sit beside it?
- Should imported playlists sync automatically?

### 3. Loose ends

- **`Routes.artist` deep links** still fall back to name-only identity. Fine —
  nothing better exists there — but it is the one path that can still land on
  the wrong same-named artist.
- **One row survives each cleanup migration on purpose.** "Part Of Me" kept its
  bad credit because it has 2 plays; a livestream kept `album = "No views"`
  because the pattern needs a digit. Both deliberate: guarded so nothing liked,
  downloaded or played is deleted for tidiness.
- **`gh` is not installed**, so PRs have to be opened in the browser.

---

## Gotchas that cost real time today

**Concave shapes cannot cast shadows.** A `MaterialShapes` cookie outline on a
component with elevation sends Skia into `SkBaseShadowTessellator::
computeConcaveShadow` and wedges the render thread — a hard ANR, not a dropped
frame. The FAB is flat for this reason. Icon buttons are safe because they have
no elevation to begin with.

**InnerTube subtitles must never be read by position.** `Song • Artist • Album •
3:45` is not a fixed layout. Reading by position produced artists called "Song",
then "2023", then "Single" (75 albums under that one), and albums called
"15M views" (242 rows). Judge tokens by **shape**, never by vocabulary — the
labels arrive translated, because `hl` is the device language.

**Pages name their subject once, at the top.** Album pages and artist pages do
not repeat the artist on every row, and album rows carry no thumbnail. Anything
parsed off them must be stamped from the header, or it arrives credited to
nobody and coverless.

**Room folds artist case on purpose**, so one artist spelled differently by two
backends stays together. That is why a *name* is not an identity and why
`artistId` now travels in the route.

**Run the wrapper as `sh ./gradlew`.** Bare `./gradlew` got rewritten to the system Gradle
mid-session and failed with `Cannot find module 'gradle-public-api-legacy'` — the same breakage as
before, arriving by a different route. The wrapper itself is fine.

**The build has a Gradle wrapper now.** It previously used whatever Gradle the
system had; a package upgrade broke `gradle-public-api-legacy` mid-session. The
wrapper pins 9.5.0, which is what the build script and README already asked for.

**The device database is readable** — the app is debuggable, which is how the
"Unknown Artist" and "15M views" bugs were diagnosed from real data rather than
guessed at:

```bash
adb shell "run-as com.wander.android.debug cat databases/wanda_music.db" > w.db
sqlite3 w.db "SELECT source, COUNT(*) FROM tracks GROUP BY source;"
```

**The phone re-locks in about a minute**, which blocks screenshots. Verifying a
UI change end to end needs it kept awake.

---

## Deliberately not done

- **Play-count and history re-keying.** `Split` now exists to make it survivable;
  the number, not the idea, is what decides whether to run it.
- **Five `infiniteRepeatable(tween(…))` loops** (shimmer, live chip, pulsing mic,
  radio FAB, social tiles) still hold hand-picked periods. `MotionScheme` has no
  notion of a loop duration, so forcing them through it would be cargo cult.
  `QueueRadioButton`'s keyframes are the same case.
- **Microphone recognition matches your own library only.** No free OSS
  fingerprinter ships a catalogue of commercial music — every one of them
  matches against a database you supply. An unknown song returns nothing rather
  than a guess. The index builds under WorkManager while charging, so it is
  empty until then.
- **Thresholds are reasoned, not tuned.** `MIN_SCORE = 12`, `MIN_MARGIN = 1.6`,
  `THRESHOLD_DECAY = 0.08f` in the fingerprinter were never fitted against real
  recordings.

---

## PR Description Draft

- **Compare / Open PR**: [feat/recording-identity-and-expressive-pass](https://github.com/Kolbxyz/Wanda/compare/main...feat/recording-identity-and-expressive-pass?expand=1)
- **Title**: `Key music by recording identity, not by source id`

### Body:

Everything the user owns was keyed to a **source**. A like written against `ytm:IGQH1FS89jE` left the Navidrome copy of the same song unliked; an artist page keyed on a name could not tell `yuri` from `Yuri`. This makes identity travel with the data instead of being re-derived downstream.

Includes the playlist-importer work that landed in parallel.

#### Parsing — one bug in four places

`InnerTubeSubtitle` read fields by position, so whatever sat where an artist usually sits became the artist: first `"Song"`, then `"2023"`, then `"Single"` — one library had **75 albums** filed under an artist called *Single*. Tokens are now judged by **shape**, not vocabulary, because the labels arrive translated and cannot be matched by name.

The same flaw sat one column over in `album`, which rejected durations but not counts — **242 rows** filed under a record called *"15M views"*. The album test is deliberately looser in exactly one way: a year can be a record title (*1989*) even though it can never be an artist.

Pages name their subject **once, at the top**, and their rows do not repeat it. Album tracks and artist-page shelves are now credited from the header — **37 Katy Perry tracks** were filed under "Unknown Artist", every one of them found on her own page. Album rows carry no thumbnail either, which is why a song had a cover in search and none opened from its own record.

#### Artist identity

Two artists can share a name, and Room folds case *on purpose* so one artist spelled differently by two backends stays together. Deriving the id from whatever Room returned for a name therefore picked *an* artist, not *the* artist — and then filtered the page down to the wrong one. Identity now travels in the route from whoever tapped: a track carries `artistId`, a related-artist tile *is* one.

Artist pages also cache their identity, so a return visit renders immediately instead of paying for a cross-source search behind a skeleton.

#### Recording identity

`isSameRecording` and `groupRecordings` extract what "the same song" means out of the deduplicator, where it existed only as a display filter. `RenditionFinder` uses it to offer every source that has the playing track, **ordered offline-first** — a downloaded track keeps its original source and priority, so ranking by source alone would offer a stream above a file already on the phone.

**Likes are migrated.** A like now belongs to the recording rather than the copy you tapped: `toggleLike` moves every rendition together, and `unifySplitLikes` repairs the ones already split. Measured on a real library, 20 liked rows became 29 — matching the merge preview's prediction of 9 split likes exactly, and the preview now reports none.

Play counts and history are **deliberately not** migrated. That half means deleting rows and summing counts, and a wrong merge there silently hides a recording the user owns. The likes half only ever *adds* a like to another copy of something already liked, so it converges, removes nothing, and needs no way back.

`RecordingMergePreview` (Settings → About) is a dry run of the remaining migration. **It writes nothing.** Against a real library it folds 63 of 2838 rows — and it is what surfaced the album-name bug above.

#### Material 3 Expressive

65 button call sites take `ButtonDefaults.shapes()`; chips and both segmented rows became `ToggleButton` / `ButtonGroup`; 9 progress indicators became wavy; 50 hardcoded corner radii moved to the theme. Motion specs come from `MotionScheme`, including `NavTransitions`, which drove *every* screen transition from a hand-rolled spring.

One hard constraint is documented in the code, learned from a hard ANR: a concave `MaterialShapes` outline on anything that casts a shadow sends Skia into `computeConcaveShadow` and wedges the render thread.

#### Also

- **Recognise music from the microphone** against your own library — landmark fingerprinting, fully offline, no vendor, no account. An unknown song returns nothing rather than a guess. There is no free OSS service with a catalogue of commercial music; every open fingerprinter matches against a database you supply.
- **A Gradle wrapper.** The build depended on whatever Gradle the system happened to have, and a package upgrade broke it mid-session. The wrapper pins the version the README already documents.
- The mini player read the *metadata* duration rather than the player's, so tracks with no published length showed no wave at all.
- History moved out of the Library tab row into its header. Both tab rows take the expressive indicator — a pill under the label, not a bar under the column.
- `NEXT.md` records where this leaves off, what comes next, and the gotchas worth not rediscovering.

#### Testing

91 unit tests, all passing; `lintDebug` clean. Two are worth calling out because they were written to fail first and did:

- `RecordingGroupingTest` covers the cases that would ruin a library — a live take never merging into the studio cut, unknown durations never merging, same title by different artists staying apart.
- `FingerprinterTest` includes a concurrency test verified by temporarily restoring the data race it guards.

Migrations verified against a real device database: 37 → 1 "Unknown Artist" (the survivor deliberately kept for its play count), 0 mislabelled albums, 242 → 1 bad album names, 20 → 29 liked rows. Guarded throughout so nothing liked, downloaded or played is deleted for tidiness.

