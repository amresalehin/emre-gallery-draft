# Emre Gallery — Complete ImageToolbox Integration Roadmap

**Status:** Planning only. No production integration has been performed by this document.
**Target branch:** `research/image-toolbox-backends`
**Pinned upstream:** `T8RIN/ImageToolbox` at `f78bc23f52d863e97cd0af061c603ac654dedc48`
**Source location:** `vendor/ImageToolbox`
**Primary goal:** deliver the full applicable ImageToolbox feature set inside Emre Gallery, using Emre's own navigation, visual language, media model, storage, image loading, OCR and face-recognition systems.

Related inventory: [IMAGE_TOOLBOX_FULL_SOURCE_INVENTORY.md](IMAGE_TOOLBOX_FULL_SOURCE_INVENTORY.md)
Feature-by-feature disposition: [IMAGE_TOOLBOX_FEATURE_PARITY_MATRIX.md](IMAGE_TOOLBOX_FEATURE_PARITY_MATRIX.md)

---

## 1. Product and engineering decision

The target is **feature parity, not an embedded second application**.

ImageToolbox is a multi-module Android application with its own application shell, navigation, dependency injection, Compose components, resources, settings, libraries and native/model dependencies. Its upstream `:app`, `:feature:root`, navigation and global application setup must not be copied wholesale into Emre. Doing so would duplicate the app shell and conflict with Emre's existing media gallery.

Instead:
- Treat the pinned upstream repository as the complete reference source.
- Extract/adapt the processing engines, feature logic, assets and tests required for each capability.
- Build Emre-native screens and navigation for the tools, reusing Emre's design system and media access.
- Keep every upstream-derived portion compliant with Apache-2.0 and all applicable transitive library/model licences.
- Do not mark a feature complete merely because its source file or dependency has been added. It is complete when users can discover it, run it, cancel it, preview it, export its result and pass its acceptance tests.

**No feature module is intentionally omitted.** The parity matrix classifies all 68 upstream feature modules into Emre feature groups, including which upstream app-shell modules should be represented by existing Emre infrastructure rather than transplanted.

## 2. Existing Emre baseline to protect

The inspected `Develop` baseline is a single Android Gradle module (`:app`) with minSdk 26, compileSdk 35, Java 17, and ABI filters `arm64-v8a` and `x86_64`.

Existing systems to reuse or integrate with:
- **Media access:** MediaStore-backed `MediaRepository` and `MediaItem`.
- **Gallery loading:** Coil 2.7 configured by `GalleryApplication`, custom `MediaStoreThumbnailFetcher`, and `PreparedThumbnailCache`.
- **Face pipeline:** `FaceImageDecoder`, ONNX Runtime, face-engine registry and face-processing coordinator.
- **OCR:** existing Tesseract4Android and Google ML Kit recognition engines.
- **Metadata:** AndroidX ExifInterface and existing map/location metadata access.
- **UI:** existing Compose app, main navigation, viewer, albums and settings.

Do not replace these with upstream equivalents. In particular:
- Do not introduce Coil 3 alongside Coil 2 simply to compile upstream's Coil-3 integrations. Adapt decoder/fetcher integration to Coil 2, or make a separately reviewed migration later.
- Do not package a second ONNX Runtime, Tesseract or ML Kit OCR stack.
- Do not add a second media picker or request media permission again from each tool.
- Do not run long processing operations on the UI thread.
- Do not let processing compete without limits with gallery scrolling, video playback, face indexing or OCR.
- Audit the current gallery memory/concurrency settings before introducing additional caches, dispatchers or image pipelines.

The baseline build and existing tests must be run on the actual integration branch before the first production-code change. The currently inspected GitHub workflow runs `testDebugUnitTest` and `assembleDebug`; retain these gates and extend them with processing-specific tests.

## 3. User experience inside Emre

### 3.1 Tool entry points

Add a searchable **Tools** destination in Emre's existing navigation. It should contain compact categories, recent tools, favourites and a search field. Do not add a second app-style home screen or copy ImageToolbox's bottom navigation.

