package nodomain.freeyourgadget.gadgetbridge.util.kotlin

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import java.io.Serializable

inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? {
    // https://issuetracker.google.com/issues/242048899
    return BundleCompat.getParcelable(this, key, T::class.java)
}

inline fun <reified T : Serializable> Bundle.getSerializableCompat(key: String): T? {
    return BundleCompat.getSerializable(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Bundle.getParcelableArrayListCompat(key: String): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList(this, key, T::class.java)
}
