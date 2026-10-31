# ImageToolbox → Emre Gallery Feature-Parity Matrix

**Upstream revision:** `T8RIN/ImageToolbox` commit `f78bc23f52d863e97cd0af061c603ac654dedc48`
**Pinned in Emre:** `vendor/ImageToolbox`
**Inventory basis:** recursive Git tree and module build files at the pinned revision.

This matrix accounts for **all 69 upstream feature Gradle modules**, all 10 upstream core modules and all 18 supporting library modules. "Disposition" describes how the feature should be delivered in Emre; it does not claim the feature is implemented.

**Current implementation state:** Emre has a Settings entry named **Tools** and a searchable, expandable catalogue grouped into 18 categories. This is feature discovery only; individual processing actions are not yet connected. Use the disposition and acceptance checklist below to track implementation.

## A. Feature modules — all 69 accounted for

| Upstream module | Emre delivery / responsibility | Disposition |
|---|---|---|
| `:feature:single-edit` | Full-screen Emre editor; non-destructive edit recipe and export | Port feature logic; rebuild UI |
| `:feature:crop` | Crop, aspect ratios, freeform crop, straighten and perspective correction | Port/adapt |
| `:feature:curves` | Tone curves and channel curves | Port/adapt |
| `:feature:compare` | Before/after and side-by-side image comparison | Port/adapt |
| `:feature:delete-exif` | Selective or complete metadata removal | Integrate with Emre metadata policy |
| `:feature:edit-exif` | Edit supported metadata fields | Integrate with Emre metadata policy |
| `:feature:filters` | Filter browser, filter chains and parameters | Port filter definitions/engine; Emre UI |
| `:feature:draw` | Pen, brush, shapes, arrows, text and drawing canvas | Port/adapt |
| `:feature:markup-layers` | Layered annotations and compositing | Port/adapt |
| `:feature:watermarking` | Text/logo/stamp watermarking and batch application | Port/adapt |
| `:feature:resize-convert` | Image resize, conversion and batch workflow | Reuse shared codec/export engine |
| `:feature:limits-resize` | Resize within maximum width/height and aspect constraints | Shared resize engine |
| `:feature:weight-resize` | Compress to a target file size | Shared encoder/quality search |
| `:feature:image-preview` | Preview imported and generated image assets | Integrate into existing viewer; avoid a parallel viewer |
| `:feature:ai-tools` | Neural enhancement and other upstream AI workflows | Port selectively after model audit |
| `:feature:erase-background` | Background/subject segmentation and removal | Emre model provider + mask editor |
| `:feature:format-conversion` | Format selection, conversion and format-specific options | Shared codec registry |
| `:feature:compression-lab` | Compare encoder, quality, dimensions and output size | Shared codec engine + dedicated UI |
| `:feature:gif-tools` | GIF decode/encode, frame extraction and conversion | Port/adapt |
| `:feature:apng-tools` | APNG decode/encode and frame workflows | Port/adapt |
| `:feature:webp-tools` | Static/animated WebP operations | Shared codec/animation engine |
| `:feature:jxl-tools` | JPEG XL decode/encode and animation where supported | Optional codec after ABI/device audit |
| `:feature:document-scanner` | Document capture, edge detection, perspective and cleanup | Reuse CameraX/platform where present; adapt scanner algorithms |
| `:feature:pdf-tools` | PDF import/export, merge/split/page operations and PDF manipulation | Separate document subsystem |
| `:feature:recognize-text` | OCR, language selection and text extraction | Reuse current Tesseract and ML Kit engines |
| `:feature:scan-qr-code` | QR/barcode scanner and payload interpretation | Port/adapt scanner; inspect ZXing/ML Kit choices |
| `:feature:image-stitch` | Panorama and image stitching | Optional OpenCV/native capability |
| `:feature:image-stacking` | Align/stack multiple images | Port/adapt; bounded memory |
| `:feature:multi-frame-fusion` | Multi-frame fusion and related composites | Port/adapt; benchmark |
| `:feature:image-splitting` | Split an image into tiles or sections | Implement through shared transform/export |
| `:feature:image-cutting` | Cutout/cutting workflows | Port/adapt; share crop/mask contracts |
| `:feature:collage-maker` | Multi-image layouts, templates and collage export | Reuse/adapt collage library; Emre UI |
| `:feature:photomosaic` | Build mosaics from source-image collections | Port/adapt; add collection/index limits |
| `:feature:color-tools` | Color conversion, mixing and color utilities | Port/adapt |
| `:feature:color-library` | Color library and stored palettes | Integrate with Emre presets |
| `:feature:pick-color` | Sample a color from image pixels | Integrate with viewer/editor gestures |
| `:feature:palette-tools` | Extract, generate and manage palettes | Port/adapt |
| `:feature:palette-pdf` | Export palette sheets/documents as PDF | Compose with PDF subsystem |
| `:feature:gradient-maker` | Linear/radial and other gradient generation | Port/adapt |
| `:feature:mesh-gradients` | Mesh-gradient authoring | Port/adapt |
| `:feature:noise-generation` | Generate noise textures | Port/adapt |
| `:feature:texture-generation` | Generate procedural textures | Port/adapt |
| `:feature:fractal-generation` | Fractal rendering/generation | Port/adapt; constrain render dimensions |
| `:feature:svg-maker` | Create SVG graphics | Port/adapt; sanitise import/render |
| `:feature:ascii-art` | Convert images to ASCII representations | Port/adapt |
| `:feature:code-preview` | Code/text-to-image preview and export | Port/adapt; sanitise inputs |
| `:feature:shader-studio` | Shader authoring, preview and presets | Isolated sandboxed/validated renderer; advanced phase |
| `:feature:screenshot-framing` | Place screenshots in frames/backgrounds | Port/adapt |
| `:feature:wallpapers-export` | Wallpaper sizing and export | Integrate with Android wallpaper intents/export |
| `:feature:archive-tools` | Create, inspect and extract supported archives | Reuse audited archive libraries; path/size protections |
| `:feature:base64-tools` | Base64 encode/decode | Emre utility screen |
| `:feature:batch-rename` | Pattern-based file renaming | SAF/MediaStore-safe transaction and undo |
| `:feature:checksum-tools` | Compute/compare checksums and hashes | Emre utility screen |
| `:feature:cipher` | File encryption/decryption | Vetted authenticated cryptography only |
| `:feature:duplicate-finder` | Exact and similarity-based duplicate discovery | Use Emre MediaRepository; hash then perceptual scan |
| `:feature:audio-cover-extractor` | Extract embedded audio artwork | Optional media metadata utility |
| `:feature:load-net-image` | User-initiated remote image import | Explicit network flow with MIME/size/TLS validation |
| `:feature:assistant` | Contextual help/assistant for tools | Adapt to Emre assistant architecture; must not upload media silently |
| `:feature:app-logs` | Processing diagnostics and shareable logs | Integrate with Emre diagnostics; redact paths/metadata |
| `:feature:usage-statistics` | Usage insights | Optional, privacy-preserving and off by default unless separately approved |
| `:feature:help` | Tool help and supported-format guidance | Emre help content; no duplicate app shell |
| `:feature:libraries-info` | Third-party library acknowledgements | Integrate into Emre About / open-source notices |
| `:feature:library-details` | Per-library licence/version details | Integrate into Emre About / open-source notices |
| `:feature:settings` | Tool preferences, export presets and defaults | Merge relevant settings into existing Emre settings |
| `:feature:media-picker` | Select media for tool operations | Reuse Emre gallery/selection/SAF flows; no second picker |
| `:feature:quick-tiles` | Quick Settings entry points | Optional Emre shortcuts, only for stable frequent actions |
| `:feature:main` | Upstream feature landing screen | Replace with Emre Tools catalogue |
| `:feature:root` | Upstream feature navigation/composition root | Do not port; connect Emre-owned feature destinations |
| `:feature:easter-egg` | Non-essential hidden interaction | Optional product decision; isolate from processing code |

