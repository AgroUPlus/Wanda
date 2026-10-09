package com.wander.android.ui.navigation

/** Reads and writes an ordered choice of enum entries as comma-separated names. */
internal object NavLayoutCodec {

    /**
     * Null means the user never chose, so [default]. A saved list keeps its order, drops names this
     * build no longer has, and may be empty: choosing nothing is a choice.
     */
    fun <T : Enum<T>> decode(raw: String?, entries: List<T>, default: List<T>): List<T> {
        if (raw == null) return default
        return raw.split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { name -> entries.firstOrNull { it.name == name } }
            .distinct()
    }

    fun encode(items: List<Enum<*>>): String = items.joinToString(",") { it.name }
}
