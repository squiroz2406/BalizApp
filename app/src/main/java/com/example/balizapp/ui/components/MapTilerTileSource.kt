package com.example.balizapp.ui.components

import com.example.balizapp.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Mosaicos de MapTiler (plan gratuito, uso no comercial). A diferencia de los servidores de
 * OpenStreetMap, permite descargar áreas para usar sin conexión. La clave sale de
 * `MAPTILER_KEY` en local.properties.
 */
object MapTilerTileSource : OnlineTileSourceBase(
    "MapTilerStreets", 0, 20, 256, ".png",
    arrayOf("https://api.maptiler.com/maps/streets-v2/256/"),
    "© MapTiler © OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) +
            "/" + MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding + "?key=" + BuildConfig.MAPTILER_KEY
}
