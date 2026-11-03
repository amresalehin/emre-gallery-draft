# ImageToolbox → Emre Gallery: Complete Architecture & Implementation Plan

Architectural blueprint and integration plan for incorporating ImageToolbox's filter catalog, markup layers, vector drawing engine, geometry croppers, and neural AI model registry into Emre Gallery's photo editor, with standalone image and document utilities housed strictly outside the editor in a dedicated Settings hub.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions have been updated and confirmed based on user direction:

- **Strict Editor Boundary (No Utilities in Editor)**: Standalone image and document utilities (Format Conversion, PDF Tools, EXIF Editor, Collage Maker, QR Scanner, Color Palette, Compression Lab) **will NOT be placed inside the photo editor page**. The photo editor remains 100% focused on photographic editing across its 7 core categories.
- **Dedicated Utilities Hub in Settings**: All standalone image and document utilities will be housed strictly outside the editor in an **"Image & Document Utilities"** hub card within the Settings screen, providing an organized suite of independent tools without cluttering the photo editor workflow.
- **Full Roadmap Execution**: P0 features (Filter Catalog, Markup Layers, Drawing Engine, AI Model Registry) and P1 features (Background Removal, Crop Masks & Free Corners, Watermarking, Tone Curves & Color Filters) will be integrated together into a cohesive, non-blocking architecture.
- **On-Demand AI Model Weight Management**: Neural models (RealESRGAN, GFPGAN, SCUNet, RMBG, Depth Anything, DDColor, etc.) will not bloat the base APK. A centralized `NeuralModelManager` will provide on-demand downloads with progress tracking, SHA-256 verification, and local disk caching under app-private storage.
- **Preserve Emre's 20-Algorithm Portrait Engine**: Emre Gallery's custom portrait retouch algorithms (Teeth Whitening, Eye Enhancement, Skin Smoothing, Reshape, etc.) will be retained and paired with ImageToolbox's Spot Healing and whole-image cleanup filters (Scratches, Hot Pixels, Despeckle, JPEG Artifacts).
- **Strict 3-Tier Placement Hierarchy**: All editor panels will strictly honor the established vertical hierarchy:
  - **Tier 1 (Bottom dock)**: Main categories (`Adjust`, `Presets`, `Crop`, `Masking`, `Healing`, `Overlays`, `AI Tools`).
  - **Tier 2 (Just above dock)**: Subcategory / Mode selector tabs.
  - **Tier 3 (Above Tier 2)**: Sub-tools, specific algorithms, ratio presets, shape icons, and model pills.
  - **Top of Panel**: Parameter sliders, live histograms, action buttons, and contextual swatches.

---

## 1. Overview & Core Concept

### What It Does
Transforms Emre Gallery's photo editor into an all-in-one, professional-grade creative suite by integrating verified algorithms and UI workflows from ImageToolbox while keeping standalone utilities cleanly segregated:
1. **Adjust**: 6 subcategories (`Light`, `Color`, `Effects`, `Detail`, `Optics`, `Blur`) backed by individual parametric filter controllers.
2. **Presets**: 14 ImageToolbox groups reorganized into Emre's 9 Tier 3 pills (`Simple`, `FX`, `B&W`, `Blur`, `Sketch`, `Colors`, `Artistic`, `Magic`, `Canvas`) plus Favorites, Recents, and intensity controls.
3. **Crop**: Free-corner cropping with boundary coercion and magnifier loupe, shape/mask profiles, perspective warping, and content-aware bounds trimming.
4. **Masking**: Filter Masking (`Filter.Type.Masking` to apply any effect locally), freehand brush, lasso loop, flood-fill with tolerance, and shape masks.
5. **Healing**: Interactive spot heal brush, automatic red-eye removal, whole-image cleanup filters (scratches, hot pixels, despeckle), and Emre's portrait retouch suite.
6. **Overlays**: Layer-based markup engine with editable Text, Stickers, Vector Shapes (lines, arrows, rectangles, polygons, stars), Drawing paths (spray, neon, privacy blur), Canvas Background, and Watermarking.
7. **AI Tools**: Model registry and manager spanning 11 categories (`Upscale`, `Enhance`, `Denoise`, `DeJPEG`, `Artifacts`, `Colorize`, `Anime`, `Style Transfer`, `Depth`, `Scans`, `Analyze`).
8. **Standalone Utilities (Outside Editor)**: Dedicated "Image & Document Utilities" hub in Settings for conversion, collage, PDF, EXIF, and palette tools.

### Target Audience & Key Value
Mobile photographers, power users, and creatives seeking desktop-class editing without complex tool fragmentation or cloud dependency, maintaining 120Hz smooth rendering on local hardware.

