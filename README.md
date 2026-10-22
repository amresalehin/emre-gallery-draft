# Emre Gallery

Native Android gallery MVP based on the supplied designs, using Kotlin, Jetpack Compose, Material 3 and Android MediaStore.

## Included source
- Home timeline with memories strip and masonry-style media tiles
- Albums screen with device folders, collection placeholders, and Favourites/Videos/Recently added/Hidden/Bin shortcuts
- Explore screen with memories, folder-derived place shortcuts and topic placeholders
- Dedicated Search screen with discovery, typing suggestions, and submitted results
- Immersive photo/video viewer with toggleable controls, horizontal navigation, and swipe-up details panel
- Settings screen with theme, tools, OCR/intelligence/index status placeholders and permissions shortcut
- Local DataStore preference persistence for favourites, hidden items, Bin, recent searches and theme
- Plan and task checklist

## Technology
- Kotlin and Jetpack Compose / Material 3
- Android SDK 35; min SDK 26; JDK 17
- MediaStore; AndroidX Media3; Coil; Preferences DataStore

## Build
Open the project root in Android Studio and allow Gradle sync. Run `assembleDebug` and `testDebugUnitTest`. A pull-request workflow is configured to build, test, and upload a debug APK artifact when successful.

Note: the checked-in `gradlew`/`gradlew.bat` are fallback launchers, not the official Gradle wrapper distribution. Use Android Studio or a locally installed Gradle 8.9+ until a proper wrapper JAR/scripts are added.

## Verification status
No successful Android build, CI run, APK artifact, or on-device test has been verified. This repository is a source MVP, not a release-ready verified APK.

Advanced OCR, captioning/tagging, semantic search, face/person clustering, GPS map integration, duplicate detection, custom nested-album persistence, non-destructive in-app editing, platform deletion/trash integration, robust paging and production-grade video-player lifecycle handling still require work. Screens that depend on unavailable engines are marked as not configured; some Explore/My Vision cards are visual/navigation placeholders rather than computed AI output.

## Documentation
- [Plan.md](Plan.md) — product behavior and architecture
- [Task.md](Task.md) — implementation checklist and acceptance criteria
- [CONTRIBUTING.md](CONTRIBUTING.md) — contribution guidance
