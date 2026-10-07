/*  Copyright (C) 2016-2024 Andreas Shimokawa, Davis Mosenkovs, Dmitry
    Markin, mvn23

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
import nodomain.freeyourgadget.gadgetbridge.util.GBToStringBuilder
import nodomain.freeyourgadget.gadgetbridge.util.RtlUtils
import nodomain.freeyourgadget.gadgetbridge.util.language.Transliterator

@Parcelize
data class CallSpec(
    var number: String? = null,
    var name: String? = null,
    var sourceName: String? = null,
    var sourceAppId: String? = null,
    var key: String? = null,
    var channelId: String? = null,
    var category: String? = null,
    var isVoip: Boolean = false,
    var command: Int = CALL_UNDEFINED,
    var dndSuppressed: Int = 0
) : DeviceTextAdaptable<CallSpec>, Parcelable {
    companion object {
        // TODO: Migrate all usages to the enum..
        const val CALL_UNDEFINED: Int = 0
        const val CALL_ACCEPT: Int = 1
        const val CALL_INCOMING: Int = 2
        const val CALL_OUTGOING: Int = 3
        const val CALL_REJECT: Int = 4
        const val CALL_START: Int = 5
        const val CALL_END: Int = 6
    }

    enum class Command {
        UNDEFINED,
        ACCEPT,
        INCOMING,
        OUTGOING,
        REJECT,
        START,
        END
    }

    override fun toString(): String {
        val tsb = GBToStringBuilder(this)
        tsb.append("command", Command.entries[command])
        tsb.append("number", number)
        tsb.append("name", name)
        tsb.append("sourceName", sourceName)
        tsb.append("sourceAppId", sourceAppId)
        tsb.append("key", key)
        tsb.append("channelId", channelId)
        tsb.append("category", category)
        if (isVoip) {
            tsb.append("isVoip", isVoip)
        }
        if (dndSuppressed != 0) {
            tsb.append("dndSuppressed", dndSuppressed)
        }
        return tsb.toString()
    }

    override fun transliterated(
        deviceSupport: DeviceSupport,
        transliterator: Transliterator?
    ): CallSpec = copy(
            name = transform(name, deviceSupport, transliterator),
            sourceName = transform(sourceName, deviceSupport, transliterator)
        )

    override fun withRtlFix(): CallSpec {
        if (!RtlUtils.rtlSupport()) return this
        return copy(
            name = name?.let(RtlUtils::fixRtl),
            sourceName = sourceName?.let(RtlUtils::fixRtl)
        )
    }
}
