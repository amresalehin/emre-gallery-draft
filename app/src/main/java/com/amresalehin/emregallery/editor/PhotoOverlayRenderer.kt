package com.amresalehin.emregallery.editor

import android.graphics.Canvas
import com.amresalehin.emregallery.editor.renderers.*

/**
 * Facade for overlay rendering operations.
 * Delegates specialized rendering to dedicated renderers (SRP):
 * - [BorderFrameRenderer]: Borders and decorative frames
 * - [ShapeRenderer]: Geometric and vector shapes
 * - [DrawLayerRenderer]: Brush strokes and layered drawings
 * - [TextOverlayRenderer]: Typography and curved text
 * - [StickerCalloutRenderer]: Stickers and comic callouts
 * - [LensFlareRenderer]: Optical flares and sunbursts
 * - [PhotoLayerStackRenderer]: Multi-layer compositing
 */
object PhotoOverlayRenderer {

    fun renderAll(canvas: Canvas, width: Float, height: Float, state: EditSessionState, density: Float) {
        // 0. Redesigned Photoshop-Style Overlay Layer Stack
        PhotoLayerStackRenderer.renderOverlayLayers(canvas, width, height, state.overlayLayers, density)

        // 1. Borders
        BorderFrameRenderer.renderBorder(canvas, width, height, state.border)

        // 2. Frames
        BorderFrameRenderer.renderFrame(canvas, width, height, state.frame, density)

        // 3. Shapes
        for (shape in state.shapes) {
            ShapeRenderer.renderShape(canvas, width, height, shape, density)
        }

        // 4. Drawing Strokes (Grouped by PicsArt Draw Layers & Blend Modes)
        DrawLayerRenderer.renderDrawLayers(canvas, width, height, state, density)

        // 5. Text Overlays
        for (textItem in state.textOverlays) {
            TextOverlayRenderer.renderTextOverlay(canvas, width, height, textItem, density)
        }

        // 6. Stickers
        for (stk in state.stickers) {
            StickerCalloutRenderer.renderSticker(canvas, width, height, stk, density)
        }

        // 7. Callouts
        for (callout in state.callouts) {
            StickerCalloutRenderer.renderCallout(canvas, width, height, callout, density)
        }

        // 8. Lens Flares
        for (flare in state.lensFlares) {
            LensFlareRenderer.renderLensFlare(canvas, width, height, flare, density)
        }
    }
}
