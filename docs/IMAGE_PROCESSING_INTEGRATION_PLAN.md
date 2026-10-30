# Emre Gallery — Image Processing Integration Plan

## Objective

Build an Emre-native image-processing subsystem using the ImageToolbox backend snapshot as a technical reference and, where appropriate, as Apache-2.0-licensed upstream implementation material.

This is an integration plan, not a claim that the imported snapshot is already integrated. The current application remains a single `:app` Gradle module. The plan deliberately avoids importing ImageToolbox screens, navigation, Compose components, or application architecture.

## Current Emre Gallery baseline

The inspected `Develop` branch already has:

- A Coil 2.7 image-loading pipeline configured in `GalleryApplication`.
- A custom `MediaStoreThumbnailFetcher` and `PreparedThumbnailCache` for small MediaStore thumbnails.
- `FaceImageDecoder`, which handles content/file URIs, EXIF orientation, downsampling, and software bitmaps for ML inference.
- EXIF access through AndroidX ExifInterface, including location extraction in the map pipeline.
- Existing OCR implementations using Tesseract4Android and Google ML Kit.
- ONNX Runtime for face detection/recognition.
- A MediaStore-backed `MediaRepository` and `MediaItem` model.

The app currently targets minSdk 26 / compileSdk 35, uses Java 17, and packages `arm64-v8a` and `x86_64`. It has only `:app` included in settings. Therefore, avoid adding native codecs or large multi-module build complexity before proving their value.

## Target architecture

Keep the current app module initially, but isolate processing behind stable Emre-owned contracts. A future extraction into Gradle library modules can happen after interfaces and tests stabilize.

Suggested package layout:

```
app/src/main/java/com/amresalehin/emregallery/
  processing/
    domain/
      model/
      contract/
      policy/
    data/
      decode/
      encode/
      transform/
      metadata/
      filters/
      export/
      pdf/
      stitch/
    runtime/
      dispatcher/
      memory/
      cancellation/
      progress/
    integration/
      coil/
      mediastore/
      face/
      ocr/
```

### Core contracts

Define small suspendable interfaces that do not expose Android implementation details unnecessarily:

- `ImageDecoder`: decode a URI or stream into a bounded, orientation-normalized result.
- `ImageEncoder`: encode a bitmap/image source to a requested format and quality.
- `ImageTransformer`: rotate, flip, crop, resize, and apply orientation safely.
- `ImageMetadataReader` / `ImageMetadataWriter`: read and selectively update EXIF/IPTC/XMP metadata.
- `ImageExportService`: coordinate output target, temporary file, encoder, metadata policy, and final MediaStore insertion.
- `ImageFilterEngine`: apply a chain of filter operations with explicit input/output ownership.
- `OcrProvider`: bridge to the already existing Emre OCR engines rather than introducing a second OCR stack.
- `BackgroundRemovalProvider`: optional pluggable inference backend.
- `PdfService` and `ImageStitchService`: independent optional capabilities.

Use typed results for unsupported formats, permission failures, decode errors, encoder failures, cancellation, and insufficient storage. Do not return ambiguous nulls for normal failure paths.

## Integration phases

### Phase 0 — Baseline and safety gate

1. Record the current `Develop` commit and establish a clean build baseline.
2. Add unit tests around existing `FaceImageDecoder`, thumbnail generation, metadata reading, and export behavior before replacing any implementation.
3. Inventory all upstream files, imports, native libraries, model files, Gradle plugins, ABI requirements, and flavor-specific sources.
4. Create a third-party notices file for any upstream source retained or adapted. Preserve required copyright, Apache-2.0 licence text, and notices. Mark substantial Emre-authored rewrites as Emre code without erasing the provenance of copied portions.
5. Keep the reference snapshot under `reference/image-toolbox-backends`; production code must not depend on that directory.

**Exit criteria:** baseline build passes, test fixtures exist, dependency and licence inventory is complete.

### Phase 1 — Unified decode pipeline (highest priority)

1. Design an Emre `ImageDecoder` contract around content URIs, file URIs, file descriptors, and streams.
2. Reconcile the upstream decoder patterns with the existing `FaceImageDecoder`; do not create a second unrelated decoder.
3. Support bounds-first decoding, target-size sampling, EXIF orientation normalization, cancellation, and deterministic bitmap ownership/recycling.
4. Keep full-resolution decode separate from thumbnail decode. Face/OCR inference must request a bounded software bitmap; Coil may use hardware bitmaps for display.
5. Add format capability detection and graceful fallback for unsupported formats.
6. Add tests for EXIF orientations 1–8, corrupt/truncated images, large panoramas, content URI permission loss, and memory pressure.

**Exit criteria:** gallery thumbnails and face/OCR decode use the shared contract without regressions or increased UI-thread work.

### Phase 2 — Transform and export pipeline

1. Add rotate, flip, crop, resize, aspect-ratio fit/fill, and lossless operations where supported.
2. Separate pixel transformation from file encoding. Avoid repeated bitmap re-encoding between operations.
3. Implement an export transaction: create temporary output, encode, validate output, apply metadata policy, then publish to MediaStore. Delete temporary files on failure/cancellation.
4. Preserve source metadata by default only when safe; explicitly handle orientation, timestamps, GPS, privacy stripping, and format-specific metadata support.
5. Add JPEG and WebP first. Add PNG lossless/lossy and AVIF/JXL/HEIC only after native dependency and device compatibility checks.
6. Use streaming/file-backed encoders where possible to avoid holding multiple full-size bitmaps in RAM.

