# Comprehensive Native Gestures, Follow-Finger Motion & Animation Architecture

Implement fluid, physics-driven native gestures, continuous follow-finger interactive drag transitions, pinch-to-zoom/pan, grid column pinch-resizing, and predictive back navigation across Emre Gallery.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following two core directions during Phase 1 clarification:
> 1. **Scope Priority**: Comprehensive gesture and motion upgrade across all screens (photo viewer, gallery grid, and app-level navigation).
> 2. **Interaction Model**: **Follow-Finger (Continuous Interaction)** — Touch coordinates are dynamically tracked in real time (`pointerInput`, velocity tracking, and continuous drag offsets) so visual elements translate, scale, and transform directly under the user's fingertips, resolving with spring physics upon release.

- **Confirmed Decision 1**: Implement continuous vertical drag-to-dismiss in the full-screen photo viewer with real-time backdrop alpha fade, proportional thumbnail scaling, and corner rounding.
- **Confirmed Decision 2**: Implement multi-touch pinch-to-zoom, two-finger pan, and double-tap zoom with spring recovery at bounds.
- **Confirmed Decision 3**: Implement dynamic multi-touch pinch-to-resize on the main gallery grid (2, 3, 4, 5 columns) with continuous visual scale feedback during gesture.
- **Confirmed Decision 4**: Support Android Predictive Back navigation (`PredictiveBackHandler`) on sub-screens and media viewer with live progress-driven scale and translation.

---

## 1. Overview & Core Concept

### What It Delivers
This upgrade replaces static view changes and abrupt modal dismissals with natural, continuous physics-based touch interactions inspired by Material 3 Motion and modern native gallery standards (such as Google Photos):
1. **Interactive Viewer Dismissal**: Dragging down or up on an unzoomed photo continuously translates the image along the finger vector, scales down toward 0.65x, rounds image corners, and dissolves the ambient black backdrop in real time. Releasing past threshold springs the photo back into the grid, while letting go early springs it back to center.
2. **Smooth Pan & Zoom Canvas**: True double-tap to zoom (toggle 1x <-> 2.5x around tap anchor) and multi-touch pinch-to-zoom up to 5x with boundary clamping and smooth deceleration.
3. **Pinch-to-Resize Gallery Grid**: Two-finger pinch on the media grid dynamically stretches/contracts the photo tiles in real-time, snapping to adjacent column layouts (2 to 5 columns) with spring animations upon release.
4. **Predictive Back Gestures**: Swiping from screen edges smoothly shrinks and translates the active screen, revealing the parent screen beneath with elevation depth before committing to pop.

### Key Value
Gives the gallery an organic, high-framerate feel where every interactive element reacts instantly to tactile input without stutter or jarring layout resets.

---

## 2. User Experience & Visual Design

### Key User Flows

#### Flow A: Continuous Follow-Finger Viewer Dismiss
```
┌─────────────────────────────────┐      ┌─────────────────────────────────┐      ┌─────────────────────────────────┐
│ Fullscreen Photo                │      │ Dragging Downward               │      │ Released Past Threshold         │
│                                 │      │                                 │      │                                 │
│ [Solid Black 100% Background]   │ ───> │ [Backdrop Fades: 60% Alpha]     │ ───> │ [Image Springs Into Grid Tile]  │
│ [Full Bleed Image (Scale 1.0)]  │      │ [Image Scale: 0.85x + Rounded]  │      │ [Parent Grid Restored Instantly]│
│                                 │      │ [Follows Finger Y Offset]       │      │                                 │
└─────────────────────────────────┘      └─────────────────────────────────┘      └─────────────────────────────────┘
```
- **Touch Began**: As finger drags vertically, controls (app bar, bottom action dock) fade out smoothly within the first 40dp of travel.
- **In-Flight Motion**: Image position tracks `(dragOffsetX, dragOffsetY)` 1:1. Card scale interpolates from `1.0f` down to `0.70f` based on drag distance. Background alpha drops proportionally from `1.0f` down to `0.2f`.
- **Threshold & Velocity**: If released with `offsetY > 160dp` or downward flick velocity `> 1200dp/s`, the viewer dismisses immediately with exit spring physics. Otherwise, a `Spring.StiffnessMediumLow` spring animates the image back to `(0, 0)` and restores 100% opacity.

