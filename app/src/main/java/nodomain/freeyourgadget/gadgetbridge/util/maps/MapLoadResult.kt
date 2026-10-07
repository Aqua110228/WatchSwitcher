package nodomain.freeyourgadget.gadgetbridge.util.maps

import org.mapsforge.map.datastore.MultiMapDataStore

data class MapLoadResult(
    val dataStore: MultiMapDataStore,
    val anyLoaded: Boolean
)