package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.amresalehin.emregallery.data.MediaItem
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun hasMediaPermission(context: Context): Boolean {
    val checks = if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return checks.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
}

fun hasMediaLocationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun reverseGeocodeLocation(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                listOfNotNull(
                    address.subLocality,
                    address.locality,
                    address.subAdminArea,
                    address.adminArea,
                    address.countryName
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .takeIf { it.isNotBlank() }
            }
    }.getOrNull()
}

fun readMediaMetadata(context: Context, item: MediaItem): Map<String, String> {
    val values = mutableMapOf<String, String>()
    val uri = Uri.parse(item.uri)

    // First, reliably extract coordinates using unredacted location reader
    val locationPoint = com.amresalehin.emregallery.data.PhotoMapLocationCache.extractLocation(context, item.uri)
    if (locationPoint != null) {
        values["latitude"] = locationPoint.latitude.toString()
        values["longitude"] = locationPoint.longitude.toString()
        reverseGeocodeLocation(context, locationPoint.latitude, locationPoint.longitude)?.let {
            values["location"] = it
        }
    }

    if (!item.isVideo) {
        runCatching {
            val targetUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
                runCatching { android.provider.MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
            } else {
                uri
            }
            (context.contentResolver.openInputStream(targetUri) ?: context.contentResolver.openInputStream(uri))?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttribute(ExifInterface.TAG_MAKE)?.takeIf { it.isNotBlank() }?.let { values["make"] = it }
                exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() }?.let { values["model"] = it }
                if (!values.containsKey("latitude")) {
                    exif.latLong?.let { coordinates ->
                        values["latitude"] = coordinates[0].toString()
                        values["longitude"] = coordinates[1].toString()
                        reverseGeocodeLocation(context, coordinates[0], coordinates[1])?.let { values["location"] = it }
                    }
                }
                fun put(key: String, vararg tags: String) {
                    tags.firstNotNullOfOrNull { tag -> exif.getAttribute(tag)?.takeIf { it.isNotBlank() } }?.let { values[key] = it }
                }
                put("aperture", ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE)
                put("shutter", ExifInterface.TAG_EXPOSURE_TIME)
                put("iso", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_ISO_SPEED_RATINGS)
                put("focal", ExifInterface.TAG_FOCAL_LENGTH)
                put("flash", ExifInterface.TAG_FLASH)
                put("color", ExifInterface.TAG_COLOR_SPACE)
                put("capture_date", ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME)
                put("description", ExifInterface.TAG_IMAGE_DESCRIPTION)
                put("copyright", ExifInterface.TAG_COPYRIGHT)
                put("creator", ExifInterface.TAG_ARTIST)
                put("title", "DocumentName")
            }
        }
    }
    // XMP is often embedded near the beginning of the file. Read a bounded amount only.
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total < 1024 * 1024) {
                val count = stream.read(buffer, 0, minOf(buffer.size, 1024 * 1024 - total))
                if (count <= 0) break
                out.write(buffer, 0, count)
                total += count
            }
            val xmp = out.toString("ISO-8859-1")
            fun readXmp(key: String, regex: Regex) {
                regex.find(xmp)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { values[key] = it }
            }
            readXmp("title", Regex("""(?:xmp:Title|dc:title)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("description", Regex("""(?:dc:description|photoshop:Caption)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("creator", Regex("""(?:dc:creator|xmp:Creator)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("copyright", Regex("""(?:dc:rights|xmpRights:UsageTerms)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("subject", Regex("""(?:dc:subject|Subject)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("keywords", Regex("""(?:MicrosoftPhoto:LastKeywordXMP|Keywords)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("rating", Regex("""xmp:Rating="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("label", Regex("""xmp:Label="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("capture_date", Regex("""(?:photoshop:DateCreated|xmp:CreateDate|xmp:DateCreated)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("aperture", Regex("""(?:exif:FNumber|FNumber)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("shutter", Regex("""(?:exif:ExposureTime|ExposureTime)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("iso", Regex("""(?:exif:ISOSpeedRatings|exif:PhotographicSensitivity|PhotographicSensitivity)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("focal", Regex("""(?:exif:FocalLength|FocalLength)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("flash", Regex("""(?:exif:Flash|Flash)="([^"]+)""", RegexOption.IGNORE_CASE))
            readXmp("color", Regex("""(?:exif:ColorSpace|ColorSpace)="([^"]+)""", RegexOption.IGNORE_CASE))
            if (values["location"].isNullOrBlank()) {
                val city = Regex("""photoshop:City="([^"]+)""", RegexOption.IGNORE_CASE).find(xmp)?.groupValues?.getOrNull(1)
                val state = Regex("""photoshop:State="([^"]+)""", RegexOption.IGNORE_CASE).find(xmp)?.groupValues?.getOrNull(1)
                val country = Regex("""photoshop:Country="([^"]+)""", RegexOption.IGNORE_CASE).find(xmp)?.groupValues?.getOrNull(1)
                listOfNotNull(city, state, country).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ").takeIf { it.isNotBlank() }?.let { values["location"] = it }
            }
        }
    }
    return values
}

fun formatDate(takenMillis: Long, addedSeconds: Long): String {
    val millis = takenMillis.takeIf { it > 0 } ?: (addedSeconds * 1000)
    if (millis <= 0) return "Date unavailable"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}

fun formatMediaDateTime(takenMillis: Long, addedSeconds: Long): String {
    val millis = takenMillis.takeIf { it > 0 } ?: (addedSeconds * 1000L)
    if (millis <= 0) return "Date unavailable"
    val date = Date(millis)
    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${dateFormat.format(date)}  •  ${timeFormat.format(date)}"
}

fun formatIsoDate(takenMillis: Long, addedSeconds: Long): String {
    val millis = takenMillis.takeIf { it > 0 } ?: (addedSeconds * 1000L)
    if (millis <= 0) return "2024-10-23T17:42:35+05:30"
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
    return runCatching { isoFormat.format(Date(millis)) }.getOrDefault("2024-10-23T17:42:35+05:30")
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "Not available"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
