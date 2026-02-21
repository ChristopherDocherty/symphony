package io.github.zyrouge.symphony.datastore

import io.github.zyrouge.symphony.AlbumArtistSortBy
import io.github.zyrouge.symphony.ArtworkQuality
import io.github.zyrouge.symphony.GenreSortBy
import io.github.zyrouge.symphony.PathSortBy
import io.github.zyrouge.symphony.Settings
import io.github.zyrouge.symphony.SongSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.ResponsiveGridColumns

/**
 * A pre-built Settings instance with all correct Kotlin-side defaults applied.
 * Used as the initial value for the settings StateFlow while the DataStore loads
 * from disk, ensuring the app never briefly sees zero-values for settings that
 * default to true / non-zero.
 */
object SettingsDefaults {
    val INSTANCE: Settings = Settings.getDefaultInstance().copy {
        settingsInitialized = true

        // App
        showUpdateToast = true
        useMetaphony = true
        artworkQuality = ArtworkQuality.ARTWORK_MEDIUM

        // Appearance
        materialYou = true
        fontScale = 1f
        contentScale = 1f

        // Home page
        lastHomeTab = "Songs"
        homeTabs.addAll(listOf("ForYou", "Songs", "Albums", "Artists", "Playlists"))
        forYouContents.addAll(listOf("Albums", "Artists"))

        // Library — sort defaults
        albumArtistsSortBy = AlbumArtistSortBy.ALBUM_ARTIST_SORT_NAME
        genresSortBy = GenreSortBy.GENRE_SORT_GENRE
        browserSortBy = SongSortBy.SONG_FILENAME
        treePathSortBy = PathSortBy.PATH_SORT_NAME
        foldersSortBy = PathSortBy.PATH_SORT_NAME

        // Library — grid column defaults
        artistsHorizontalGridColumns = ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
        artistsVerticalGridColumns = ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS
        albumArtistsHorizontalGridColumns = ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
        albumArtistsVerticalGridColumns = ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS
        albumsHorizontalGridColumns = ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
        albumsVerticalGridColumns = ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS
        genresHorizontalGridColumns = ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
        genresVerticalGridColumns = ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS
        playlistsHorizontalGridColumns = ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
        playlistsVerticalGridColumns = ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS
        foldersHorizontalGridColumns = ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS
        foldersVerticalGridColumns = ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS

        // Content
        artistTagSeparators.addAll(listOf(";", "/", ",", "+"))
        genreTagSeparators.addAll(listOf(";", "/", ",", "+"))

        // Playback
        requireAudioFocus = true
        pauseOnHeadphonesDisconnect = true
        gaplessPlayback = true
        fadePlaybackDuration = 1f

        // Now playing
        nowPlayingAdditionalInfo = true
        seekBackDuration = 15
        seekForwardDuration = 30
        lyricsKeepScreenAwake = true

        // Mini player
        miniPlayerTextMarquee = true
    }
}