Recommended categories:
1. Edit
2. Filters & effects
3. AI tools
4. Create & combine
5. Convert & compress
6. Scan & extract
7. PDF & documents
8. Color & design
9. File utilities
10. Advanced tools

### 3.2 Contextual actions

Expose actions in the media viewer's existing action surface:
- Edit, crop, rotate and resize
- Apply filters and effects
- Remove background
- Convert or compress
- Extract text / scan QR
- Edit or remove metadata
- Compare, split or create from selected media
- Export/share

Actions that require several source images should use Emre's existing media selection model. Tools should return to the originating album/viewer context after completion.

### 3.3 Shared tool interaction contract

Every applicable tool should support:
- Input selection from existing media, multi-select, camera capture where relevant, and explicit file import.
- A non-destructive preview and adjustable parameters.
- Clear input/output format, size and destination details.
- Progress for long work, cancellation and recoverable errors.
- Safe output preview before publish.
- Save as a copy by default; never overwrite the source silently.
- Share/open result, and an optional "open in viewer" action.
- Batch mode when its operation is naturally batchable.
- Accessibility labels, large-text resilience, and tablet/foldable layouts.

Keep the interface minimal and consistent with Emre's monochrome visual language. Feature complexity belongs inside the individual tool, not on the gallery home screen.

## 4. Target architecture

Start with clear package boundaries in the existing app. Extract stable boundaries into Gradle modules only after the first vertical slices work. Do not create 68 Gradle modules merely to mirror upstream.

Proposed modules after boundaries stabilise:

```text
:app
:processing:contracts       # image, operation, result, capability, export contracts
:processing:runtime         # dispatchers, bounded queues, memory and cancellation policy
:processing:decode          # URI/stream decoding, bounds, orientation, format detection
:processing:encode          # encoders, compression and format conversion
:processing:metadata        # EXIF/XMP/IPTC read/write and privacy policy
:processing:effects         # transforms, filters, curves, shader execution
:processing:composite       # drawing, layers, collage, stitching, stacking, fusion
:processing:ai              # model registry, background removal and AI operations
:processing:documents       # scanner, OCR adapter, PDF import/export/editing
:processing:utilities      # archives, checksum, cipher, QR, batch file operations
:feature:tools              # Emre-native tool catalogue, discovery and shared navigation
:feature:editor             # single-image editor and edit history
:feature:creation           # creation/composition workflows
:feature:documents          # scan and PDF screens
:feature:ai-tools            # AI feature screens and model manager
:feature:file-tools          # non-image utilities
```

If module extraction causes disproportionate Gradle configuration cost, keep the same boundaries as packages first. One module per upstream tool is not a requirement.

### 4.1 Processing contracts

Use typed, suspendable interfaces. The initial contract set should include:
- `MediaSourceResolver`: resolve MediaItem, content URI, file URI, stream and persisted document URI without duplicating media selection.
- `ImageDecoder`: inspect bounds and decode to a requested target size, software/hardware config and color profile.
- `ImageEncoder`: encode one source to a capability-checked output format and quality.
- `ImageTransformer`: rotate, flip, crop, scale, perspective-correct and compose operations.
- `MetadataReader` / `MetadataWriter`: read/write supported metadata fields with scoped-storage permissions.
- `FilterEngine`: execute a parameterised sequence on CPU/GPU with explicit resource ownership.
- `ImageExportService`: transactional export, validation, metadata policy and MediaStore publication.
- `ProcessingOperation`: progress, cancellation, stage events, errors and result references.
- `CapabilityRegistry`: runtime support by API, codec, ABI, installed model and available memory.
- `ModelRepository`: model catalogue, download, checksum/signature verification, storage accounting, activation and removal.
- `DocumentService`, `OcrProvider`, `BackgroundRemovalProvider`, `StitchService`, `ArchiveService` and `BarcodeService`.

