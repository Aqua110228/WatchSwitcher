/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.endurain.EndurainTokenManager
import nodomain.freeyourgadget.gadgetbridge.activities.endurain.WandererTokenManager
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import java.io.File

/** File format a service accepts when an activity is uploaded. */
enum class WorkoutPayloadFormat { FIT, GPX }

/**
 * Outcome of bringing the track of an already-uploaded activity in line with the local workout.
 *
 * [newRemoteActivityId] and [newPhotoMediaId] are set when the service could only do this by
 * re-creating the activity, so the caller stores the new ids. [unsupported] means the service
 * offers no way to do it at all and there is nothing to retry. [refusal] is a localized reason
 * the update was deliberately not attempted, to show the user.
 */
data class TrackUpdateResult(
    val success: Boolean,
    val newRemoteActivityId: String? = null,
    val newPhotoMediaId: Int? = null,
    val refusal: String? = null,
    val unsupported: Boolean = false
)

/**
 * One online fitness tracker, described by what it accepts and by what it can still change on an
 * activity after the initial upload. [WorkoutUploadWorker] drives every service through this, so
 * supporting another one means adding an implementation rather than another branch.
 */
interface WorkoutUploadTarget {
    /**
     * Value stored in `WorkoutUpload.service`. Persisted, so see the registry in
     * [WorkoutUploadStore] before choosing one.
     */
    val service: Int

    /** The service's own name, for anything that lists services to the user. */
    @get:StringRes
    val nameRes: Int

    /** Format to export a workout in for this service. */
    val format: WorkoutPayloadFormat

    /** Whether a workout with no GPS track can be uploaded at all. */
    val requiresTrack: Boolean

    /** Notification id used to report a failure, so services do not overwrite each other. */
    val failureNotificationId: Int

    /** Whether the user has finished setting this service up. */
    fun isLoggedIn(context: Context): Boolean

    /** Whether the user asked for workouts to be uploaded to this service automatically. */
    fun isAutoUploadEnabled(prefs: GBPrefs): Boolean

    /** Localized "upload to this service failed: %s" message. */
    fun failureMessage(context: Context, reason: String): String

    /**
     * Web address of the activity [remoteActivityId] on the service, for opening it in a browser,
     * or null when it cannot be built because the service is not set up.
     */
    fun activityUrl(context: Context, remoteActivityId: String): String?

    fun upload(
        context: Context,
        summary: BaseActivitySummary,
        payload: File
    ): WorkoutUploader.UploadResult

    /** Pushes the name and activity kind onto an activity that already exists remotely. */
    fun updateMetadata(
        context: Context,
        remoteActivityId: String,
        summary: BaseActivitySummary
    ): Boolean

    /**
     * Makes the track of the remote activity match [payload]. [row] is what was last uploaded, so
     * a service that has to re-create the activity can tell a track being added from one merely
     * changing, and can weigh that against how destructive re-creating is.
     */
    fun updateTrack(
        context: Context,
        remoteActivityId: String,
        payload: File,
        summary: BaseActivitySummary,
        row: WorkoutUpload
    ): TrackUpdateResult

    /**
     * Brings the header photo of an existing remote activity in line with the local workout, or
     * null when the service has no media API. [photoFile] is null when the photo was removed.
     */
    fun syncPhoto(
        context: Context,
        remoteActivityId: String,
        photoFile: File?,
        previousMediaId: Int?
    ): WorkoutUploader.PhotoSyncResult?
}

/** Every supported upload target. */
object WorkoutUploadTargets {
    val ALL: List<WorkoutUploadTarget> = listOf(EndurainUploadTarget, WandererUploadTarget)

    /** Targets the user has both enabled and finished setting up. */
    fun active(context: Context, prefs: GBPrefs): List<WorkoutUploadTarget> =
        ALL.filter { it.isAutoUploadEnabled(prefs) && it.isLoggedIn(context) }
}

object EndurainUploadTarget : WorkoutUploadTarget {
    override val service = WorkoutUploadStore.SERVICE_ENDURAIN
    override val nameRes = R.string.pref_category_endurain
    override val format = WorkoutPayloadFormat.FIT

    // A FIT can be synthesized from the summary alone, so a trackless workout still uploads.
    override val requiresTrack = false

    override val failureNotificationId = 4711

    override fun isLoggedIn(context: Context) = EndurainTokenManager(context).isLoggedIn()

    override fun isAutoUploadEnabled(prefs: GBPrefs) =
        prefs.getBoolean(GBPrefs.ENDURAIN_AUTO_UPLOAD_ENABLED, false)

    override fun failureMessage(context: Context, reason: String): String =
        context.getString(R.string.auto_upload_failed_endurain, reason)

