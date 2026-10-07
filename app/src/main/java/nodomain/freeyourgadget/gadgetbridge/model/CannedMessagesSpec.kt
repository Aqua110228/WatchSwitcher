/*  Copyright (C) 2016-2024 Andreas Shimokawa

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

@Parcelize
data class CannedMessagesSpec @JvmOverloads constructor(
    var type: Int = TYPE_GENERIC,
    var cannedMessages: Array<String>? = null
) : Parcelable {
    companion object {
        const val TYPE_GENERIC: Int = 0
        const val TYPE_REJECTEDCALLS: Int = 1
        const val TYPE_NEWSMS: Int = 2
    }
}