Failures must be typed (unsupported format, permission revoked, decode/encode failure, invalid input, corrupt model, insufficient storage, cancellation). Do not use a nullable bitmap or generic exception as the only outcome.

### 4.2 Non-destructive edit model

Represent edits as a serialisable operation recipe, not as successive destructive bitmap rewrites:
- Source media ID / content URI and source fingerprint
- Ordered operations and typed parameters
- Crop geometry in normalised coordinates
- Filter IDs and parameter values
- Layer/markup payloads
- Output profile and metadata/privacy policy
- Recipe/schema version

Preview at a bounded resolution. Render the final output once during export. Store drafts/history separately from the original media. Support undo/redo and reopening a draft.

### 4.3 Transactional export

All tools that create a file must use the same export path:
1. Validate source, parameters, permissions, free space and target format.
2. Create an app-private temporary output.
3. Stream or render into the temporary target.
4. Flush and validate by reopening/probing the output.
5. Apply explicit metadata policy (preserve, selectively edit, or strip).
6. Publish to MediaStore / selected document provider.
7. Remove temporary artifacts and release bitmaps/native resources on success, failure or cancellation.

Never publish a partially written file. Never modify an original in place without an explicit, confirmed destructive workflow and a recovery strategy.

## 5. Dependency, model and source strategy

### 5.1 No wholesale Gradle transplant

Do not copy upstream `settings.gradle.kts`, `build-logic`, version catalog, plugin aliases, app flavours or global Gradle properties into Emre. The upstream project has its own Kotlin/AGP/Compose/Coil versions and dependency graph. Resolve dependencies against Emre's toolchain one by one.

### 5.2 Compatibility audit

For every upstream feature and support library, record:
- Source files/resources/tests required
- Existing Emre implementation to reuse or replace
- Maven coordinates and exact pinned version
- Minimum API and device support
- Native libraries and ABI coverage
- Model files, size, source, checksum and licence
- R8/consumer rules and manifest components
- APK/AAB size delta
- Privacy/network behaviour
- Licence/NOTICE obligations
- Unit and instrumentation coverage

The upstream version catalog references native/image dependencies including AVIF, JPEG XL, JPEGli, image codecs, OpenCV, ONNX/GenAI, PDFBox, archive libraries, ZXing, cryptography, and multiple ImageToolbox support libraries. None should be added blindly. Maintain a dependency decision record with accepted/rejected/deferred status.

### 5.3 Existing dependency reuse

- Reuse ExifInterface for supported EXIF fields; add another metadata library only for proven format gaps.
- Reuse current ONNX Runtime and OCR engines.
- Adapt to Coil 2's current pipeline.
- Evaluate OpenCV only for document scanning, stitching or operations that cannot be met efficiently otherwise.
- Keep model files out of the base APK where possible. Use an explicit model manager with user-visible download size, storage location, verification, cancellation and deletion.
- No silent model downloads and no remote upload of private photos for local tools.
- Remote image import is a separate, user-initiated feature; validate schemes, TLS, size, MIME type and redirects.

### 5.4 Licensing and provenance

Maintain `THIRD_PARTY_NOTICES.md` and a machine-readable dependency/model inventory. Preserve the upstream Apache-2.0 licence and applicable source copyright notices for copied/adapted sections. Independently verify the licences of all transitive native libraries, fonts, assets, model weights and bundled resources. The upstream project's licence does not automatically relicense every dependency. Do not remove or rewrite third-party provenance.

## 6. Full feature integration sequence

The work is divided into vertical slices. Each slice must produce usable UI, backend behaviour, tests and a passing workflow before the next high-risk slice is merged.

### Phase 0 — Baseline, inventory and risk closure
- Fetch submodule recursively in CI and developer setup; verify the pinned SHA.
- Record the exact Emre base commit and current build output.
- Run the existing unit-test and debug-assemble workflow.
- Audit all 68 upstream feature modules, 10 core modules and 18 support libraries.
- Inventory native ABIs, model assets, licenses, manifest entries and dependency versions.
- Identify existing Emre overlaps for decoder, metadata, OCR, ONNX, media selection, storage and image loading.
- Establish APK size, build time, memory, scroll frame-time, viewer open-time and face-index throughput baselines.