---

## 2. User Experience & Visual Design

### Key User Flows

```
[Gallery / Viewer]
        │
        ├─► (Edit Action) ──► [Photo Editor Screen] (Pure Photographic Focus)
        │                            │
        │                            ├─► [Adjust]   ──► (Light, Color, Effects, Detail, Optics, Blur)
        │                            ├─► [Presets]  ──► (9 Groups, Favorites, Intensity Slider)
        │                            ├─► [Crop]     ──► (Basic, Free Corners + Loupe, Shape Masks, Perspective)
        │                            ├─► [Masking]  ──► (Filter Masking, Brush, Lasso, Flood Fill)
        │                            ├─► [Healing]  ──► (Spot Heal, Red-Eye, Cleanup, Portrait Retouch)
        │                            ├─► [Overlays] ──► (Frames, Text, Stickers, Shapes, Drawing, BG, Watermark)
        │                            └─► [AI Tools] ──► (Model Registry, Progress Bar, Live Preview)
        │
        └─► (Settings) ──────► [Settings Screen]
                                     │
                                     └─► [Image & Document Utilities Hub] (Outside Editor)
                                              ├─ Format Conversion (JPG, PNG, WebP, AVIF, JXL, SVG)
                                              ├─ Compression Lab & Resize
                                              ├─ PDF & Scan Tools
                                              ├─ Collage Maker & Image Stitching
                                              ├─ EXIF Metadata Inspector & Stripper
                                              └─ Color Palette Extractor & QR Scanner
```

### Visual Identity & Theme
- **Theme**: Luxury studio dark mode (`#0D0D0D` canvas, `#141414` panel surfaces, `#1F1F1F` component cards).
- **Accents**: Pure white active indicators (`#FFFFFF`), electric amber secondary badges (`#FFB300`), and soft slate muted borders (`#FFFFFF1F`).
- **Typography**: M3 typography tokens with tabular figures for real-time numeric slider badges (`EV`, `%`, `px`, `deg`).
- **Component Styling**: 48dp minimum touch targets, haptic feedback on slider snaps and tool toggles, and seamless edge-to-edge system bar padding.

---

## 3. Key Product Decisions & Trade-Offs

### 1. Pure Editor Scope (No Utilities in Editor)
- **Decision**: Standalone utilities (PDF, Format Converter, Collage, EXIF, QR) are strictly excluded from the Photo Editor page. They are hosted in a dedicated hub under Settings.
- **Rationale**: Keeps the editor interface uncluttered, responsive, and dedicated exclusively to creative photo editing and color grading.

### 2. Parametric Controls vs. Generic Sliders
- **Decision**: Rather than collapsing all filters into generic sliders, every filter from ImageToolbox (`Gaussian Blur`, `Lens Distortion`, `CLAHE`, `Color Balance`, `Tilt-Shift`) exposes its native parameters via a dynamic parameter builder.
- **Rationale**: Preserves photographic precision while avoiding confusing, monolithic slider lists.

### 3. Markup Layer Objects vs. Raster Drawing
- **Decision**: Overlays (Text, Stickers, Shapes) are rendered as separate transformable layer objects with position, scale, rotation, and drop shadows, while Drawing operates on a dedicated raster mask layer.
- **Rationale**: Prevents destructive rasterization until export, allowing non-destructive repositioning and styling of annotations and stickers.

