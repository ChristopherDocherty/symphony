package io.github.zyrouge.symphony.utils

object LrcSerializer {
    private val timestampRegex = Regex("""^\[\s*(\d+):(\d+)\.(\d+)?\s*](.*)""")

    private fun stripTimestamp(line: String): String {
        return timestampRegex.matchEntire(line)?.groupValues?.get(4)?.trim() ?: line.trim()
    }

    fun toLines(raw: String): List<String> {
        return raw.split(Regex("""\n|\r\n|\r""")).map { stripTimestamp(it) }
    }

    fun serialize(lines: List<String>, timestamps: List<Long?>): String {
        val sb = StringBuilder()
        lines.forEachIndexed { i, line ->
            val ts = timestamps.getOrNull(i)
            if (ts != null) {
                val mm = ts / 60000
                val ss = (ts % 60000) / 1000
                val ms = ts % 1000
                sb.append("[%02d:%02d.%03d]%s".format(mm, ss, ms, line))
            } else {
                sb.append(line)
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