**Exit:** baseline reproducible; parity matrix reviewed; dependency/licence/asset inventory completed; no source has been wired into production yet.

### Phase 1 — Shared processing foundation
- Add contracts, capability registry and typed error/result types.
- Implement URI resolver for MediaStore, SAF, file and stream sources.
- Create bounded processing dispatchers, operation queue, cancellation and progress.
- Implement memory budget policy and bitmap/native resource ownership.
- Implement temporary-file manager and transactional exporter.
- Add edit recipe/history persistence.
- Integrate a Tools catalogue shell and viewer contextual entry points using placeholder capability state.

**Exit:** a fake/test processor can execute through the full operation lifecycle; exports are atomic; existing gallery functionality remains unaffected.

### Phase 2 — Decode, metadata and format I/O
- Consolidate upstream and Emre decode behaviour behind one Emre decoder contract.
- Support bounds-first decode, EXIF orientations 1–8, downsampling, color-space policy, animated formats and PDF preview as separate decode paths.
- Integrate supported raster, vector, RAW and high-efficiency formats by device capability.
- Centralise metadata reading and selective writing; add GPS privacy controls.
- Implement JPEG, PNG and WebP output, then AVIF, HEIC/HEIF, JPEG XL, JPEG 2000, TIFF, BMP, GIF/APNG, QOI, ICO and other formats found in the compatibility audit.
- Handle animated input/output without converting every frame into an unbounded in-memory bitmap list.
- Add batch conversion, compression profiles, target-size/quality controls and output comparison.

**Exit:** all advertised formats show real device support status; supported decode/encode round trips pass tests; unsupported codecs fail clearly without crashing; originals remain untouched.

### Phase 3 — Core editor and non-destructive transformations
- Crop with aspect presets, freeform geometry, straighten, perspective correction and content-aware options where supported.
- Rotate, flip, resize, scale, fit/fill, canvas expansion and borders.
- Implement the editor operation recipe, undo/redo, drafts and final rendering.
- Drawing tools, text/shapes/arrows, eraser/highlighter, lasso/selection, markup layers, blend modes and watermark/logo/text overlays.
- Batch crop/resize/rotate/watermark where sensible.

**Exit:** edit session can be resumed; preview and final output match within tolerance; save-copy and scoped-storage flows work.

### Phase 4 — Filter engine and visual effects
- Build a filter registry and typed parameter schema.
- Port the full filter catalogue, including the upstream filter definitions and assets (the README describes 500+ filters).
- Cover tonal, color, monochrome, blur, sharpen, denoise, edge, artistic, pixelation, dithering, distortion, morphology, LUT and film-style families.
- Implement curves, levels, histograms, color grading and LUT import/export.
- Implement GPU/shader acceleration with CPU fallback, explicit resource ownership, bounded render targets and validated shader execution.
- Add filter search, favourites, recent effects, parameter reset and filter-chain recipes.

**Exit:** each filter is discoverable and has a test/visual fixture or documented limitation; chains remain responsive and cancel cleanly; no preview operation allocates full-size intermediates unnecessarily.

### Phase 5 — Composition and creative generation
- Collage layouts, freeform canvas, frames and image placement.
- Panorama/image stitching and alignment.
- Image stacking, multi-frame fusion and image splitting/cutting.
- Photomosaic.
- Color palette extraction, color picker, color library, color conversions/harmonies.
- Linear/radial/mesh gradients, noise and texture generation, fractal generation.
- SVG creation, ASCII art and code-to-image preview.
- Screenshot framing and wallpaper export.
- Audio album-cover extraction.
- Add import/export of each relevant project format and templates.

**Exit:** multi-source workflows support reorder/remove/replace, handle mixed dimensions and orientation, and export in supported formats.

