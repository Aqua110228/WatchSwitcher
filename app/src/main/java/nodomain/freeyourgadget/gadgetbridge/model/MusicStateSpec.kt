/*  Copyright (C) 2016-2024 Andreas Shimokawa, Avamander, Carsten Pfeiffer,
    Daniele Gobbetti, Steffen Liebergeld, Taavi Eomäe

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
data class MusicStateSpec @JvmOverloads constructor(
    var state: Byte = STATE_UNKNOWN.toByte(),
    /**
     * Position of the current media in seconds
     */
    var position: Int = STATE_UNKNOWN, //FIXME: this looks wrong
    /**
     * Speed of playback, usually 0 or 100 (full speed)
     */
    var playRate: Int = STATE_UNKNOWN,
    var shuffle: Byte = STATE_UNKNOWN.toByte(),
    var repeat: Byte = STATE_UNKNOWN.toByte()
) : Parcelable {
    constructor(old: MusicStateSpec) : this(
        state = old.state,
        position = old.position,
        playRate = old.playRate,
        shuffle = old.shuffle,
        repeat = old.repeat
    )

    companion object {
        const val STATE_UNKNOWN: Int = -1

        const val STATE_PLAYING: Int = 0
        const val STATE_PAUSED: Int = 1
        const val STATE_STOPPED: Int = 2

        const val STATE_SHUFFLE_ENABLED: Int = 1
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is MusicStateSpec) return false
        return this.state == other.state &&
                kotlin.math.abs(this.position - other.position) <= 2 &&
                this.playRate == other.playRate &&
                this.shuffle == other.shuffle &&
                this.repeat == other.repeat
    }

    override fun hashCode(): Int {
        var result = state.toInt()
//ignore the position -- it is taken into account in equals()
//result = 31 * result + position;
        result = 31 * result + playRate
        result = 31 * result + shuffle.toInt()
        result = 31 * result + repeat.toInt()
        return result
    }

    override fun toString(): String {
        return "MusicStateSpec{state=$state, position=$position, playRate=$playRate, shuffle=$shuffle, repeat=$repeat}"
    }
}
