# Emre Gallery — Development Tasks

Track implementation status honestly. A UI surface is not complete until its action uses real data, state persists where appropriate, failures are handled, and the acceptance criteria pass.

## P0 — Buildable foundation

- [ ] `FOUND-01` Add Gradle wrapper and confirm clean Android Studio sync.
- [ ] `FOUND-02` Configure Kotlin, Compose, Material 3, SDK levels and dependency versions.
- [ ] `FOUND-03` Implement adaptive light/dark theme, app typography, spacing and component tokens.
- [ ] `FOUND-04` Add edge-to-edge system bars, insets, navigation and back handling.
- [ ] `MEDIA-01` Implement Android-version-aware media permission request and denial/limited-access states.
- [ ] `MEDIA-02` Query images/videos from MediaStore with stable URI IDs, date, MIME type, dimensions, size and folder metadata.
- [ ] `MEDIA-03` Handle permission revocation, empty libraries, unreadable media and cursor/query failures.

**Acceptance:** clean build; app opens; permission flow is correct; real device media appears without hard-coded gallery entries.

## P1 — Home and viewer

- [ ] `HOME-01` Implement shared floating search entry point on Home.
- [ ] `HOME-02` Implement memories/story strip and date-grouped masonry-style media timeline.
- [ ] `HOME-03` Preserve aspect ratios and scroll position; load thumbnails asynchronously and cancel stale work.
- [ ] `HOME-04` Add date-group selection and batch-selection state.
- [ ] `VIEW-01` Implement immersive photo viewer with initially hidden controls.
- [ ] `VIEW-02` Add tap-to-toggle chrome, back, favourite, more and Share/Edit/Add to/Delete actions.
- [ ] `VIEW-03` Add horizontal media navigation and pinch zoom/pan for photos.
- [ ] `VIEW-04` Implement swipe-up metadata bottom sheet and dismissal gesture.
- [ ] `VIEW-05` Display available image metadata: date/time, location, caption, OCR status/text, tags, people, albums, device, dimensions, size and EXIF fields.
- [ ] `VIDEO-01` Integrate Media3 playback and lifecycle cleanup.
- [ ] `VIDEO-02` Add play/pause, seek, duration and volume controls.
- [ ] `VIDEO-03` Display available video metadata and support swipe-up details.
- [ ] `ACTION-01` Implement safe deletion/trash behavior with platform confirmation and recoverable failures.
- [ ] `ACTION-02` Persist favourites/hidden/Bin states and custom album memberships.

**Acceptance:** photo/video viewer opens from real items, gesture/control behavior matches the spec, actions affect the correct media, and detail fields are omitted or marked unavailable when metadata is absent.

## P1 — Albums

- [ ] `ALBUM-01` Build Albums top bar with search, create, sort, view-mode and filter affordances.
- [ ] `ALBUM-02` Render device folders with representative cover and current item count.
- [ ] `ALBUM-03` Implement custom album creation, rename, cover choice and deletion.
- [ ] `ALBUM-04` Implement nested parent collections and membership persistence.
- [ ] `ALBUM-05` Wire Favourites, Videos, Recently added, Hidden and Bin.
- [ ] `ALBUM-06` Distinguish removing an album from deleting its contents.

**Acceptance:** counts and covers derive from current data; mutations survive process restart; empty folders/collections have useful states.

## P1 — Search

- [ ] `SEARCH-01` Implement Stage A discovery: people/place/album/topic suggestions and recent searches.
- [ ] `SEARCH-02` Implement Stage B typing: keyboard, live categorized suggestions, clear action and filter controls.
- [ ] `SEARCH-03` Implement Stage C submitted results with count, date grouping, sorting, filters and layout selection.
- [ ] `SEARCH-04` Search available filenames, folder names and metadata; debounce, cancel stale queries and support IME submit.
- [ ] `SEARCH-05` Persist and clear recent searches; add no-results, indexing-incomplete and error states.
- [ ] `SEARCH-06` Keep semantic/image search behind an explicit capability interface until embeddings and indexes exist.

**Acceptance:** all three stages are distinct and functional; result counts match filtered results; recent-search clear works; pressing back behaves predictably with keyboard open.

## P2 — Explore

- [ ] `EXPLORE-01` Build memory/story cards from actual dates and media.
- [ ] `EXPLORE-02` Build place cards from GPS metadata and user-confirmed locations.
- [ ] `EXPLORE-03` Integrate a real map provider and permission-aware location preview.
- [ ] `EXPLORE-04` Build topic collections from actual classifiers/tags.
- [ ] `PEOPLE-01` Define a face/person provider interface and local persisted cluster model.
- [ ] `PEOPLE-02` Implement naming, merge/split and exclusion only with an actual face-detection pipeline.
- [ ] `EXPLORE-05` Wire See all routes, counts, loading, empty and error states.

**Acceptance:** no fabricated people/places/topics; each card opens the media it represents; missing metadata is handled gracefully.

## P2 — Settings and indexing

- [ ] `SET-01` Implement settings search and separate Library, Theme, Tools, Intelligence, OCR, Indexing, Duplicates and About destinations.
- [ ] `SET-02` Persist System/Light/Dark theme setting and test contrast.
- [ ] `INDEX-01` Add scan progress, scanned/total counts, last run, error count and retry/rescan actions.
- [ ] `OCR-01` Define an OCR engine interface, language settings, progress, cancellation and retry; wire an engine before claiming OCR works.
- [ ] `AI-01` Define opt-in provider/model selection and capability compatibility; never expose dead provider controls.
- [ ] `DUP-01` Implement actual exact/near-duplicate detection before enabling removal actions.
- [ ] `ABOUT-01` Include version, privacy information, licences and acknowledgements.

## P2 — Quality

- [ ] `A11Y-01` Add content descriptions, semantics, TalkBack order, scalable text and minimum touch targets.
- [ ] `PERF-01` Test large libraries with mixed image/video types, cancellation, paging and memory pressure.
- [ ] `TEST-01` Unit-test media filtering/sorting and state transitions.
- [ ] `TEST-02` Add repository tests with fake MediaStore/data providers.
- [ ] `TEST-03` Add UI tests for navigation, search stages, viewer actions, permissions and empty states.
- [ ] `CI-01` Add GitHub Actions build/test workflow and upload APK artifact on the desired trigger.
- [ ] `REL-01` Build debug APK and verify installation and smoke tests on a physical Android device.

## P3 — Later enhancements

- [ ] `AI-02` Add image/text embeddings and a persisted semantic index.
- [ ] `AI-03` Add captioning/tagging and write back metadata only with explicit user control and recoverable originals.
- [ ] `EDIT-01` Add non-destructive in-app editing with edit history.
- [ ] `SYNC-01` Consider optional encrypted backup/sync as a separate, explicit opt-in feature.

## Release gate

- [ ] All P0 tasks complete.
- [ ] P1 core library, search and viewer criteria pass.
- [ ] No dead controls or false AI status claims.
- [ ] Clean Gradle build and automated checks pass.
- [ ] APK installs and smoke-tests on target Android version.
- [ ] README reflects actual build state and remaining limitations.
