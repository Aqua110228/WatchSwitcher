/*  Copyright (C) 2026 Arjan Schrijver

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.endurain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.Gson
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.util.InternetUtils
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File

enum class EndurainAuthType {
    NONE,
    AUTH_TOKEN,
    REFRESH_TOKEN
}

@GsonSerialized
data class EndurainLoginResponse(
    val session_id: String? = null,
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Int? = null,
    val refresh_token_expires_in: Int? = null,
    val token_type: String? = null,
    val mfa_required: Boolean? = null,
    val username: String? = null,
    val detail: String? = null
)

@GsonSerialized
data class EndurainMfaVerifyRequest(
    val username: String,
    val mfa_code: String
)

@GsonSerialized
data class EndurainTokenExchangeRequest(
    val code_verifier: String
)

/**
 * The parts of an Endurain activity that the user owns rather than the uploaded file: everything
 * that has to be carried over by hand when an activity is re-created from a new track.
 *
 * [hasUserContent] reports whether the user has written anything of their own on the activity,
 * which is the signal for refusing to destroy it.
 */
data class EndurainActivityDetails(
    val name: String?,
    val activityType: Int?,
    val description: String?,
    val privateNotes: String?,
    val gearId: Int?,
    val visibility: Int?,
    val hideFlags: Map<String, Boolean>
) {
    val hasUserContent: Boolean
        get() = !description.isNullOrBlank() || !privateNotes.isNullOrBlank() || gearId != null
}

/**
 * Result of looking an activity up. [Gone] exists because an activity that is merely absent has
 * to be told apart from a request that failed: the first is re-uploaded, the second retried.
 *
 * Endurain answers 200 with a body of `null` for an activity that does not exist, rather than
 * 404, so the body decides: <https://codeberg.org/endurain-project/endurain/issues/911>. A 404 is
 * read as [Gone] too, which is what the same answer looks like from a corrected server.
 */
sealed interface EndurainActivityLookup {
    data class Found(val details: EndurainActivityDetails) : EndurainActivityLookup
    object Gone : EndurainActivityLookup
    object Failed : EndurainActivityLookup
}

/**
 * A photo or video attached to an Endurain activity. [mediaPath] is the server-side storage
 * path rather than a fetchable one, so a client that needs the file takes its basename and asks
 * for /activity_media/{basename}:
 * <https://codeberg.org/endurain-project/endurain/issues/912>.
 */
@GsonSerialized
data class EndurainActivityMedia(
    val id: Int,
    val activityId: Int,
    val mediaPath: String
)

@GsonSerialized
data class EndurainIdentityProvider(
    val id: String,
    val name: String,
    val slug: String
)

