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

/**
 * Persistent SQLite cache for image and video GPS metadata, including media with no GPS coordinates.
 */
class PhotoMapLocationCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "photo_map_locations.db", null, 3) {

    data class CachedLocation(val point: GeoPoint?)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS photo_locations (uri TEXT PRIMARY KEY NOT NULL, latitude REAL, longitude REAL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS photo_locations")
        onCreate(db)
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete("photo_locations", null, null)
    }

    @Synchronized
    fun loadAll(): Map<String, CachedLocation> {
        val result = mutableMapOf<String, CachedLocation>()
        readableDatabase.query(
            "photo_locations", arrayOf("uri", "latitude", "longitude"),
            null, null, null, null, null
        ).use { cursor ->
            val uriColumn = cursor.getColumnIndexOrThrow("uri")
            val latitudeColumn = cursor.getColumnIndexOrThrow("latitude")
            val longitudeColumn = cursor.getColumnIndexOrThrow("longitude")
            while (cursor.moveToNext()) {
                val uri = cursor.getString(uriColumn)
                val hasCoordinates = !cursor.isNull(latitudeColumn) && !cursor.isNull(longitudeColumn)
                val point = if (!hasCoordinates) {
                    null
                } else {
                    val lat = cursor.getDouble(latitudeColumn)
                    val lon = cursor.getDouble(longitudeColumn)
                    if (
                        lat.isFinite() && lon.isFinite() &&
                        lat in -90.0..90.0 && lon in -180.0..180.0 &&
                        !(lat == 0.0 && lon == 0.0)
                    ) {
                        GeoPoint(lat, lon)
                    } else {
                        null
                    }
                }

                // Old builds could cache (0, 0) as a real GPS coordinate. Do not turn
                // an invalid coordinate into a permanent "no GPS" cache entry: omit it
                // so the next scan re-reads the source metadata.
                if (hasCoordinates && point == null) continue
                result[uri] = CachedLocation(point)
            }
        }
        return result
    }

    @Synchronized
    fun save(uri: String, point: GeoPoint?) {
        val values = ContentValues().apply {
            put("uri", uri)
            if (point == null) {
                putNull("latitude")
                putNull("longitude")
            } else {
                put("latitude", point.latitude)
                put("longitude", point.longitude)
            }
        }
        writableDatabase.insertWithOnConflict(
            "photo_locations", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Scans a batch of media items, caching GPS coordinates to the database.
     * Can be safely called from background services or foreground screens.
     */
    fun scanMediaBatch(
        context: Context,
        media: List<MediaItem>,
        forceRescan: Boolean = false,
        onProgress: ((scanned: Int, total: Int, located: Int) -> Unit)? = null
    ): Int {
        // Enforce background priority so this scanner never contends with the UI thread or RenderThread
        runCatching {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        }
        val items = media
        if (forceRescan) {
            clearAll()
        }
        val cached = loadAll()
        val pending = if (forceRescan) items else items.filterNot { cached.containsKey(it.uri) }
        if (pending.isEmpty()) return 0

        var newlyLocated = 0
        var totalLocated = cached.values.count { it.point != null }

        // Process in throttled batches with batched transactions to minimize disk sync and CPU usage
        val batchSize = 25
        for (batch in pending.chunked(batchSize)) {
            // Parse media before starting the DB transaction. ContentResolver/EXIF I/O can be
            // slow; holding a write lock during it blocks other location-cache readers/writers.
            val extracted = batch.map { item ->
                item to extractLocation(context, item.uri, isVideo = item.isVideo)
            }

            val db = writableDatabase
            db.beginTransaction()
            try {
                for ((item, point) in extracted) {
                    val values = ContentValues().apply {
                        put("uri", item.uri)
                        if (point == null) {
                            putNull("latitude")
                            putNull("longitude")
                        } else {
                            put("latitude", point.latitude)
                            put("longitude", point.longitude)
                        }
                    }
                    db.insertWithOnConflict("photo_locations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    if (point != null) {
                        newlyLocated++
                        totalLocated++
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            onProgress?.invoke(newlyLocated, pending.size, totalLocated)
        }
        return newlyLocated
    }

    companion object {
        /**
         * Safely extracts latitude and longitude from an image URI's EXIF data or video metadata.
         * Handles unredacted MediaStore URIs (via ACCESS_MEDIA_LOCATION and MediaStore.setRequireOriginal),
         * seekable FileDescriptors, stream fallbacks, and ISO 6709 coordinates.
         */
        fun extractLocation(
            context: Context,
            uriString: String,
            isVideo: Boolean = false
        ): GeoPoint? = PhotoLocationExtractor.extractLocation(context, uriString, isVideo)
    }
}
