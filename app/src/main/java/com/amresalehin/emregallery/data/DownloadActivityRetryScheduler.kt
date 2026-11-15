package com.amresalehin.emregallery.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Classifies transient download failures, computes exponential backoff delays with jitter,
 * and manages deduplicated coroutine retry timers.
 */
internal object DownloadActivityRetryScheduler {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeRetryJobs = ConcurrentHashMap<String, Job>()

    fun isTemporaryFailure(statusCode: Int, errorMessage: String?): Boolean {
        if (statusCode == 429) return true
        if (statusCode in listOf(500, 502, 503, 504)) return true
        if (statusCode == -1) {
            val msg = errorMessage?.lowercase(Locale.ROOT).orEmpty()
            if (msg.contains("timeout") ||
                msg.contains("reset") ||
                msg.contains("connection") ||
                msg.contains("failed to connect") ||
                msg.contains("network") ||
                msg.contains("socket") ||
                msg.contains("broken pipe") ||
                msg.contains("429")
            ) {
                return true
            }
        }
        return false
    }

    fun calculateRetryDelaySeconds(attempt: Int, retryAfterSeconds: Long?): Long {
        if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
            return retryAfterSeconds.coerceIn(1L, 300L)
        }
        val baseDelays = listOf(2L, 4L, 8L, 16L, 32L)
        val index = (attempt - 1).coerceIn(0, baseDelays.size - 1)
        val base = baseDelays[index]
        val maxJitter = (base * 0.5).toLong().coerceAtLeast(1L)
        val jitter = (0..maxJitter).random()
        return base + jitter
    }

    fun schedule(
        itemId: String,
        delaySeconds: Long,
        onExecute: suspend () -> Unit
    ) {
        activeRetryJobs[itemId]?.cancel()
        val job = scope.launch {
            if (delaySeconds > 0L) {
                delay(delaySeconds * 1000L)
            }
            onExecute()
            activeRetryJobs.remove(itemId)
        }
        activeRetryJobs[itemId] = job
    }

    fun resumePersistedRetries(
        context: Context,
        activities: List<DownloadActivityItem>,
        onScheduleItem: (Context, String, Long) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val retryingItems = activities.filter { it.status == DownloadActivityStatus.RETRYING }
        for (item in retryingItems) {
            val nextTime = item.nextRetryTimestampMillis
            val remainingSec = if (nextTime != null) {
                val remainingMillis = nextTime - now
                if (remainingMillis > 0) (remainingMillis + 999L) / 1000L else 0L
            } else {
                0L
            }
            onScheduleItem(context, item.id, remainingSec)
        }
    }

    fun cancel(itemId: String) {
        activeRetryJobs[itemId]?.cancel()
        activeRetryJobs.remove(itemId)
    }

    fun cancelAll() {
        for ((_, job) in activeRetryJobs) {
            job.cancel()
        }
        activeRetryJobs.clear()
    }
}
