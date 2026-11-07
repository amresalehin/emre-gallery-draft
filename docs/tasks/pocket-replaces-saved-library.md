# Task: Replace the legacy Saved Library with Pocket

**Status:** Planned  
**Integration branch:** `task/pocket-replaces-saved-library`  
**Base branch:** `restore-point-before-removing-downloader-20261107110000`  
**Pocket source branch:** `fix/pocket-build-failure`  
**Eventual PR target:** `Develop`

## Objective

Make **Pocket the one canonical saved-content library** in Emre Gallery.

Pocket replaces the legacy `DownloaderViewMode.SAVED_LIBRARY` experience in the old Media Inbox. This is a product replacement, not an addition of a second saved-library screen.

Preserve the existing browser, media extraction, download queue, background worker, downloader engines, and other non-library downloader functionality from the restore-point branch. Saved and downloaded content should converge into Pocket rather than being split between two competing libraries.

## Product decisions

1. Pocket is the single destination for saved webpages, bookmarks, visual clippings, notes, tags, collections, and downloaded media associated with saved content.
2. Remove the old Saved Library mode, tab/filter, and duplicate navigation entry points once Pocket is fully integrated.
3. Keep the browser, extraction, download queue, download processing, and other downloader capabilities. Do not remove downloader infrastructure merely because its old Saved Library UI is being replaced.
4. A webpage/source should normally have one Pocket entry. Multiple downloaded images, videos, or audio files from that source become attachments on that entry.
5. Keep local media files in place during migration. Do not move or delete the files as part of metadata migration.
6. Preserve media-only legacy records even when they have no usable source URL. Do not fabricate a webpage URL just to fit the current Pocket model.

## Branch integration strategy

Use the restore point as the integration base because it retains the downloader/browser implementation.

Port the Pocket-specific implementation from `fix/pocket-build-failure` selectively:

- `pocket/PocketItem.kt`
- `pocket/PocketRepository.kt`
- `pocket/PocketScreen.kt`
- `pocket/PocketDetailScreen.kt`
- the required Pocket navigation, destination, and layout changes

Reconcile shared integration points manually rather than replacing whole files:

- `ui/GalleryApp.kt`
- `ui/Destination.kt`
- `ui/NavigationComponents.kt`
- `ui/AlbumsScreen.kt`
- `GalleryApplication.kt`
- `MainActivity.kt`
- `AndroidManifest.xml`
- `app/build.gradle.kts`
- root Gradle configuration and version catalog, if present

**Do not merge the Pocket branch's downloader-removal changes wholesale.** The branch comparison shows that its six-commit delta removes downloader/browser files, Python integration, media-inbox code, and downloader tests alongside adding Pocket. Preserve the restore-point versions of those capabilities, then integrate Pocket around them.

## Verified legacy storage and migration source

The restore-point branch's `MediaInboxRepository` stores data in:

- SharedPreferences file: `media_inbox_prefs`
- key: `inbox_stacks_json`

The legacy data model is:

- `MediaInboxStack`: `id`, `title`, `sourceDomain`, `sourceUrl`, `items`, `timestampMillis`, `description`
- `MediaInboxItem`: `id`, `title`, `localUri`, `sourceUrl`, `isVideo`, `isAudio`, `isStream`, `format`, `sizeBytes`, `dateAddedMillis`

The Pocket branch's `PocketRepository` stores JSON in DataStore under:

- key: `pocket_items_data_v1`

The current `PocketItem` has title, URL, domain, remote `imageUrls`, timestamp, note, tags, description, favourite state, and read state. It does **not** currently model local downloaded-file attachments. Remote preview images and local downloaded files must remain distinct fields/types.

### Migration safety warning

The legacy repository's `deleteStack()` and `clearAll()` methods delete local files referenced by the stacks. The `addItemToStack()` replacement path can also delete a replaced local file.

Therefore:

- Do not invoke `clearAll()`, `deleteStack()`, or destructive replacement behavior as part of migration.
- Do not delete or rewrite the legacy SharedPreferences payload during the initial migration.
- Do not assume a failed Pocket JSON decode means the existing Pocket library is empty. The current Pocket repository falls back to an empty list on decode failure; harden this path so corrupt or unreadable data causes a safe abort, not an overwrite.
- Keep a recoverable copy of the original legacy JSON and verify destination records and file references before considering migration complete.

## Work plan

### Phase 1 — Establish the integration baseline

- [ ] Create the integration branch from `restore-point-before-removing-downloader-20261107110000`.
- [ ] Record the restore-point commit SHA and the Pocket source commit SHA in the eventual PR.
- [ ] Build and run the restore-point branch before integration; capture the baseline test/build result.
- [ ] Inventory all legacy Saved Library entry points, modes, labels, filters, routes, and repository call sites.
- [ ] Inventory browser, scraper, extractor, queue, worker, downloader, and share-intent entry points that must remain functional.

### Phase 2 — Port Pocket without removing downloader infrastructure

- [ ] Port the Pocket model, repository, list screen, detail screen, and required UI assets.
- [ ] Reconcile navigation and application initialization without replacing the restore-point implementation wholesale.
- [ ] Keep existing browser and downloader dependencies, manifest declarations, services, receivers, workers, and permissions required by the preserved implementation.
- [ ] Remove only dead or duplicate Saved Library presentation code after Pocket is wired.
- [ ] Keep unrelated gallery, album, viewer, and storage-statistics behavior unchanged.

### Phase 3 — Extend Pocket's data model for downloaded media

