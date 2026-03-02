package io.github.zyrouge.symphony.services.groove

import android.net.Uri

data class WishlistAlbum(
    val id: String,
    val artist: String,
    val name: String,
    val year: Int?,
    val priority: Int,
    val dirDocId: String,
    val artworkUri: Uri?,
)
