package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.http

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.DoublePrecisionFloat
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.SimpleValue
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.e175.klaus.solarpositioning.SolarEvents
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather.getWeatherSpec
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsWeatherHandler
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.http.ZeppOsWeatherHandlerV5.DayPartForecast
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.http.ZeppOsWeatherHandlerV5.Metadata
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.http.ZeppOsWeatherHandlerV5.toOffsetDateTime
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import kotlin.math.roundToInt
import co.nstant.`in`.cbor.model.Array as CborArray
import co.nstant.`in`.cbor.model.Map as CborMap

/**
 * Similar to V5, but:
 * - CBOR-encoded (format=cbor query parameter)
 *     - maps have indefinite length
 *     - decimal numbers are always float64
 *     - timestamps are still ISO strings with an offset
 * - Some datasets have new fields
 *     - hourlyWeather: temperatureApparent, temperatureDewPoint, precipitationChance, daylight
 *     - dailyWeather: sunriseCivil/sunsetCivil, sunVisibility/moonVisibility (normal, alwaysUp or alwaysDown), precipitationChance
 * - hourlyAirQuality has a new format, with metadata version=2
 *     - The response includes a full AQI standard with code and a category table (ranges, widths, colors)
 *     - Each hour has an AQI integer, the category and a cursorPosition
 *     - The pollutant fields were removed
 *     - The standard depends on the region: q_us-epa, q_eu-eea, q_cn-mee or a_glb-plume
 * - dailyAirQuality only has AQI per day. aqiLevel and the pollutant fields were removed.
 */
@Suppress("SameParameterValue")
object ZeppOsWeatherHandlerV6 {
    private val LOG: Logger = LoggerFactory.getLogger(ZeppOsWeatherHandlerV6::class.java)

    private val AQI_STANDARD_PLUME = AirQualityStandard(
        code = "a_glb-plume",
        displayName = "AQI (AccuWeather)",
        hasNumericAqi = true,
        isContinuous = true,
        standardType = 1,
        categories = listOf(
            AirQualityCategory(0, "Excellent", 0, 19, 6, 0x00e400),
            AirQualityCategory(1, "Fair", 20, 49, 9, 0xffff00),
            AirQualityCategory(2, "Poor", 50, 99, 14, 0xff7e00),
            AirQualityCategory(3, "Unhealthy", 100, 149, 14, 0xff0000),
            AirQualityCategory(4, "Very Unhealthy", 150, 249, 29, 0x8f3f97),
            AirQualityCategory(5, "Dangerous", 250, 350, 29, 0x7e0023),
        ),
        totalWidth = 351,
        globalMin = 0,
    )

    @JvmStatic
    fun handleHttpRequest(path: String, query: Map<String, String>): ByteArray? {
        val weatherSpec = getWeatherSpec()

        if (weatherSpec == null) {
            LOG.error("No weather in weather instance")
            return null
        }

        // /weather/v6/<LOCALE>/<LATITUDE>/<LONGITUDE>
        if (!Regex("^/weather/v6/[^/]+/[^/]*/[^/]*/?$").matches(path)) {
            LOG.error("Unknown path: {}", path)
            return null
        }

        val datasetsParam = query["datasets"]
        if (datasetsParam.isNullOrBlank()) {
            LOG.error("No datasets parameter provided")
            return null
        }

        val datasets = datasetsParam.split(",").map { it.trim() }

        val response = JsonObject()

        for (dataset in datasets) {
            val datasetObject: Any? = when (dataset) {
                "place" -> ZeppOsWeatherHandlerV5.createPlace(weatherSpec)
                "hourlyWeather" -> createHourlyWeather(weatherSpec)
                "hourlyAirQuality" -> createHourlyAirQuality(weatherSpec)
                "dailyIndices" -> ZeppOsWeatherHandlerV5.createDailyIndices(weatherSpec)
                "dailyWeather" -> createDailyWeather(weatherSpec)
                "dailyAirQuality" -> createDailyAirQuality(weatherSpec)
                "dailyTide" -> null
                else -> {
                    LOG.warn("Unknown weather dataset requested: {}", dataset)
                    null
                }
            }

            if (datasetObject != null) {
                response.add(dataset, ZeppOsWeatherHandlerV5.GSON.toJsonTree(datasetObject))
            }
        }

        return try {
            val baos = ByteArrayOutputStream()
            CborEncoder(baos).encode(toCbor(response))
            baos.toByteArray()
        } catch (e: CborException) {
            LOG.error("Failed to encode weather response", e)
            null
        }
    }