- [ ] Add a typed `PocketAttachment` model for local downloaded assets.
- [ ] Include at least: stable attachment ID, local URI/path, source URL, media type/flags, format, size, and date added.
- [ ] Persist attachments in Pocket JSON and implement backward-compatible deserialization for existing Pocket records with no attachments.
- [ ] Support multiple attachments per Pocket entry without duplicating the source webpage.
- [ ] Support legacy media-only records with no source URL.
- [ ] Keep remote preview image URLs separate from local attachment URIs.
- [ ] Ensure deleting a Pocket entry has an explicit, safe attachment-retention/deletion policy; do not silently delete files during migration.

### Phase 4 — Migrate the old Saved Library

- [ ] Read and parse `media_inbox_prefs/inbox_stacks_json` without mutating the source.
- [ ] Validate the full source payload before writing any migrated records.
- [ ] Preserve stack grouping by mapping one legacy source stack to one Pocket entry where appropriate.
- [ ] Preserve stack ID, title, source URL/domain, description, timestamp, and all item IDs/metadata.
- [ ] Map every `localUri` to a Pocket attachment; preserve the exact URI and verify it resolves where possible.
- [ ] Merge with existing Pocket records by stable ID/source URL using deterministic duplicate handling; do not discard either side's attachments.
- [ ] Handle missing fields, empty URLs, duplicate source URLs, duplicate media IDs, malformed JSON, and unreadable files safely.
- [ ] Make migration idempotent so retrying or restarting cannot create duplicate Pocket entries or attachments.
- [ ] Record migration version/status only after a validated write succeeds.
- [ ] Keep the original SharedPreferences data intact as a recovery source until release verification is complete.
- [ ] Report migrated stack count, Pocket-entry count, attachment count, skipped/invalid records, and unresolved local URIs for verification.

### Phase 5 — Connect browser and downloader flows to Pocket

- [ ] Make Save URL / Share to Emre create or update a Pocket entry.
- [ ] Reuse existing browser/session and metadata-extraction capabilities; avoid creating a second competing extraction pipeline.
- [ ] On successful download, resolve the source Pocket entry and attach the downloaded asset to it.
- [ ] Group multiple assets from the same source under one Pocket entry.
- [ ] Ensure retries and duplicate downloads do not create duplicate attachments.
- [ ] Keep download queue/progress/error/retry controls accessible independently of the old Saved Library mode.
- [ ] Preserve existing downloads and local files if a Pocket metadata update fails; surface a recoverable error instead of deleting the output.

### Phase 6 — Replace the legacy Saved Library UI

- [ ] Replace the old Saved Library destination/tab/filter with the Pocket destination.
- [ ] Redirect all legacy Saved Library shortcuts and menu entries to Pocket.
- [ ] Remove `DownloaderViewMode.SAVED_LIBRARY` and its UI-only state after all call sites are migrated.
- [ ] Retain non-library downloader modes and browser entry points.
- [ ] Avoid duplicate Pocket/Saved Library destinations or duplicate saved-content lists.
- [ ] Keep device media statistics/overview distinct from Pocket; it is not the saved-web-content library.

### Phase 7 — Tests and regression validation

- [ ] Unit-test legacy JSON parsing and conversion for all legacy fields.
- [ ] Test migration with empty, malformed, partially populated, and duplicate legacy datasets.
- [ ] Test migration when Pocket already contains records.
- [ ] Test migration idempotency across repeated runs and simulated interruption.
- [ ] Test that migration never deletes, moves, or rewrites local media files.
- [ ] Test that all migrated local URIs and attachment metadata survive app restart.
- [ ] Test backward compatibility with old `pocket_items_data_v1` records that contain no attachments.
- [ ] Test save URL, share-to-Pocket, note/tag editing, read/favourite state, and Pocket detail navigation.
- [ ] Test download completion attaching media to the correct Pocket entry.
- [ ] Regression-test browser sessions, media extraction, download queue, retries, background worker, playback, and share receiver.
- [ ] Regression-test gallery home, albums, viewer, and device storage overview.
- [ ] Run unit tests and the debug APK build through GitHub Actions.

### Phase 8 — Finish integration

- [ ] Review the final diff for accidental downloader/Python/manifest/dependency removals.
- [ ] Verify no legacy Saved Library navigation remains.
- [ ] Verify migration diagnostics and recovery behavior.
- [ ] Update relevant documentation and user-facing labels.
- [ ] Open a focused PR from this integration branch into `Develop`; do not merge directly into `Develop` before CI and regression checks pass.

## Acceptance criteria

The task is complete only when:

- [ ] Pocket is the sole user-facing saved-content library.
- [ ] Legacy Saved Library stacks appear in Pocket with their metadata and downloaded media intact.
- [ ] Existing Pocket entries remain intact and are not duplicated.
- [ ] Local attachment URIs/files remain valid after migration and app restart.
- [ ] Migration is idempotent, recoverable, and non-destructive.
- [ ] Browser, metadata extraction, download queue, downloader engines, background processing, and share flow still work.
- [ ] No duplicate Saved Library/Pocket route remains.
- [ ] Existing gallery and viewer behavior remains intact.
- [ ] CI unit tests pass and the debug APK builds successfully.

## Out of scope

- Rewriting the downloader engine or browser architecture.
- Replacing existing extraction engines with a new scraping framework.
- Deleting legacy files or SharedPreferences data as part of the initial migration.
- Redesigning unrelated gallery, album, viewer, or device-storage screens.
- Merging the integration into `Develop` before the acceptance criteria are met.

## Source references

- [Pocket implementation branch](https://github.com/amresalehin/emre-gallery-draft/tree/fix/pocket-build-failure)
- [Downloader-preserving restore point](https://github.com/amresalehin/emre-gallery-draft/tree/restore-point-before-removing-downloader-20261107110000)
- [Branch comparison](https://github.com/amresalehin/emre-gallery-draft/compare/restore-point-before-removing-downloader-20261107110000...fix/pocket-build-failure)