class EndurainApiClient(
    private val baseUrl: String,
    private val tokenManager: EndurainTokenManager
) {
    private val gson = Gson()
    private val LOG = LoggerFactory.getLogger(EndurainApiClient::class.java)

    /**
     * Build headers with authentication tokens
     */
    private fun buildHeaders(auth: EndurainAuthType): MutableMap<String, String> {
        val headers = mutableMapOf("X-Client-Type" to "mobile")

        when (auth) {
            EndurainAuthType.AUTH_TOKEN -> {
                tokenManager.getAccessToken()?.let { token ->
                    headers["Authorization"] = "Bearer $token"
                }
            }
            EndurainAuthType.REFRESH_TOKEN -> {
                tokenManager.getRefreshToken()?.let { token ->
                    headers["Authorization"] = "Bearer $token"
                }
            }
            else -> {}
        }

        return headers
    }

    /**
     * Username/Password Login
     */
    fun login(username: String, password: String): EndurainLoginResponse? {
        try {
            val uri = "$baseUrl/api/v1/auth/login".toUri()

            // Form-encoded body
            val body = "username=${Uri.encode(username)}&password=${Uri.encode(password)}"

            val headers = buildHeaders(EndurainAuthType.NONE)
            headers["Content-Type"] = "application/x-www-form-urlencoded"

            val responseText = InternetUtils.doStringRequest(
                uri = uri,
                method = "POST",
                requestHeaders = headers,
                body = body
            )

            return if (responseText != null) {
                gson.fromJson(responseText, EndurainLoginResponse::class.java)
            } else {
                LOG.error("Login failed: empty response")
                null
            }
        } catch (e: Exception) {
            LOG.error("Login error", e)
            return null
        }
    }

    /**
     * MFA Verification
     */
    fun verifyMfa(username: String, mfaCode: String): EndurainLoginResponse? {
        try {
            val uri = "$baseUrl/api/v1/auth/mfa/verify".toUri()

            val request = EndurainMfaVerifyRequest(username, mfaCode)
            val body = gson.toJson(request)

            val headers = buildHeaders(EndurainAuthType.NONE)
            headers["Content-Type"] = "application/json"

            val responseText = InternetUtils.doStringRequest(
                uri = uri,
                method = "POST",
                requestHeaders = headers,
                body = body
            )

            return if (responseText != null) {
                gson.fromJson(responseText, EndurainLoginResponse::class.java)
            } else {
                LOG.error("MFA verification failed: empty response")
                null
            }
        } catch (e: Exception) {
            LOG.error("MFA verification error", e)
            return null
        }
    }

    /**
     * Token Refresh
     */
    fun refreshToken(): EndurainLoginResponse? {
        try {
            val uri = "$baseUrl/api/v1/auth/refresh".toUri()

            val headers = buildHeaders(EndurainAuthType.REFRESH_TOKEN)
            headers["Content-Type"] = "application/json"

            val responseText = InternetUtils.doStringRequest(
                uri = uri,
                method = "POST",
                requestHeaders = headers,
                body = "{}"
            )

            return if (responseText != null) {
                gson.fromJson(responseText, EndurainLoginResponse::class.java)
            } else {
                LOG.error("Token refresh failed: empty response")
                null
            }
        } catch (e: Exception) {
            LOG.error("Token refresh error", e)
            return null
        }
    }

    /**
     * Logout
     */
    fun logout(): Boolean {
        try {
            val uri = "$baseUrl/api/v1/auth/logout".toUri()

            val headers = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            headers["Content-Type"] = "application/json"

            InternetUtils.doStringRequest(
                uri = uri,
                method = "POST",
                requestHeaders = headers,
                body = "{}"
            )

            tokenManager.clearTokens()
            return true
        } catch (e: Exception) {
            LOG.error("Logout error", e)
            return false
        }
    }

    /**
     * Get list of available identity providers
     */
    fun getIdentityProviders(): List<EndurainIdentityProvider>? {
        try {
            val uri = "$baseUrl/api/v1/public/idp".toUri()

            val headers = buildHeaders(EndurainAuthType.NONE)
            headers["Content-Type"] = "application/json"

            val responseText = InternetUtils.doStringRequest(
                uri = uri,
                requestHeaders = headers
            )

            return if (responseText != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<EndurainIdentityProvider>>() {}.type
                gson.fromJson(responseText, type)
            } else {
                LOG.error("Failed to fetch identity providers")
                null
            }
        } catch (e: Exception) {
            LOG.error("Error fetching identity providers", e)
            return null
        }
    }

    /**
     * Exchange OAuth session for tokens (PKCE flow)
     */
    fun exchangeOAuthSession(sessionId: String, codeVerifier: String): EndurainLoginResponse? {
        try {
            val uri = "$baseUrl/api/v1/public/idp/session/$sessionId/tokens".toUri()

            val request = EndurainTokenExchangeRequest(codeVerifier)
            val body = gson.toJson(request)

            val headers = buildHeaders(EndurainAuthType.NONE)
            headers["Content-Type"] = "application/json"

            val responseText = InternetUtils.doStringRequest(
                uri = uri,
                method = "POST",
                requestHeaders = headers,
                body = body
            )

            LOG.debug("OAuth token result: $responseText")

            return if (responseText != null) {
                gson.fromJson(responseText, EndurainLoginResponse::class.java)
            } else {
                LOG.error("OAuth token exchange failed: empty response")
                null
            }
        } catch (e: Exception) {
            LOG.error("OAuth token exchange error", e)
            return null
        }
    }

    /**
     * Generic authenticated API request
     */
    fun doAuthenticatedRequest(
        endpoint: String,
        method: String = "GET",
        body: String? = null
    ): String? {
        try {
            val uri = "$baseUrl$endpoint".toUri()

            val headers = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            headers["Content-Type"] = "application/json"

            return InternetUtils.doStringRequest(
                uri = uri,
                method = method,
                requestHeaders = headers,
                body = body
            )
        } catch (e: Exception) {
            LOG.error("Authenticated request error", e)
            return null
        }
    }

    /**
     * Upload an activity file. Endurain accepts FIT or GPX; Gadgetbridge currently always sends
     * FIT (built from the workout). [callback] receives the new activity id on success, or a
     * null id plus a human-readable [reason] on failure.
     */
    fun uploadActivity(file: File, callback: (id: Int?, reason: String?) -> Unit) {
        Thread {
            try {
                val uri = "$baseUrl/api/v1/activities/create/upload".toUri()
                val headers = buildHeaders(EndurainAuthType.AUTH_TOKEN)

                InternetUtils.uploadBinaryFile(
                    uri = uri,
                    file = file,
                    requestHeaders = headers
                ) { success, statusCode, responseText, reason ->
                    if (success && statusCode != null && statusCode in 200..299 && responseText != null) {
                        LOG.debug("Response $statusCode from Endurain: $responseText")
                        val jsonArray = JSONArray(responseText)
                        val firstObject = jsonArray.getJSONObject(0)
                        val id = firstObject.getInt("id")
                        callback(id, null)
                    } else {
                        LOG.error("Activity upload failed (status {}, reason {})", statusCode, reason)
                        callback(null, reason ?: statusCode?.let { "HTTP $it" })
                    }
                }
            } catch (e: Exception) {
                LOG.error("Activity upload error", e)
                callback(null, e.localizedMessage)
            }
        }.start()
    }

    /**
     * Upload activity photo. [callback], when provided, fires with the id of the created media
     * entry, or null on failure, so callers off the main thread (the auto-upload worker's photo
     * sync) can wait for the result and remember which entry to replace later.
     */
    fun uploadActivityPhoto(activityId: Int, file: File, callback: ((mediaId: Int?) -> Unit)? = null) {
        Thread {
            var converted: File? = null
            try {
                val uploadFile = if (file.extension.lowercase() in ALLOWED_PHOTO_EXTENSIONS) {
                    file
                } else {
                    converted = convertToJpeg(file)
                    if (converted == null) {
                        callback?.invoke(null)
                        return@Thread
                    }
                    converted
                }
                val uri = "$baseUrl/api/v1/activities_media/upload/activity_id/$activityId".toUri()
                val headers = buildHeaders(EndurainAuthType.AUTH_TOKEN)

                InternetUtils.uploadBinaryFile(
                    uri = uri,
                    file = uploadFile,
                    requestHeaders = headers
                ) { success, statusCode, responseText, reason ->
                    if (success && responseText != null) {
                        LOG.debug("Response ($statusCode) from Endurain: $responseText")
                        val mediaId = try {
                            JSONObject(responseText).optInt("id").takeIf { it > 0 }
                        } catch (e: Exception) {
                            LOG.warn("Could not read media id from Endurain response", e)
                            null
                        }
                        callback?.invoke(mediaId)
                    } else {
                        LOG.error("Activity photo upload to Endurain failed. Response ($statusCode, reason {}) received: $responseText", reason)
                        callback?.invoke(null)
                    }
                }
            } catch (e: Exception) {
                LOG.error("Activity photo upload error", e)
                callback?.invoke(null)
            } finally {
                converted?.delete()
            }
        }.start()
    }

    /** Re-encodes [file] as a JPEG in the cache directory, or answers null when it cannot be decoded. */
    private fun convertToJpeg(file: File): File? {
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) {
            LOG.warn("Cannot decode photo {} to convert it for Endurain", file.name)
            return null
        }
        return try {
            val jpeg = File.createTempFile("endurain_photo", ".jpg", GBApplication.getContext().cacheDir)
            val written = jpeg.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (!written) {
                LOG.warn("Cannot encode photo {} as JPEG for Endurain", file.name)
                jpeg.delete()
                return null
            }
            LOG.debug("Converted photo {} to JPEG for Endurain", file.name)
            jpeg
        } finally {
            bitmap.recycle()
        }
    }

    /** Reads back the user-owned parts of [activityId]. */
    fun getActivityDetails(activityId: Int): EndurainActivityLookup {
        try {
            val uri = "$baseUrl/api/v1/activities/$activityId".toUri()
            val response = InternetUtils.doStringRequestWithStatus(
                uri = uri,
                requestHeaders = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            )
            // An activity that does not exist is reported as 200 with a body of `null`
            // (<https://codeberg.org/endurain-project/endurain/issues/911>); 404 is what the same
            // answer looks like once that is corrected. Both mean gone, not failed.
            if (response.statusCode == 404) {
                LOG.info("Endurain activity {} no longer exists", activityId)
                return EndurainActivityLookup.Gone
            }
            if (response.statusCode !in 200..299 || response.body == null) {
                LOG.error("Reading activity {} failed (status {})", activityId, response.statusCode)
                return EndurainActivityLookup.Failed
            }
            if (response.body.isBlank() || response.body == "null") {
                LOG.info("Endurain activity {} no longer exists", activityId)
                return EndurainActivityLookup.Gone
            }
            val json = JSONObject(response.body)
            val hideFlags = HIDE_FLAGS
                .filter { json.has(it) && !json.isNull(it) }
                .associateWith { json.getBoolean(it) }
            return EndurainActivityLookup.Found(
                EndurainActivityDetails(
                    name = json.optStringOrNull("name"),
                    activityType = json.optIntOrNull("activity_type"),
                    description = json.optStringOrNull("description"),
                    privateNotes = json.optStringOrNull("private_notes"),
                    gearId = json.optIntOrNull("gear_id"),
                    visibility = json.optIntOrNull("visibility"),
                    hideFlags = hideFlags
                )
            )
        } catch (e: Exception) {
            LOG.error("Error reading activity {}", activityId, e)
            return EndurainActivityLookup.Failed
        }
    }

    /**
     * Deletes an activity and everything derived from it. The endpoint answers 200 with a message
     * body.
     */
    fun deleteActivity(activityId: Int): Boolean {
        try {
            val uri = "$baseUrl/api/v1/activities/$activityId/delete".toUri()
            val response = InternetUtils.doStringRequestWithStatus(
                uri = uri,
                method = "DELETE",
                requestHeaders = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            )
            if (response.statusCode !in 200..299) {
                LOG.error("Deleting activity {} failed (status {}): {}", activityId, response.statusCode, response.body)
                return false
            }
            return true
        } catch (e: Exception) {
            LOG.error("Error deleting activity {}", activityId, e)
            return false
        }
    }

    /**
     * Restores the user-owned fields of [details] onto [activityId], for an activity that has
     * just been re-created from a new track. [name] and [activityKind] come from the local
     * workout and win over what [details] carried.
     */
    fun restoreActivityDetails(
        activityId: Int,
        activityKind: ActivityKind,
        name: String?,
        details: EndurainActivityDetails
    ): Boolean {
        try {
            val uri = "$baseUrl/api/v1/activities/edit".toUri()
            val headers = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            headers["Content-Type"] = "application/json"

            val bodyJson = JSONObject().apply {
                put("id", activityId)
                put("activity_type", activityLookup[activityKind.ordinal] ?: GENERIC_ACTIVITY_TYPE)
                if (name != null) put("name", name)
                details.description?.let { put("description", it) }
                details.privateNotes?.let { put("private_notes", it) }
                details.gearId?.let { put("gear_id", it) }
                details.visibility?.let { put("visibility", it) }
                for ((flag, value) in details.hideFlags) {
                    put(flag, value)
                }
            }

            val response = InternetUtils.doStringRequestWithStatus(
                uri = uri,
                method = "PUT",
                requestHeaders = headers,
                body = bodyJson.toString()
            )
            if (response.statusCode !in 200..299) {
                LOG.error("Restoring activity {} failed (status {})", activityId, response.statusCode)
                return false
            }
            return true
        } catch (e: Exception) {
            LOG.error("Error restoring activity {}", activityId, e)
            return false
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    /**
     * Media currently attached to [activityId], or null when the request failed. An activity
     * with no media returns an empty list.
     */
    fun listActivityMedia(activityId: Int): List<EndurainActivityMedia>? {
        try {
            val uri = "$baseUrl/api/v1/activities_media/activity_id/$activityId".toUri()
            val response = InternetUtils.doStringRequestWithStatus(
                uri = uri,
                requestHeaders = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            )
            if (response.statusCode !in 200..299 || response.body == null) {
                LOG.error("Listing media of activity {} failed (status {})", activityId, response.statusCode)
                return null
            }
            // The endpoint answers null rather than [] when the activity has no media.
            if (response.body.isBlank() || response.body == "null") {
                return emptyList()
            }
            val array = JSONArray(response.body)
            return (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                EndurainActivityMedia(
                    entry.getInt("id"),
                    entry.getInt("activity_id"),
                    entry.getString("media_path")
                )
            }
        } catch (e: Exception) {
            LOG.error("Error listing media of activity {}", activityId, e)
            return null
        }
    }

    /**
     * Deletes one media entry. The endpoint answers 204 with no body.
     */
    fun deleteActivityMedia(mediaId: Int): Boolean {
        try {
            val uri = "$baseUrl/api/v1/activities_media/$mediaId".toUri()
            val response = InternetUtils.doStringRequestWithStatus(
                uri = uri,
                method = "DELETE",
                requestHeaders = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            )
            if (response.statusCode !in 200..299) {
                LOG.error("Deleting media {} failed (status {}): {}", mediaId, response.statusCode, response.body)
                return false
            }
            return true
        } catch (e: Exception) {
            LOG.error("Error deleting media {}", mediaId, e)
            return false
        }
    }

    /**
     * Edit uploaded activity
     */
    fun editActivity(id: Int, activityKind: ActivityKind, name: String?): Boolean {
        try {
            val uri = "$baseUrl/api/v1/activities/edit".toUri()
            val headers = buildHeaders(EndurainAuthType.AUTH_TOKEN)
            headers["Content-Type"] = "application/json"

            val activityType = activityLookup[activityKind.ordinal] ?: GENERIC_ACTIVITY_TYPE

            val bodyJson = JSONObject().apply {
                put("id", id)
                put("activity_type", activityType)
                if (name != null) put("name", name)
            }

            val result = InternetUtils.doStringRequest(
                method = "PUT",
                uri = uri,
                requestHeaders = headers,
                body = bodyJson.toString()
            )
            LOG.info("editActivity result: {}", result)
            return true
        } catch (e: Exception) {
            LOG.error("Activity edit error", e)
            return false
        }
    }

    /**
     * Fetch server version string
     */
    fun fetchVersion(): String? {
        try {
            val uri = "$baseUrl/api/v1/about".toUri()

            val headers = buildHeaders(EndurainAuthType.NONE)

            val result = InternetUtils.doJsonRequest(
                uri = uri,
                requestHeaders = headers,
            )
            return result?.getString("version")
        } catch (e: Exception) {
            LOG.error("Fetching server version error", e)
            return null
        }
    }

    /** Endurain's activity type for a workout whose kind it has no code for. */
    private val GENERIC_ACTIVITY_TYPE = 10

    /** The only photo extensions Endurain accepts; anything else is answered 400 EXTENSION_NOT_ALLOWED. */
    private val ALLOWED_PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png")

    /**
     * Per-activity display toggles the user can set. Listed so they survive an activity being
     * re-created, since the new upload would otherwise reset them to their defaults.
     */
    private val HIDE_FLAGS = listOf(
        "hide_start_time", "hide_location", "hide_map", "hide_hr", "hide_power",
        "hide_cadence", "hide_elevation", "hide_speed", "hide_pace", "hide_laps",
        "hide_workout_sets_steps", "hide_gear"
    )

    /**
     * Lookup map to convert ActivityKind to the integer Endurain expects,
     * based on https://docs.endurain.com/developer-guide/supported-types/
     */
    val activityLookup = mapOf(
        ActivityKind.RUNNING.ordinal to 1,
        ActivityKind.TRAIL_RUN.ordinal to 2,
        ActivityKind.VIRTUAL_RUN.ordinal to 3,
        ActivityKind.CYCLING.ordinal to 4,
        ActivityKind.ROAD_BIKE.ordinal to 4,
        ActivityKind.GRAVEL_BIKE.ordinal to 5,
        ActivityKind.MOUNTAIN_BIKE.ordinal to 6,
        ActivityKind.POOL_SWIM.ordinal to 8,
        ActivityKind.SWIMMING_OPENWATER.ordinal to 9,
        ActivityKind.TRAINING.ordinal to 10,
        ActivityKind.WALKING.ordinal to 11,
        ActivityKind.HIKING.ordinal to 12,
        ActivityKind.ROWING.ordinal to 13,
        ActivityKind.YOGA.ordinal to 14,
        ActivityKind.SKIING.ordinal to 15,
        ActivityKind.SNOWBOARDING.ordinal to 17,
        ActivityKind.TRANSITION.ordinal to 18,
        ActivityKind.STRENGTH_TRAINING.ordinal to 19,
        ActivityKind.CROSSFIT.ordinal to 20,
        ActivityKind.TENNIS.ordinal to 21,
        ActivityKind.TABLE_TENNIS.ordinal to 22,
        ActivityKind.BADMINTON.ordinal to 23,
        ActivityKind.SQUASH.ordinal to 24,
        ActivityKind.RACQUETBALL.ordinal to 25,
        ActivityKind.PICKLEBALL.ordinal to 26,
        ActivityKind.BIKE_COMMUTE.ordinal to 27,
        ActivityKind.INDOOR_CYCLING.ordinal to 28,
        ActivityKind.WINDSURFING.ordinal to 30,
        ActivityKind.INDOOR_WALKING.ordinal to 31,
        ActivityKind.STAND_UP_PADDLEBOARDING.ordinal to 32,
        ActivityKind.SURFING.ordinal to 33,
        ActivityKind.TRACK_RUN.ordinal to 34,
        ActivityKind.E_BIKE.ordinal to 35,
        ActivityKind.E_MOUNTAIN_BIKE.ordinal to 36,
        ActivityKind.ICE_SKATING.ordinal to 37,
        ActivityKind.SOCCER.ordinal to 38,
        ActivityKind.PADEL.ordinal to 39,
        ActivityKind.TREADMILL.ordinal to 40,
        ActivityKind.CARDIO.ordinal to 41,
        ActivityKind.KAYAKING.ordinal to 42,
        ActivityKind.SAILING.ordinal to 43,
        ActivityKind.INLINE_SKATING.ordinal to 45,
        ActivityKind.HIIT.ordinal to 46,
        ActivityKind.OUTDOOR_RUNNING.ordinal to 1,
        ActivityKind.STREET_RUNNING.ordinal to 1,
        ActivityKind.ULTRA_RUN.ordinal to 1,
        ActivityKind.CROSS_COUNTRY_RUNNING.ordinal to 2,
        ActivityKind.INDOOR_TRACK_RUNNING.ordinal to 34,
        ActivityKind.INDOOR_RUNNING.ordinal to 40,
        ActivityKind.OUTDOOR_CYCLING.ordinal to 4,
        ActivityKind.BIKE_TOUR.ordinal to 4,
        ActivityKind.CYCLING_DOWNHILL.ordinal to 6,
        ActivityKind.CYCLO_CROSS.ordinal to 29,
        ActivityKind.DYNAMIC_CYCLE.ordinal to 28,
        ActivityKind.SWIMMING.ordinal to 8,
        ActivityKind.ARTISTIC_SWIMMING.ordinal to 8,
        ActivityKind.OUTDOOR_WALKING.ordinal to 11,
        ActivityKind.RACE_WALKING.ordinal to 11,
        ActivityKind.AIR_WALKER.ordinal to 41,
        ActivityKind.MOUNTAIN_HIKE.ordinal to 12,
        ActivityKind.TRAIL_HIKE.ordinal to 12,
        ActivityKind.ROWING_MACHINE.ordinal to 13,
        ActivityKind.SAIL_RACE.ordinal to 43,
        ActivityKind.SAIL_EXPEDITION.ordinal to 43,
        ActivityKind.ELLIPTICAL_TRAINER.ordinal to 41,
        ActivityKind.MMA_HIIT.ordinal to 46,
        ActivityKind.JUMP_ROPING.ordinal to 47,
        ActivityKind.BACKCOUNTRY_SKIING.ordinal to 15,
        ActivityKind.INDOOR_SKIING.ordinal to 15,
        ActivityKind.CROSS_COUNTRY_SKIING.ordinal to 16,
        ActivityKind.XC_CLASSIC_SKI.ordinal to 16,
        ActivityKind.XC_SKATE_SKI.ordinal to 16,
        ActivityKind.BACKCOUNTRY_SNOWBOARDING.ordinal to 17,
        ActivityKind.SNOWSHOE.ordinal to 44,
        ActivityKind.INDOOR_ICE_SKATING.ordinal to 37,
        ActivityKind.ROLLER_SKATING.ordinal to 45,
        ActivityKind.PLATFORM_TENNIS.ordinal to 21,
        ActivityKind.BEACH_SOCCER.ordinal to 38
    )
}
