package com.pomodoro.update

internal data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for ((a, b) in listOf(major to other.major, minor to other.minor, patch to other.patch)) {
            if (a != b) return a.compareTo(b)
        }
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) return if (prerelease.isEmpty()) 1 else -1
        for ((a, b) in prerelease.zip(other.prerelease)) {
            if (a == b) continue
            val aNumber = a.all(Char::isDigit)
            val bNumber = b.all(Char::isDigit)
            return when {
                aNumber && bNumber -> a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
                aNumber -> -1
                bNumber -> 1
                else -> a.compareTo(b)
            }
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val (major, minor, patch, suffix) = match.destructured
            val parts = if (suffix.isEmpty()) emptyList() else suffix.lowercase().split('.')
            if (parts.any { it.isEmpty() || (it.all(Char::isDigit) && it.length > 1 && it.startsWith('0')) }) return null
            return ReleaseVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                if (patch.isEmpty()) 0 else patch.toIntOrNull() ?: return null,
                parts,
            )
        }
    }
}
