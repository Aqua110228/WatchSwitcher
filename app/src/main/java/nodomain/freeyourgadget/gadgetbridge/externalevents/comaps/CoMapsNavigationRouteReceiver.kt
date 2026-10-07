package nodomain.freeyourgadget.gadgetbridge.externalevents.comaps

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import nodomain.freeyourgadget.gadgetbridge.GBApplication.deviceService
import nodomain.freeyourgadget.gadgetbridge.model.NavigationRouteSpec
import nodomain.freeyourgadget.gadgetbridge.util.NavigationUtils
import nodomain.freeyourgadget.gadgetbridge.util.maps.TrackPoint
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class CoMapsNavigationRouteReceiver internal constructor(
    private val handler: Handler,
    private val app: Application,
    private val dataUri: Uri,
) : ContentObserver(handler) {

    private var lastQueryTime = 0L

    private val pendingQuery = Runnable { this.queryNavigationData() }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)

        if (!NavigationUtils.shouldSendNavigation(app, "comaps")) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastQueryTime

        handler.removeCallbacks(pendingQuery)

        if (elapsed >= THROTTLE_MS) {
            lastQueryTime = now
            queryNavigationData()
        } else {
            handler.postDelayed(pendingQuery, THROTTLE_MS - elapsed)
        }
    }

    private fun queryNavigationData() {
        lastQueryTime = SystemClock.elapsedRealtime()

        val resolver = app.contentResolver
        try {
            resolver.query(
                dataUri,
                arrayOf(
                    NavigationContract.Route.Columns.SEQ,
                    NavigationContract.Route.Columns.LATITUDE,
                    NavigationContract.Route.Columns.LONGITUDE,
                    NavigationContract.Route.Columns.REVISION,
                ),
                null, null, null,
            ).use { cursor ->
                val navRoute = NavigationRouteSpec()
                if (cursor == null || !cursor.moveToFirst()) {
                     deviceService().onSetNavigationRoute(navRoute);
                    return
                }

                val latIdx = cursor.getColumnIndexOrThrow(NavigationContract.Route.Columns.LATITUDE)
                val lonIdx = cursor.getColumnIndexOrThrow(NavigationContract.Route.Columns.LONGITUDE)
                val revIdx = cursor.getColumnIndex(NavigationContract.Route.Columns.REVISION)

                navRoute.revision = if (revIdx >= 0) cursor.getInt(revIdx) else 0

                val points = ArrayList<TrackPoint>(cursor.count)
                do {
                    points.add(TrackPoint(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)))
                } while (cursor.moveToNext())

                navRoute.route = points

                LOG.debug("NAVI: {}", navRoute)

                deviceService().onSetNavigationRoute(navRoute)
            }
        } catch (e: SecurityException) {
            LOG.debug("Permission to read CoMaps navigation data has not been granted")
        } catch (e: Exception) {
            LOG.error("Error querying CoMaps navigation data", e)
        }
    }

    private object NavigationContract {
        object Route {
            object Columns {
                const val SEQ: String = "seq"
                const val LATITUDE: String = "lat"
                const val LONGITUDE: String = "lon"
                const val REVISION: String = "revision"
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CoMapsNavigationRouteReceiver::class.java)

        private const val THROTTLE_MS = 300L
    }
}