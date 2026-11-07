# Task: Build an Intelligent, Media-Aware Bookmarking System

**Status:** Planned  
**Priority:** High  
**Target area:** Pocket / Bookmark  
**Branch:** `feature/pocket-inapp-browser-preview`  
**Parent goal:** Turn a saved URL into an indexed collection containing the webpage, its metadata, preview, readable content and discoverable media.

## 1. Product objective

Build a reusable in-app browser and page-enrichment backend for Emre. Saving a URL must do more than store a link and an Open Graph thumbnail: Emre should render the page, resolve accurate metadata, generate a visual preview, extract readable content, discover associated media, and make the result searchable.

The bookmark must be persisted immediately. Crawling and enrichment happen afterwards and must never make saving a link depend on a successful page load.

Emre owns the browser integration, media discovery, persistence and download pipeline. Karakeep is a reference for crawling quality, metadata resolution, content extraction, crawl orchestration and AI-enrichment patterns—not a runtime dependency or a replacement architecture.

## 2. Desired user experience

When a user saves a URL:

1. The bookmark appears immediately with a safe fallback title and domain.
2. Emre opens the URL in its in-app browser backend and waits for a meaningful page-ready condition.
3. The backend resolves metadata and generates a page preview.
4. Emre extracts readable page content and discovers associated images, videos, audio and downloadable files.
5. The discovered information is normalized, validated, deduplicated and attached to the bookmark.
6. Configured AI providers enrich the page and media with summaries, tags, captions and embeddings.
7. The bookmark detail screen presents the page and its associated media as one coherent collection.
8. The user chooses which media to download. Downloads use Emre's existing download queue and storage pipeline.

The user must be able to open the original webpage even if crawling, preview generation or AI enrichment fails.

## 3. Architecture principles

- **Browser-first page understanding:** Resolve the rendered DOM, not just the initial HTTP response.
- **Deterministic discovery:** Browser inspection, HTTP responses and dedicated extractors discover URLs. LLMs must not invent media URLs.
- **Asynchronous enrichment:** Saving is immediate; processing is a separate, observable job.
- **Evidence and provenance:** Retain how each metadata field and media candidate was discovered.
- **One source of truth:** Bookmark, page metadata, preview and discovered media remain linked by a stable bookmark ID.
- **Reuse Emre infrastructure:** Extend the existing media capture, extraction, normalization and download components instead of creating a parallel downloader.
- **Configurable AI:** Text, vision, embedding and OCR capabilities are separate provider roles.
- **Privacy and user control:** Do not silently download every discovered asset or overwrite user-edited bookmark fields.

## 4. Workstreams and requirements

### A. Reusable in-app browser backend

Create a browser abstraction that can be used by Pocket and future link-processing features. Avoid coupling page extraction to a Compose screen or directly to `PocketRepository`.

Expected capabilities:

- Create, load, inspect, capture and dispose a browser session.
- Handle HTTP/HTTPS URLs, redirects, page title changes and canonical URL resolution.
- Execute page JavaScript and inspect the rendered DOM.
- Wait for configurable page-ready conditions: DOM content, a short network-quiet window, and a bounded post-load settle period. Do not wait indefinitely for long-polling pages.
- Observe relevant resource requests and responses without consuming or corrupting the page's network stream.
- Capture cookies and request context only for requests explicitly associated with the user's browser session and subject to the app's privacy rules.
- Support cancellation, timeouts, lifecycle interruption and renderer-process failure.
- Report crawl state and meaningful failure reasons.
- Reuse a browser session when appropriate; do not create an unbounded WebView for every bookmark.

Security requirements:

- Restrict top-level navigation to HTTP/HTTPS unless a separately reviewed handler is introduced.
- Disable file/content access and avoid adding a JavaScript bridge to untrusted pages.
- Keep mixed content blocked and Safe Browsing enabled where supported.
- Validate redirects and resource URLs; prevent local-network, loopback and other disallowed targets from being fetched by background extractors.
- Apply resource, memory and time budgets. Never allow a page to keep a hidden browser alive indefinitely.
- Do not bypass authentication, paywalls, anti-bot controls or site access restrictions. Use only session state the user has legitimately provided.

### B. Metadata resolver

Create a dedicated resolver that gathers candidates from multiple sources and applies deterministic precedence and validation.

Inspect at least:

- Open Graph: title, description, image, site name and URL.
- Twitter/X card metadata.
- Standard HTML title and description.
- Canonical link.
- JSON-LD structured data, including nested article/product/image structures.
- Rendered headings and readable-content fallback.
- Favicon and applicable touch icons.
- Final URL after redirects.

