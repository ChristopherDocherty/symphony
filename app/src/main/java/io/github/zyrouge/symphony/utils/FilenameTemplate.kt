package io.github.zyrouge.symphony.utils

import io.github.zyrouge.symphony.services.groove.SONG_TAG_FIELDS
import io.github.zyrouge.symphony.services.groove.Song

object FilenameTemplate {
    private val ILLEGAL_CHARS = Regex("""[/\\:*?"<>|]""")

    val PLACEHOLDERS: Map<String, (Song) -> String> = buildMap {
        for (field in SONG_TAG_FIELDS) {
            val key = "%${field.tagKey.lowercase()}%"
            put(key, when (field.tagKey) {
                "TRACKNUMBER" -> { song -> song.trackNumber?.let { "%02d".format(it) } ?: "" }
                "DISCNUMBER" -> { song -> song.discNumber?.let { "%02d".format(it) } ?: "" }
                else -> { song -> field.getValue(song) }
            })
        }
    }

    fun apply(template: String, song: Song): String {
        var result = template
        for ((placeholder, getValue) in PLACEHOLDERS) {
            result = result.replace(placeholder, getValue(song), ignoreCase = true)
        }
        return sanitize(result)
    }

    fun sanitize(name: String): String {
        return ILLEGAL_CHARS.replace(name, "").trim().trimEnd('.')
    }
}
