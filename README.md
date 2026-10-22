# Emre Gallery

Native Android gallery MVP built with Kotlin, Jetpack Compose and Material 3, based on the supplied screen designs. Source is in the `app/` module.

## Included

- Home media timeline and memories strip
- Device-folder and collection surfaces in Albums
- Explore surfaces for memories, folders/places and topic shortcuts
- Dedicated Search screen with discovery, type-ahead and submitted-result stages
- Immersive image and video viewer, controls, swipe navigation and metadata panel
- Settings with appearance, local media scan and status surfaces
- MediaStore repository for local image/video discovery
- DataStore persistence for favourites, hidden items, Bin state, search history and theme preference
- Plan and implementation checklist (`Plan.md`, `Task.md`)

## Technology

- Kotlin, Jetpack Compose, Material 3
- Android SDK 35, minimum SDK 26, JDK 17
- MediaStore and AndroidX Media3
- Coil for thumbnails; Preferences DataStore for local preferences

## Build

Open the project root in Android Studio and allow Gradle sync. Build with `assembleDebug` and run `testDebugUnitTest`. The CI workflow is configured for pull requests and manual dispatch; it uploads a debug APK if the build succeeds.

The checked-in `gradlew` scripts are lightweight fallbacks, not the complete official Gradle wrapper. Android Studio or an installed Gradle 8.9+ is required to build unless the official wrapper JAR/scripts are added later.

## Verification and known limitations

A successful Gradle build, CI run, APK creation and physical-device test have **not** been verified. Do not treat this repository as a release-ready or build-verified APK yet.

The current implementation is an MVP. Person/face clustering, actual GPS/map integration, AI captions/tags and semantic search, OCR execution, duplicate detection, full custom/nested album persistence, non-destructive editing, complete deletion/trash integration, robust paging and production video-player lifecycle handling require further work. The matching screen surfaces are clearly labelled where engines are not configured; some current Explore and My Vision cards are visual/navigation placeholders rather than computed AI results.

## Documentation

- [`Plan.md`](Plan.md) — product behavior and technical architecture
- [`Task.md`](Task.md) — implementation checklist and acceptance criteria
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — development expectations
