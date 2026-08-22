package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reading Agro's JSON without a data class per payload.
 *
 * Every one of these tolerates a missing or wrong-typed field rather than throwing. The server and
 * the app are versioned separately — a phone that has not updated will meet fields it does not know
 * and miss fields it expects — and a whole screen failing over one absent value is a worse outcome
 * than that value being absent.
 *
 * One file rather than a private copy per API class: there were two definitions of `long` before
 * this existed, and the second one broke the build.
 */

internal fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.bool(key: String): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: false

internal fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

internal fun JsonObject.longs(key: String): List<Long> =
    this[key]?.jsonArray.orEmpty().map { it.jsonPrimitive.longOrNull ?: 0L }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.objects(key: String): List<JsonObject> =
    this[key]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }

/** A named number, as every one of Agro's stat lists is shaped. */
internal fun JsonObject.entries(key: String): List<StatEntry> =
    this[key]?.jsonArray.orEmpty().mapNotNull { element ->
        val entry = element.jsonObject
        val name = entry["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        StatEntry(name, entry["value"]?.jsonPrimitive?.longOrNull ?: 0L)
    }
