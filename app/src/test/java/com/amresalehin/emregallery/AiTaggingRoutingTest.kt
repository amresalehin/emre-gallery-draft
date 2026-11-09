package com.amresalehin.emregallery

import com.amresalehin.emregallery.ai.tagging.AiTaggingRoute
import com.amresalehin.emregallery.ai.tagging.AiImageTagger
import com.amresalehin.emregallery.ai.tagging.AiTaggingSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AiTaggingRoutingTest {

    @Test
    fun smartVisionSelectionRoutesOnlyToSmartVision() {
        assertEquals(
            AiTaggingRoute.SMART_VISION,
            AiImageTagger.resolveModelRoute(AiTaggingSettings.MODEL_SMART_VISION)
        )
    }

    @Test
    fun cloudSelectionRoutesOnlyToCloud() {
        assertEquals(
            AiTaggingRoute.CLOUD,
            AiImageTagger.resolveModelRoute(AiTaggingSettings.MODEL_CLOUD)
        )
    }

    @Test
    fun supportedLocalModelIdsRemainLocalRoutes() {
        val localIds = listOf(
            AiTaggingSettings.MODEL_FLORENCE_2,
            AiTaggingSettings.MODEL_GEMMA_4,
            AiTaggingSettings.MODEL_LFM_2_5,
            AiTaggingSettings.MODEL_QWEN_3_5
        )
        localIds.forEach { modelId ->
            assertEquals(modelId, AiTaggingRoute.LOCAL_MODEL, AiImageTagger.resolveModelRoute(modelId))
        }
    }

    @Test
    fun unknownModelDoesNotBecomeCloudOrSmartVision() {
        assertEquals(
            AiTaggingRoute.UNKNOWN,
            AiImageTagger.resolveModelRoute("some-unregistered-model")
        )
    }
}