    private fun toCbor(element: JsonElement): DataItem {
        when (element) {
            is JsonNull -> {
                return SimpleValue.NULL
            }

            is JsonObject -> {
                val map = CborMap()
                // The library does not write the break byte for an empty indefinite-length map
                if (!element.isEmpty) {
                    @Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")
                    map.setChunked(true)
                }
                for ((key, value) in element.entrySet()) {
                    map.put(UnicodeString(key), toCbor(value))
                }
                return map
            }

            is JsonArray -> {
                val array = CborArray()
                element.forEach {
                    array.add(toCbor(it))
                }
                return array
            }

            is JsonPrimitive -> {
                when {
                    element.isBoolean -> {
                        return if (element.asBoolean)
                            SimpleValue.TRUE
                        else
                            SimpleValue.FALSE
                    }

                    element.isString -> {
                        return UnicodeString(element.asString)
                    }

                    else -> return when (val number = element.asNumber) {
                        // toString keeps the short decimal form of a float, such as 4.34
                        is Float, is Double -> DoublePrecisionFloat(number.toString().toDouble())
                        else -> {
                            val value = number.toLong()
                            if (value >= 0) UnsignedInteger(value) else NegativeInteger(value)
                        }
                    }
                }
            }

            else -> throw IllegalStateException("Unknown JsonElement subclass ${element.javaClass.simpleName}")
        }
    }

    private fun createHourlyWeather(weatherSpec: WeatherSpec): HourlyWeather {
        // Round down to the hour, so that the entry covering the current hour is kept
        val cutoff = weatherSpec.timestamp - Math.floorMod(weatherSpec.timestamp, 3600)

        val filteredHourly = weatherSpec.hourly.filter { it.timestamp != 0 && it.timestamp >= cutoff }

        // Devices only display weather once a known hour starts (#6663)
        val currentHourMissing = filteredHourly.firstOrNull()?.timestamp != cutoff

        val hours = buildList {
            if (currentHourMissing) {
                add(
                    HourlyWeatherHour(
                        forecastStart = toOffsetDateTime(Date(cutoff * 1000L)),
                        conditionCode = ZeppOsWeatherHandler.mapToZeppOsWeatherCode(weatherSpec.currentConditionCode)
                            .toString(),
                        humidity = weatherSpec.currentHumidity / 100.0f,
                        pressure = weatherSpec.pressure,
                        temperature = weatherSpec.currentTemp - 273f,
                        uvIndex = weatherSpec.uvIndex.roundToInt(),
                        visibility = weatherSpec.visibility,
                        windDirection = weatherSpec.windDirection,
                        windSpeed = weatherSpec.windSpeed,
                        windScale = weatherSpec.windSpeedAsBeaufort(),
                        // New V6 fields
                        temperatureApparent = weatherSpec.feelsLikeTemp.takeIf { it != 0 }?.let { it - 273f },
                        temperatureDewPoint = weatherSpec.dewPoint.takeIf { it != 0 }?.let { it - 273f },
                        precipitationChance = weatherSpec.precipProbability / 100.0f,
                        daylight = isDaylight(weatherSpec, cutoff.toLong()),
                    )
                )
            }
            filteredHourly.forEach {
                add(
                    HourlyWeatherHour(
                        forecastStart = toOffsetDateTime(Date(it.timestamp * 1000L)),
                        conditionCode = ZeppOsWeatherHandler.mapToZeppOsWeatherCode(it.conditionCode).toString(),
                        humidity = it.humidity / 100.0f,
                        pressure = if (it.pressure > 0) it.pressure else weatherSpec.pressure,
                        temperature = it.temp - 273f,
                        uvIndex = it.uvIndex.roundToInt(),
                        visibility = weatherSpec.visibility, // TODO WeatherSpec does not support hourly visibility
                        windDirection = it.windDirection,
                        windSpeed = it.windSpeed,
                        windScale = it.windSpeedAsBeaufort(),
                        // New V6 fields
                        temperatureApparent = null, // TODO WeatherSpec does not support hourly feelsLikeTemp
                        temperatureDewPoint = it.dewPoint.takeIf { dewPoint -> dewPoint != 0 }
                            ?.let { dewPoint -> dewPoint - 273f },
                        precipitationChance = it.precipProbability / 100.0f,
                        daylight = isDaylight(weatherSpec, it.timestamp.toLong()),
                    )
                )
            }
        }.take(72)

        return HourlyWeather(
            metadata = ZeppOsWeatherHandlerV5.createMetadata(weatherSpec),
            hours = hours
        )
    }

