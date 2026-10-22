# Emre Gallery — Product & Technical Plan

## 1. Product goal

Build a native Android gallery that combines a clean, photo-first library with albums, contextual search, discovery surfaces, and an immersive photo/video viewer. The supplied screenshots are the visual reference. This plan describes intended behavior; advanced AI services are not considered implemented until their processing pipeline exists and is tested.

## 2. Platform and principles

- Native Android; Kotlin, Jetpack Compose, Material 3.
- Support adaptive light/dark themes and edge-to-edge layouts.
- Use MediaStore as the primary source for on-device media; respect Android media permissions and scoped storage.
- Keep core browsing and local collection state offline-first.
- Use stable content URIs and metadata IDs; never assume filesystem paths are available.
- Provide accessible touch targets, TalkBack labels, loading/empty/error states, and graceful permission recovery.

## 3. Navigation model

Primary destinations: Home, Albums, Explore, Settings. A shared floating search entry point appears on Home, Albums and Explore and opens a dedicated Search screen. Search transitions are distinct: (1) discovery before typing, (2) live suggestions and category matches while typing, and (3) submitted query results. Photo/video viewing is a separate immersive flow, and swiping upward reveals a metadata panel.

## 4. Home

- Search bar at the top with clear/search entry affordance.
- Horizontal memory/story cards above the main library where space permits.
- Date-grouped photo/video timeline.
- Dense, varied-height masonry-like media layout; preserve aspect ratios and avoid layout jumps as thumbnails load.
- Selection mode, select-all per date group, and batch-action affordances.
- Floating/persistent bottom navigation consistent with the reference.
- Correct scroll restoration when changing tabs or returning from a viewer.

## 5. Albums

- Search field plus create-folder/album, sort, grid/list layout, and filter controls.
- Device folders with representative cover thumbnails and item counts.
- My Vision section for curated or AI-generated collections, including create-collection flow with a title and a matching rule.
- Nested albums grouped into user-facing parent collections such as Personal, Work, Projects and References.
- Other collections: Favourites, Videos, Recently added, Hidden and Bin.
- Album membership, renaming, reordering, deletion, and empty states must be persisted; clearly distinguish deleting an album from deleting media.

## 6. Explore

- Memories/story cards with date or trip ranges.
- People & pets: detected-person clusters, user-assigned names, merging/splitting clusters, and exclusion controls. This requires an actual on-device or remote face pipeline; do not fabricate detections.
- Places: location map/heatmap and place cards sourced from EXIF GPS or user-confirmed location metadata.
- Topic/Things collections such as Mountains, Food, Animals, Flowers, Documents and Outdoor, backed by classifiers/tags rather than static decoration.
- All sections should have a See all route, count, empty state and permission/error handling.

## 7. Search

### Stage A — before typing
- Shared search box, People, Places, Albums, Things, and recent searches.
- Recent searches can be cleared and tapping an entity opens its result route.

### Stage B — while typing
- Keyboard appears, with clear and filter controls.
- Live categorized suggestions for people, places, albums, files and tags.
- Suggestions update as the query changes; support keyboard submit and IME actions.
- Debounce expensive matching; cancel stale queries.

### Stage C — submitted query
- Search title, result count, media-type/date/location refinement chips, relevance sorting and layout selector.
- Date-grouped media results plus matching People, Places, Albums, Files and Tags sections where relevant.
- Handle no results, incomplete metadata, partial indexing and query errors.

The basic MVP may search available filenames/folder names. Semantic text/image search requires an embedding/indexing pipeline and must be labelled unavailable until it exists.

## 8. Photo viewer

- Open with controls hidden for an unobstructed full-screen image.
- Tap toggles chrome: back, favourite, tools/more, and bottom actions for Share, Edit, Add to, Delete and More.
- Horizontal swipe changes media; pinch zoom and pan apply to images, with sensible gesture arbitration.
- Swipe up opens a bottom-sheet details panel; dismiss downward or close to return to the clean viewer.
- Show name, capture date/time, location/map preview, description/caption, OCR text status, tags, people, album membership, camera/device fields, dimensions, file size, and available EXIF/IPTC/XMP metadata.
- Editing is delegated to an installed system/editor app in the MVP; do not claim in-app non-destructive editing until implemented.
- Mutating actions require appropriate confirmations and handle permission or provider failures.

## 9. Video viewer

- Same immersive entry, hidden chrome and tap-to-show controls as photo viewer.
- Media3 playback, play/pause, seek bar, elapsed/total duration, mute/volume, and correct lifecycle handling.
- Swipe left/right navigates media; swipe up opens the same details sheet.
- Metadata includes duration, resolution, frame rate and codec when available, file size, date/time, location, caption/tags/people/albums and other available metadata.
- Handle audio focus, rotation/config changes, buffering, unsupported codec, revoked URI and player release.

## 10. Settings

- Search settings.
- Library overview and indexing status, including media count, scanned count, errors and last scan.
- Theme: system, light, dark; consistent Material 3 tokens.
- Tools: utilities for local media workflows.
- Intelligence: provider/model choices and opt-in status when actual services exist.
- OCR: engine/language settings and batch progress when the OCR pipeline is available.
- Duplicates or Similar: real similarity/deduplication workflow only after implementation.
- About: app version, licences, privacy description and open-source notices.

## 11. Data and architecture

Recommended layers:

- `data/media`: MediaStore queries, paging, URI handling, metadata and thumbnail access.
- `data/library`: favourites, hidden IDs, Bin state, search history and user collections.
- `domain`: use cases for querying, filtering, sorting, mutations and state transitions.
- `ui`: four primary destinations, search stages, media viewer, detail sheet and settings.
- `media`: Media3 lifecycle and thumbnail/media loading.
- `ai` (future): explicit interfaces for OCR, captioning, tagging, embeddings and face clustering; each operation records status/errors and can be retried.

Use Room/DataStore as appropriate for durable app state. Avoid placing all logic in a single Activity/ViewModel. Keep UI state immutable and lifecycle-aware. Add repository and use-case tests.

## 12. Privacy and permissions

- Request only required media permissions, using Android-version-specific image/video permissions when applicable.
- Explain access before permission prompts; handle limited media access and revoked permissions.
- No media upload without clear disclosure and explicit user opt-in.
- Avoid logging filenames, locations or personal metadata by default.
- Hidden and Bin semantics must be clear; local hiding is not cryptographic protection.

## 13. Performance

- Use paging/lazy layouts and thumbnail-sized requests; never decode an entire library at full resolution for the grid.
- Cache thumbnails carefully and cancel work on scroll/navigation.
- Avoid repeated full MediaStore scans; support incremental refresh and recoverable indexing.
- Profile with large libraries, mixed formats and lower-memory devices.

## 14. Delivery phases

1. Foundation: project setup, theme, navigation, permissions and MediaStore repository.
2. Library: Home timeline, folder/album listings, viewer, Media3 playback, details panel and local persistence.
3. Search: discovery, live suggestions, submitted results, filters and history.
4. Explore: memories and metadata-derived place/topic collections; wire map and people providers separately.
5. Settings, accessibility, mutation safety, tests, performance and release automation.
6. AI extensions: OCR, captions/tags, face grouping, embeddings and duplicate detection with clear opt-in, status and fallback behavior.

## 15. Definition of done

Each major interaction works with real device media and persisted state, permissions are correctly handled, empty/error/loading states are present, TalkBack and large text are usable, scrolling remains responsive on large libraries, photo/video navigation and details gestures work, automated tests pass, and a clean Android build produces an APK. Screenshots alone do not satisfy completion.
