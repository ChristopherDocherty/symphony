package io.github.zyrouge.symphony.services.groove

data class SongTagField(
    val label: String,
    val tagKey: String,
    val getValue: (Song) -> String,
    val hint: String? = null,
)

val SONG_TAG_FIELDS = listOf(
    SongTagField("Title", "TITLE", getValue = { it.title }),
    SongTagField("Artist", "ARTIST", getValue = { it.artists.joinToString(", ") }),
    SongTagField("Album", "ALBUM", getValue = { it.album ?: "" }),
    SongTagField("Album Artist", "ALBUMARTIST", getValue = { it.albumArtists.joinToString(", ") }),
    SongTagField("Composer", "COMPOSER", getValue = { it.composers.joinToString(", ") }),
    SongTagField("Genre", "GENRE", getValue = { it.genres.joinToString(", ") }),
    SongTagField("Year", "DATE", getValue = { it.date?.toString() ?: "" }),
    SongTagField("Track Number", "TRACKNUMBER", getValue = { it.trackNumber?.toString() ?: "" }),
    SongTagField("Track Total", "TRACKTOTAL", getValue = { it.trackTotal?.toString() ?: "" }),
    SongTagField("Disc Number", "DISCNUMBER", getValue = { it.discNumber?.toString() ?: "" }),
    SongTagField("Disc Total", "DISCTOTAL", getValue = { it.discTotal?.toString() ?: "" }),
) + ALBUM_STRING_FILTER_FIELDS.map { filter ->
    SongTagField(filter.label, filter.tagName, getValue = { song -> song.customTags[filter.tagName] ?: "" }, hint = filter.hint)
}