    private fun isDaylight(weatherSpec: WeatherSpec, timestamp: Long): Boolean {
        return !weatherSpec.isTimeNight(timestamp * 1000L)
    }

    private fun createHourlyAirQuality(weatherSpec: WeatherSpec): HourlyAirQuality? {
        val aqi = weatherSpec.airQuality?.aqi?.takeIf { it >= 0 } ?: return null

        val hourStart = weatherSpec.timestamp - Math.floorMod(weatherSpec.timestamp, 3600)

        return HourlyAirQuality(
            metadata = ZeppOsWeatherHandlerV5.createMetadata(weatherSpec).copy(version = 2),
            code = AQI_STANDARD_PLUME.code,
            standard = AQI_STANDARD_PLUME,
            hours = listOf(createHourlyAirQualityHour(AQI_STANDARD_PLUME, Date(hourStart * 1000L), aqi)),
        )
    }

    private fun createHourlyAirQualityHour(
        standard: AirQualityStandard,
        forecastStart: Date,
        aqi: Int
    ): HourlyAirQualityHour {
        val category = standard.categories.lastOrNull { aqi >= it.min } ?: standard.categories.first()
        val cursorPosition = ((aqi - standard.globalMin + 0.5) / standard.totalWidth * 100).roundToInt()

        return HourlyAirQualityHour(
            forecastStart = toOffsetDateTime(forecastStart),
            aqi = aqi,
            aqiDisplay = aqi.toString(),
            category = category.label,
            color = category.color,
            categoryIndex = category.categoryIndex,
            cursorPosition = cursorPosition.coerceIn(0, 100),
        )
    }

    private fun createDailyAirQuality(weatherSpec: WeatherSpec): DailyAirQuality? {
        val days = (listOf(weatherSpec.todayAsDaily()) + weatherSpec.forecasts).mapIndexedNotNull { i, day ->
            val aqi = day.airQuality?.aqi?.takeIf { it >= 0 } ?: return@mapIndexedNotNull null
            val dayTimestamp = weatherSpec.timestamp * 1000L + i * 86400_000L
            DailyAirQualityDay(
                forecastStart = toOffsetDateTime(DateTimeUtils.dayStart(Date(dayTimestamp))),
                forecastEnd = toOffsetDateTime(DateTimeUtils.dayEnd(Date(dayTimestamp))),
                aqi = aqi.toString(),
            )
        }

        if (days.isEmpty()) {
            return null
        }

        return DailyAirQuality(
            metadata = ZeppOsWeatherHandlerV5.createMetadata(weatherSpec),
            days = days,
        )
    }

