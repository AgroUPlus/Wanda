package com.wander.android.core.update

/**
 * A release version in the semver shape the tags use: `1.7.0`, `v1.7.0`, `1.7.0-beta.1`.
 *
 * Ordered by semver precedence (semver.org §11): the numeric parts first, then a pre-release ranks
 * *below* the release it leads to, so `1.7.0-beta1` is older than `1.7.0` and newer than `1.6.0`.
 * Build metadata (`+…`) is ignored, as the spec requires. A missing trailing part counts as zero,
 * so `1.7` and `1.7.0` rank the same.
 */
internal data class ReleaseVersion(
    val numbers: List<Long>,
    val preRelease: List<String>
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int {
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val byNumber = numbers.getOrElse(i) { 0L }.compareTo(other.numbers.getOrElse(i) { 0L })
            if (byNumber != 0) return byNumber
        }
        return comparePreRelease(preRelease, other.preRelease)
    }

    companion object {
        private val NUMBER = Regex("""\d+""")
        private val IDENTIFIER = Regex("""[0-9A-Za-z-]+""")

        /** [text] as a version, or null when it is not one (a tag like `embedder-v1`). */
        fun parse(text: String): ReleaseVersion? {
            val withoutBuild = text.trim().removePrefix("v").substringBefore('+')
            val core = withoutBuild.substringBefore('-')
            val numbers = core.split('.').map { part ->
                if (!NUMBER.matches(part)) return null
                part.toLongOrNull() ?: return null
            }
            val preRelease = if ('-' in withoutBuild) {
                withoutBuild.substringAfter('-').split('.').onEach { if (!IDENTIFIER.matches(it)) return null }
            } else {
                emptyList()
            }
            return ReleaseVersion(numbers, preRelease)
        }

        /** No pre-release outranks any; otherwise identifier by identifier, then the longer list. */
        private fun comparePreRelease(a: List<String>, b: List<String>): Int {
            if (a.isEmpty() || b.isEmpty()) return b.size.compareTo(a.size)
            for (i in 0 until minOf(a.size, b.size)) {
                val byIdentifier = compareIdentifier(a[i], b[i])
                if (byIdentifier != 0) return byIdentifier
            }
            return a.size.compareTo(b.size)
        }

        /** Numeric identifiers compare as numbers and rank below alphanumeric ones, which compare in ASCII order. */
        private fun compareIdentifier(a: String, b: String): Int {
            val aNumber = a.takeIf { NUMBER.matches(it) }?.toBigInteger()
            val bNumber = b.takeIf { NUMBER.matches(it) }?.toBigInteger()
            return when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}

/** Whether [latest] is a later version than [current]; null when either is not a version. */
internal fun isNewer(latest: String, current: String): Boolean? {
    val latestVersion = ReleaseVersion.parse(latest) ?: return null
    val currentVersion = ReleaseVersion.parse(current) ?: return null
    return latestVersion > currentVersion
}
