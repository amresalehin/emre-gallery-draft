# Full ImageToolbox source dependency

Emre Gallery's image-processing feature-parity work is pinned to the complete upstream
ImageToolbox source repository as a Git submodule.

- Upstream: https://github.com/T8RIN/ImageToolbox
- Pinned commit: `f78bc23f52d863e97cd0af061c603ac654dedc48`
- Local path: `vendor/ImageToolbox`
- Upstream license: Apache License 2.0 (see `vendor/ImageToolbox/LICENSE`)
- Source tree at the pinned revision: 5,828 tracked files, including 4,076 Kotlin files,
  69 feature modules, 10 core modules and 18 supporting libraries.

## Why this is a submodule

The complete upstream project is a multi-module Android application, not a standalone
image-processing library. It contains its own application, dependency-injection setup,
navigation, UI system, resource system, Gradle convention plugins, native libraries,
model assets and feature modules. Copying only selected Kotlin files would omit essential
resources and runtime dependencies; copying its application wholesale into Emre Gallery
would create a second app architecture and conflict with Emre's existing application.

The submodule pins the full, reproducible source tree without merging ImageToolbox's
application into Emre's Gradle project. Clone with:

```bash
git clone --recurse-submodules https://github.com/amresalehin/emre-gallery-draft.git
```

For an existing clone:

```bash
git submodule update --init --recursive
```

## Integration rule

Do not include the upstream `:app` module or replace Emre's `settings.gradle.kts`,
application manifest, navigation, theme, media repository, Coil pipeline, face-recognition
pipeline or OCR engines with upstream equivalents. Extract/adapt the required feature
modules behind Emre-owned interfaces and UI. Preserve the upstream copyright and Apache-2.0
license obligations for every copied or adapted source file, and retain third-party notices
for bundled libraries and model weights.

## Upstream feature-module inventory

The pinned source contains the following feature modules:

- AI tools, filters, curves, single edit, crop, resize/convert, weight/limits resize
- Drawing, markup layers, watermarking, background erasure
- Format conversion, compression lab, GIF, APNG, WebP and JPEG XL tools
- EXIF editing and deletion, OCR/text recognition, PDF tools, document scanning
- Image stitching, stacking, splitting, cutting, collage and photomosaic
- Color tools, palettes, color library, curves, gradients, mesh gradients and color picking
- Image comparison, duplicate finder, batch rename, checksums and cipher tools
- Archive tools, Base64 tools, ASCII art and code preview
- QR/barcode scanning, SVG generation, fractal/noise/texture generation
- Screenshot framing, wallpaper export, audio-cover extraction and image preview
- Shader Studio, media picker, quick tiles, help and tool discovery

The complete module and resource inventory lives in the pinned submodule tree and its
upstream `settings.gradle.kts`, `ARCHITECTURE.md` and `README.md`.

## Next integration stages

1. Audit every feature module's Gradle dependencies, native ABI requirements, assets,
   models and license notices against Emre's existing app.
2. Build a compatibility matrix: reuse existing Coil 2.x, ONNX Runtime, Tesseract,
   ML Kit OCR, ExifInterface, MediaStore and gallery repository integrations where possible.
3. Port the shared image-processing engine and safe export pipeline first.
4. Integrate each feature group behind Emre-native contracts and screens.
5. Add only required native dependencies and model assets, with ABI filters and APK-size
   budgets; do not package all upstream models by default.
6. Run unit/instrumentation tests and the Android CI workflow after each integration group.

**Important:** adding this submodule makes the complete upstream source available in the
repository; it does not by itself compile those modules into Emre Gallery or mean the
features are already exposed in the app.
