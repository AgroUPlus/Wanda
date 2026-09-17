# Security

Wanda is a privacy-first Android music client. It stores credentials for remote services (Navidrome,
Agro, YouTube cookies) and caches media locally. This document outlines our threat model and
security posture.

## Reporting a vulnerability

Email **contact@kolbxyz.xyz** with `SECURITY` in the subject. Please include steps to reproduce, the
expected behavior, and the observed outcome. There is no monetary bounty, but valid reports receive
a prompt response and attribution in release notes.

Please do not disclose security issues through public GitHub issues.

## Supported versions

| Version | Supported |
|---|---|
| Latest release / `main` | :white_check_mark: |
| Older releases | :x: |

## The Threat Model & Architecture

Wanda operates under the following security guarantees:

1. **Android Keystore-backed Secrets**  
   Every credential (Navidrome salt, Agro device tokens, YouTube cookies) is managed via `SecureStorage`
   backed by the Android Keystore using AES-256-GCM encryption in `EncryptedSharedPreferences`. Credentials
   are never stored in plain text, never written to Room databases, and never emitted in application logs.

2. **No Cleartext Traffic**  
   `android:usesCleartextTraffic="false"` is enforced globally in `AndroidManifest.xml` and validated
   by Android Network Security Config. Connections to backends require HTTPS. Unencrypted HTTP is permitted
   only for local development (`10.0.2.2`, `127.0.0.1`) or through an explicit per-domain user exception.

3. **Backup Protection**  
   `android:allowBackup="false"` is set with explicit data-extraction rules to prevent extraction of
   sensitive cache or session data via ADB backups.

4. **Zero Telemetry and Zero Trackers**  
   Wanda includes no Google Play Services dependencies, no Firebase, no crash reporting SDKs, and no
   analytics services. Network requests are made solely to backends configured by the user (Navidrome,
   Agro, YouTube, Internet Archive, LRCLIB).

5. **Incognito Mode**  
   Activating Incognito mode immediately stops scrobbling, play-count updates, and history persistence
   at the repository and source level.

6. **End-to-End Encryption for P2P & Handoff**  
   Realtime drops, shared sessions, and P2P handoff use authenticated encryption (ChaCha20-Poly1305 / X25519),
   ensuring intermediary network relays cannot decrypt audio streams or private notes.
