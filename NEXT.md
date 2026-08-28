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

## Next, in order

### 1. Build `Split` before finishing the recording migration

The remaining half of the recording model folds **play counts and history** onto
a shared recording row. That means deleting rows and summing counts, and it is
the one step with no way back: a wrong merge silently hides a recording the user
owns, with nothing on screen to say so.

`Split` — pinning a recording's renditions permanently apart — is cheap now and
impossible to retrofit once a year of history has merged onto the wrong row.
`ArtistEntity`-style `pinned_apart` column is already sketched in the design.

**Check the numbers first.** Settings → About → **Merge preview** is a dry run
that writes nothing. Last measured, with Navidrome connected:

```
track rows        : 2838
recordings after  : 2775
rows folded away  : 63
likes split today : 9   → now 0, repaired
```

Only 63 rows fold. If that is still small, the migration is not urgent — its
value grows as more Navidrome/local music arrives. Decide on the number, not on
the idea.

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

- **Play-count and history re-keying.** See `Split`, above.
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

