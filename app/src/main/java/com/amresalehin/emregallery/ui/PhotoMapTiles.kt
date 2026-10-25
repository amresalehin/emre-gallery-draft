package com.amresalehin.emregallery.ui

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Custom tile sources for ESRI World Imagery satellite tiles and CartoDB Dark Matter night tiles.
 */
object PhotoMapTiles {

    fun satelliteTileSource(): OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        1,
        19,
        256,
        ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Esri World Imagery"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + imageFilenameEnding()
    }

    fun nightTileSource(): XYTileSource = XYTileSource(
        "CartoDBDarkMatter",
        0,
        20,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
            "https://d.basemaps.cartocdn.com/dark_all/"
        )
    )
}
