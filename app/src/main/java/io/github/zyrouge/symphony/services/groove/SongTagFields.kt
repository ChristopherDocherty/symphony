package io.github.zyrouge.symphony.services.groove

data class SongTagField(
    val label: String,
    val tagKey: String,
    val getValue: (Song) -> String,
)

val SONG_TAG_FIELDS = listOf(
    SongTagField("Title", "TITLE") { it.title },
    SongTagField("Artist", "ARTIST") { it.artists.joinToString(", ") },
    SongTagField("Album", "ALBUM") { it.album ?: "" },
    SongTagField("Album Artist", "ALBUMARTIST") { it.albumArtists.joinToString(", ") },
    SongTagField("Composer", "COMPOSER") { it.composers.joinToString(", ") },
    SongTagField("Genre", "GENRE") { it.genres.joinToString(", ") },
    SongTagField("Year", "DATE") { it.date?.toString() ?: "" },
    SongTagField("Track Number", "TRACKNUMBER") { it.trackNumber?.toString() ?: "" },
    SongTagField("Track Total", "TRACKTOTAL") { it.trackTotal?.toString() ?: "" },
    SongTagField("Disc Number", "DISCNUMBER") { it.discNumber?.toString() ?: "" },
    SongTagField("Disc Total", "DISCTOTAL") { it.discTotal?.toString() ?: "" },
) + ALBUM_STRING_FILTER_FIELDS.map { filter ->
    SongTagField(filter.label, filter.tagName) { song -> song.customTags[filter.tagName] ?: "" }
}
