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
}
