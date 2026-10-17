package com.example.balizapp.ui.components

import com.example.balizapp.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Mosaicos de MapTiler (plan gratuito, uso no comercial). A diferencia de los servidores de
 * OpenStreetMap, permite descargar áreas para usar sin conexión. La clave sale de
 * `MAPTILER_KEY` en local.properties. Cada estilo tiene su propio nombre, así que sus mosaicos
 * se guardan por separado en la caché.
 */
class MapTilerTileSource private constructor(name: String, style: String) : OnlineTileSourceBase(
    name, 0, 20, 256, ".png",
    arrayOf("https://api.maptiler.com/maps/$style/256/"),
    "© MapTiler © OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) +
            "/" + MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding + "?key=" + BuildConfig.MAPTILER_KEY

    companion object {
        val Streets = MapTilerTileSource("MapTilerStreets", "streets-v2")
        val Dark = MapTilerTileSource("MapTilerStreetsDark", "streets-v2-dark")

        fun forTheme(dark: Boolean) = if (dark) Dark else Streets
    }
}