### 4. Progressive AI Model Architecture
- **Decision**: Models are enumerated via a lightweight metadata manifest. When a user selects a model for the first time, an inline progress indicator tracks download and SHA-256 verification before caching locally.
- **Rationale**: Keeps APK download size under 40MB while supporting multi-gigabyte neural pipelines.

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   PhotoEditorScreen                                    │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Top Bar: Back / Undo / Redo / History / Replay / Share / Export / Save         │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                                │   │
│   │                           Interactive Photo Canvas                             │   │
│   │       (Zoom/Pan · Free Corner Pins & Loupe · Markup Layer Handles · Drawing)   │   │
│   │                                                                                │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Active Sub-Panel: Sliders · Color Palette · Action Buttons · Histograms        │   │
│   ├────────────────────────────────────────────────────────────────────────────────┤   │
│   │ Tier 3: Sub-Tools / Presets / Shape Icons / AI Models                          │   │
│   ├────────────────────────────────────────────────────────────────────────────────┤   │
│   │ Tier 2: Subcategory Pills (e.g. Light·Color·Effects·Detail·Optics·Blur)        │   │
│   ├────────────────────────────────────────────────────────────────────────────────┤   │
│   │ Tier 1: Main Dock (Adjust · Presets · Crop · Masking · Healing · Overlays · AI)│   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ State & Actions
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                PhotoEditorViewModel                                    │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Session State: History Stack · Active Tool · Markup Layers · Mask Bitmap       │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           │ Dispatches
│                   ┌───────────────────────┼────────────────────────┐                   │
│                   ▼                       ▼                        ▼                   │
│        ┌────────────────────┐  ┌────────────────────┐   ┌────────────────────┐         │
│        │ ImageFilterEngine  │  │ MarkupLayerEngine  │   │ NeuralModelManager │         │
│        │ (Adjust & Presets) │  │ (Text/Shape/Draw)  │   │ (Local Onnx/Ncnn)  │         │
│        └────────────────────┘  └────────────────────┘   └────────────────────┘         │
└────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        SettingsScreen (Outside Photo Editor)                           │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Card: "Image & Document Utilities" ───────────────────────────────────────────┐│   │
│   └───────────────────────────────────────────────────────────────────────────────│┘   │
│                                                                                   ▼    │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                       UtilitiesHubScreen / Dialog                              │   │
│   │   ├─ Format Converter (JPG, PNG, WebP, AVIF, JXL, SVG)                         │   │
│   │   ├─ Compression Lab & Weight Resizer                                          │   │
│   │   ├─ Document Scanner & PDF Tools                                              │   │
│   │   ├─ Collage Maker & Image Stitcher                                            │   │
│   │   ├─ EXIF Metadata Inspector & Stripper                                        │   │
│   │   └─ Color Palette Extractor & QR Scanner                                      │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Module Breakdown & Implementation Phases

#### Phase 1: Adjust & Presets Parametric Filter Engine
- Expand `AdjustCategory` with verified ImageToolbox parameters:
  - `LIGHT`: Tone Mapping, Histogram Equalization, CLAHE.
  - `COLOR`: Color Balance, Channel Mix, Color Replacement, Monochrome/Solarize.
  - `EFFECTS`: Bloom, Bokeh, Halftone, Anaglyph, Chromatic Aberration.
  - `DETAIL`: Unsharp Mask, Multiscale Sharpen, Deblur, Denoise.
  - `OPTICS`: Lens Distortion, Sphere Refraction.
  - `BLUR`: Gaussian, Box, Zoom, Motion, Tilt-Shift, Lens Blur.
- Expand `Presets` with the 9 verified groups (`Simple`, `FX`, `B&W`, `Blur`, `Sketch`, `Colors`, `Artistic`, `Magic`, `Canvas`) with intensity controls, Favorites, and Recents.

#### Phase 2: Geometry & Crop Expansion
- Add `Free Corners` mode with 4 draggable corner pins and interactive magnifier loupe.
- Add `Shape Masks` (rounded corners, cut corners, star, heart, oval).
- Integrate perspective distortion and content-aware auto-crop.

#### Phase 3: Masking & Healing
- Filter Masking: Apply any adjustment or filter strictly inside the painted mask.
- Selection Tools: Brush, Lasso path, Flood Fill with color tolerance, and Shape selections.
- Healing: Spot Heal brush with live source preview, Red-Eye automatic filter, whole-image cleanup (scratches/hot pixels), and Emre's portrait retouch suite.

#### Phase 4: Overlays & Markup Layers
- Implement transformable `MarkupLayer` hierarchy:
  - `TextLayer`: custom fonts, gradients, drop shadows, curvature, outlines.
  - `ShapeLayer`: arrows, rectangles, polygons, stars, stroke/fill styling.
  - `StickerLayer`: image/sticker assets with blend modes and opacity.
  - `DrawingLayer`: spray, neon, highlighter, privacy blur, pixelation.
  - `WatermarkLayer`: configurable text/stamp/timestamp/logo overlay.
  - `BackgroundLayer`: cutout subject extraction and solid/gradient/image canvas backing.

#### Phase 5: AI Tools Registry & On-Demand Manager
- `NeuralModelManager`:
  - Categories: `Upscale`, `Enhance`, `Denoise`, `DeJPEG`, `Artifacts`, `Colorize`, `Anime`, `Style Transfer`, `Depth`, `Scans`, `Analyze`.
  - Manifest-based model downloader with resume support, disk cache validation, and execution pipeline.

#### Phase 6: Standalone Utilities Hub in Settings
- Build the **"Image & Document Utilities"** hub card in the Settings screen.
- Provide clean dedicated dialogs/screens for:
  - Format Converter (JPG, PNG, WebP, AVIF, JXL, SVG) & Compression Lab.
  - PDF & Document Scan Tools.
  - Collage Maker & Multi-Image Stitching.
  - EXIF Metadata Inspector & Stripper.
  - Color Palette Extractor & QR Scanner.
