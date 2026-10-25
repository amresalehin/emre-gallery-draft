package com.amresalehin.emregallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.osmdroid.util.GeoPoint

/**
 * Persistent SQLite cache for photo GPS metadata, including photos with no GPS coordinates.
 */
class PhotoMapLocationCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "photo_map_locations.db", null, 2) {

    data class CachedLocation(val point: GeoPoint?)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE photo_locations (uri TEXT PRIMARY KEY NOT NULL, latitude REAL, longitude REAL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS photo_locations")
        onCreate(db)
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
                val point = if (cursor.isNull(latitudeColumn) || cursor.isNull(longitudeColumn)) {
                    null
                } else {
                    GeoPoint(cursor.getDouble(latitudeColumn), cursor.getDouble(longitudeColumn))
                }
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

    companion object {
        /**
         * Safely extracts latitude and longitude from an image URI's EXIF data.
         */
        fun extractLocation(context: Context, uriString: String): GeoPoint? {
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    ExifInterface(stream).latLong?.let { c ->
                        if (c[0].isFinite() && c[1].isFinite() && c[0] in -90.0..90.0 && c[1] in -180.0..180.0) {
                            GeoPoint(c[0], c[1])
                        } else null
                    }
                }
            }.getOrNull()
        }
    }
}
