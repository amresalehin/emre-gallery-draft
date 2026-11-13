package com.amresalehin.emregallery.data

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import org.json.JSONObject
import java.io.File

/** Audio/video stream probing and lossless container muxing.
 *
 * yt-dlp downloads independent tracks into private temporary storage. This class
 * combines them without transcoding and only returns a muxed file after FFprobe
 * confirms that both expected streams exist.
 */
object MediaStreamMuxer {
    private const val TAG = "MediaStreamMuxer"

    data class ProbeResult(
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val streamCount: Int
    )

    fun probe(file: File): ProbeResult? {
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            val session = FFprobeKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-show_entries", "stream=codec_type",
                    "-of", "json",
                    file.absolutePath
                )
            )
            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.w(TAG, "ffprobe failed for ${file.name}: ${session.allLogsAsString}")
                return null
            }
            val streams = JSONObject(session.output ?: "{}").optJSONArray("streams") ?: return null
            var hasVideo = false
            var hasAudio = false
            for (index in 0 until streams.length()) {
                when (streams.optJSONObject(index)?.optString("codec_type")) {
                    "video" -> hasVideo = true
                    "audio" -> hasAudio = true
                }
            }
            ProbeResult(hasVideo, hasAudio, streams.length())
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to probe ${file.name}", t)
            null
        }
    }

    /**
     * Muxes one video-only file and one audio-only file with stream copy.
     * MP4 is tried first; Matroska is a compatibility fallback for codecs MP4
     * cannot carry. No silent/video-only result is returned as a successful merge.
     */
    fun mux(video: File, audio: File, target: File): File? {
        if (!video.isFile || video.length() <= 0L || !audio.isFile || audio.length() <= 0L) return null
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val preferred = if (target.extension.equals("mp4", true)) target else File(
            target.parentFile,
            target.nameWithoutExtension + ".mp4"
        )
        val candidates = listOf(
            preferred,
            File(preferred.parentFile, preferred.nameWithoutExtension + ".mkv")
        ).distinctBy { it.absolutePath }

        for (output in candidates) {
            runCatching { if (output.exists()) output.delete() }
            val args = mutableListOf(
                "-nostdin", "-hide_banner", "-y",
                "-i", video.absolutePath,
                "-i", audio.absolutePath,
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c", "copy"
            )
            if (output.extension.equals("mp4", true)) {
                args += listOf("-movflags", "+faststart")
            }
            args += output.absolutePath

            try {
                val session = FFmpegKit.executeWithArguments(args.toTypedArray())
                if (ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0L) {
                    val result = probe(output)
                    if (result?.hasVideo == true && result.hasAudio) return output
                }
                Log.w(TAG, "Mux failed for ${output.name}: ${session.allLogsAsString}")
            } catch (t: Throwable) {
                Log.e(TAG, "Mux exception for ${output.name}", t)
            }
            runCatching { output.delete() }
        }
        return null
    }
}
