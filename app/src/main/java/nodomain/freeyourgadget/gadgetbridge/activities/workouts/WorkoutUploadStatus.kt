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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload

/**
 * What one workout's upload state looks like across every service at once, which is all the
 * workout list has room to show. The per-service detail lives on the workout detail screen.
 */
enum class WorkoutUploadStatus(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int
) {
    /** Every applicable service holds this workout as it currently stands. */
    UPLOADED(R.drawable.ic_public, R.string.workout_upload_status_uploaded),

    /** At least one applicable service holds it and at least one does not. */
    PARTIAL(R.drawable.ic_help_outline, R.string.workout_upload_status_partial),

    /** Nothing has taken it yet, or a local edit has not been pushed out yet. */
    PENDING(R.drawable.ic_access_time, R.string.workout_upload_status_pending),

    /** The last attempt on at least one service failed. */
    FAILED(R.drawable.ic_cancel, R.string.workout_upload_status_failed)
}

/**
 * Overall state of [summary] across [targets], given its [rows] keyed by service, or null when
 * no service applies to the workout and none ever took it, so there is nothing worth showing.
 *
 * An error wins over everything else, because it is the only state the user can act on. Otherwise
 * a service counts as in step only when its stored fingerprint still matches the workout, so an
 * edit made after the upload reads as pending rather than done.
 *
 * [storedSummaryData] lets a caller resolving a whole list pass the value it already read; see
 * [WorkoutUploadStore.storedSummaryData].
 */
@JvmOverloads
fun workoutUploadStatus(
    summary: BaseActivitySummary,
    rows: Map<Int, WorkoutUpload>,
    targets: List<WorkoutUploadTarget>,
    storedSummaryData: String? = null
): WorkoutUploadStatus? {
    val applicable = targets.filter {
        !it.requiresTrack || WorkoutUploader.summaryHasTrack(summary)
    }
    if (applicable.isEmpty() && rows.isEmpty()) {
        return null
    }
    if (rows.values.any { it.status == WorkoutUploadStore.STATUS_FAILED || it.lastError != null }) {
        return WorkoutUploadStatus.FAILED
    }
    val sourceHash = WorkoutUploadStore.sourceHashOf(summary, storedSummaryData)
    val inStep = rows.values.count {
        it.status == WorkoutUploadStore.STATUS_SUCCESS && it.sourceHash == sourceHash
    }
    return when {
        inStep >= applicable.size && inStep > 0 -> WorkoutUploadStatus.UPLOADED
        inStep > 0 -> WorkoutUploadStatus.PARTIAL
        else -> WorkoutUploadStatus.PENDING
    }
}
