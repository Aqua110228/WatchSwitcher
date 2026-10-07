package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.PushNotificationService
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.HydrationInterceptor
import nodomain.freeyourgadget.gadgetbridge.util.protobuf.buildWith
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

@Suppress("SameParameterValue")
class PushNotificationHandler(private val deviceSupport: GarminSupport) {
    fun handle(service: PushNotificationService): Boolean {
        if (service.hasLastPushNotification()) {
            LOG.debug("Last push notification received by the watch: {}", service.lastPushNotification.id)
            return true
        }

        if (service.hasPushNotificationAck()) {
            val ack = service.pushNotificationAck
            if (ack.status == 0) {
                LOG.debug("Push notification {} acknowledged", ack.id)
            } else {
                LOG.warn("Push notification {} failed, status={}", ack.id, ack.status)
            }
            return true
        }

        LOG.warn("Unknown push notification message: {}", service)
        return false
    }

    /**
     * A push with the hydration total, goal and last entry for the current day.
     */
    fun hydrationUpdate(): Smart? {
        val device = deviceSupport.device
        val date = LocalDate.now()
        val day = HydrationSampleProvider.toDay(date)

        val data = GBApplication.acquireDbReadOnly().use { db ->
            val provider = device.deviceCoordinator.getHydrationSampleProvider(device, db.daoSession) ?: return null
            val lastEntry = provider.getLastEntry(day)

            JsonObject().apply {
                @Suppress("SpellCheckingInspection")
                addProperty("ciqPayloadType", "hydration_update")
                addProperty("dailyHydrationTotal", provider.getDayTotal(day))
                addProperty("dailyHydrationGoal", ActivityUser().hydrationGoalMl)
                addProperty("calendarDate", date.toString())
                if (lastEntry != null) {
                    addProperty("lastEntryTimestamp", HydrationInterceptor.formatTimestampLocal(lastEntry.timestamp))
                } else {
                    add("lastEntryTimestamp", JsonNull.INSTANCE)
                }
            }
        }

        return push(HYDRATION_APP_ID, data)
    }

    /**
     * A push with the hydration settings.
     */
    fun hydrationSettings(): Smart {
        val data = JsonObject().apply {
            @Suppress("SpellCheckingInspection")
            addProperty("ciqPayloadType", "hydration_settings")
            HydrationInterceptor.buildSettings(deviceSupport.devicePrefs, true).entrySet().forEach { (key, value) ->
                add(key, value)
            }
        }

        return push(HYDRATION_APP_ID, data)
    }

    private fun push(appId: String, data: JsonObject): Smart {
        val payload = JsonObject().apply {
            add("data", data)
            add("gns", JsonObject())
        }
        val id = String.format(Locale.ROOT, "%d-%08x", System.currentTimeMillis(), Random.nextInt())

        LOG.debug("Sending push notification {} for {}: {}", id, appId, payload)

        return Smart.newBuilder().buildWith {
            pushNotificationService = PushNotificationService.newBuilder().buildWith {
                pushNotification = PushNotificationService.PushNotification.newBuilder().buildWith {
                    this.id = id
                    this.appId = appId
                    this.payload = payload.toString()
                }
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PushNotificationHandler::class.java)

        private const val HYDRATION_APP_ID = "0b8d4130-e3c6-4217-bd6e-af3933cb1c47"
    }
}
