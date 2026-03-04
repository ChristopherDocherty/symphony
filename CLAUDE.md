# Symphony — Project Context

## IMPORTANT: CLAUDE CANNOT BUILD THIS PROJECT. DO NOT RUN GRADLE OR ANY BUILD COMMANDS. THE APP MUST BE BUILT IN ANDROID STUDIO BY THE USER.

Android music player app written in Kotlin + Jetpack Compose (Material 3).
Package: `io.github.zyrouge.symphony`

---

## Architecture

### Core ViewModel
`Symphony` (`services/`) extends `AndroidViewModel`. It owns all services and is passed everywhere as `ViewContext.symphony`. Use `symphony.viewModelScope` for coroutines tied to the ViewModel lifecycle.

Services implement `Symphony.Hooks` to receive lifecycle events (`onSymphonyReady`, `onSymphonyDestroy`, etc.). Register new services in the `hooks` list in `Symphony.kt`.

### Services
- **`Groove`** — music library. Owns all repositories (`album`, `artist`, `song`, `genre`, `playlist`, etc.). Scans the filesystem and caches to Room. Use `symphony.groove.album.ids()`, `.get(id)`, etc.
- **`Radio`** — playback engine. Key sub-components:
  - `RadioQueue` — queue state. Uses `concurrentListOf` (backed by `CopyOnWriteArrayList`). `currentSongIndex` + `currentQueue`/`originalQueue`. Queue mutations and reads happen on different threads — be careful with TOCTOU when checking size then indexing.
  - `RadioSession` — media session, notification, and broadcast receiver lifecycle. `updateAsync` runs on `symphony.groove.coroutineScope` (background thread).
  - Gapless playback: `Radio` holds a `nextPlayer` pre-prepared via `prepareAsync()`. When ready, it's linked to the current player with `MediaPlayer.setNextMediaPlayer()` so the hardware chains streams with zero gap. On `onSongFinish`, if `nextPlayer.isPlaying` is true the hardware already started it — swap references and call `activate()`, do NOT call `start()`. Always call `setNextMediaPlayer(null)` before stopping or destroying a player.
  - `RadioNotificationManager` — foreground service lifecycle state machine (DESTROYED → PREPARING → READY). Must call `startForeground()` within 5s of `startForegroundService()`. When starting the service, always set any state the `onServiceStart` handler depends on *before* calling `createService()`.
  - `RadioPlayer` — wraps `MediaPlayer`. State: Unprepared → Preparing → Prepared → Finished → Destroyed. `usable` = Prepared. `isPlaying` delegates to `MediaPlayer.isPlaying`. Call `activate()` when a player is started via gapless handoff (sets `hasPlayedOnce` so speed/pitch params apply).
  - `RadioNotificationService` — the Android `Service`. Signals start/stop via `RadioNotificationService.events` (an `Eventer`).
