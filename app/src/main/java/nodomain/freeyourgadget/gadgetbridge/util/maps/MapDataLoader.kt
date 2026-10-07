package nodomain.freeyourgadget.gadgetbridge.util.maps

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

class MapDataLoader(private val context: Context) {

    fun loadMultiMapDataStore(): MapLoadResult {
        val multiMapDataStore = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        var anyLoaded = false

        val prefs = GBApplication.getPrefs()
        val folderUri = prefs.getString(MapsManager.PREF_MAPS_FOLDER, "")
        val documentFiles: Array<DocumentFile> = if (folderUri.isNotEmpty()) {
            DocumentFile.fromTreeUri(context, Uri.parse(folderUri))?.listFiles() ?: emptyArray()
        } else {
            emptyArray()
        }

        LOG.debug("Got {} map files", documentFiles.size)

        for (documentFile in documentFiles) {
            if (!documentFile.canRead()) continue
            val name = documentFile.name
            if (name == null || !name.endsWith(".map")) continue

            LOG.debug("Loading {}", name)
            try {
                val inputStream = context.contentResolver.openInputStream(documentFile.uri) as? FileInputStream
                    ?: throw IOException("Failed to open input stream for $name")
                val mapFile = MapFile(inputStream, 0, null)
                multiMapDataStore.addMapDataStore(mapFile, true, true)
                anyLoaded = true
            } catch (e: Exception) {
                LOG.error("Failed to load map file", e)
            }
        }

        return MapLoadResult(multiMapDataStore, anyLoaded)
    }

    fun resolveTheme(): XmlRenderTheme {
        val prefs = GBApplication.getPrefs()
        val themePrefValue = prefs.getString(MapsManager.PREF_MAP_THEME, "default").uppercase(Locale.ROOT)
        return try {
            MapTheme.valueOf(themePrefValue)
        } catch (e: Exception) {
            LOG.error("Failed to find theme {}", themePrefValue, e)
            MapTheme.DEFAULT
        }
    }

    fun resolveTrackColor(): Int {
        return GBApplication.getPrefs().getInt(
            MapsManager.PREF_TRACK_COLOR,
            ContextCompat.getColor(context, R.color.map_track_default)
        )
    }

    fun resolveTrackStyle(): TrackStyle = TrackStyle(color = resolveTrackColor())

    companion object {
        private val LOG = LoggerFactory.getLogger(MapDataLoader::class.java)
    }
}