    private fun createDailyWeather(weatherSpec: WeatherSpec): DailyWeather {
        val location = weatherSpec.getLocationObject()

        return DailyWeather(
            metadata = ZeppOsWeatherHandlerV5.createMetadata(weatherSpec),
            days = (listOf(weatherSpec.todayAsDaily()) + weatherSpec.forecasts).take(10).mapIndexed { i, day ->
                val dayTimestamp = weatherSpec.timestamp * 1000L + i * 86400_000L
                val calendar = GregorianCalendar()
                calendar.setTime(Date(dayTimestamp))

                val sunrise = WeatherSpec.sunriseComputed(day.sunRise, calendar, location)
                val sunset = WeatherSpec.sunsetComputed(day.sunSet, calendar, location)

                val civilTwilight = location?.let {
                    WeatherSpec.solarDay(calendar, it, SolarEvents.Horizon.CIVIL_TWILIGHT)
                }?.takeIf { it.rises().isNotEmpty() && it.sets().isNotEmpty() }

                val solarDay = location?.let { WeatherSpec.solarDay(calendar, it) }
                val sunVisibility = when {
                    solarDay?.alwaysAbove() == true -> "alwaysUp"
                    solarDay?.alwaysBelow() == true -> "alwaysDown"
                    else -> "normal"
                }

                val daytimeForecastStart: Date
                val daytimeForecastEnd: Date
                if (sunrise != null && sunset != null) {
                    daytimeForecastStart = sunrise
                    daytimeForecastEnd = sunset
                } else {
                    // We do not have sunrise and sunset.. use averages for now
                    val averageCalendar = Calendar.getInstance()

                    averageCalendar.setTime(Date(dayTimestamp))
                    averageCalendar.set(Calendar.HOUR_OF_DAY, 6)
                    daytimeForecastStart = averageCalendar.time

                    averageCalendar.setTime(Date(dayTimestamp))
                    averageCalendar.set(Calendar.HOUR_OF_DAY, 18)
                    daytimeForecastEnd = averageCalendar.time
                }

                val nightDurationSeconds = 3600 * 24 - ((daytimeForecastEnd.time - daytimeForecastStart.time) / 1000L)

                DailyWeatherDay(
                    forecastStart = toOffsetDateTime(DateTimeUtils.dayStart(Date(dayTimestamp))),
                    forecastEnd = toOffsetDateTime(DateTimeUtils.dayEnd(Date(dayTimestamp))),
                    conditionCode = ZeppOsWeatherHandler.mapToZeppOsWeatherCode(day.conditionCode).toString(),
                    maxUvIndex = day.uvIndex.roundToInt(),
                    moonPhaseLunarDay = day.lunarDay().toString(),
                    moonPhase = getMoonPhaseString(day.moonPhase),
                    moonrise = if (day.moonRise > 0) toOffsetDateTime(Date(day.moonRise * 1000L)) else null,
                    moonset = if (day.moonSet > 0) toOffsetDateTime(Date(day.moonSet * 1000L)) else null,
                    sunrise = sunrise?.let { toOffsetDateTime(it) },
                    sunset = sunset?.let { toOffsetDateTime(it) },
                    sunriseCivil = civilTwilight?.let { toOffsetDateTime(Date.from(it.rises()[0].toInstant())) },
                    sunsetCivil = civilTwilight?.let { toOffsetDateTime(Date.from(it.sets()[0].toInstant())) },
                    sunVisibility = sunVisibility,
                    moonVisibility = "normal", // TODO WeatherSpec does not support moon visibility
                    temperatureMax = day.maxTemp - 273f,
                    temperatureMin = day.minTemp - 273f,
                    precipitationChance = day.precipProbability / 100.0f,
                    daytimeForecast = DayPartForecast(
                        forecastStart = toOffsetDateTime(daytimeForecastStart),
                        forecastEnd = toOffsetDateTime(daytimeForecastEnd),
                        conditionCode = ZeppOsWeatherHandler.mapToZeppOsWeatherCode(day.conditionCode).toString(),
                        humidity = day.humidity / 100.0f,
                        windDirection = day.windDirection,
                        windSpeed = day.windSpeed,
                        windScale = day.windSpeedAsBeaufort(),
                    ),
                    overnightForecast = DayPartForecast(
                        forecastStart = toOffsetDateTime(daytimeForecastEnd),
                        forecastEnd = toOffsetDateTime(daytimeForecastEnd).plusSeconds(nightDurationSeconds),
                        conditionCode = ZeppOsWeatherHandler.mapToZeppOsWeatherCode(day.conditionCode).toString(),
                        humidity = day.humidity / 100.0f,
                        windDirection = day.windDirection,
                        windSpeed = day.windSpeed,
                        windScale = day.windSpeedAsBeaufort(),
                    ),
                )
            }
        )
    }