- **`Database`** — wraps `CacheDatabase` (Room, song cache + last.fm cache) and `PersistentDatabase` (Room, playlists). Uses `fallbackToDestructiveMigration()`.
- **`LastFmService`** — fetches album/artist scrobble counts from Last.fm API, persists to `lastfm_cache` Room table.
- **`LastFmScrobbler`** — plain `object` (not a `Symphony.Hooks` service) in `services/lastfm/`. Handles all write-path Last.fm API interactions: `sign()` (MD5 sig), `getToken()`, `buildAuthUrl()`, `getSession()`, `scrobble()`, `getRecentTracks()`, `getRecentTracksPage()` (paginated, supports `from`/`to` unix-second params). Used directly from composables on IO thread.
- **`LastFmBackupService`** — `Symphony.Hooks` service that backs up scrobble history to a SAF directory and derives per-song play counts. Two sync modes:
  - *Initial pull*: timestamp-checkpoint approach resumable across restarts. Fetches newest-first with `to = oldestFetched - 1` per page. Checkpoint is written **only after** a successful SAF write, so a write failure doesn't advance the cursor. Marks `initialComplete` when done.
  - *Daily sync*: collects all pages in memory with `from = newestSeen`, then writes atomically — a partial API failure (null page) abandons the whole sync without touching the CSV or advancing `newestSeen`. Guards against `newestSeen == 0` to prevent accidental full re-pull.
  - Play counts stored in `lastfm_play_counts` Room table (keyed `"artist_lc|track_lc"`), mirrored in a `ConcurrentHashMap` for synchronous lookup via `getSongScrobbleCount(artist, track)`.
  - **Rolling zip archives**: after each CSV append, if `scrobbles.csv` exceeds 5 MB (~50 k rows), the file is read, rows are deduplicated by exact string match (`distinct()`), min/max timestamps extracted for the filename, then written as `scrobbles_YYYYMMDD_YYYYMMDD.zip`. If the zip write fails the partial zip is deleted. Active CSV is deleted after a successful archive.
  - **Rebuild**: `rebuildPlayCounts()` scans the backup dir for `scrobbles.csv` + all `scrobbles*.zip` files, parses via `ZipInputStream.readBytes()` (safe — no cross-entry buffering), and atomically replaces the Room table via `LastFmPlayCountStore.replace()` (`@Transaction` clear + insert).

### Eventer
`utils/Eventer.kt` — simple synchronous pub/sub. `dispatch()` calls all subscribers inline on the calling thread. No threading guarantees.

### Settings
Stored via Proto DataStore (proto3). Schema: `app/src/main/proto/setting.proto`.

- **Reads (reactive/Compose):** `context.symphony.settingsState.collectAsState()`
- **Reads (synchronous):** `symphony.settingsState.value.fieldName`
- **Writes:** `symphony.settings.updateData { it.copy { field = value } }`
- Proto string fields default to `""`, bool to `false`, uint32 to `0`.
- Field number ranges: App 10–19, Appearance 20–29, Home 30–39, Library 40–79, Filtering 80–89, Playback 300–399, NowPlaying 400–499, MiniPlayer 500–599, Last.fm 600–699.
- Used filtering fields: 80 `songs_filter_pattern`, 81 `min_song_duration`, 82 `artist_tag_separators`, 83 `genre_tag_separators`, 84 `hidden_album_ids`, 85 `show_hidden_albums`, 86 `debug_mode`.
- Used Last.fm fields: 600 `last_fm_api_key`, 601 `last_fm_username`, 602 `show_scrobble_counts`, 603 `last_fm_api_secret`, 604 `last_fm_session_key`, 605 `last_fm_backup_dir`, 606 `last_fm_backup_enabled`, 607 `last_fm_backup_newest_seen`, 608 `last_fm_backup_oldest_fetched`, 609 `last_fm_backup_initial_complete`, 610 `last_fm_backup_total`, 611 `last_fm_backup_fetched_count`, 612 `last_fm_backup_last_sync`.

### HTTP
`HttpClient` singleton in `utils/Http.kt` (OkHttp). Pattern:
```kotlin
val req = Request.Builder().url(url).build()
val body = HttpClient.newCall(req).execute().body?.string()
val json = JSONObject(body)
```

### Room Database
`CacheDatabase` — bump `version`, add entity to `entities = [...]`, add `AutoMigration(from, to)`, add abstract DAO getter. `fallbackToDestructiveMigration()` is set — a failed migration wipes all cached data (songs will re-scan on next launch).

`PersistentDatabase` — stores user data (playlists). Does **not** use `fallbackToDestructiveMigration()`. Always add a proper `AutoMigration(from, to)` when changing its schema. New nullable/defaulted columns work automatically; other changes may require a `MigrationSpec`.

