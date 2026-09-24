package com.wander.android.data.importer

/**
 * The browser identity behind every plain HTTP request the importer's parsers make (Spotify's
 * web-player token endpoint, Apple Music's share-page scrape) — a normal-looking mobile Chrome
 * User-Agent, since some of these are unofficial endpoints that reject requests without one.
 *
 * Keep the Chrome version here current: a server that branches on UA-sniffed feature support can
 * misbehave for a version far enough out of date to look suspicious.
 */
internal const val IMPORT_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/153.0.0.0 Mobile Safari/537.36"
