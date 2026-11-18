package io.github.deepseektts.update

/**
 * Pure version and redirect parsing.
 *
 * Update checks read the GitHub web redirect instead of the REST API first:
 * unauthenticated api.github.com allows only 60 requests per hour per IP, while
 * the releases redirect is ordinary web traffic that does not touch that budget.
 */
object ReleaseVersion {
    private val TAG_PATTERN = Regex("[vV]?\\d+(\\.\\d+){0,3}")

    fun normalize(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").trim()

    fun segments(value: String): List<Int>? {
        val parts = normalize(value).split(".")
        if (parts.isEmpty()) return null
        val result = ArrayList<Int>(parts.size)
        for (part in parts) {
            val digits = part.takeWhile(Char::isDigit)
            if (digits.isEmpty()) return null
            result.add(digits.toIntOrNull() ?: return null)
        }
        return result
    }

    /** True only when [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = segments(candidate) ?: return false
        val b = segments(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Extracts "v1.2.3" from a "/releases/tag/v1.2.3" redirect target. */
    fun tagFromRedirect(location: String?): String? {
        val raw = location ?: return null
        val marker = "/releases/tag/"
        val index = raw.indexOf(marker)
        if (index < 0) return null
        val tag = raw.substring(index + marker.length)
            .substringBefore("?")
            .substringBefore("#")
            .trim()
            .trimEnd('/')
        if (tag.isEmpty() || tag.contains('/')) return null
        return tag.takeIf { TAG_PATTERN.matches(it) }
    }
}
