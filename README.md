# Emre Gallery

Emre Gallery is a native Android gallery MVP built with Kotlin, Jetpack Compose and Material 3. It is based on the supplied screen designs and uses Android MediaStore to access on-device photos and videos.

## Current scope

- Home gallery with date-grouped media and masonry-style cards
- Albums, device folders, custom collections, favourites, videos, recently added, hidden items and Bin
- Search discovery, typing suggestions and submitted results
- Explore surfaces for memories, places and topics
- Immersive image/video viewer with tap-to-show controls and swipe-up details
- Settings and local persistence for collection and media states
- Project planning and implementation checklist in `Plan.md` and `Task.md`

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Android MediaStore
- AndroidX Media3 for video playback
- Gradle Kotlin DSL

## Build

Open the repository root in Android Studio with Android SDK Platform 35 and JDK 17 or newer. Allow Gradle sync, then run:

```powershell
.\gradlew.bat assembleDebug
```

or on Linux/macOS:

```bash
./gradlew assembleDebug
```

The expected output is `app/build/outputs/apk/debug/app-debug.apk`.

## Verification status

The project source has been packaged and checked for basic Kotlin parser-level issues and Android XML validity. A full Android build and on-device run have not been verified in the authoring environment because Android SDK/Gradle installation and network access were unavailable.

## Planned integrations

Production OCR, AI captions and semantic search, robust people/face grouping, map tiles/geocoding, duplicate detection, hierarchical album management and a full non-destructive editor require further implementation. Do not treat these as completed just because the corresponding surfaces are specified.

## Documentation

- [`Plan.md`](Plan.md): product and technical plan
- [`Task.md`](Task.md): implementation checklist and acceptance criteria
