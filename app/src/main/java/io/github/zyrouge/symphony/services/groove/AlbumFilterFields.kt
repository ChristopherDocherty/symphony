package io.github.zyrouge.symphony.services.groove

import io.github.zyrouge.symphony.AlbumFilter

const val BLANK_TAG_VALUE = ""

data class StringFilterField(
    val label: String,
    val tagName: String,
    val getSelected: (AlbumFilter) -> List<String>,
    val applyTo: (AlbumFilter.Builder, List<String>) -> AlbumFilter.Builder,
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
)