    private fun getMoonPhaseString(degrees: Int): String = when (Math.floorMod(degrees, 360)) {
        in 0..6 -> "new"
        in 7..83 -> "waxingCrescent"
        in 84..96 -> "firstQuarter"
        in 97..173 -> "waxingGibbous"
        in 174..186 -> "full"
        in 187..263 -> "waningGibbous"
        in 264..276 -> "thirdQuarter"
        in 277..353 -> "waningCrescent"
        else -> "new"
    }

    @GsonSerialized
    data class HourlyWeatherHour(
        val forecastStart: OffsetDateTime,
        val conditionCode: String,
        val humidity: Float,
        val pressure: Float,
        val temperature: Float,
        val uvIndex: Int,
        val visibility: Float,
        val windDirection: Int,
        val windSpeed: Float,
        val windScale: Int,
        // New V6 fields
        val temperatureApparent: Float?,
        val temperatureDewPoint: Float?,
        val precipitationChance: Float,
        val daylight: Boolean,
    )

    @GsonSerialized
    data class HourlyWeather(
        val metadata: Metadata,
        val hours: List<HourlyWeatherHour>,
    )

    @GsonSerialized
    data class AirQualityCategory(
        val categoryIndex: Int,
        val label: String,
        val min: Int,
        val max: Int,
        val widthPct: Int,
        val color: Int,
    )

    @GsonSerialized
    data class AirQualityStandard(
        val code: String,
        val displayName: String,
        val hasNumericAqi: Boolean,
        val isContinuous: Boolean,
        val standardType: Int,
        val categories: List<AirQualityCategory>,
        val totalWidth: Int,
        val globalMin: Int,
    )

    @GsonSerialized
    data class HourlyAirQualityHour(
        val forecastStart: OffsetDateTime,
        val aqi: Int,
        val aqiDisplay: String,
        val category: String,
        val color: Int,
        val categoryIndex: Int,
        val cursorPosition: Int,
    )

    @GsonSerialized
    data class HourlyAirQuality(
        val metadata: Metadata,
        val code: String,
        val standard: AirQualityStandard,
        val hours: List<HourlyAirQualityHour>,
    )

    @GsonSerialized
    data class DailyAirQualityDay(
        val forecastStart: OffsetDateTime,
        val forecastEnd: OffsetDateTime,
        val aqi: String,
    )

    @GsonSerialized
    data class DailyAirQuality(
        val metadata: Metadata,
        val days: List<DailyAirQualityDay>,
    )

    @GsonSerialized
    data class DailyWeatherDay(
        val forecastStart: OffsetDateTime,
        val forecastEnd: OffsetDateTime,
        val conditionCode: String,
        val maxUvIndex: Int,
        val moonPhaseLunarDay: String,
        val moonPhase: String,
        val moonrise: OffsetDateTime?,
        val moonset: OffsetDateTime?,
        val sunrise: OffsetDateTime?,
        val sunset: OffsetDateTime?,
        val sunriseCivil: OffsetDateTime?,
        val sunsetCivil: OffsetDateTime?,
        val sunVisibility: String,
        val moonVisibility: String,
        val temperatureMax: Float,
        val temperatureMin: Float,
        val precipitationChance: Float,
        val daytimeForecast: DayPartForecast,
        val overnightForecast: DayPartForecast,
    )

    @GsonSerialized
    data class DailyWeather(
        val metadata: Metadata,
        val days: List<DailyWeatherDay>,
    )
}
