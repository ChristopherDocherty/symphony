package io.github.zyrouge.symphony.utils

object LrcSerializer {
    private val timestampRegex = Regex("""^\[\s*(\d+):(\d+)\.(\d+)?\s*](.*)""")

    // Strip any leading LRC timestamps from a line and return the text portion.
    private fun stripTimestamp(line: String): String {
        return timestampRegex.matchEntire(line)?.groupValues?.get(4)?.trim() ?: line.trim()
    }

    // Convert raw LRC (or plain text) content into plain text lines without timestamps.
    fun toLines(raw: String): List<String> {
        return raw.split(Regex("""\n|\r\n|\r""")).map { stripTimestamp(it) }
    }

    // Serialize lines + optional timestamps (ms) into an LRC string.
    // Lines without timestamps are emitted as plain text lines.
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
