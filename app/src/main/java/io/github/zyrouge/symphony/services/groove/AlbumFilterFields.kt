package io.github.zyrouge.symphony.services.groove

import io.github.zyrouge.symphony.AlbumFilter

data class StringFilterField(
    val label: String,
    val tagName: String,
    val available: List<String>,
    val getSelected: (AlbumFilter) -> List<String>,
    val applyTo: (AlbumFilter.Builder, List<String>) -> AlbumFilter.Builder,
)

val ALBUM_STRING_FILTER_FIELDS = listOf(
    StringFilterField(
        label = "Release Type",
        tagName = "RELEASETYPE",
        available = listOf("Album", "EP", "Single", "Compilation", "Live", "Demo", "Remix"),
        getSelected = { it.releaseTypeList },
        applyTo = { b, v -> b.clearReleaseType().addAllReleaseType(v) },
    ),
    StringFilterField(
        label = "Original Owner",
        tagName = "ORIGINALOWNER",
        available = listOf("Self", "Gift", "Inherited", "Borrowed"),
        getSelected = { it.originalOwnerList },
        applyTo = { b, v -> b.clearOriginalOwner().addAllOriginalOwner(v) },
    ),
    StringFilterField(
        label = "Listened To Status",
        tagName = "LISTENEDTOSTATUS",
        available = listOf("Unlistened", "In Progress", "Listened", "Revisiting"),
        getSelected = { it.listenedToStatusList },
        applyTo = { b, v -> b.clearListenedToStatus().addAllListenedToStatus(v) },
    ),
    StringFilterField(
        label = "Country",
        tagName = "RELEASECOUNTRY",
        available = listOf("US", "UK", "JP", "DE", "FR", "SE", "AU", "CA"),
        getSelected = { it.countryList },
        applyTo = { b, v -> b.clearCountry().addAllCountry(v) },
    ),
    StringFilterField(
        label = "Collections",
        tagName = "COLLECTIONS",
        available = listOf("Favourites", "Wishlist", "Loaned Out", "To Rip"),
        getSelected = { it.collectionsList },
        applyTo = { b, v -> b.clearCollections().addAllCollections(v) },
    ),
)
