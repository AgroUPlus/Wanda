package com.wander.android.ui.screens.home.layout

import androidx.compose.runtime.Immutable
import com.wander.android.ui.screens.home.HomeSectionStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * What the user chose for one Home shelf. Only choices are stored — never titles or tracks — so a
 * shelf whose content changes (a feed shelf, a genre) needs no migration.
 *
 * A [style] or [count] of null means "keep the shelf's own default".
 */
@Serializable
@Immutable
data class ShelfConfig(
    val id: String,
    val enabled: Boolean = true,
    val style: HomeSectionStyle? = null,
    val count: Int? = null,
    /** A genre shelf's categories: library genre tags, or the Christian category. */
    val categories: List<String> = emptyList(),
    /** For the Christian category: the language codes to draw artists from. Empty means all. */
    val languages: List<String> = emptyList()
)

/** The stored layout is a JSON list in order; an empty list means "never customised". */
internal object ShelfConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ShelfConfig.serializer())

    fun encode(configs: List<ShelfConfig>): String = json.encodeToString(serializer, configs)

    /**
     * A blob that cannot be read yields the default layout rather than a crash on Home: the layout
     * is a convenience, and a newer build's unreadable value should not take the screen down.
     */
    fun decode(raw: String?): List<ShelfConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
