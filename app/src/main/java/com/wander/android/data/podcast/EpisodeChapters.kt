package com.wander.android.data.podcast

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

data class Chapter(val startMs: Long, val title: String)

/** Podcasting 2.0 chapters (`application/json+chapters`): `{"chapters":[{"startTime":0,"title":"…"}]}`. */
object EpisodeChapters {

    /**
     * Chapters in playing order. Entries marked `"toc": false` are skipped: the spec uses that for
     * markers that are not meant to be listed, such as an ad slot.
     *
     * @throws IOException if the text is not a chapters file.
     */
    fun parse(text: String): List<Chapter> {
        try {
            val chapters = Json.parseToJsonElement(text).jsonObject["chapters"]?.jsonArray
                ?: throw IOException("The file has no chapters")
            return chapters.mapNotNull { element ->
                val entry: JsonObject = element.jsonObject
                if (entry["toc"]?.jsonPrimitive?.booleanOrNull == false) return@mapNotNull null
                val start = entry["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val title = entry["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                Chapter((start * 1000).toLong(), title)
            }.sortedBy { it.startMs }
        } catch (e: SerializationException) {
            throw IOException("The chapters file is not valid JSON")
        } catch (e: IllegalArgumentException) {
            // `jsonObject` / `jsonArray` / `jsonPrimitive` throw this when an element has the wrong shape.
            throw IOException("The chapters file is not shaped like a chapters file")
        }
    }
}
