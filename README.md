# Nocturne Music — Android MP3 Player

Experimental Android native Kotlin app. **Source project, not a tested APK.**

## Features
- Local MP3 library via Android MediaStore (Android 10+)
- Embedded album artwork via MediaMetadataRetriever
- Playback, pause, seek, previous/next, shuffle, repeat
- Search by title, artist and album; sorting; favorites
- MP3 ID3v2.3 tag editor (title, artist, album, genre, year, track) with suggestions from existing names; matches substrings
- Android permission dialogs for editing and deleting audio files
- Playback queue, four accent colors, app icon
- Favorites JSON export/import; app updates retain preferences when app ID and signing key remain identical
- No internet permission, no ads

## Build APK
Option A: Android Studio (JDK 17, Android SDK 35): open project, sync Gradle, **Build > Build APK(s)**.

Option B: Upload this project's files to a GitHub repository, open **Actions > Build Android APK > Run workflow** and download the generated `NocturneMusic-debug-apk` artifact.

APK path after a successful build: `app/build/outputs/apk/debug/app-debug.apk`.

**Important:** GitHub Actions debug APKs may use a different debug signing key across runs. Android requires the same signing key for in-place updates. To maintain favorites across rebuilds, export favorites to JSON before replacing or uninstalling an app, and import afterwards. For production use, sign all releases with a permanent keystore.

## Limitations / roadmap
- No Android SDK was available to compile or run this project in the authoring environment; build and device testing are required.
- Background playback / lock-screen media controls require a foreground MediaSession service, not yet implemented.
- Queue is editable by adding tracks and choosing tracks, but drag-and-drop reordering is not implemented.
- Genre-specific visual themes per song/album are a future feature, not implemented.
- The tag editor preserves normal ID3 frames including embedded APIC artwork; uncommon ID3 flags, compressed/unsynchronized tags, and malformed MP3 files require further validation. Make backups before bulk editing.
- Large artwork decoded directly may consume memory; image caching/downsampling is recommended.
- Favorites use title + artist + album + duration as identity; changing tags outside the app or duplicating identical tracks can affect matching.
- The app currently uses Android platform widgets rather than Jetpack Compose.
