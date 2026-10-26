package com.amresalehin.emregallery

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import com.amresalehin.emregallery.ui.ThumbnailBoundsTracker
import com.amresalehin.emregallery.ui.transitionContentScale
import com.amresalehin.emregallery.data.isVideoMediaStorePath
import com.amresalehin.emregallery.ui.ViewerVideoState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ViewerPlaybackStateTest {

    @Before
    fun setUp() {
        ThumbnailBoundsTracker.clear()
    }

    @Test
    fun transitionContentScaleMatchesThumbnailCropWithoutDistortingImage() {
        val screen = Size(width = 1080f, height = 2400f)
        val thumbnailScaleX = 360f / screen.width
        val thumbnailScaleY = 360f / screen.height
        val sources = listOf(
            Size(width = 400f, height = 200f), // Landscape
            Size(width = 200f, height = 400f), // Portrait
            Size(width = 300f, height = 300f)  // Square
        )

        sources.forEach { source ->
            val thumbnail = Size(
                width = screen.width * thumbnailScaleX,
                height = screen.height * thumbnailScaleY
            )
            val cropAtThumbnail = ContentScale.Crop.computeScaleFactor(source, thumbnail)
            val fitAtFullscreen = ContentScale.Fit.computeScaleFactor(source, screen)

            fun assertEffectiveScale(progress: Float) {
                val scale = transitionContentScale(
                    progress = progress,
                    thumbnailScaleX = thumbnailScaleX,
                    thumbnailScaleY = thumbnailScaleY,
                    sourceImageWidth = source.width.toInt(),
                    sourceImageHeight = source.height.toInt()
                ).computeScaleFactor(source, screen)
                val fraction = progress.coerceIn(0f, 1f)
                val parentScaleX = thumbnailScaleX + (1f - thumbnailScaleX) * fraction
                val parentScaleY = thumbnailScaleY + (1f - thumbnailScaleY) * fraction
                val effectiveX = scale.scaleX * parentScaleX
                val effectiveY = scale.scaleY * parentScaleY
                val expected = cropAtThumbnail.scaleX +
                    (fitAtFullscreen.scaleX - cropAtThumbnail.scaleX) * fraction

                assertEquals("Effective X scale at progress=$progress for source=$source", expected, effectiveX, 0.001f)
                assertEquals("Effective Y scale at progress=$progress for source=$source", expected, effectiveY, 0.001f)
                assertEquals("Image must not distort at progress=$progress for source=$source", effectiveX, effectiveY, 0.001f)
            }

            // Closed endpoint uses the actual square thumbnail crop, not a fullscreen crop.
            assertEffectiveScale(0f)
            // Keep the rendered image aspect-preserving while the parent rectangle changes shape.
            assertEffectiveScale(0.5f)
            // Open endpoint returns to ordinary fullscreen Fit.
            assertEffectiveScale(1f)
        }
    }

    @Test
    fun transitionContentScaleClampsProgressToValidRange() {
        val source = Size(width = 400f, height = 200f)
        val screen = Size(width = 1080f, height = 2400f)
        val thumbnailScaleX = 360f / screen.width
        val thumbnailScaleY = 360f / screen.height
        val thumbnail = Size(
            width = screen.width * thumbnailScaleX,
            height = screen.height * thumbnailScaleY
        )
        val crop = ContentScale.Crop.computeScaleFactor(source, thumbnail)
        val fit = ContentScale.Fit.computeScaleFactor(source, screen)

        val beforeStart = transitionContentScale(
            progress = -1f,
            thumbnailScaleX = thumbnailScaleX,
            thumbnailScaleY = thumbnailScaleY,
            sourceImageWidth = source.width.toInt(),
            sourceImageHeight = source.height.toInt()
        ).computeScaleFactor(source, screen)
        val afterEnd = transitionContentScale(
            progress = 2f,
            thumbnailScaleX = thumbnailScaleX,
            thumbnailScaleY = thumbnailScaleY,
            sourceImageWidth = source.width.toInt(),
            sourceImageHeight = source.height.toInt()
        ).computeScaleFactor(source, screen)

        assertEquals(crop.scaleX, beforeStart.scaleX * thumbnailScaleX, 0.001f)
        assertEquals(crop.scaleY, beforeStart.scaleY * thumbnailScaleY, 0.001f)
        assertEquals(fit.scaleX, afterEnd.scaleX, 0.001f)
        assertEquals(fit.scaleY, afterEnd.scaleY, 0.001f)
    }

    @Test
    fun transitionGeometryKeepsDisplayedSizeStableAcrossPlaceholderAndOriginal() {
        val screen = Size(width = 1080f, height = 2400f)
        val thumbnailScaleX = 360f / screen.width
        val thumbnailScaleY = 360f / screen.height
        val original = Size(width = 4032f, height = 3024f)
        val placeholder = Size(width = 180f, height = 135f)

        listOf(0f, 0.5f, 1f).forEach { progress ->
            val placeholderScale = transitionContentScale(
                progress, thumbnailScaleX, thumbnailScaleY, 4032, 3024
            ).computeScaleFactor(placeholder, screen)
            val originalScale = transitionContentScale(
                progress, thumbnailScaleX, thumbnailScaleY, 4032, 3024
            ).computeScaleFactor(original, screen)
            assertEquals(placeholderScale.scaleX * placeholder.width, originalScale.scaleX * original.width, 0.01f)
            assertEquals(placeholderScale.scaleY * placeholder.height, originalScale.scaleY * original.height, 0.01f)
        }
    }

    @Test
    fun noBoundsFallbackFitsUsingActualSourceDimensions() {
        val original = Size(width = 4032f, height = 3024f)
        val placeholder = Size(width = 180f, height = 135f)
        val screen = Size(width = 1080f, height = 2400f)
        val expected = ContentScale.Fit.computeScaleFactor(original, screen)
        val actual = transitionContentScale(
            progress = 1f,
            thumbnailScaleX = 1f,
            thumbnailScaleY = 1f,
            sourceImageWidth = original.width.toInt(),
            sourceImageHeight = original.height.toInt()
        ).computeScaleFactor(placeholder, screen)

        assertEquals(expected.scaleX * original.width, actual.scaleX * placeholder.width, 0.01f)
        assertEquals(expected.scaleY * original.height, actual.scaleY * placeholder.height, 0.01f)
    }

    @Test
    fun mediaStoreVideoPathsBypassTheThumbnailShortcut() {
        assertTrue(isVideoMediaStorePath(listOf("external", "video", "media", "42")))
        assertTrue(isVideoMediaStorePath(listOf("external_primary", "VIDEO", "media", "42")))
        assertFalse(isVideoMediaStorePath(listOf("external", "images", "media", "42")))
        assertFalse(isVideoMediaStorePath(emptyList()))
    }

    @Test
    fun testPlaybackIntentPreservedWhenUserPauses() {
        var sessionPlayIntent = true

        // Video 1 starts with session autoplay intent = true
        val video1State = ViewerVideoState(
            player = null,
            isVideo = true,
            initialPlayIntent = sessionPlayIntent,
            onPlayIntentChange = { sessionPlayIntent = it }
        )
        // Without an active player instance in a pure unit test, isPlaying reflects initial intent
        assertFalse(video1State.isPlaying) // null player -> false

        // Simulate session intent tracking
        var intentCalls = mutableListOf<Boolean>()
        val tracker = ViewerVideoState(
            player = null,
            isVideo = true,
            initialPlayIntent = true,
            onPlayIntentChange = { intentCalls.add(it) }
        )
        tracker.isPlaying = true

        // User manually pauses
        tracker.togglePlayPause()
        assertFalse("Playback state must be paused", tracker.isPlaying)
        assertEquals("Session intent must be set to false", listOf(false), intentCalls)

        // User swipes to video 2: video 2 is created with sessionPlayIntent = false
        val video2SessionIntent = intentCalls.last()
        assertFalse("Next video must inherit paused intent", video2SessionIntent)

        val video2State = ViewerVideoState(
            player = null,
            isVideo = true,
            initialPlayIntent = video2SessionIntent,
            onPlayIntentChange = { intentCalls.add(it) }
        )
        assertFalse("Next video must initialize as paused", video2State.isPlaying)
    }

    @Test
    fun testFullscreenToggleAndMute() {
        val state = ViewerVideoState(
            player = null,
            isVideo = true,
            initialPlayIntent = true,
            onPlayIntentChange = {}
        )

        assertFalse("Fullscreen should start false", state.isFullscreen)
        state.toggleFullscreen()
        assertTrue("Fullscreen should now be true", state.isFullscreen)
        state.toggleFullscreen()
        assertFalse("Fullscreen should toggle back to false", state.isFullscreen)

        assertFalse("Mute should start false", state.isMuted)
        state.toggleMute()
        assertTrue("Mute should now be true", state.isMuted)
        state.toggleMute()
        assertFalse("Mute should toggle back to false", state.isMuted)
    }

    @Test
    fun testThumbnailBoundsTracker() {
        val testUri = "content://media/external/images/media/42"
        val sampleBounds = Rect(100f, 200f, 400f, 600f)

        assertNull(ThumbnailBoundsTracker.getBounds(testUri))
        assertNull(ThumbnailBoundsTracker.activeViewerUri)

        ThumbnailBoundsTracker.updateBounds(testUri, sampleBounds)
        assertEquals(sampleBounds, ThumbnailBoundsTracker.getBounds(testUri))

        ThumbnailBoundsTracker.captureOpeningBounds(testUri)
        val movedBounds = Rect(120f, 220f, 420f, 620f)
        ThumbnailBoundsTracker.updateBounds(testUri, movedBounds)
        assertEquals(sampleBounds, ThumbnailBoundsTracker.getOpeningBounds(testUri))
        ThumbnailBoundsTracker.clearOpeningBounds(testUri)
        assertEquals(movedBounds, ThumbnailBoundsTracker.getOpeningBounds(testUri))

        val unmeasuredUri = "content://media/external/images/media/99"
        ThumbnailBoundsTracker.captureOpeningBounds(unmeasuredUri)
        ThumbnailBoundsTracker.updateBounds(unmeasuredUri, sampleBounds)
        assertNull(ThumbnailBoundsTracker.getOpeningBounds(unmeasuredUri))
        ThumbnailBoundsTracker.clearOpeningBounds(unmeasuredUri)
        assertEquals(sampleBounds, ThumbnailBoundsTracker.getOpeningBounds(unmeasuredUri))

        ThumbnailBoundsTracker.activeViewerUri = testUri
        assertEquals(testUri, ThumbnailBoundsTracker.activeViewerUri)

        ThumbnailBoundsTracker.clear()
        assertNull(ThumbnailBoundsTracker.getBounds(testUri))
        assertNull(ThumbnailBoundsTracker.activeViewerUri)
    }

    @Test
    fun testReturnAnimationCoordinateMath() {
        val screenWidth = 1080f
        val screenHeight = 2400f
        val thumbBounds = Rect(left = 72f, top = 300f, right = 432f, bottom = 660f)

        val thumbW = thumbBounds.width // 360f
        val thumbH = thumbBounds.height // 360f
        val thumbCenterX = thumbBounds.left + thumbW / 2f // 252f
        val thumbCenterY = thumbBounds.top + thumbH / 2f // 480f

        val screenCenterX = screenWidth / 2f // 540f
        val screenCenterY = screenHeight / 2f // 1200f

        val destTransX = thumbCenterX - screenCenterX // -288f
        val destTransY = thumbCenterY - screenCenterY // -720f

        val destScale = maxOf(thumbW / screenWidth, thumbH / screenHeight)

        // At progress = 1f (fullscreen)
        val progressFull = 1f
        val currentScaleFull = destScale + (1f - destScale) * progressFull
        val currentTransXFull = destTransX * (1f - progressFull)
        val currentTransYFull = destTransY * (1f - progressFull)

        assertEquals(1f, currentScaleFull, 0.001f)
        assertEquals(0f, currentTransXFull, 0.001f)
        assertEquals(0f, currentTransYFull, 0.001f)

        // At progress = 0f (thumbnail)
        val progressThumb = 0f
        val currentScaleThumb = destScale + (1f - destScale) * progressThumb
        val currentTransXThumb = destTransX * (1f - progressThumb)
        val currentTransYThumb = destTransY * (1f - progressThumb)

        assertEquals(destScale, currentScaleThumb, 0.001f)
        assertEquals(destTransX, currentTransXThumb, 0.001f)
        assertEquals(destTransY, currentTransYThumb, 0.001f)
    }
}