### Feature accounting by group

- Editing and visual manipulation: 14 modules
- Composition and multi-image workflows: 7 modules
- AI tools: 2 modules
- Format and compression tools: 6 modules
- Document, OCR and QR tools: 4 modules
- Color, generation and creative tools: 16 modules
- File and media utilities: 8 modules
- App-shell, discovery and supporting experiences: 12 modules

**Total: 69 feature modules.** App-shell modules are accounted for but should not replace Emre's existing application shell.

## B. Core modules — all 10 accounted for

| Upstream core module | Relevant content | Emre disposition |
|---|---|---|
| `:core:data` | Decode/encode, image I/O, saving, resources and repositories | Extract/adapt useful processing code; replace upstream storage with Emre MediaStore/SAF |
| `:core:domain` | Domain models and contracts | Use as reference; define Emre-owned stable contracts |
| `:core:filters` | Filter models and filter implementations | Port definitions/algorithms behind Emre FilterEngine |
| `:core:di` | Hilt dependency injection | Do not transplant wholesale; use existing Emre DI pattern or add Hilt only after decision |
| `:core:resources` | Strings, icons, fonts, images and other resource catalogue | Rebuild only necessary assets with attribution and Emre design system |
| `:core:settings` | Preferences and settings contracts | Merge relevant preferences into Emre DataStore/settings |
| `:core:ui` | Shared Compose components/theme | Rebuild Emre-native UI; do not copy upstream app theme wholesale |
| `:core:utils` | Shared utilities, dispatchers and helper code | Audit and selectively adapt |
| `:core:crash` | Error/crash handling | Integrate with existing diagnostics and crash policy |
| `:core:ksp` | KSP processors/code generation | Do not adopt unless a specific required processor is proven necessary |

