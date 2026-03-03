package io.github.zyrouge.symphony.services.groove

import io.github.zyrouge.symphony.AlbumFilter

const val BLANK_TAG_VALUE = ""

data class StringFilterField(
    val label: String,
    val tagName: String,
    val getSelected: (AlbumFilter) -> List<String>,
    val applyTo: (AlbumFilter.Builder, List<String>) -> AlbumFilter.Builder,
    val sortValues: (List<String>) -> List<String> = { it.sorted() },
)

val ALBUM_STRING_FILTER_FIELDS = listOf(
    StringFilterField(
        label = "Release Type",
        tagName = "RELEASETYPE",
        getSelected = { it.releaseTypeList },
        applyTo = { b, v -> b.clearReleaseType().addAllReleaseType(v) },
    ),
    StringFilterField(
        label = "Original Owner",
        tagName = "ORIGINALOWNER",
        getSelected = { it.originalOwnerList },
        applyTo = { b, v -> b.clearOriginalOwner().addAllOriginalOwner(v) },
    ),
    StringFilterField(
        label = "Listened To Status",
        tagName = "LISTENEDTOSTATUS",
        getSelected = { it.listenedToStatusList },
        applyTo = { b, v -> b.clearListenedToStatus().addAllListenedToStatus(v) },
    ),
    StringFilterField(
        label = "Country",
        tagName = "RELEASECOUNTRY",
        getSelected = { it.countryList },
        applyTo = { b, v -> b.clearCountry().addAllCountry(v) },
    ),
    StringFilterField(
        label = "Collections",
        tagName = "COLLECTIONS",
        getSelected = { it.collectionsList },
        applyTo = { b, v -> b.clearCollections().addAllCollections(v) },
    ),
    StringFilterField(
        label = "AOTY Rank",
        tagName = "AOTY",
        getSelected = { it.albumOfTheYearRankList },
        applyTo = { b, v -> b.clearAlbumOfTheYearRank().addAllAlbumOfTheYearRank(v) },
        sortValues = { values ->
            val (blank, rest) = values.partition { it == BLANK_TAG_VALUE }
            blank + rest.sortedWith(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })
        },
    ),
)

// ── Debug filter field ──────────────────────────────────────────────────────

/**
 * A filter field whose available values are fixed (not derived from song custom tags).
 * The actual album-level matching is performed inside [AlbumRepository.getAlbums].
 */
data class DebugAlbumFilterField(
    val label: String,
    /** All possible values shown in the picker UI. */
    val values: List<String>,
    val getSelected: (AlbumFilter) -> List<String>,
    val applyTo: (AlbumFilter.Builder, List<String>) -> AlbumFilter.Builder,
)

// Bitrate-range bucket identifiers (stored in AlbumFilter.bitrate_range)
const val BITRATE_RANGE_UNKNOWN = "unknown"
const val BITRATE_RANGE_LOW = "<150 kbps"
const val BITRATE_RANGE_MEDIUM = "128–256 kbps"
const val BITRATE_RANGE_HIGH = "256–320 kbps"
const val BITRATE_RANGE_VERY_HIGH = "≥320 kbps"

// Artwork-source identifiers (stored in AlbumFilter.artwork_source)
const val ARTWORK_SOURCE_EMBEDDED = "embedded"
const val ARTWORK_SOURCE_DIRECTORY = "directory file"
const val ARTWORK_SOURCE_NONE = "none"

val ALBUM_DEBUG_FILTER_FIELDS = listOf(
    DebugAlbumFilterField(
        label = "Bitrate",
        values = listOf(
            BITRATE_RANGE_UNKNOWN,
            BITRATE_RANGE_LOW,
            BITRATE_RANGE_MEDIUM,
            BITRATE_RANGE_HIGH,
            BITRATE_RANGE_VERY_HIGH,
        ),
        getSelected = { it.bitrateRangeList },
        applyTo = { b, v -> b.clearBitrateRange().addAllBitrateRange(v) },
    ),
    DebugAlbumFilterField(
        label = "Artwork Source",
        values = listOf(
            ARTWORK_SOURCE_EMBEDDED,
            ARTWORK_SOURCE_DIRECTORY,
            ARTWORK_SOURCE_NONE,
        ),
        getSelected = { it.artworkSourceList },
        applyTo = { b, v -> b.clearArtworkSource().addAllArtworkSource(v) },
    ),
)
