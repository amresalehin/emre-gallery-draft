package com.amresalehin.emregallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.osmdroid.util.GeoPoint
import java.io.File


internal object PhotoLocationExtractor {
    fun extractLocation(
        context: Context,
        uriString: String,
        isVideo: Boolean = false
    ): GeoPoint? {
        val uri = Uri.parse(uriString)

        // Direct local file support
        if (uri.scheme == null || uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path ?: uriString
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val directPoint = runCatching {
                    val exif = ExifInterface(file)
                    getGeoPointFromExif(exif)
                }.getOrNull()
                if (directPoint != null) return directPoint
            }
        }

        // On Android 10+ (API 29+), content URIs require MediaStore.setRequireOriginal(uri)
        // with ACCESS_MEDIA_LOCATION to prevent stripping of EXIF GPS coordinates.
        val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else {
            uri
        }

        // 1. Try ExifInterface with FileDescriptor (handles seekable streams correctly for TIFF/EXIF headers)
        val fromFd = runCatching {
            context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                getGeoPointFromExif(exif)
            }
        }.getOrNull()
        if (fromFd != null) return fromFd

        // 2. Try ExifInterface with InputStream on targetUri
        val fromStream = runCatching {
            context.contentResolver.openInputStream(targetUri)?.use { stream ->
                val exif = ExifInterface(stream)
                getGeoPointFromExif(exif)
            }
        }.getOrNull()
        if (fromStream != null) return fromStream

        // 3. Fallback to original uri (in case setRequireOriginal failed due to provider quirks)
        if (targetUri != uri) {
            val fromOrigFd = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    getGeoPointFromExif(exif)
                }
            }.getOrNull()
            if (fromOrigFd != null) return fromOrigFd

            val fromOrigStream = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    getGeoPointFromExif(exif)
                }
            }.getOrNull()
            if (fromOrigStream != null) return fromOrigStream
        }

        // Avoid spinning up native media decoders for photos. This fallback is for video
        // container metadata (ISO 6709) only; invoking it for every photo without EXIF GPS
        // is expensive across large libraries and can exhaust native media resources.
        if (isVideo) {
            val fromMetadata = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val locStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                    parseIso6709(locStr)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
            if (fromMetadata != null) return fromMetadata
        }

        return null
    }

    private fun getGeoPointFromExif(exif: ExifInterface): GeoPoint? {
        // First check the standard latLong property
        val latLong = exif.latLong
        if (latLong != null && latLong.size >= 2) {
            val lat = latLong[0]
            val lon = latLong[1]
            if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) {
                return GeoPoint(lat, lon)
            }
        }

        // Fallback: parse raw GPS EXIF tags manually if latLong parser failed
        val latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val lonStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        if (!latStr.isNullOrBlank() && !lonStr.isNullOrBlank()) {
            val lat = parseDms(latStr, latRef)
            val lon = parseDms(lonStr, lonRef)
            if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) {
                return GeoPoint(lat, lon)
            }
        }

        return null
    }

    private fun parseDms(dms: String, ref: String?): Double? {
        return runCatching {
            val parts = dms.split(',', ' ').filter { it.isNotBlank() }
            val degrees: Double
            val minutes: Double
            val seconds: Double
            if (parts.size >= 3) {
                degrees = parseRational(parts[0])
                minutes = parseRational(parts[1])
                seconds = parseRational(parts[2])
            } else if (parts.size == 1) {
                degrees = parseRational(parts[0])
                minutes = 0.0
                seconds = 0.0
            } else {
                return null
            }
            var decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
            val r = ref?.trim()?.uppercase()
            if (r == "S" || r == "W") {
                decimal = -decimal
            }
            decimal
        }.getOrNull()
    }

    private fun parseRational(part: String): Double {
        val sub = part.split('/')
        return if (sub.size == 2) {
            val denom = sub[1].toDoubleOrNull() ?: 1.0
            if (denom != 0.0) (sub[0].toDoubleOrNull() ?: 0.0) / denom else 0.0
        } else {
            part.toDoubleOrNull() ?: 0.0
        }
    }

    private fun parseIso6709(raw: String?): GeoPoint? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(raw.trim()) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) {
            GeoPoint(lat, lon)
        } else null
    }
}
