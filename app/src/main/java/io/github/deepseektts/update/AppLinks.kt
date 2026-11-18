package io.github.deepseektts.update

/** Public entry points shown in the app and README. */
object AppLinks {
    const val REPO = "https://github.com/limao996/deepseek-tts"
    const val RELEASES = "https://github.com/limao996/deepseek-tts/releases/latest"
    /** Donation page (Afdian). Opening it never sends app data. */
    const val AFDIAN = "https://afdian.com/a/limao996"
    fun releaseTag(tag: String) = REPO + "/releases/tag/" + tag
}