For every field, preserve source/provenance and resolve conflicts consistently. Validate URLs, resolve relative URLs against the final page URL, normalize whitespace and entities, reject empty or malformed values, and avoid replacing user-edited values during later refreshes.

The hero/cover image should be selected separately from the full discovered-media collection. Prefer strong page-level signals such as Open Graph and structured data; use rendered-content candidates as fallbacks.

### C. Preview generation

Generate and persist:

- A representative screenshot/viewport preview of the rendered page.
- A selected cover image URL (and cached thumbnail where appropriate).
- Favicon/site identity assets where practical.

Preview generation must use bounded dimensions and compression, avoid UI-thread disk work, clean up replaced/deleted preview files, and tolerate pages that cannot be rendered or captured. A blank or failed screenshot must not replace a valid existing preview.

Keep screenshot previews distinct from source images: the UI and data model must know which is which.

### D. Readable page content

Extract the main article/page content while excluding navigation, cookie banners, advertisements, repeated menus and unrelated boilerplate where possible.

Retain:

- Plain-text or structured readable content.
- Author/publisher/date metadata when available.
- Content extraction method and source URL.
- Extraction timestamp and crawl status.

Content extraction should be deterministic and testable. AI summarization is a separate enrichment step, not a substitute for extraction.

### E. Media discovery and normalization

Use and extend Emre's existing components where available:

- `MediaCaptureEngine` — rendered DOM and network resource discovery.
- `BrowserSession` — browser-driven extraction.
- `ExtractionCoordinator` — orchestration and engine fan-out.
- `GalleryDlEngine` — yt-dlp/gallery-dl supported extractors.
- `MediaNormalizer` — canonicalization, variant merging and duplicate handling.
- `DownloadQueueManager` / `MediaDownloadWorker` — user-selected downloads.

Discover candidate images, video, audio, posters, documents and downloadable files, including lazy-loaded and dynamically inserted resources. Do not copy the crawler's media-blocking behavior from archiving-focused systems.

Each candidate should retain:

- Stable ID and bookmark ID.
- Original and normalized URL.
- Media type and validated MIME type.
- Discovery source/provenance.
- Dimensions, duration, resolution and size when available.
- Referer and the minimum required request context.
- Thumbnail/poster where available.
- Validation state, duplicate relationship and confidence/evidence.
- Download state, if the user elects to download it.

Filter trackers, obvious ads, tiny decorative images and invalid resources without discarding legitimate content. Merge duplicate/quality variants while retaining the best candidate and its provenance. Candidate scoring must be explainable; do not let an LLM overrule failed URL or MIME validation.

Do not automatically download every discovered asset. The bookmark stores references and metadata until the user selects assets for download.

### F. AI enrichment and model configuration

Integrate through Emre's existing AI configuration/provider infrastructure. Do not hardcode one model or make crawling dependent on AI availability.

Separate roles:

- **Text inference:** page summaries, tags, categorization and metadata assistance.
- **Vision inference:** image captions, visual categories and media understanding.
- **Embeddings:** semantic indexing of page content and media descriptions.
- **OCR:** text extraction from image/document content.

Support configured local and remote providers through their existing interfaces. Record model/provider identity and processing status. Make AI jobs retryable and independently disableable. AI output must be treated as enrichment, not ground truth; preserve original extracted evidence.

### G. Persistence and job orchestration

Keep bookmark metadata separate from the potentially large set of discovered media records. Use stable IDs and explicit relations. Evaluate Room or the existing persistent repository abstraction for bookmark-media records rather than continually growing the DataStore JSON array.

The enrichment pipeline needs:

- States such as queued, loading, extracting, previewing, enriching, complete, partial and failed.
- Idempotent jobs; saving the same URL must not create uncontrolled duplicate work.
- Bounded concurrency, per-domain rate limits, timeouts and exponential backoff.
- Cancellation and retry controls.
- Partial success: metadata can succeed while media extraction fails, and vice versa.
- Progress and last-updated timestamps.
- Safe cleanup when a bookmark is deleted or refreshed.
- User edits protected from background metadata refreshes.

### H. Bookmark UI

The bookmark detail view should present, in a consistent hierarchy:

1. Page identity: favicon, site, title, URL and date.
2. Hero/cover and rendered page preview.
3. Description, readable content and AI summary where available.
4. Tags and relevant metadata.
5. Associated media, grouped by type and showing useful details.
6. Extraction/enrichment status and retry action when needed.
7. Explicit download/selection actions connected to Emre's existing queue.

