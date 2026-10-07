package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminPreferences
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.HydrationContainer
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminHttpRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminHttpResponse
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.util.Locale
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.util.GB

class HydrationInterceptor(private val deviceSupport: GarminSupport) : HttpInterceptor {
    override fun supports(request: GarminHttpRequest): Boolean {
        if (request.domain != "connectapi.garmin.com" && request.domain != "connectapi.garmin.cn") {
            return false
        }
        return request.path == PATH_REGISTER ||
            request.path == PATH_SETTINGS ||
            request.path.startsWith(PATH_SUMMARY)
    }

    @Suppress("IntroduceWhenSubject")
    override fun handle(request: GarminHttpRequest): GarminHttpResponse? {
        markSupported()

        val path = request.path
        val method = request.method

        try {
            val json: JsonElement? = when {
                method == "PUT" && path == PATH_REGISTER -> JsonObject()
                method == "GET" && path == PATH_SETTINGS -> getSettings()
                method == "PUT" && path == PATH_LOG -> {
                    val dailySummary = log(parseBody(request))
                    GB.signalActivityDataFinish(deviceSupport.device)
                    dailySummary
                }
                method == "GET" && path.startsWith(PATH_DAILY) -> {
                    val date = LocalDate.parse(path.substring(PATH_DAILY.length))
                    GBApplication.acquireDbReadOnly().use { db ->
                        dailySummary(db.daoSession, date)
                    }
                }

                method == "PUT" && path.startsWith(PATH_ALL_DATA) -> {
                    val date = LocalDate.parse(path.substring(PATH_ALL_DATA.length))
                    saveSettings(parseBody(request))
                    GBApplication.acquireDbReadOnly().use { db ->
                        dailySummary(db.daoSession, date)
                    }
                }

                else -> null
            }

            if (json == null) {
                LOG.warn("Unknown hydration request {} {}", method, path)
                return null
            }

            return jsonResponse(json)
        } catch (e: Exception) {
            LOG.error("Failed to handle hydration request {} {}", method, path, e)
            return null
        }
    }

    private fun markSupported() {
        val prefs = deviceSupport.devicePrefs
        if (!prefs.getBoolean(GarminPreferences.PREF_GARMIN_HYDRATION_SUPPORTED, false)) {
            prefs.preferences.edit {
                putBoolean(GarminPreferences.PREF_GARMIN_HYDRATION_SUPPORTED, true)
            }
        }
    }

    private fun log(body: JsonObject): JsonElement? {
        val timestampLocal = LocalDateTime.parse(body.get("timestampLocal").asString, TIMESTAMP_LOCAL_PARSER)
        val volumeMl = body.get("valueInML").asDouble
        val date = timestampLocal.toLocalDate()
        val timestamp = timestampLocal.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        LOG.debug("Logging hydration entry at {} for {}: {}ml", timestamp, date, volumeMl)

        return GBApplication.acquireDB().use { db ->
            val provider = getProvider(db.daoSession) ?: return null
            provider.addEntry(HydrationSampleProvider.toDay(date), timestamp, volumeMl)
            dailySummary(db.daoSession, date)
        }
    }

    private fun dailySummary(session: DaoSession, date: LocalDate): JsonElement? {
        val provider = getProvider(session) ?: return null
        val day = HydrationSampleProvider.toDay(date)
        val lastEntry = provider.getLastEntry(day)

        return JsonObject().apply {
            addProperty("userId", DBHelper.getUser(session).id)
            addProperty("calendarDate", date.toString())
            addProperty("valueInML", provider.getDayTotal(day))
            addProperty("goalInML", ActivityUser().hydrationGoalMl.toDouble())
            @Suppress("SpellCheckingInspection")
            add("dailyAverageinML", JsonNull.INSTANCE)
            if (lastEntry != null) {
                addProperty("lastEntryTimestampLocal", formatTimestampLocal(lastEntry.timestamp))
            } else {
                add("lastEntryTimestampLocal", JsonNull.INSTANCE)
            }
            add("sweatLossInML", JsonNull.INSTANCE)
            addProperty("activityIntakeInML", 0.0)
        }
    }

    private fun getSettings(): JsonObject {
        return buildSettings(deviceSupport.devicePrefs, false)
    }

