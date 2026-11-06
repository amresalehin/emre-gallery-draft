# Unified media pipeline refactor

## Problem found

The downloader already had a multi-provider coordinator, but it was not the sole owner of candidate policy. `DualMediaExtractor` maintained a large custom merge/poster-folding algorithm, `MediaClassifier` had another deduplication implementation, `DownloaderSession` independently merged items, and browser capture inserted discoveries into the session directly. At the same time, gallery-dl and yt-dlp could both be scheduled for the same URL, and provider selection used loose substring matching for yt-dlp hosts.

## Plan and implementation

1. **Keep discovery specialized.** Static HTML/DOM parsing, browser/network capture, gallery-dl, and yt-dlp remain providers; do not duplicate their discovery logic or replace site-specific capabilities.
2. **Centralize candidate policy.** `MediaCandidatePipeline` is the shared boundary for rejecting invalid media, transport fragments, ad/tracker endpoints, and image UI chrome; it delegates canonical URL ranking, duplicate merging, and video-poster folding to the existing classifier rules.
3. **Centralize list updates.** The coordinator and `DownloaderSession` now call the shared pipeline. Existing IDs are retained when a higher-quality duplicate replaces an item, keeping UI selection keys stable.
4. **Choose one specialized extractor.** Exact host/subdomain matching selects yt-dlp for its core video/audio hosts. Otherwise, a supported gallery-dl extractor may run. Static HTML and browser discovery remain complementary and concurrent because they capture different page behavior.
5. **Protect against regressions.** Add pure unit coverage for junk rejection, quality selection and ID stability, YouTube poster folding, and lookalike host rejection.

## Intentional boundaries

This change does not delete the legacy extractor implementations, change downloader storage, bypass site authentication, or attempt to defeat anti-bot challenges. It reduces overlapping orchestration and ensures every item entering shared results/session state receives the same quality and junk policy. Existing provider-specific parsing remains available behind the coordinator.

## Validation

Run `bash ./gradlew --no-daemon testDebugUnitTest` (and the CI `assembleDebug` task where an Android SDK/NDK is available). The development environment's checked-out `gradlew` lacks its executable bit, so invoke it through `bash` rather than changing file permissions.
