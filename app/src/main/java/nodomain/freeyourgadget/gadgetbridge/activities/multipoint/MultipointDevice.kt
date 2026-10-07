package nodomain.freeyourgadget.gadgetbridge.activities.multipoint

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MultipointDevice @JvmOverloads constructor(
    val address: String,
    val name: String?,
    val isConnected: Boolean = false,
    val isActive: Boolean = false,
    val canForget: Boolean = false,
) : Parcelable