## C. Supporting libraries — all 18 accounted for

| Upstream library module | Purpose | Emre decision |
|---|---|---|
| `:lib:archive` | Archive read/write | Evaluate against vetted existing/available archive libraries |
| `:lib:ascii` | ASCII art conversion | Reuse for ASCII tool if licence and API fit |
| `:lib:collages` | Collage composition | Reuse/adapt for collage feature |
| `:lib:colors` | Color utilities | Reuse/adapt after dependency review |
| `:lib:cropper` | Crop UI/geometry | Use as algorithm reference; adapt UI to Emre |
| `:lib:curves` | Curves UI/processing | Reuse/adapt with Emre visual system |
| `:lib:documentscanner` | Scanner engine/components | Benchmark against platform/CameraX options |
| `:lib:dynamic-theme` | Dynamic theming | Do not import as a replacement for Emre theme |
| `:lib:fast-scroller` | Scrolling UI component | Prefer Emre existing components; only port if measurable need |
| `:lib:gesture` | Gesture helpers | Use selectively; avoid conflicting viewer/editor gesture systems |
| `:lib:image` | Image helpers | Audit individually; avoid competing image loaders/caches |
| `:lib:modalsheet` | Modal bottom sheet component | Rebuild/adapt to existing Emre sheets |
| `:lib:neural-tools` | Neural model/inference wrappers | Reuse ONNX runtime; review model formats and licences |
| `:lib:opencv-tools` | OpenCV wrappers/algorithms | Optional native dependency; ABI/APK/memory gate required |
| `:lib:palette` | Palette logic | Reuse/adapt for color/palette features |
| `:lib:qrose` | QR generation | Reuse/adapt after dependency audit |
| `:lib:snowfall` | Decorative snowfall effect | Optional visual effect; not a processing dependency |
| `:lib:zoomable` | Zoomable image component | Avoid replacing Emre viewer; potentially useful within editor canvas |

## D. Shared technical dependencies and assets to audit

The upstream version catalogue includes or references:
- AVIF, HEIC/HEIF, JPEG XL, JPEGli and other format-specific encoders/decoders.
- Animated GIF/APNG/WebP tooling, JPEG 2000, QOI, PSD, DjVu, RAW and TIFF handlers.
- GPU image filtering, JH Labs/GMIC-style effects, histogram and advanced crop support.
- OpenCV, ONNX Runtime / GenAI, ML Kit segmentation and other AI model paths.
- PDFBox, QR/Barcode, archive and compression libraries, cryptographic libraries.
- Fonts, SVG/Lottie/resources, filter presets/LUTs, ONNX model files and native `.so` binaries.

This list is an audit queue, not an approved dependency list. For each item determine whether it is actually needed, supported on Emre's minSdk/ABIs, compatible with current Kotlin/AGP/Compose versions, licensed for redistribution, and worth its APK and memory cost.

## E. What counts as feature parity

A row is only marked **Done** after all applicable checks pass:

1. Tool appears in Emre's Tools search/catalogue and relevant viewer context menu.
2. User can select the correct input(s) using Emre's media selection model.
3. Backend executes on the processing runtime with progress, cancellation and typed failures.
4. Preview/parameters are usable in Emre's UI.
5. Output is validated and saved through the shared transactional export path.
6. Original source is preserved by default.
7. Unit/instrumentation/golden tests exist for core behaviour.
8. Large input, low-memory and rapid-cancellation behaviour are tested.
9. Licence/dependency/asset and APK-size impacts are documented.
10. Existing gallery scrolling, viewer, face indexing and OCR regressions are ruled out.

A row may be classified as **Not applicable** only with a documented product/platform reason (for example, an upstream app shell that is replaced by Emre's own shell). It must not be silently dropped.
