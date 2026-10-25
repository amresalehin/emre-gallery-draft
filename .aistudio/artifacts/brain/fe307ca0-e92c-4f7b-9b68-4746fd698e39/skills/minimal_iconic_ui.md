# Minimal, Clutter-Free, Aesthetic UI: "Icons Tell the Story"

This design skill documents the principles and Jetpack Compose patterns that define the clean, aesthetic, and icon-driven user interface:

## Principles

1. **Icons Tell the Story**
   - Pair every control with an expressive, universally understood Material icon.
   - Use icon-only controls (e.g. Sun/Moon pill toggle) when the visual metaphor is unmistakable.
   - Avoid redundant explanations; let icons and concise labels convey purpose.

2. **Less Clutter, Zero AI Slop**
   - Eliminate verbose descriptions, paragraphs, and disclaimers under settings and controls.
   - Remove redundant separate sub-screens when inline dropdowns, segmented controls, or bottom action rows can handle the task in place.

3. **High-Density, Unified Cards**
   - Group related subsystems into unified, self-contained cards (e.g., OCR Card with progress, model toggle, language dropdown, and cache controls; Face Recognition Card with progress, confidence/cluster sliders, engine dropdown, and clustering actions).
   - Use 18–20dp rounded shapes, 1dp subtle border strokes (`outlineVariant.copy(alpha = 0.35f)`), and modern surface container elevation.

4. **Compact Inline Controls**
   - **Segmented button rows**: Kept compact (36–38dp height) for binary/ternary mode switches (such as `[ Fast | Best ]`).
   - **Inline dropdown menus**: Tap a pill showing the active state; inside the menu show clean titles, subtitles, checkmarks for active items, and download/install icons for missing resources.
   - **Compact Sliders**: Icon on the left (22dp), centered Column containing the label and slim Slider (24–26dp), and formatted numeric value aligned on the right.
   - **Header Primary Actions**: The main action button (Start / Pause / Resume) is embedded directly in the card header next to the title.

## Compose Patterns Implemented in Emre Gallery
- **Theme Card**: Replaced with a single-row card containing an icon-only Light/Dark toggle with sun and moon icons.
- **OCR Card**: Progress, Fast/Best model switcher, Language selection dropdown with on-demand download, and cache management.
- **Face Recognition Card**: Progress, detection confidence slider, min faces per person slider, clustering distance slider, model selection (Buffalo_L vs Native), and clustering controls.
