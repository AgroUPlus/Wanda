# Contributing to Wanda

## Licence

Wanda is licensed **AGPL-3.0** (see `LICENSE`). That applies to the whole work, including any
contribution merged into it.

The AGPL's section 13 is the one worth reading before you build anything on top: if you run a
modified Wanda where other people can reach it over a network, those people must be offered its
source. This is deliberate, and it is why the project can be given away without also giving away
the ability to run it as a service.

## Contributor License Agreement

Every contribution requires agreement to [`CLA.md`](CLA.md). Add this line to your commit
messages, or state it once in the pull request description:

```
Contribution-License: I agree to the CLA at CLA.md
```

You keep the copyright in your work. The CLA grants the right to **sublicense**, which is what
keeps the project's licence changeable in future — that possibility ends permanently at the first
contribution merged without one, which is why this is asked for up front rather than later.

## Before you open a pull request

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

`CLAUDE.md` holds the rules of the road — the 300-line file cap, `IMusicSource` as the only source
abstraction, Room as the offline source of truth, no speculative fallbacks. Read it first; it is
shorter than this paragraph implies.

## `TrackDeduplicator` has a second implementation

Agro's `src/norm.rs` is a port of it, and the two must agree exactly — a shared library index
built on two different normalisations produces nonsense diffs. Change both or neither.