    override fun activityUrl(context: Context, remoteActivityId: String): String? {
        val server = GBApplication.getPrefs().preferences
            .getString(WorkoutUploader.PREF_ENDURAIN_SERVER, null) ?: return null
        return "${server.trimEnd('/')}/activity/$remoteActivityId"
    }

    override fun upload(context: Context, summary: BaseActivitySummary, payload: File) =
        WorkoutUploader.uploadToEndurainBlocking(context, summary, payload)

    override fun updateMetadata(context: Context, remoteActivityId: String, summary: BaseActivitySummary) =
        WorkoutUploader.updateEndurainMetadataBlocking(context, remoteActivityId, summary)

    /**
     * Endurain can edit an activity's metadata but never its track, so the only way to push a
     * changed track is to delete the activity and upload it again under a new id.
     *
     * That is allowed unconditionally only when it is purely additive: the activity was uploaded
     * without a track and now has one, which is the case of a workout synced from a watch that
     * records none, with a GPX attached afterwards. Any other change re-creates an activity the
     * user may have been looking at for a while, so it needs their explicit consent.
     */
    override fun updateTrack(
        context: Context,
        remoteActivityId: String,
        payload: File,
        summary: BaseActivitySummary,
        row: WorkoutUpload
    ): TrackUpdateResult {
        val trackAdded = row.hadTrack != true && WorkoutUploader.summaryHasTrack(summary)
        val allowed = trackAdded || GBApplication.getPrefs()
            .getBoolean(GBPrefs.ENDURAIN_REPLACE_ON_TRACK_CHANGE, false)
        if (!allowed) {
            return TrackUpdateResult(success = false, unsupported = true)
        }

        val (result, refusal) = WorkoutUploader.recreateEndurainActivityBlocking(
            context, summary, remoteActivityId, payload
        )
        if (result != null) {
            return TrackUpdateResult(
                success = true,
                newRemoteActivityId = result.remoteActivityId,
                newPhotoMediaId = result.photoMediaId
            )
        }
        return TrackUpdateResult(
            success = false,
            refusal = when (refusal) {
                WorkoutUploader.RecreateRefusal.REMOTE_EDITED ->
                    context.getString(R.string.auto_upload_endurain_remote_edited)

                else -> null
            }
        )
    }

    override fun syncPhoto(
        context: Context,
        remoteActivityId: String,
        photoFile: File?,
        previousMediaId: Int?
    ) = WorkoutUploader.syncEndurainPhotoBlocking(context, remoteActivityId, photoFile, previousMediaId)
}

object WandererUploadTarget : WorkoutUploadTarget {
    override val service = WorkoutUploadStore.SERVICE_WANDERER
    override val nameRes = R.string.pref_category_wanderer
    override val format = WorkoutPayloadFormat.GPX

    // Wanderer stores trails, so there is nothing to upload without a track.
    override val requiresTrack = true

    override val failureNotificationId = 4712

    override fun isLoggedIn(context: Context) = WandererTokenManager(context).isLoggedIn()

    override fun isAutoUploadEnabled(prefs: GBPrefs) =
        prefs.getBoolean(GBPrefs.WANDERER_AUTO_UPLOAD_ENABLED, false)

    override fun failureMessage(context: Context, reason: String): String =
        context.getString(R.string.auto_upload_failed_wanderer, reason)

    /**
     * A trail page is namespaced by the handle of the account that owns it, and a handle that does
     * not resolve answers 500 rather than falling back to the trail, so there is no link to give
     * until the handle is known.
     */
    override fun activityUrl(context: Context, remoteActivityId: String): String? {
        val server = GBApplication.getPrefs().preferences
            .getString(WorkoutUploader.PREF_WANDERER_SERVER, null) ?: return null
        val handle = WandererTokenManager(context).getHandle() ?: return null
        return "${server.trimEnd('/')}/trail/view/@$handle/$remoteActivityId"
    }

    override fun upload(context: Context, summary: BaseActivitySummary, payload: File) =
        WorkoutUploader.uploadToWandererBlocking(context, payload)

    // Wanderer infers a trail's name and type from the uploaded file.
    override fun updateMetadata(context: Context, remoteActivityId: String, summary: BaseActivitySummary) = true

    // Wanderer replaces the track on the existing trail, so the id and the user's edits survive.
    override fun updateTrack(
        context: Context,
        remoteActivityId: String,
        payload: File,
        summary: BaseActivitySummary,
        row: WorkoutUpload
    ) = TrackUpdateResult(
        success = WorkoutUploader.updateWandererTrackBlocking(context, remoteActivityId, payload, summary)
    )

    override fun syncPhoto(
        context: Context,
        remoteActivityId: String,
        photoFile: File?,
        previousMediaId: Int?
    ): WorkoutUploader.PhotoSyncResult? = null
}