**Exit criteria:** export round-trip tests pass; no partial files remain after failures; source media is never modified in place without an explicit user action.

### Phase 3 — Metadata subsystem

1. Centralize EXIF reading now duplicated across map, viewer, and decoder code.
2. Define metadata DTOs independent of ExifInterface.
3. Add selective metadata writing and explicit privacy controls for GPS and identifying fields.
4. Ensure edits to MediaStore-owned content use the correct scoped-storage write request/URI permissions.
5. Verify metadata round-trips on JPEG and supported TIFF/HEIF variants before expanding.

**Exit criteria:** viewer/map metadata comes from one reader; write operations are tested and permission-safe.

### Phase 4 — Filters and GPU execution

1. Start with a small Emre filter model: operation id, typed parameters, color-space expectations, and input/output dimensions.
2. Implement a CPU reference path for correctness and a GPU path only where it materially improves throughput.
3. Treat shader source as untrusted input: validate limits, reject unsafe constructs, and enforce execution/resource bounds.
4. Keep preview rendering non-destructive; only render a final output during export.
5. Avoid allocating a new full-resolution bitmap for every filter in a chain. Use ping-pong render targets or bounded intermediate buffers.

**Exit criteria:** deterministic output within defined tolerances, no GPU resource leaks, and cancellation works during long filter chains.

### Phase 5 — Optional advanced capabilities

Integrate independently and only if product requirements justify them:

- **OCR:** reuse Emre's existing Tesseract and ML Kit engines. Adapt the upstream OCR orchestration concepts only; do not package a duplicate OCR runtime.
- **Background removal:** create a provider interface and benchmark available on-device models. Avoid shipping multiple model backends by default.
- **Stitching:** prototype OpenCV stitching as an optional capability. Measure native APK size, ABI coverage, memory use, and latency before adoption.
- **PDF:** treat PDF rendering/editing as a separate optional feature. It is not part of the core gallery decode/export path.
- **Additional codecs:** add one codec at a time behind capability checks, with device/API fallback and native library size accounting.

**Exit criteria:** each optional feature has a measured user benefit, acceptable binary-size impact, supported-device matrix, and independent tests.

## Performance and memory policy

- Never perform decode, encode, EXIF traversal, OpenCV work, or shader compilation on the main thread.
- Use bounded concurrency based on memory class and operation type. Do not assume maximum parallelism is always faster.
- Keep thumbnail and full-resolution caches separate, with explicit byte budgets and trim callbacks.
- Reuse the existing Coil loader and prepared-thumbnail cache; avoid duplicate thumbnail caches.
- Use cancellation-aware coroutines and structured concurrency.
- Avoid unbounded byte-array buffering for large files. Prefer file descriptors, streams, and temporary files.
- Record per-stage timings and peak allocation estimates in debug builds.
- Do not retain decoded bitmaps longer than the active operation or a deliberately bounded cache lifetime.

## Dependency and APK-size policy

- No new native dependency is approved merely because it exists upstream.
- First use Android platform APIs and libraries already present in Emre Gallery.
- Reuse existing ONNX Runtime, Tesseract4Android, and ML Kit dependencies where they already meet requirements.
- Evaluate native codecs/OpenCV separately for ABI support, transitive native libraries, license notices, R8/packaging rules, and APK/AAB size.
- Keep optional native features behind Gradle product flavors or dynamic delivery only if the project later adopts that distribution model.
- Build and inspect APK Analyzer output after each dependency addition.

## Test matrix

- Unit: sizing math, sampling, EXIF orientation, metadata policy, format selection, transform composition, error mapping.
- Instrumented: MediaStore URI access, scoped-storage writes, HEIF/AVIF/JXL decode on supported devices, bitmap config behavior, lifecycle cancellation.
- Golden-image: rotation/flip/crop/resize/filter output with tolerance thresholds.
- Stress: 12–50 MP images, repeated export, rapid cancellation, low-memory callbacks, concurrent thumbnail scrolling plus face indexing.
- Compatibility: minSdk 26, current Android versions, arm64-v8a and x86_64; explicitly document formats unavailable on older devices.
- Regression: home feed scroll smoothness, viewer open latency, face indexing stability, thumbnail cache hit rate, and no app termination under indexing load.

## Recommended implementation order

1. Shared decoder contract + merge existing FaceImageDecoder behavior.
2. Metadata reader consolidation.
3. Transform primitives.
4. Transactional export with JPEG/WebP.
5. PNG and advanced codecs based on demand.
6. Filter engine.
7. OCR adapter using existing engines.
8. Optional background removal.
9. Optional stitching.
10. Optional PDF tools.

## Provenance and ownership

Emre Gallery owns its application architecture, package structure, integration code, tests, policies, and original implementations. Any code copied or adapted from ImageToolbox remains subject to the upstream Apache-2.0 terms. Keep upstream copyright notices and licence text for those portions, and record the upstream commit used. Do not claim third-party-authored code as wholly Emre-authored. This does not prevent substantial Emre-specific rewrites or original implementations from being owned by the Emre Gallery project.

## Current status

- [x] Backend-only reference snapshot imported under `reference/image-toolbox-backends/upstream`.
- [x] Integration plan documented.
- [ ] Baseline build and tests verified on this branch.
- [ ] Dependency/native library inventory completed.
- [ ] Decoder contract implemented.
- [ ] Existing decoder migrated and regression-tested.
- [ ] Transform/export pipeline implemented.
- [ ] Metadata consolidation implemented.
- [ ] Optional capabilities evaluated individually.

No production implementation has been changed by this plan commit.