#### Flow B: Pinch-to-Zoom & Double-Tap Anchoring
- **Pinch**: Simultaneous two-finger pointer tracking calculates focal center and scale delta. Clamped between `1.0f` and `5.0f`.
- **Double Tap**: Taps detect focal offset and smoothly animate zoom between `1.0f` and `2.75f` using `Animatable` spring curve.
- **Pan Handoff**: When scale `> 1.05f`, drag gestures pan the image viewport instead of changing photos or dismissing. When pan hits the horizontal boundary, edge resistance rubber-bands before allowing neighbor photo peek.

#### Flow C: Interactive Gallery Grid Pinch-to-Columns
- **Two Finger Pinch on Grid**: Moving fingers apart scales current thumbnails up (animating toward fewer columns, e.g. 3 -> 2); pinching fingers together scales thumbnails down (animating toward more columns, e.g. 3 -> 4 -> 5).
- **Haptic Snap**: Haptic click fires when crossing density thresholds; releasing triggers a smooth animated re-layout into the new column configuration, stored persistently in DataStore.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale | Alternatives Considered |
|---|---|---|---|
| **Viewer Drag Physics** | Custom `pointerInput` with `detectDragGestures` and `Animatable` offsets | Gives exact frame-by-frame control over translation, scale, corner clip, and backdrop alpha simultaneously | Standard Compose `SwipeToDismissBox` (inflexible, limited to horizontal/card semantics) |
| **Pinch-to-Zoom Engine** | Custom Matrix/Offset gesture state with boundary clamping | Allows seamless handoff between pan-when-zoomed and swipe-neighbor-when-unzoomed | Third-party zoomable libraries (avoid adding large third-party dependencies) |
| **Grid Column Scaling** | Interactive scale factor state applied via `Modifier.graphicsLayer` during pinch, committed on release | Zero lag during finger tracking; clean recomposition only occurs when finger lifts | Recomputing grid column count on every gesture delta frame (causes heavy recompositions) |
| **Predictive Back** | `PredictiveBackHandler` with animated scale, translation, and scrim alpha | Native Android 14/15/16 predictive back behavior that respects system gesture navigation | Standard immediate `BackHandler` without pre-commit visual feedback |

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Tree                         │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  MainActivity                                                          │
│  └── GalleryApp (Theme & Scaffold)                                     │
│      ├── PredictiveBackHandler (Edge-to-edge back swipe progress)      │
│      │                                                                 │
│      ├── AnimatedContent (Destination Switcher with Shared Curves)      │
│      │   │                                                             │
│      │   ├── HomeScreen / MediaGrid                                    │
│      │   │   ├── PinchGestureModifier (Dynamic column zoom)            │
│      │   │   └── LazyVerticalGrid (Items with staggered spring entry)  │
│      │   │                                                             │
│      │   └── ViewerScreen                                              │
│      │       ├── InteractiveDismissContainer                           │
│      │       │   ├── BackdropAlpha (Interpolated from drag delta)      │
│      │       │   ├── FollowFingerDragLayer (scale, translation, clip)  │
│      │       │   └── ZoomableMediaBox (Pinch, pan, double-tap zoom)    │
│      │       │                                                         │
│      │       └── ViewerControlsOverlay (AnimatedVisibility fade)       │
│      │                                                                 │
│      └── PreferencesRepository (DataStore: gridColumns, sort, theme)   │
└────────────────────────────────────────────────────────────────────────┘
```

### Motion Curve Specifications
- **Spring Physics for Dismiss Cancel**: `spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)`
- **Spring Physics for Dismiss Exit**: `tween(durationMillis = 220, easing = FastOutLinearInEasing)`
- **Zoom Spring**: `spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)`
- **Grid Layout Animation**: `tween(durationMillis = 280, easing = FastOutSlowInEasing)`

---

## Verification & Validation Plan
1. **Interactive Viewer Dismiss**: Test vertical drag down and drag up on photos and videos; verify smooth follow-finger tracking, backdrop fade, corner rounding, and snappy threshold dismiss.
2. **Zoom & Pan**: Test pinch gesture up to 5x zoom, double tap to toggle zoom level, boundary bounce, and seamless transition between panning and neighbor swiping.
3. **Grid Column Pinch**: Test two-finger pinch-in and pinch-out on photo grid; verify column density switches smoothly between 2, 3, 4, and 5 columns and persists across app restarts.
4. **Predictive Back**: Test back swipe on sub-screens and media viewer; verify scale-down reveal and cancellation spring.
5. **Compilation & Performance**: Run `compile_applet` to confirm 60+ FPS gesture tracking without frame drops or memory leaks.
