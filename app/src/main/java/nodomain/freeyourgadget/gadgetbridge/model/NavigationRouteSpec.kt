package nodomain.freeyourgadget.gadgetbridge.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import nodomain.freeyourgadget.gadgetbridge.util.maps.TrackPoint

@Parcelize
data class NavigationRouteSpec(
    var route: List<TrackPoint> = emptyList(),
    var revision: Int = 0,
) : Parcelable