package io.github.zyrouge.symphony.datastore

import android.content.Context
import io.github.zyrouge.symphony.AlbumArtistSortBy
import io.github.zyrouge.symphony.ArtworkQuality
import io.github.zyrouge.symphony.GenreSortBy
import io.github.zyrouge.symphony.HomePageBottomBarLabelVisibility
import io.github.zyrouge.symphony.NowPlayingControlsLayout
import io.github.zyrouge.symphony.NowPlayingLyricsLayout
import io.github.zyrouge.symphony.PathSortBy
import io.github.zyrouge.symphony.PlaylistSortBy
import io.github.zyrouge.symphony.SongSortBy
import io.github.zyrouge.symphony.SongSortPreference
import io.github.zyrouge.symphony.ThemeMode
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.ResponsiveGridColumns

object SettingsMigration {
    /**
     * Runs once per install. If [Settings.settingsInitialized] is already true the function
     * returns immediately. Otherwise it reads every key from the legacy SharedPreferences
     * "settings" file and writes the equivalent values into the proto DataStore, then sets
     * [Settings.settingsInitialized] = true so the block never runs again.
     *
     * Existing proto sort-preference fields (2–8) and album-filter fields (801–802) written
     * by earlier versions of the app are preserved — they are only overwritten from
     * SharedPreferences when they are still at their default (unset) state.
     */
    suspend fun migrate(context: Context) {
        context.settingsDataStore.updateData { current ->
            if (current.settingsInitialized) return@updateData current

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            current.copy {
                settingsInitialized = true

                // ---------------------------------------------------------------
                // App
                // ---------------------------------------------------------------
                val lang = prefs.getString("language", null)
                if (!lang.isNullOrEmpty()) language = lang

                readIntroductoryMessage = prefs.getBoolean("introductory_message", false)
                artworkQuality = parseArtworkQuality(prefs.getString("artwork_quality", null))
                useMetaphony = prefs.getBoolean("use_metaphony", true)
                caseSensitiveSorting = prefs.getBoolean("case_sensitive_sorting", false)

                val queue = prefs.getString("previous_song_queue", null)
                if (!queue.isNullOrEmpty()) previousSongQueue = queue

                // ---------------------------------------------------------------
                // Appearance
                // ---------------------------------------------------------------
                themeMode = parseThemeMode(prefs.getString("theme_mode", null))
                materialYou = prefs.getBoolean("material_you", true)

                val primaryColorStr = prefs.getString("primary_color", null)
                if (!primaryColorStr.isNullOrEmpty()) primaryColor = primaryColorStr

                val fontFamilyStr = prefs.getString("font_family", null)
                if (!fontFamilyStr.isNullOrEmpty()) fontFamily = fontFamilyStr

                fontScale = prefs.getFloat("font_scale", 1f).takeIf { it > 0f } ?: 1f
                contentScale = prefs.getFloat("content_scale", 1f).takeIf { it > 0f } ?: 1f

                // ---------------------------------------------------------------
                // Home page
                // ---------------------------------------------------------------
                val lastTab = prefs.getString("home_last_page", null)
                lastHomeTab = if (!lastTab.isNullOrEmpty()) lastTab else "Songs"

                val rawTabs = prefs.getString("home_tabs", null)
                homeTabs.clear()
                if (!rawTabs.isNullOrEmpty()) {
                    homeTabs.addAll(rawTabs.split(",").filter { it.isNotEmpty() })
                } else {
                    homeTabs.addAll(listOf("ForYou", "Songs", "Albums", "Artists", "Playlists"))
                }

                homePageBottomBarLabelVisibility = parseBottomBarVisibility(
                    prefs.getString("home_page_bottom_bar_label_visibility", null)
                )

                val rawForYou = prefs.getString("for_you_contents", null)
                forYouContents.clear()
                if (!rawForYou.isNullOrEmpty()) {
                    forYouContents.addAll(rawForYou.split(",").filter { it.isNotEmpty() })
                } else {
                    forYouContents.addAll(listOf("Albums", "Artists"))
                }

                // ---------------------------------------------------------------
                // Library — artists
                // ---------------------------------------------------------------
                artistsHorizontalGridColumns = prefs.getInt(
                    "last_used_artists_horizontal_grid_columns",
                    ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
                )
                artistsVerticalGridColumns = prefs.getInt(
                    "last_used_artists_vertical_grid_columns",
                    ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
                )

                // ---------------------------------------------------------------
                // Library — album artists
                // ---------------------------------------------------------------
                albumArtistsSortBy = parseAlbumArtistSortBy(
                    prefs.getString("last_used_album_artists_sort_by", null)
                )
                albumArtistsSortReverse =
                    prefs.getBoolean("last_used_album_artists_sort_reverse", false)
                albumArtistsHorizontalGridColumns = prefs.getInt(
                    "last_used_album_artists_horizontal_grid_columns",
                    ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
                )
                albumArtistsVerticalGridColumns = prefs.getInt(
                    "last_used_album_artists_vertical_grid_columns",
                    ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
                )

                // ---------------------------------------------------------------
                // Library — albums
                // ---------------------------------------------------------------
                albumsHorizontalGridColumns = prefs.getInt(
                    "last_used_albums_horizontal_grid_columns",
                    ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
                )
                albumsVerticalGridColumns = prefs.getInt(
                    "last_used_albums_vertical_grid_columns",
                    ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
                )

                // ---------------------------------------------------------------
                // Library — genres
                // ---------------------------------------------------------------
                genresSortBy = parseGenreSortBy(prefs.getString("last_used_genres_sort_by", null))
                genresSortReverse = prefs.getBoolean("last_used_genres_sort_reverse", false)
                genresHorizontalGridColumns = prefs.getInt(
                    "last_used_genres_horizontal_grid_columns",
                    ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
                )
                genresVerticalGridColumns = prefs.getInt(
                    "last_used_genres_vertical_grid_columns",
                    ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
                )

                // ---------------------------------------------------------------
                // Library — playlists
                // ---------------------------------------------------------------
                playlistsSortBy = parsePlaylistSortBy(
                    prefs.getString("last_used_playlists_sort_by", null)
                )
                playlistsSortReverse = prefs.getBoolean("last_used_playlists_sort_reverse", false)
                playlistsHorizontalGridColumns = prefs.getInt(
                    "last_used_playlists_horizontal_grid_columns",
                    ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
                )
                playlistsVerticalGridColumns = prefs.getInt(
                    "last_used_playlists_vertical_grid_columns",
                    ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
                )

                // ---------------------------------------------------------------
                // Library — browser / tree / folders
                // ---------------------------------------------------------------
                browserSortBy = parseSongSortBy(
                    prefs.getString("last_used_folder_sort_by", null),
                    SongSortBy.SONG_FILENAME,
                )
                browserSortReverse = prefs.getBoolean("last_used_folder_sort_reverse", false)

                val browserPathStr = prefs.getString("last_used_folder_path", null)
                if (!browserPathStr.isNullOrEmpty()) browserPath = browserPathStr

                treePathSortBy = parsePathSortBy(
                    prefs.getString("last_used_tree_path_sort_by", null)
                )
                treePathSortReverse = prefs.getBoolean("last_used_tree_path_sort_reverse", false)

                foldersSortBy = parsePathSortBy(
                    prefs.getString("last_used_folders_sort_by", null)
                )
                foldersSortReverse = prefs.getBoolean("last_used_folders_sort_reverse", false)
                foldersHorizontalGridColumns = prefs.getInt(
                    "last_used_folders_horizontal_grid_columns",
                    ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
                )
                foldersVerticalGridColumns = prefs.getInt(
                    "last_used_folders_vertical_grid_columns",
                    ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
                )

                // ---------------------------------------------------------------
                // Library — folder lists
                // ---------------------------------------------------------------
                val disabledPaths = prefs.getStringSet("last_disabled_tree_paths", null)
                disabledTreePaths.clear()
                if (!disabledPaths.isNullOrEmpty()) disabledTreePaths.addAll(disabledPaths)

                val blacklist = prefs.getStringSet("blacklist_folders", null)
                blacklistFolders.clear()
                if (!blacklist.isNullOrEmpty()) blacklistFolders.addAll(blacklist)

                val whitelist = prefs.getStringSet("whitelist_folders", null)
                whitelistFolders.clear()
                if (!whitelist.isNullOrEmpty()) whitelistFolders.addAll(whitelist)

                val mediaFolderUris = prefs.getStringSet("media_folders", null)
                mediaFolders.clear()
                if (!mediaFolderUris.isNullOrEmpty()) mediaFolders.addAll(mediaFolderUris)

                // ---------------------------------------------------------------
                // Content filtering
                // ---------------------------------------------------------------
                val filterPattern = prefs.getString("songs_filter_pattern", null)
                if (!filterPattern.isNullOrEmpty()) songsFilterPattern = filterPattern

                minSongDuration = prefs.getInt("min_song_duration", 0)

                val artistSeps = prefs.getStringSet("artist_tag_separators", null)
                artistTagSeparators.clear()
                if (!artistSeps.isNullOrEmpty()) {
                    artistTagSeparators.addAll(artistSeps)
                } else {
                    artistTagSeparators.addAll(listOf(";", "/", ",", "+"))
                }

                val genreSeps = prefs.getStringSet("genre_tag_separators", null)
                genreTagSeparators.clear()
                if (!genreSeps.isNullOrEmpty()) {
                    genreTagSeparators.addAll(genreSeps)
                } else {
                    genreTagSeparators.addAll(listOf(";", "/", ",", "+"))
                }

                // ---------------------------------------------------------------
                // Existing proto sort prefs (fields 2–8): only overwrite from
                // SharedPreferences when the field is still at its unset default.
                // ---------------------------------------------------------------
                if (uiPlaylistViewSongsSort == SongSortPreference.getDefaultInstance()) {
                    uiPlaylistViewSongsSort = uiPlaylistViewSongsSort.copy {
                        by = parseSongSortBy(
                            prefs.getString("last_used_playlist_songs_sort_by", null),
                            SongSortBy.SONG_CUSTOM,
                        )
                        reverse = prefs.getBoolean("last_used_playlist_songs_sort_reverse", false)
                    }
                }
                if (uiAlbumViewSongsSort == SongSortPreference.getDefaultInstance()) {
                    uiAlbumViewSongsSort = uiAlbumViewSongsSort.copy {
                        by = parseSongSortBy(
                            prefs.getString("last_used_album_songs_sort_by", null),
                            SongSortBy.SONG_TRACK_NUMBER,
                        )
                    }
                }

                // ---------------------------------------------------------------
                // Playback
                // ---------------------------------------------------------------
                fadePlayback = prefs.getBoolean("fade_playback", false)
                fadePlaybackDuration =
                    prefs.getFloat("fade_playback_duration", 1f).takeIf { it > 0f } ?: 1f
                requireAudioFocus = prefs.getBoolean("require_audio_focus", true)
                ignoreAudioFocusLoss = prefs.getBoolean("ignore_audio_focus_loss", false)
                playOnHeadphonesConnect = prefs.getBoolean("play_on_headphones_connect", false)
                pauseOnHeadphonesDisconnect =
                    prefs.getBoolean("pause_on_headphones_disconnect", true)
                gaplessPlayback = prefs.getBoolean("gapless_playback", true)

                // ---------------------------------------------------------------
                // Now playing
                // ---------------------------------------------------------------
                nowPlayingAdditionalInfo =
                    prefs.getBoolean("show_now_playing_additional_info", true)
                nowPlayingSeekControls = prefs.getBoolean("enable_seek_controls", false)

                // Both SP entries share the same key "seek_back_duration" (existing bug).
                // Migrate the stored value to seekBackDuration; seekForwardDuration gets 30.
                seekBackDuration = prefs.getInt("seek_back_duration", 15)
                seekForwardDuration = 30

                nowPlayingControlsLayout = parseNowPlayingControlsLayout(
                    prefs.getString("now_playing_controls_layout", null)
                )
                nowPlayingLyricsLayout = parseNowPlayingLyricsLayout(
                    prefs.getString("now_playing_lyrics_layout", null)
                )
                lyricsKeepScreenAwake = prefs.getBoolean("lyrics_keep_screen_awake", true)

                // ---------------------------------------------------------------
                // Mini player
                // ---------------------------------------------------------------
                miniPlayerTrackControls = prefs.getBoolean("mini_player_extended_controls", false)
                miniPlayerSeekControls = prefs.getBoolean("mini_player_seek_controls", false)
                miniPlayerTextMarquee = prefs.getBoolean("mini_player_text_marquee", true)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Enum mapping helpers — convert stored Kotlin enum names to proto enum values
    // ---------------------------------------------------------------------------

    private fun parseThemeMode(name: String?): ThemeMode = when (name) {
        "SYSTEM_BLACK" -> ThemeMode.THEME_SYSTEM_BLACK
        "LIGHT" -> ThemeMode.THEME_LIGHT
        "DARK" -> ThemeMode.THEME_DARK
        "BLACK" -> ThemeMode.THEME_BLACK
        else -> ThemeMode.THEME_SYSTEM
    }

    private fun parseBottomBarVisibility(name: String?): HomePageBottomBarLabelVisibility =
        when (name) {
            "VISIBLE_WHEN_ACTIVE" -> HomePageBottomBarLabelVisibility.BOTTOM_BAR_VISIBLE_WHEN_ACTIVE
            "INVISIBLE" -> HomePageBottomBarLabelVisibility.BOTTOM_BAR_INVISIBLE
            else -> HomePageBottomBarLabelVisibility.BOTTOM_BAR_ALWAYS_VISIBLE
        }

    private fun parseArtworkQuality(name: String?): ArtworkQuality = when (name) {
        "Low" -> ArtworkQuality.ARTWORK_LOW
        "High" -> ArtworkQuality.ARTWORK_HIGH
        "Loseless" -> ArtworkQuality.ARTWORK_LOSELESS
        else -> ArtworkQuality.ARTWORK_MEDIUM
    }

    private fun parseAlbumArtistSortBy(name: String?): AlbumArtistSortBy = when (name) {
        "CUSTOM" -> AlbumArtistSortBy.ALBUM_ARTIST_CUSTOM
        "TRACKS_COUNT" -> AlbumArtistSortBy.ALBUM_ARTIST_TRACKS_COUNT
        "ALBUMS_COUNT" -> AlbumArtistSortBy.ALBUM_ARTIST_ALBUMS_COUNT
        else -> AlbumArtistSortBy.ALBUM_ARTIST_SORT_NAME
    }

    private fun parseGenreSortBy(name: String?): GenreSortBy = when (name) {
        "CUSTOM" -> GenreSortBy.GENRE_SORT_CUSTOM
        "TRACKS_COUNT" -> GenreSortBy.GENRE_SORT_TRACKS_COUNT
        else -> GenreSortBy.GENRE_SORT_GENRE
    }

    private fun parsePlaylistSortBy(name: String?): PlaylistSortBy = when (name) {
        "TITLE" -> PlaylistSortBy.PLAYLIST_SORT_TITLE
        "TRACKS_COUNT" -> PlaylistSortBy.PLAYLIST_SORT_TRACKS_COUNT
        else -> PlaylistSortBy.PLAYLIST_SORT_CUSTOM
    }

    private fun parsePathSortBy(name: String?): PathSortBy = when (name) {
        "CUSTOM" -> PathSortBy.PATH_SORT_CUSTOM
        else -> PathSortBy.PATH_SORT_NAME
    }

    private fun parseSongSortBy(name: String?, default: SongSortBy): SongSortBy =
        SongSortBy.values().find { it.name == name } ?: default

    private fun parseNowPlayingControlsLayout(name: String?): NowPlayingControlsLayout =
        when (name) {
            "CompactRight" -> NowPlayingControlsLayout.CONTROLS_COMPACT_RIGHT
            "Traditional" -> NowPlayingControlsLayout.CONTROLS_TRADITIONAL
            else -> NowPlayingControlsLayout.CONTROLS_COMPACT_LEFT
        }

    private fun parseNowPlayingLyricsLayout(name: String?): NowPlayingLyricsLayout =
        when (name) {
            "SeparatePage" -> NowPlayingLyricsLayout.LYRICS_SEPARATE_PAGE
            else -> NowPlayingLyricsLayout.LYRICS_REPLACE_ARTWORK
        }
}
