package nodomain.freeyourgadget.gadgetbridge.util.kotlin

import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.io.Serializable

fun Intent.getDevice(): GBDevice? {
    return getParcelableCompat(GBDevice.EXTRA_DEVICE)
}

inline fun <reified T : Parcelable> Intent.getParcelableCompat(key: String): T? {
    // https://issuetracker.google.com/issues/242048899
    return IntentCompat.getParcelableExtra(this, key, T::class.java)
}

inline fun <reified T : Serializable> Intent.getSerializableCompat(key: String): T? {
    return IntentCompat.getSerializableExtra(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Intent.getParcelableArrayListCompat(key: String): ArrayList<T>? {
    return IntentCompat.getParcelableArrayListExtra(this, key, T::class.java)
}
