# Emre image-editor integration status

This document distinguishes the Emre-native image editor from the upstream ImageToolbox reference snapshot in this directory.

## Integrated into Emre Gallery

The editor is a native Compose destination and uses Emre's existing Coil 2 image loader. Its current working feature set is:

- Free crop and aspect-ratio presets: 1:1, 4:3, 3:2, 16:9 and 9:16.
- 90-degree rotation and horizontal / vertical mirroring.
- Brightness, exposure (EV), contrast, saturation, temperature and tint controls.
- Forty colour presets across portrait, film, vintage, monochrome and colour families, with adjustable filter strength.
- Vignette, film grain, fade, soft-focus, sharpening and pixelation effects.
- On-device **person** background segmentation via the bundled ML Kit Selfie Segmenter, with transparent PNG export; this model is not a general object segmenter.
- Drawing & Annotation: non-destructive pen/highlighter strokes, annotation-only eraser, lines, arrows, rectangle/ellipse outlines and tap-positioned text layers.
- Eight annotation colours, adjustable stroke width, per-markup undo/redo, remove-last and clear-all controls; annotation layers render in preview and are composited into JPEG/PNG export.
- Original-versus-edited preview comparison.
- A 30-state undo / redo history for crop, transform, adjustment, filter and effect changes.
- A bounded 2048-pixel working preview; full-resolution decoding is deferred until export.
- Save ordinary edits as a new JPEG in `Pictures/Emre`; person cutouts are saved as transparent PNG. The source is never overwritten.
- Best-effort EXIF transfer for capture date, camera/lens, exposure and GPS fields; output orientation and pixel dimensions are normalized to the exported pixels.
- MediaStore pending-row export on Android 10+, with a legacy public-folder path and runtime write-permission request on Android 8–9.

The viewer's Edit action and the Tools shortcut open this native editor. Video items are intentionally excluded.

## Not integrated yet

The upstream source snapshot is not a complete application module. The following are **not** provided by the current Emre editor slice:

- The full upstream filter/effect catalogue, LUT import, GPU shader studio or shader pipeline.
- General-purpose object/background removal model selection and execution (the current implementation is limited to people segmentation).
- Advanced drawing tools not included in the current slice: stickers/images, flood fill, spray/neon brushes, lasso/freeform selection, brush transforms, spot healing and generative inpainting.
- Image format conversion and the full upstream compression backends.
- OpenCV stitching, photomosaic, collage and image compositing tools.
- The upstream OCR and PDF implementations (Emre has its own OCR and PDF code paths).
- Batch editing, animation editing, RAW/HEIC/AVIF/PSD workflows, or non-JPEG export formats.
- A complete preset marketplace or hundreds of ready-made effects.

Do not describe these as available Emre features until they have been adapted, connected to Emre's dependencies and UI, and verified.

## Export and metadata notes

All exports are separate JPEG files. Metadata transfer is best-effort: unsupported tags, provider restrictions, or malformed source EXIF do not block the pixel export. Since the output pixels are transformed, the orientation flag is reset to Normal and image pixel dimensions are updated. GPS tags are retained when present in the source; users should be aware that the edited file may therefore retain location metadata.

The annotation renderer keeps markup separate from the source bitmap until export; its eraser clears only the annotation compositing layer. Markups use normalized coordinates against the transformed/cropped preview and are rendered at export resolution. Annotation layers are held in the current in-memory 30-state undo/redo recipe and are not yet persisted as reopenable project files.

The AI cutout uses Google's bundled ML Kit Selfie Segmenter (beta API); the bundled model adds approximately 4.5 MB to the app. It runs on-device and exports transparent PNG; PNG output does not copy EXIF metadata in this slice. Segmentation is designed for people and should not be advertised as arbitrary object removal. The preview is intentionally bounded to reduce memory pressure. Export re-decodes the original URI at full resolution and runs image processing off the main thread. Filter matrices are blended with identity at the selected strength. Vignette, grain, fade, sharpening and pixelation are processed row-by-row; soft-focus uses a reduced-resolution intermediate and bilinear sampling instead of a full-size blur buffer. Very large source images can still require substantial temporary memory during export.

## Verification

The Android workflow is configured to continue to debug APK assembly and artifact upload after unit tests fail, while retaining a failing test result in the overall job. The Ente face-pipeline unit test has previously failed in CI while fetching a Maven artifact over HTTP; this is an external dependency/network failure, not an image-editor assertion. See the latest run from the branch for the current result.

This branch has not been validated on a physical Android device or emulator in this work session. Runtime permission prompts, image-provider edge cases, EXIF retention and visual crop/gesture behaviour still need device QA.

## Upstream reference and licensing

The `upstream/` subtree remains a technical reference snapshot from the pinned upstream revision described in [README.md](README.md). Its source headers and Apache License 2.0 notices must be retained for any copied/adapted upstream code. This integration status file does not change the provenance or licensing of that snapshot.