Keep the bookmark useful when only partial information is available. Do not show placeholder media as though it were successfully extracted.

## 5. Suggested implementation phases

### Phase 1 — Browser backend and preview foundation
- Define browser session and result interfaces.
- Implement bounded page loading, redirect handling, page-ready waits and lifecycle cleanup.
- Implement DOM metadata resolution and field precedence.
- Implement local preview capture and safe persistence.
- Integrate the backend with Pocket without blocking immediate bookmark creation.
- Add fallback, failure and cancellation handling.

### Phase 2 — Page content and crawl orchestration
- Add readable-content extraction.
- Add a persistent enrichment job/state model.
- Add retries, per-domain limits, idempotency and progress reporting.
- Preserve user-authored fields during recrawls.

### Phase 3 — Media discovery
- Connect the browser's DOM/network observations to Emre's existing extraction components.
- Merge Gallery-dl/yt-dlp and browser candidates.
- Add validation, provenance, filtering, deduplication and confidence/evidence.
- Persist bookmark-media relations separately from bookmark metadata.

### Phase 4 — AI enrichment
- Connect configured text, vision, embedding and OCR providers.
- Add independent, retryable AI jobs.
- Persist tags, summaries, captions and embeddings with model provenance.

### Phase 5 — Bookmark collection experience
- Present the preview, readable content and media collection.
- Add per-item selection and download actions.
- Connect downloads to the existing queue and show download states.
- Add refresh, retry and partial-failure UI.

## 6. Acceptance criteria

### Browser and metadata
- A static HTML page resolves its title, description, canonical URL, site name, favicon and image metadata.
- A JavaScript-rendered page resolves metadata inserted after initial HTML load.
- Redirects resolve to the final page URL while preserving the originally saved URL.
- Relative image, canonical and favicon URLs resolve against the final page URL.
- Conflicting metadata follows documented precedence and records provenance.
- A timeout, SSL failure, renderer crash or offline state does not lose the bookmark.
- Cancellation always disposes the browser and does not leave hidden WebViews behind.

### Preview and content
- A successfully rendered page produces a nonblank, compressed local preview.
- A failed or blank capture does not replace a previously valid preview.
- Preview files are cleaned up when replaced or when the bookmark is deleted.
- Readable-content extraction excludes common boilerplate on representative article pages.

### Media
- Lazy-loaded images and media inserted after page load can be discovered.
- DOM and network observations do not interfere with page playback or consume response bodies.
- Invalid, tracker and duplicate candidates are filtered/merged with explainable evidence.
- HLS/DASH streams are not incorrectly treated as ordinary downloadable files.
- No media is downloaded until the user explicitly selects it.

### Reliability and AI
- Bookmark saving returns/persists before crawling completes.
- Crawl and AI work are independently retryable and cancellable.
- AI being unavailable does not block bookmark creation, metadata resolution or media discovery.
- User-edited title, note, description and tags are not silently overwritten.
- Repeated enrichment does not create duplicate bookmark-media records.

### Integration and quality
- Existing Emre extraction and download APIs are reused rather than duplicated.
- Unit tests cover URL normalization, metadata precedence, candidate validation, deduplication and persistence migration.
- Instrumented tests cover WebView lifecycle, DOM extraction, preview generation and cancellation.
- Run `testDebugUnitTest` and `assembleDebug` successfully in GitHub Actions.

## 7. Explicit non-goals

- Replacing Emre's downloader with Karakeep's downloader.
- Automatically downloading all page media.
- Using an LLM as the primary URL crawler or treating generated URLs as verified assets.
- Bypassing authentication, DRM, paywalls or anti-bot protections.
- Rebuilding the Pocket UI before the underlying browser, metadata and persistence contracts are stable.
- Introducing a server dependency for core bookmark capture when the work can run on-device.

## 8. Current implementation baseline

The current feature branch contains an initial WebView-based metadata and preview implementation in:

- `PocketBrowserBackend.kt`
- `PocketRepository.kt`
- `PocketItem.kt`
- `PocketDetailScreen.kt`
- `GalleryApp.kt`

This is a foundation, not completion of the requirements above. In particular, the full reusable browser abstraction, robust resource interception, readable-content extraction, persistent crawl orchestration, complete media discovery, AI enrichment and end-to-end tests remain follow-up work.

The Android workflow was also updated to run on `feature/**` branches. Verify CI on the feature branch before treating Phase 1 as complete.
