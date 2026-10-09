package com.wander.android.ui.screens.home.layout

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Enough history before judging: a week of counting, and a few plays to show the shelves get used at all. */
private const val MIN_DAYS = 7
private const val MIN_TOTAL_PLAYS = 5
private const val MAX_SUGGESTED = 3

/** Which shelves are worth offering to replace. */
internal object ShelfSuggestions {

    /**
     * Shelves on Home, in order, that nothing has been played from. Empty until there is enough
     * history to say so, and never more than [MAX_SUGGESTED]: a list of every shelf is not advice.
     */
    fun rarelyUsed(onHome: List<String>, usage: ShelfUsage, now: Long): List<String> {
        if (usage.since == 0L || now - usage.since < MIN_DAYS * DAY_MILLIS) return emptyList()
        if (usage.plays.values.sum() < MIN_TOTAL_PLAYS) return emptyList()
        return onHome.filter { usage.playsFrom(it) == 0 }.take(MAX_SUGGESTED)
    }
}