### Playlist Soft Delete
Playlists are soft-deleted (`ignored = 1` in DB) rather than hard-deleted, so the `.m3u` file on disk is preserved. `PlaylistRepository` maintains an in-memory `ignoredIds` set (loaded from DB at `fetch()`, updated immediately on `delete()`). `add()` checks this set so rescans don't resurrect ignored playlists. To hard-delete a playlist row from the DB, call `PlaylistStore.delete()` directly (used internally for ID swaps only).

---

## UI

### Navigation
All routes are `@Serializable` objects or data classes. Registered in `ui/view/Base.kt` via `baseComposable<RouteType>`. `navController.navigate(RouteObject)` to navigate.

### ViewContext
Passed through all composables. Contains `symphony`, `activity`, `navController`.

### Settings Screens
Pattern: `@Serializable object FooSettingsViewRoute` + `@Composable fun FooSettingsView(context: ViewContext)`.
To add a new one:
1. Create `ui/view/settings/FooSettingsView.kt`
2. Register route in `Base.kt`
3. Add tile in `Settings.kt`

Available settings tile components (in `ui/components/settings/`): `SettingsSimpleTile`, `SettingsSwitchTile`, `SettingsSliderTile`, `SettingsTextInputTile`, `SettingsSideHeading`, `SettingsLinkTile`.

### Multi-Select Editing Pattern
Two parallel patterns exist — one for albums, one for songs.

**Albums:** `AlbumsPageState` (in `Albums.kt`) implements `HomePageState`. `AlbumGrid` accepts `pageState: AlbumsPageState?`. Long-click a tile to enter multi-select; click toggles selection. `SquareGrooveTile` renders the checkbox overlay. `BulkAlbumEditDialog` takes `albumIds` and resolves songs from them. The multi-select bottom bar (select all / edit / hide / exit) is overlaid in `AlbumsView`.

**Songs:** `SongsPageState` (in `Songs.kt`) implements `HomePageState`. `SongList` accepts `pageState: SongsPageState?`. Long-click a `SongCard` to enter multi-select; click toggles selection. `SongCard` renders a `Checkbox` replacing the `leading` slot and highlights the card. `BulkSongEditDialog` takes `songIds` directly. The multi-select bottom bar (select all / edit / exit) and the dialog are both embedded inside `SongList` itself, so any view that passes a `pageState` to `SongList` automatically gets multi-select. `SongsPageState` is registered in `Home.kt`'s `pageStates` map and also instantiated locally in Album, Artist, AlbumArtist, Genre, Playlist, and Folders views.

`BulkAlbumEditDialog` / `BulkSongEditDialog` both use `SONG_TAG_FIELDS` + `AudioMetadataParser.write()` then `groove.fetchPaths()` to persist changes. Fields with multiple distinct values across the selection show `"(multiple values)"` placeholder and write only if a new value is entered.

### Album Filter Dialog (`AlbumFilterDialog.kt`)
Two kinds of filter fields:
- **`StringFilterField`** — backed by custom song tags (e.g. `RELEASETYPE`). Available values loaded dynamically from `AlbumRepository.getAvailableTagValues()`. Defined in `ALBUM_STRING_FILTER_FIELDS`.
- **`DebugAlbumFilterField`** — fixed set of values; matching logic lives in `AlbumRepository.getAlbums()`. Defined in `ALBUM_DEBUG_FILTER_FIELDS`. Only shown in the dialog when `settings.debugMode` is `true`.

Current debug filter fields (proto fields 8–9 on `AlbumFilter`):
- **Bitrate** (`bitrate_range`) — buckets: `<150 kbps`, `128–256 kbps`, `256–320 kbps`, `≥320 kbps`, `unknown`. Classified by the *minimum* known bitrate across all songs in the album (tracked in `AlbumRepository.minBitrateCache`). Threshold for "low" is 150,000 bps.
- **Artwork Source** (`artwork_source`) — `embedded`, `directory file`, `none`. Determined per-album at filter time by checking `directoryArtworkCache` first (same priority as `SongRepository.getArtworkUri`).

