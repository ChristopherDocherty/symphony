package io.github.zyrouge.symphony.services.groove

import android.net.Uri

data class WishlistListing(
    val url: String,
    val price: Double,
)

data class WishlistAlbum(
    val id: String,
    val artist: String,
    val name: String,
    val year: Int?,
    val priority: Int,
    val pending: Boolean = false,
    val dirDocId: String,
    val artworkUri: Uri?,
    val listings: List<WishlistListing> = emptyList(),
)
