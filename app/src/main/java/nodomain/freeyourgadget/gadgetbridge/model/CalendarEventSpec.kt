/*  Copyright (C) 2016-2024 Andreas Shimokawa, Daniele Gobbetti, Gabriele
    Monaco

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
package nodomain.freeyourgadget.gadgetbridge.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.util.RtlUtils
import nodomain.freeyourgadget.gadgetbridge.util.language.Transliterator

@Parcelize
data class CalendarEventSpec @JvmOverloads constructor(
    var type: Byte = TYPE_UNKNOWN,
    var id: Long = 0,
    var eventId: Long = 0,
    var timestamp: Int = 0,
    var durationInSeconds: Int = 0,
    var title: String? = null,
    var description: String? = null,
    var location: String? = null,
    var calName: String? = null,
    var calendarColor: Int = 0,
    var color: Int = 0,
    var allDay: Boolean = false,
    var reminders: ArrayList<Long>? = null,
    var status: Int = 0,
    var attendingStatus: Int = 0
) : DeviceTextAdaptable<CalendarEventSpec>, Parcelable {
    override fun transliterated(
        deviceSupport: DeviceSupport,
        transliterator: Transliterator?
    ): CalendarEventSpec = copy(
            title = transform(title, deviceSupport, transliterator),
            description = transform(description, deviceSupport, transliterator),
            location = transform(location, deviceSupport, transliterator),
            calName = transform(calName, deviceSupport, transliterator)
        )

    override fun withRtlFix(): CalendarEventSpec {
        if (!RtlUtils.rtlSupport()) return this
        return copy(
            title = title?.let(RtlUtils::fixRtl),
            description = description?.let(RtlUtils::fixRtl),
            location = location?.let(RtlUtils::fixRtl),
            calName = calName?.let(RtlUtils::fixRtl)
        )
    }

    companion object {
        const val TYPE_UNKNOWN: Byte = 0
        const val TYPE_SUNRISE: Byte = 1
        const val TYPE_SUNSET: Byte = 2
    }

}