Debug filters are only applied when `debugMode` is `true`; the `AlbumFilter` proto fields are otherwise ignored.

Presets save and restore both string and debug field states via the shared `AlbumFilter` proto message.

### Sort Labels
`AlbumSortBy.label(context: ViewContext)` is an extension function at the bottom of `AlbumGrid.kt`. Same pattern for `ArtistSortBy` in `ArtistGrid.kt`.

### Cover Art Search Pattern
`AlbumCoverArtDialog` (and `AddWishlistAlbumDialog`) include a "Search on Covers" button that:
1. Copies a search query (album name) to the clipboard via `LocalClipboardManager`
2. Opens `https://covers.musichoarders.xyz/` via `LocalUriHandler`
3. Shows a hint string (`R.string.CoverSearchHint`) once clicked

Reuse this pattern wherever a URL input field needs a cover-art lookup shortcut. The existing strings `SearchOnCovers`, `CoverSearchHint`, and `ImageUrl` in `strings.xml` cover it.

### Last.fm Auth Flow
Auth lives in `LastFmSettingsView.kt`. Flow: enter API key + secret → tap "Authenticate" → `LastFmScrobbler.getToken()` on IO thread → open browser with `buildAuthUrl()` → `AlertDialog` shown → on Done: `LastFmScrobbler.getSession()` → session key saved to `settings.lastFmSessionKey`. "Disconnect" clears the session key. The Authenticate button is disabled when either API key or secret is blank.

### Last.fm Backup UI
Also in `LastFmSettingsView.kt`, below the auth section. SAF directory picker (same `OpenDocumentTree` + `makePersistableReadWriteUri` pattern as wishlist), enable switch, and three conditional states:
- Initial pull not yet started: "Never synced" + "Sync now" button → calls `symphony.lastFmBackup.startInitialPull()`
- Initial pull in progress: `LinearProgressIndicator` with `fetched / total` from `initialPullProgress: StateFlow<Pair<Long,Long>>`
- After first sync complete: "Last synced: \<date\>" + "Sync now" button + "Rebuild play counts from backup" tile

### Manual Scrobbler Tab
`ManualScrobblerView` (in `ui/view/home/ManualScrobbler.kt`) is a home tab — no `HomePageState` subclass needed (no sort/filter dropdown). Uses a `Box` + `LazyColumn` + a `SnackbarHost` anchored at `BottomCenter` (avoids nested `Scaffold`). Timestamp field is pre-filled with current time formatted as `yyyy-MM-dd HH:mm:ss`; copy-to-form from recent tracks also copies the timestamp. Recent tracks list is loaded via `LaunchedEffect(Unit)`; "now playing" entries (`timestampSeconds == 0`) are filtered out.

### Album Timeline Tab
`AlbumTimelineView` (in `ui/view/home/AlbumTimeline.kt`) is a home tab that shows a bar chart of albums per year, sharing the same filter as the Albums view.

- **Shared filter:** reads/writes `settings.uiAlbumGridAlbumFilter` — the same proto field used by `AlbumGrid`. Toggling the filter in either Albums or Timeline updates both.
- **Data:** calls `AlbumRepository.getAlbums()` (with `AlbumSortBy.ALBUM_NAME`, no reverse) then groups results by `Album.startYear`. The full year range (`minYear..maxYear`) is always rendered; years with no albums show an empty column.
- **Chart:** pure Compose, no charting library. A `Box` with `drawBehind` gridlines (25/50/75/100%) layered behind a `LazyRow` of bar columns. Each column uses `Box(contentAlignment = BottomCenter)` + `fillMaxHeight(fraction)` for proportional height. Zero-count bars render as an empty column (no bar, no label).
- **`AlbumTimelinePageState`** is the minimal `HomePageState` pattern — just a `showFilterDialog` flag with a single "Filter" `DropdownMenuItem`. Use this as a template for filter-only tabs that don't need multi-select.
- **Note:** `fillMaxHeight` requires an explicit import (`androidx.compose.foundation.layout.fillMaxHeight`) — it is not pulled in transitively.