    private fun saveSettings(body: JsonObject) {
        LOG.debug("Saving settings: {}", body)

        deviceSupport.devicePrefs.preferences.edit {
            body.get("hydrationContainers")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.take(HydrationContainer.COUNT)
                ?.forEachIndexed { i, element ->
                    val container = element.asJsonObject
                    container.get("volume")?.takeIf { it.isJsonPrimitive }?.let {
                        putString(HydrationContainer.volumeKey(i + 1), it.asString)
                    }
                    parseUnit(container.get("unit"))?.let {
                        putString(HydrationContainer.unitKey(i + 1), it.key)
                    }
                }
            parseUnit(body.get("hydrationMeasurementUnit"))?.let {
                putString(DeviceSettingsPreferenceConst.PREF_HYDRATION_UNIT, it.key)
            }
            //body.get("hydrationAutoGoalEnabled")?.takeIf { it.isJsonPrimitive }?.let {
            //    putBoolean(GarminPreferences.PREF_HYDRATION_AUTO_GOAL, it.asBoolean)
            //}
        }
    }

    private fun parseUnit(element: JsonElement?): HydrationUnit? {
        return HydrationUnit.fromKey(element?.takeIf { it.isJsonPrimitive }?.asString)
    }

    private fun parseBody(request: GarminHttpRequest): JsonObject {
        return JsonParser.parseString(String(request.bodyToSend, StandardCharsets.UTF_8)).asJsonObject
    }

    private fun getProvider(session: DaoSession): HydrationSampleProvider? {
        val device = deviceSupport.device
        return device.deviceCoordinator.getHydrationSampleProvider(device, session)
    }

    private fun jsonResponse(json: JsonElement): GarminHttpResponse {
        val response = GarminHttpResponse()
        response.status = 200
        response.headers["content-type"] = "application/json"
        response.body = json.toString().toByteArray(StandardCharsets.UTF_8)
        return response
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(HydrationInterceptor::class.java)

        private const val PATH_REGISTER = "/pushnotification-service/ciq/hydration/register"
        private const val PATH_SETTINGS = "/userprofile-service/userprofile/user-settings/hydration"
        private const val PATH_SUMMARY = "/usersummary-service/usersummary/hydration/"
        private const val PATH_LOG = PATH_SUMMARY + "log"
        private const val PATH_DAILY = PATH_SUMMARY + "daily/"
        private const val PATH_ALL_DATA = PATH_SUMMARY + "allData/"

        /**
         * Local date and time, without zero padding, with an optional fraction of the second.
         */
        private val TIMESTAMP_LOCAL_PARSER: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 1, 2, SignStyle.NOT_NEGATIVE)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT)

        private val TIMESTAMP_LOCAL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.S", Locale.ROOT)

        @JvmStatic
        fun buildSettings(prefs: Prefs, withContainerNames: Boolean): JsonObject {
            val containers = JsonArray()
            for (container in 1..HydrationContainer.COUNT) {
                val volume = HydrationContainer.getVolume(prefs, container)
                containers.add(JsonObject().apply {
                    if (withContainerNames) {
                        addProperty("name", "Container $container")
                    }
                    addProperty("volume", if (volume % 1 == 0.0) volume.toInt() else volume)
                    addProperty("unit", HydrationContainer.getUnit(prefs, container).key)
                })
            }

            return JsonObject().apply {
                addProperty(
                    "hydrationMeasurementUnit",
                    getUnit(prefs, DeviceSettingsPreferenceConst.PREF_HYDRATION_UNIT).key
                )
                add("hydrationContainers", containers)
                // TODO auto goal?
                addProperty("hydrationAutoGoalEnabled", false)
            }
        }

        /**
         * Formats a timestamp in epoch ms as a local date and time, with one fraction digit.
         */
        @JvmStatic
        fun formatTimestampLocal(timestamp: Long): String {
            return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIMESTAMP_LOCAL_FORMATTER)
        }

        private fun getUnit(prefs: Prefs, key: String): HydrationUnit {
            return HydrationUnit.fromKey(prefs.getString(key, null)) ?: HydrationUnit.MILLILITER
        }
    }
}
