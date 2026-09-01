package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Joins the groups the fingerprinter says hold one recording.
 *
 * Run after the metadata pass rather than instead of it, because the two answer different
 * halves of the question. The metadata rules group rows nothing has fingerprinted — most of a
 * streaming library — and the links group rows the tags could never have joined: a file whose
 * artist field holds the uploader's name shares no key with anything, and lands in a bucket of
 * its own however obviously it is the same performance.
 *
 * A link crosses the duration tolerance as well as the key. Two encodings of one recording can
 * differ by more than three seconds when one carries a silent tail, and the samples have
 * already answered the question the tolerance was standing in for.
 *
 * A split still refuses the join, and refuses it for the whole group: if the user has kept any
 * member of one group apart from any member of the other, the two do not merge. Merging them
 * on the strength of a different pair would fold together exactly the two rows they said to
 * keep separate.
 */
internal fun foldLinkedGroups(
    groups: List<List<UnifiedTrack>>,
    splits: SplitSet,
    links: RecordingLinkSet
): List<List<UnifiedTrack>> {
    if (links.size == 0) return groups

    val folded = groups.map { it.toMutableList() }.toMutableList()
    var index = 0
    while (index < folded.size) {
        val group = folded[index]
        var other = index + 1
        while (other < folded.size) {
            val candidate = folded[other]
            val linked = group.any { a -> candidate.any { b -> links.isLinked(a.id, b.id) } }
            val apart = group.any { a -> candidate.any { b -> splits.isApart(a.id, b.id) } }
            if (linked && !apart) {
                group += candidate
                folded.removeAt(other)
                // The merged group may now reach a group already passed over, so start its
                // neighbours again rather than walking on from here.
                other = index + 1
            } else {
                other++
            }
        }
        index++
    }
    return folded
}
