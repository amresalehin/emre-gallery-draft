package com.amresalehin.emregallery

import android.graphics.Bitmap
import android.graphics.RectF
import com.amresalehin.emregallery.editor.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoEditorTest {

    @Test
    fun testTransformParams_applyRotationAndFlip() {
        val src = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val params = TransformParams(
            rotationDegrees = 90,
            flipHorizontal = true
        )
        val transformed = PhotoProcessor.applyTransform(src, params)
        assertNotNull(transformed)
        // Rotating 90 degrees swaps width and height
        assertEquals(200, transformed.width)
        assertEquals(100, transformed.height)
    }

    @Test
    fun testTransformParams_applyCrop() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val params = TransformParams(
            cropRect = RectF(0.25f, 0.25f, 0.75f, 0.75f)
        )
        val cropped = PhotoProcessor.applyTransform(src, params)
        assertNotNull(cropped)
        assertEquals(50, cropped.width)
        assertEquals(50, cropped.height)
    }

    @Test
    fun testAdjustmentsAndFilter_nonDestructive() {
        val src = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val adj = AdjustmentParams(
            brightness = 20f,
            contrast = 15f,
            saturation = 10f
        )
        val processed = PhotoProcessor.applyAdjustmentsAndFilter(src, adj, FilterType.VIVID, 1.0f)
        assertNotNull(processed)
        assertNotSame(src, processed)
        assertEquals(src.width, processed.width)
        assertEquals(src.height, processed.height)
    }

    @Test
    fun testRenderFinal_pipelineExecutesWithoutCrash() = runBlocking {
        val src = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val state = EditSessionState(
            transform = TransformParams(rotationDegrees = 180),
            adjustments = AdjustmentParams(exposure = 10f, sharpness = 15f),
            filter = FilterType.FILM,
            frame = FrameStyle.POLAROID
        )
        val finalBmp = PhotoProcessor.renderFinal(src, state)
        assertNotNull(finalBmp)
        assertEquals(60, finalBmp.width)
        assertEquals(60, finalBmp.height)
    }

    @Test
    fun testEditSessionState_undoRedo() {
        val vm = PhotoEditorViewModel()
        assertFalse(vm.canUndo.value)
        assertFalse(vm.canRedo.value)

        vm.rotate90()
        assertTrue(vm.canUndo.value)
        assertFalse(vm.canRedo.value)
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)

        vm.rotate90()
        assertEquals(180, vm.sessionState.value.transform.rotationDegrees)

        vm.undo()
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)
        assertTrue(vm.canRedo.value)

        vm.redo()
        assertEquals(180, vm.sessionState.value.transform.rotationDegrees)
    }

    @Test
    fun testSliderAdjustment_undoRedo() {
        val vm = PhotoEditorViewModel()
        assertEquals(0f, vm.sessionState.value.adjustments.brightness, 0.001f)

        // Live slider drag
        vm.updateAdjustmentValue("Brightness", 25f)
        assertEquals(25f, vm.sessionState.value.adjustments.brightness, 0.001f)
        // User releases slider
        vm.commitAdjustmentValue("Brightness", 25f)
        assertTrue(vm.canUndo.value)

        // Undo slider drag
        vm.undo()
        assertEquals(0f, vm.sessionState.value.adjustments.brightness, 0.001f)
        assertTrue(vm.canRedo.value)

        // Redo slider drag
        vm.redo()
        assertEquals(25f, vm.sessionState.value.adjustments.brightness, 0.001f)
    }

    @Test
    fun testFilterSelection_undoRedo() {
        val vm = PhotoEditorViewModel()
        assertEquals(FilterType.NONE, vm.sessionState.value.filter)

        vm.setFilter(FilterType.VIVID, 0.8f)
        assertEquals(FilterType.VIVID, vm.sessionState.value.filter)
        assertTrue(vm.canUndo.value)

        vm.undo()
        assertEquals(FilterType.NONE, vm.sessionState.value.filter)
        assertTrue(vm.canRedo.value)

        vm.redo()
        assertEquals(FilterType.VIVID, vm.sessionState.value.filter)
    }

    @Test
    fun testArtisticEffects_renderWithoutCrash() {
        val src = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        // Verify artistic effects
        val oil = PhotoEffectsEngine.applyOilPainting(src, 0.7f)
        assertNotNull(oil)
        val cartoon = PhotoEffectsEngine.applyCartoon(src, 0.8f)
        assertNotNull(cartoon)
        val kaleidoscope = PhotoEffectsEngine.applyKaleidoscope(src, 0.5f)
        assertNotNull(kaleidoscope)
        val glitch = PhotoEffectsEngine.applyRgbGlitch(src, 0.6f)
        assertNotNull(glitch)
    }

    @Test
    fun testExtendedAdjustments_pipeline() {
        val src = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        val adj = AdjustmentParams(
            vibrance = 25f,
            dehaze = 15f,
            dodge = 10f,
            burn = 5f,
            whiteBalanceKelvin = 6500f,
            levels = LevelsParams(blackPoint = 10f, whitePoint = 240f),
            colorBalance = ColorBalanceParams(shadowRed = 5f, highlightBlue = 10f),
            gradientMap = GradientMapType.SEPIA_GOLD,
            duotone = DuotoneParams(enabled = false)
        )
        val result = PhotoAdjustEngine.applyExtendedAdjustments(src, adj)
        assertNotNull(result)
        assertEquals(30, result.width)
        assertEquals(30, result.height)
    }

    @Test
    fun testRetouch_teethWhiteningAndSmudge() {
        val src = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val strokes = listOf(
            RetouchStroke(type = RetouchType.TEETH_WHITENING, points = listOf(androidx.compose.ui.geometry.Offset(20f, 20f)), radius = 10f),
            RetouchStroke(type = RetouchType.EYE_ENHANCEMENT, points = listOf(androidx.compose.ui.geometry.Offset(15f, 15f)), radius = 8f),
            RetouchStroke(type = RetouchType.SMUDGE_BRUSH, points = listOf(androidx.compose.ui.geometry.Offset(10f, 10f), androidx.compose.ui.geometry.Offset(25f, 25f)), radius = 12f)
        )
        val result = PhotoRetouchEngine.applyRetouch(src, strokes)
        assertNotNull(result)
        assertEquals(40, result.width)
    }

    @Test
    fun testTransform_cornerPinAndShapeCropAndAutoLevel() {
        val src = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val shapeCropped = PhotoTransformEngine.applyShapeCrop(src, ShapeCropType.HEART)
        assertNotNull(shapeCropped)

        val pinned = PhotoTransformEngine.applyCornerPinPerspective(
            src,
            listOf(CornerPinPoint(0f, 0f), CornerPinPoint(1f, 0.1f), CornerPinPoint(0.9f, 1f), CornerPinPoint(0.1f, 0.9f))
        )
        assertNotNull(pinned)

        val angle = PhotoTransformEngine.detectHorizonAngle(src)
        assertTrue(angle in -45f..45f)
    }

    @Test
    fun testCutout_backgroundModes() {
        val src = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        val bg = BackgroundSettings(
            mode = BackgroundMode.SOLID,
            solidColor = androidx.compose.ui.graphics.Color.Black,
            subjectShadow = true
        )
        val result = PhotoCutoutEngine.applyBackgroundCutout(src, bg)
        assertNotNull(result)
        assertEquals(30, result.width)
    }

    @Test
    fun testEditorMode_cancelRevertsToBaseline() {
        val vm = PhotoEditorViewModel()
        assertEquals(EditorMode.NONE, vm.currentMode.value)
        assertEquals(0, vm.sessionState.value.transform.rotationDegrees)

        // Enter Crop mode
        vm.enterMode(EditorMode.CROP)
        assertEquals(EditorMode.CROP, vm.currentMode.value)

        // Rotate in mode
        vm.rotate90()
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)
        assertTrue(vm.hasModeChanges())

        // Cancel mode - must roll back to 0 degrees and exit to NONE
        vm.cancelModeChanges()
        assertEquals(EditorMode.NONE, vm.currentMode.value)
        assertEquals(0, vm.sessionState.value.transform.rotationDegrees)
        assertFalse(vm.hasModeChanges())
    }

    @Test
    fun testEditorMode_applyCommitsChanges() {
        val vm = PhotoEditorViewModel()
        vm.enterMode(EditorMode.CROP)
        vm.rotate90()
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)

        // Apply mode - must keep 90 degrees and commit to history
        vm.applyModeChanges("Crop Applied")
        assertEquals(EditorMode.NONE, vm.currentMode.value)
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)
        assertTrue(vm.canUndo.value)

        // Undo should bring it back to before mode entry (0 degrees)
        vm.undo()
        assertEquals(0, vm.sessionState.value.transform.rotationDegrees)
    }

    @Test
    fun testEditorMode_resets() {
        val vm = PhotoEditorViewModel()
        vm.rotate90()
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)

        vm.resetCrop()
        assertEquals(0, vm.sessionState.value.transform.rotationDegrees)
        assertNull(vm.sessionState.value.transform.cropRect)

        vm.setHslAdjustment(ColorBand.BLUE, HslBandAdjustment(hueShift = 20f))
        assertNotNull(vm.sessionState.value.adjustments.hslAdjustments[ColorBand.BLUE])

        vm.resetColorMix()
        assertTrue(vm.sessionState.value.adjustments.hslAdjustments.isEmpty())
    }

    @Test
    fun testPresetTier3Categories_andTier2Presets() {
        val categories = com.amresalehin.emregallery.editor.ui.PresetTier3Tabs
        assertEquals(9, categories.size)
        assertTrue(categories.contains("SIMPLE"))
        assertTrue(categories.contains("FX"))
        assertTrue(categories.contains("B&W"))
        assertTrue(categories.contains("BLUR"))
        assertTrue(categories.contains("SKETCH"))
        assertTrue(categories.contains("COLORS"))
        assertTrue(categories.contains("ARTISTIC"))
        assertTrue(categories.contains("MAGIC"))
        assertTrue(categories.contains("CANVAS"))

        // Verify presets exist for all categories
        categories.forEach { cat ->
            val presets = com.amresalehin.emregallery.editor.ui.generatePresetsForCategory(cat)
            assertTrue("Category $cat should have presets", presets.isNotEmpty())
            assertEquals("First item should be None", "None", presets.first().name)
        }

        // Verify specific presets from screenshots
        val simplePresets = com.amresalehin.emregallery.editor.ui.generatePresetsForCategory("SIMPLE").map { it.name }
        assertTrue(simplePresets.contains("Dodger"))
        assertTrue(simplePresets.contains("BRL2"))
        assertTrue(simplePresets.contains("BRL4"))
        assertTrue(simplePresets.contains("APR2"))
        assertTrue(simplePresets.contains("Light Cross"))
        assertTrue(simplePresets.contains("Sunny"))

        val fxPresets = com.amresalehin.emregallery.editor.ui.generatePresetsForCategory("FX").map { it.name }
        assertTrue(fxPresets.contains("HDR1"))
        assertTrue(fxPresets.contains("Noise"))
        assertTrue(fxPresets.contains("Film"))
        assertTrue(fxPresets.contains("Film2"))
        assertTrue(fxPresets.contains("Film3"))
        assertTrue(fxPresets.contains("Holga2"))
        assertTrue(fxPresets.contains("VNYL3"))
        assertTrue(fxPresets.contains("INDIE1"))

        val magicPresets = com.amresalehin.emregallery.editor.ui.generatePresetsForCategory("MAGIC").map { it.name }
        assertTrue(magicPresets.contains("Flora"))
        assertTrue(magicPresets.contains("Pastel"))
        assertTrue(magicPresets.contains("Undead"))
        assertTrue(magicPresets.contains("White Ice"))
        assertTrue(magicPresets.contains("Geode"))
        assertTrue(magicPresets.contains("Sketchy"))
        assertTrue(magicPresets.contains("Pow"))
        assertTrue(magicPresets.contains("Flare"))
        assertTrue(magicPresets.contains("Animation"))
    }

    @Test
    fun testPresetApplication_viewModelUpdatesState() {
        val vm = PhotoEditorViewModel()
        val simplePresets = com.amresalehin.emregallery.editor.ui.generatePresetsForCategory("SIMPLE")
        val lightCross = simplePresets.first { it.name == "Light Cross" }

        vm.applyProfile(lightCross)
        assertEquals(FilterType.LIGHT_CROSS, vm.sessionState.value.filter)

        // Update intensity / fade
        vm.updatePresetIntensity(0.75f)
        assertEquals(0.75f, vm.sessionState.value.filterIntensity, 0.001f)

        // Update blend mode
        vm.updatePresetBlendMode("Multiply")
        assertEquals("Multiply", vm.sessionState.value.filterBlendMode)
    }

    @Test
    fun testMainBottomTabs_autoRemoved() {
        val tabs = com.amresalehin.emregallery.editor.ui.MainBottomTab.entries
        assertEquals(8, tabs.size)
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.ADJUST))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.PRESETS))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.CROP))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.DRAW))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.MASKING))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.HEALING))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.OVERLAYS))
        assertTrue(tabs.contains(com.amresalehin.emregallery.editor.ui.MainBottomTab.AI_TOOLS))
    }

    @Test
    fun testEditHistory_andJumpToStep() {
        val vm = PhotoEditorViewModel()
        val initialHistory = vm.getHistoryList()
        assertEquals(1, initialHistory.size)
        assertEquals(0, vm.getCurrentHistoryIndex())

        vm.rotate90()
        assertEquals(2, vm.getHistoryList().size)
        assertEquals(1, vm.getCurrentHistoryIndex())

        vm.rotate90()
        assertEquals(3, vm.getHistoryList().size)
        assertEquals(2, vm.getCurrentHistoryIndex())
        assertEquals(180, vm.sessionState.value.transform.rotationDegrees)

        // Jump to step 1
        vm.jumpToHistoryStep(1)
        assertEquals(1, vm.getCurrentHistoryIndex())
        assertEquals(90, vm.sessionState.value.transform.rotationDegrees)

        // Jump back to original step 0
        vm.jumpToHistoryStep(0)
        assertEquals(0, vm.getCurrentHistoryIndex())
        assertEquals(0, vm.sessionState.value.transform.rotationDegrees)
    }

    @Test
    fun testOverlaysManagement_addDuplicateDelete() {
        val vm = PhotoEditorViewModel()
        val textItem = TextOverlayItem(id = "txt1", text = "Watermark")
        vm.addTextOverlay(textItem)
        assertEquals(1, vm.sessionState.value.textOverlays.size)
        assertEquals("Watermark", vm.sessionState.value.textOverlays[0].text)

        vm.duplicateTextOverlay("txt1")
        assertEquals(2, vm.sessionState.value.textOverlays.size)

        vm.deleteTextOverlay("txt1")
        assertEquals(1, vm.sessionState.value.textOverlays.size)

        val sticker = StickerItem(id = "stk1", stickerId = "star")
        vm.addSticker(sticker)
        assertEquals(1, vm.sessionState.value.stickers.size)
        vm.duplicateSticker("stk1")
        assertEquals(2, vm.sessionState.value.stickers.size)
        vm.deleteSticker("stk1")
        assertEquals(1, vm.sessionState.value.stickers.size)
    }

    @Test
    fun testAiToolsSettings() {
        val vm = PhotoEditorViewModel()
        assertFalse(vm.sessionState.value.aiSettings.autoEnhance)

        vm.toggleAiAutoEnhance(true)
        assertTrue(vm.sessionState.value.aiSettings.autoEnhance)

        vm.setAiAutoEnhanceIntensity(1.5f)
        assertEquals(1.5f, vm.sessionState.value.aiSettings.autoEnhanceIntensity, 0.001f)

        vm.setAiExpand(0.35f)
        assertEquals(0.35f, vm.sessionState.value.aiSettings.generativeExpandRatio, 0.001f)

        vm.setAiStyleTransfer(AiStyleTransfer.CYBERPUNK)
        assertEquals(AiStyleTransfer.CYBERPUNK, vm.sessionState.value.aiSettings.styleTransfer)

        vm.setAiTryOnStyle(AiTryOnStyle.BIKER_LEATHER)
        assertEquals(AiTryOnStyle.BIKER_LEATHER, vm.sessionState.value.aiSettings.tryOnStyle)
    }

    @Test
    fun testThumbnailBitmap_stateFlow() {
        val vm = PhotoEditorViewModel()
        assertNull(vm.thumbnailBitmap.value)
        val dummy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        vm.setThumbnailBitmapForTesting(dummy)
        assertEquals(dummy, vm.thumbnailBitmap.value)
    }

    @Test
    fun testFilterColorMatrix_validForPresets() {
        val testPresets = listOf(
            FilterType.VIVID, FilterType.FILM, FilterType.DODGER, FilterType.BRL2,
            FilterType.BRL4, FilterType.APR2, FilterType.LIGHT_CROSS, FilterType.NOIR,
            FilterType.SEPIA, FilterType.VINTAGE, FilterType.CYBER, FilterType.FLORA
        )
        testPresets.forEach { filter ->
            val matrix = PhotoProcessor.getFilterMatrix(filter)
            assertNotNull("Matrix for $filter should not be null", matrix)
            assertEquals(20, matrix!!.array.size)
        }

        com.amresalehin.emregallery.editor.ui.PresetTier3Tabs.forEach { cat ->
            val presets = com.amresalehin.emregallery.editor.ui.generatePresetsForCategory(cat)
            presets.forEach { preset ->
                val matrix = PhotoProcessor.getFilterMatrix(preset.filterType)
                if (matrix != null) {
                    assertEquals(20, matrix.array.size)
                }
            }
        }
    }

    @Test
    fun testLightcraftMasking_createLayerAndComponent() {
        val vm = PhotoEditorViewModel()
        assertTrue(vm.sessionState.value.maskLayers.isEmpty())

        // 1. Create a mask layer
        val layer = vm.createMaskLayer(MaskComponentType.BRUSH, "Subject Highlight")
        assertEquals(1, vm.sessionState.value.maskLayers.size)
        assertEquals("Subject Highlight", vm.sessionState.value.maskLayers[0].name)
        assertEquals(layer.id, vm.sessionState.value.activeMaskId)
        assertEquals(1, vm.sessionState.value.maskLayers[0].components.size)
        assertEquals(MaskComponentType.BRUSH, vm.sessionState.value.maskLayers[0].components[0].type)

        // 2. Add Linear Gradient component
        vm.addComponentToMask(layer.id, MaskComponentType.LINEAR_GRADIENT, MaskCombineMode.ADD)
        val updatedLayer = vm.sessionState.value.maskLayers[0]
        assertEquals(2, updatedLayer.components.size)
        assertEquals(MaskComponentType.LINEAR_GRADIENT, updatedLayer.components[1].type)

        // 3. Duplicate layer
        vm.duplicateMaskLayer(layer.id)
        assertEquals(2, vm.sessionState.value.maskLayers.size)
        assertTrue(vm.sessionState.value.maskLayers[1].name.contains("Copy"))

        // 4. Toggle layer enabled
        vm.toggleMaskLayerEnabled(layer.id)
        assertFalse(vm.sessionState.value.maskLayers[0].isEnabled)

        // 5. Invert layer
        vm.invertMaskLayer(layer.id)
        assertTrue(vm.sessionState.value.maskLayers[0].isInverted)

        // 6. Delete layer
        vm.deleteMaskLayer(layer.id)
        assertEquals(1, vm.sessionState.value.maskLayers.size)
    }

    @Test
    fun testLightcraftMasking_localAdjustmentsAndAmount() {
        val vm = PhotoEditorViewModel()
        val layer = vm.createMaskLayer(MaskComponentType.RADIAL_GRADIENT)

        // Adjust Exposure, Contrast, Temp, Clarity
        vm.updateMaskLocalAdjustment(layer.id, "Exposure", 25f)
        vm.updateMaskLocalAdjustment(layer.id, "Contrast", 15f)
        vm.updateMaskLocalAdjustment(layer.id, "Temperature", 30f)
        vm.updateMaskLocalAdjustment(layer.id, "Clarity", 20f)

        val updated = vm.sessionState.value.maskLayers.first { it.id == layer.id }
        assertEquals(25f, updated.adjustments.exposure, 0.001f)
        assertEquals(15f, updated.adjustments.contrast, 0.001f)
        assertEquals(30f, updated.adjustments.temperature, 0.001f)
        assertEquals(20f, updated.adjustments.clarity, 0.001f)
        assertFalse(updated.adjustments.isNeutral())

        // Set Mask Amount
        vm.setMaskAmount(layer.id, 1.5f)
        assertEquals(1.5f, vm.sessionState.value.maskLayers.first { it.id == layer.id }.amount, 0.001f)

        // Reset adjustments
        vm.resetMaskAdjustments(layer.id)
        val reset = vm.sessionState.value.maskLayers.first { it.id == layer.id }
        assertTrue(reset.adjustments.isNeutral())
        assertEquals(1.0f, reset.amount, 0.001f)
    }

    @Test
    fun testLightcraftMasking_overlaySettings() {
        val vm = PhotoEditorViewModel()
        assertTrue(vm.sessionState.value.maskOverlaySettings.showOverlay)
        assertEquals(MaskOverlayColor.RUBY_RED, vm.sessionState.value.maskOverlaySettings.overlayColor)

        vm.toggleMaskOverlay()
        assertFalse(vm.sessionState.value.maskOverlaySettings.showOverlay)

        vm.setMaskOverlayColor(MaskOverlayColor.EMERALD_GREEN)
        assertEquals(MaskOverlayColor.EMERALD_GREEN, vm.sessionState.value.maskOverlaySettings.overlayColor)

        vm.setMaskOverlayOpacity(0.85f)
        assertEquals(0.85f, vm.sessionState.value.maskOverlaySettings.opacity, 0.001f)
    }

    @Test
    fun testLightcraftMasking_applyMaskLayersEngine() {
        val src = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val layer = LightcraftMaskLayer(
            id = "layer1",
            name = "Test Mask",
            amount = 1.0f,
            components = listOf(
                MaskComponent(
                    id = "comp1",
                    type = MaskComponentType.RADIAL_GRADIENT,
                    combineMode = MaskCombineMode.ADD
                )
            ),
            adjustments = MaskLocalAdjustments(
                exposure = 30f,
                contrast = 20f
            )
        )

        val processed = PhotoMaskEngine.applyMaskLayers(src, listOf(layer))
        assertNotNull(processed)
        assertEquals(40, processed.width)
        assertEquals(40, processed.height)
    }

    @Test
    fun testPicsartDraw_layersAndBlendModes() {
        val vm = PhotoEditorViewModel()
        assertEquals(1, vm.sessionState.value.drawLayers.size)
        val initialLayerId = vm.sessionState.value.activeDrawLayerId

        // Add drawing stroke to initial layer
        val stroke1 = DrawingStroke(
            id = "s1",
            points = listOf(androidx.compose.ui.geometry.Offset(10f, 10f), androidx.compose.ui.geometry.Offset(20f, 20f)),
            color = androidx.compose.ui.graphics.Color.Red,
            width = 12f,
            brushType = BrushType.PEN,
            opacity = 0.8f,
            hardness = 0.9f,
            layerId = initialLayerId
        )
        vm.addDrawingStroke(stroke1)
        assertEquals(1, vm.sessionState.value.drawingStrokes.size)

        // Add new draw layer
        vm.addDrawLayer("Art Layer 2")
        assertEquals(2, vm.sessionState.value.drawLayers.size)
        val newLayerId = vm.sessionState.value.activeDrawLayerId
        assertNotEquals(initialLayerId, newLayerId)

        // Set blend mode and opacity
        vm.setDrawLayerBlendMode(newLayerId, DrawBlendMode.MULTIPLY)
        assertEquals(DrawBlendMode.MULTIPLY, vm.sessionState.value.drawLayers.first { it.id == newLayerId }.blendMode)

        vm.setDrawLayerOpacity(newLayerId, 0.75f)
        assertEquals(0.75f, vm.sessionState.value.drawLayers.first { it.id == newLayerId }.opacity, 0.001f)

        // Toggle visibility & lock
        vm.toggleDrawLayerVisibility(newLayerId)
        assertFalse(vm.sessionState.value.drawLayers.first { it.id == newLayerId }.isVisible)

        vm.toggleDrawLayerLock(newLayerId)
        assertTrue(vm.sessionState.value.drawLayers.first { it.id == newLayerId }.isLocked)

        // Duplicate layer
        vm.duplicateDrawLayer(initialLayerId)
        assertEquals(3, vm.sessionState.value.drawLayers.size)
        assertEquals(2, vm.sessionState.value.drawingStrokes.size)

        // Delete layer
        vm.deleteDrawLayer(newLayerId)
        assertEquals(2, vm.sessionState.value.drawLayers.size)
    }

    @Test
    fun testPicsartDraw_symmetryAndShapes() {
        val vm = PhotoEditorViewModel()
        vm.setDrawSymmetryMode(DrawSymmetryMode.VERTICAL)
        assertEquals(DrawSymmetryMode.VERTICAL, vm.sessionState.value.drawSymmetryMode)

        // Stamp a star shape
        vm.addDrawShapeStroke(
            shapeType = DrawShapeType.STAR,
            isFilled = false,
            strokeWidth = 4f,
            color = androidx.compose.ui.graphics.Color.Yellow,
            opacity = 1.0f,
            centerX = 100f,
            centerY = 100f,
            radius = 30f
        )
        assertEquals(1, vm.sessionState.value.drawingStrokes.size)
        val starStroke = vm.sessionState.value.drawingStrokes.first()
        assertTrue(starStroke.points.size >= 10)

        // Clear layer
        vm.clearDrawingStrokes(starStroke.layerId)
        assertEquals(0, vm.sessionState.value.drawingStrokes.size)
    }

    @Test
    fun testPicsartDraw_renderNewBrushesAndLayersWithoutCrash() = runBlocking {
        val src = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val strokes = listOf(
            DrawingStroke(
                id = "s_sparkle",
                points = listOf(androidx.compose.ui.geometry.Offset(10f, 10f), androidx.compose.ui.geometry.Offset(50f, 50f)),
                color = androidx.compose.ui.graphics.Color.Cyan,
                width = 8f,
                brushType = BrushType.SPARKLE,
                opacity = 0.9f,
                layerId = "l1"
            ),
            DrawingStroke(
                id = "s_rainbow",
                points = listOf(androidx.compose.ui.geometry.Offset(5f, 70f), androidx.compose.ui.geometry.Offset(70f, 70f)),
                color = androidx.compose.ui.graphics.Color.Red,
                width = 10f,
                brushType = BrushType.RAINBOW,
                opacity = 1.0f,
                layerId = "l1"
            ),
            DrawingStroke(
                id = "s_glow",
                points = listOf(androidx.compose.ui.geometry.Offset(20f, 40f), androidx.compose.ui.geometry.Offset(60f, 40f)),
                color = androidx.compose.ui.graphics.Color.Magenta,
                width = 14f,
                brushType = BrushType.GLOW,
                opacity = 0.85f,
                hardness = 0.5f,
                layerId = "l2"
            )
        )
        val layers = listOf(
            DrawLayer(id = "l1", name = "Layer 1", isVisible = true, opacity = 1.0f, blendMode = DrawBlendMode.NORMAL),
            DrawLayer(id = "l2", name = "Layer 2", isVisible = true, opacity = 0.8f, blendMode = DrawBlendMode.ADD)
        )
        val state = EditSessionState(
            drawingStrokes = strokes,
            drawLayers = layers
        )
        val rendered = PhotoProcessor.renderFinal(src, state)
        assertNotNull(rendered)
        assertEquals(80, rendered.width)
        assertEquals(80, rendered.height)
    }
}