### Phase 6 — AI tools and model management
- AI feature catalogue with model capability and device requirement metadata.
- Background removal and foreground/subject segmentation.
- Subject/object masks and manual mask refinement.
- Image enhancement, upscaling, denoise/deblur/restoration and supported neural effects from the upstream catalogue.
- Reuse current ONNX Runtime where compatible; evaluate ML Kit segmentation or upstream ONNX implementation by quality, latency and APK/model size.
- Download models on demand with checksum verification, resume/cancel, storage quota, deletion and model versioning.
- CPU/GPU/NNAPI execution selection only when validated on actual devices.

**Exit:** each shipped AI tool has a known model licence, tested model checksum, minimum device capability, memory ceiling, offline behaviour and user-visible download lifecycle. No model is silently bundled or downloaded.

### Phase 7 — Document, OCR, scanner and QR workflows
- Camera/document scanner, edge detection, perspective correction, crop, deskew and enhancement.
- Multi-page capture, reorder, rotate, delete and combine.
- Image-to-PDF, PDF-to-image, merge/split/reorder/crop/rotate/compress and page numbering.
- PDF watermark/stamp/signature where supported, metadata and protection operations where technically and legally valid.
- OCR with language selection, Tesseract/ML Kit engine selector, page/layout modes and batch recognition.
- Searchable PDF creation and text export.
- QR/barcode scan and generation with safe parsing of URLs, Wi-Fi, contacts, calendar and other payloads.

**Exit:** OCR uses the existing Emre engines; scan/PDF flows preserve page order and orientation; permission and document URI persistence tests pass.

### Phase 8 — File and power-user utilities
- Archive create/extract and supported formats; inspect entry list before extraction.
- Checksum/hash tools and compare.
- Base64 encode/decode.
- Encryption/decryption using vetted cryptographic APIs, modern authenticated encryption, safe key handling and clear warnings; never roll custom cryptography.
- Batch rename with preview, collision handling and reversible transaction log.
- Duplicate finder: exact hashes first, perceptual similarity as a separate opt-in scan.
- Remote image import initiated by the user.
- Libraries/about and diagnostic information represented within Emre's own settings.
- Optional Quick Settings shortcuts for selected frequent operations.

**Exit:** utilities have safe path traversal protections, archive-bomb limits, collision tests, authenticated encryption tests and undo/recovery for rename/export operations.

### Phase 9 — Complete parity, polish and release
- Compare every feature module in the parity matrix against a working Emre screen or an explicit "not applicable" rationale.
- Add onboarding/discovery for Tools without cluttering the gallery.
- Complete accessibility, localization, large-screen layout and dark/light appearance.
- Add import/export project formats and migration/versioning for edit recipes.
- Test low-memory, large images, mixed media and rapid operation cancellation.
- Review native crash traces, APK Analyzer output, build time and CI reliability.
- Complete legal notices and model/library attribution.
- Publish an internal feature parity checklist and release notes.

**Exit:** no unclassified upstream feature remains; every implemented feature passes its acceptance checklist; deferred features are clearly documented with an explicit technical, legal or platform reason.

## 7. Performance, safety and data-integrity requirements

- Separate thumbnail decode, viewer decode, editor preview and full-resolution export budgets.
- Bound concurrency according to available memory and operation class; do not use unrestricted full-pool parallelism for large bitmaps.
- Use streaming/file-backed work for large files. Avoid duplicate full-resolution copies.
- Respect Android memory callbacks and process death; persist operation state where a task is safely resumable.
- Keep face indexing and gallery browsing responsive during batch processing.
- Support pause/cancel at safe checkpoints for long operations.
- Never trust file extensions alone; inspect actual content and dimensions.
- Protect against decompression bombs, malformed image/PDF/archive files, excessive dimensions, zip-slip/path traversal and malicious SVG/shader input.
- Validate downloaded models and remote content before use.
- Avoid network access unless a feature explicitly requires it and the user initiated it.
- Do not silently strip GPS or silently preserve sensitive location metadata; make metadata policy visible at export.