### Zap Tab
`ZapView` (in `ui/view/home/Zap.kt`) is a home tab that plays random 5-second snippets from the library. `ZapPageState` holds `isZapping`, `playedSongs`, and `skipTick`. Playback loop uses `LaunchedEffect(isZapping, skipTick)` — Compose cancels the coroutine on any key change, so `delay(5_000L)` is interrupted immediately on Stop or Skip; auto-advance increments `skipTick` after the delay. Each snippet starts at a random position in the middle 80% of the song (10%–90% of `song.duration`) passed via `Radio.PlayOptions(startPosition = ...)` to `queue.add()`.

### String Resources
`app/src/main/res/values/strings.xml`. Always add new strings here rather than using literals in composables.

---

## Key File Locations

| What | Where |
|---|---|
| Proto settings schema | `app/src/main/proto/setting.proto` |
| Room cache DB | `services/database/CacheDatabase.kt` |
| DB accessor | `services/database/Database.kt` |
| Main ViewModel | `Symphony.kt` |
| Nav graph | `ui/view/Base.kt` |
| Settings screen list | `ui/view/Settings.kt` |
| String resources | `app/src/main/res/values/strings.xml` |
| HTTP utility | `utils/Http.kt` |
| Album sort + filter logic | `services/groove/repositories/AlbumRepository.kt` |
| Artist sort logic | `services/groove/repositories/ArtistRepository.kt` |
| Last.fm service | `services/lastfm/LastFmService.kt` |
| Last.fm DB store | `services/database/store/LastFmCacheStore.kt` |
| Radio queue | `services/radio/RadioQueue.kt` |
| Radio session / media session | `services/radio/RadioSession.kt` |
| Radio notification manager | `services/radio/RadioNotificationManager.kt` |
| Radio foreground service | `services/radio/RadioNotificationService.kt` |
| Event bus utility | `utils/Eventer.kt` |
| Album multi-select state | `ui/view/home/Albums.kt` (`AlbumsPageState`) |
| Album bulk tag editor | `ui/components/BulkAlbumEditDialog.kt` |
| Song multi-select state | `ui/view/home/Songs.kt` (`SongsPageState`) |
| Song bulk tag editor | `ui/components/BulkSongEditDialog.kt` |
| Song list (with multi-select) | `ui/components/SongList.kt` |
| Song card (with multi-select) | `ui/components/SongCard.kt` |
| Album filter dialog | `ui/components/AlbumFilterDialog.kt` |
| Album filter field definitions | `services/groove/AlbumFilterFields.kt` |
| Cover art downloader dialog | `ui/components/AlbumCoverArtDialog.kt` |
| Cover art file manager dialog | `ui/components/AlbumArtManagerDialog.kt` |
| Album tile + dropdown menu | `ui/components/AlbumTile.kt` (`AlbumDropdownMenu`) |
| Wishlist data class | `services/groove/WishlistAlbum.kt` |
| Wishlist repository | `services/groove/repositories/WishlistRepository.kt` |
| Wishlist home page | `ui/view/home/Wishlist.kt` |
| Wishlist grid + tile | `ui/components/WishlistGrid.kt` |
| Wishlist add/edit dialog | `ui/components/AddWishlistAlbumDialog.kt` |
| Last.fm scrobbler (write API) | `services/lastfm/LastFmScrobbler.kt` |
| Last.fm backup service | `services/lastfm/LastFmBackupService.kt` |
| Last.fm play count store | `services/database/store/LastFmPlayCountStore.kt` |
| Manual scrobbler home tab | `ui/view/home/ManualScrobbler.kt` |
| Album timeline home tab | `ui/view/home/AlbumTimeline.kt` |