## 8. Quality gates and test plan

### Every pull request
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- Static checks/lint where configured.
- Licence and dependency-diff review.
- APK/native library size report when dependency or asset footprint changes.

### Processing test suite
- Decode: EXIF orientation 1–8, corrupt/truncated files, large panoramas, content URI revocation, unsupported formats.
- Encode: round trips, MIME/magic agreement, quality/size controls, alpha handling, metadata policies.
- Export: cancellation at every stage, disk full, permission denied, process death, duplicate names and cleanup.
- Editor: operation ordering, undo/redo, recipe persistence and golden-image comparisons.
- Filters: representative golden fixtures, parameter bounds, GPU fallback and cancellation.
- AI: model checksum, download interruption, insufficient RAM, inference cancellation and offline startup.
- Documents: multi-page ordering, skew/orientation, searchable OCR, large PDF, corrupt PDF and scoped storage.
- Utilities: archive traversal, expansion limits, checksum correctness, encryption authentication and rename collisions.

### Device and regression matrix
- minSdk 26 plus supported current Android versions.
- arm64-v8a and x86_64; any additional ABI requires explicit build/size testing.
- Low-memory and standard devices.
- Stress images in the 12–50 MP range and larger where memory permits.
- Concurrent gallery scroll, viewer playback, face indexing, OCR and tool processing.
- Track: gallery frame pacing, thumbnail latency/cache hit rate, viewer open latency, peak heap/native memory, processing throughput, APK size and build time.

**Release gate:** no regression in the existing gallery/viewer/face workflow beyond agreed thresholds; no silent source mutation or incomplete output; no unsupported feature is presented as available.

## 9. Delivery order and implementation tickets

The initial implementation should be split into reviewable pull requests on `research/image-toolbox-backends`:

1. **ITB-00 Baseline + audit** — build/test baseline, submodule checkout, source/dependency/licence/model/ABI inventory.
2. **ITB-01 Contracts + runtime** — operation lifecycle, typed results, cancellation, bounded dispatchers, memory policy.
3. **ITB-02 URI + export** — source resolver, transactional export, MediaStore/SAF integration.
4. **ITB-03 Decoder + metadata** — unify Emre decoder, orientation and metadata policy.
5. **ITB-04 Basic formats** — JPEG/PNG/WebP and output profiles.
6. **ITB-05 Editor** — crop/rotate/flip/resize and recipe history.
7. **ITB-06 Markup** — drawing/layers/text/shapes/watermarks.
8. **ITB-07 Filters** — filter registry, curves, core filter port and GPU path.
9. **ITB-08 Advanced codecs** — animated formats, AVIF/HEIF/JXL/JP2/TIFF and other audited codecs.
10. **ITB-09 Composition** — collage/stitch/stack/fusion/split/photomosaic and creative generation.
11. **ITB-10 AI runtime** — capability/model manager, background removal and enhancement.
12. **ITB-11 Scan + OCR + PDF** — scanner, existing OCR adapter, PDF workflows, QR/barcodes.
13. **ITB-12 Utilities** — archive, cipher, checksum, Base64, duplicate, rename and auxiliary tools.
14. **ITB-13 Parity/release** — close matrix gaps, hardening, performance and documentation.

Each ticket must name the upstream files/modules it draws from, the Emre classes it touches, the licence/dependency implications, tests, and explicit non-goals.

## 10. Current status

- [x] Full upstream source pinned as a Git submodule under `vendor/ImageToolbox`.
- [x] Source inventory and upstream module inventory documented.
- [x] Emre baseline architecture and overlap points identified from `Develop`.
- [ ] Verify a clean CI checkout with submodules initialised.
- [ ] Establish and record a green baseline build on this branch.
- [ ] Complete source-to-feature file mapping and per-feature dependency/asset/licence audit.
- [ ] Implement ITB-00 and ITB-01.
- [ ] Integrate feature slices through ITB-13.

**This document is a roadmap, not evidence that the listed capabilities are already implemented or that a build has passed.**
