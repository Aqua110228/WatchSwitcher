package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.InternetHelperSingleton
import nodomain.freeyourgadget.internethelper.aidl.http.HttpRequest
import nodomain.freeyourgadget.internethelper.aidl.http.HttpResponse
import nodomain.freeyourgadget.internethelper.aidl.http.IHttpCallback
import java.io.IOException

class InternetHelperDebugFragment : AbstractDebugFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_internethelper, rootKey)

        refreshVersion()

        onClick(PREF_SEND) { sendRequest() }
    }

    override fun onResume() {
        super.onResume()
        refreshVersion()
    }

    private fun refreshVersion() {
        val versionPref = findPreference<Preference>(PREF_VERSION) ?: return
        Thread {
            InternetHelperSingleton.ensureInternetHelperBoundBlocking()
            val version = InternetHelperSingleton.getHttpService()?.version()
            activity?.runOnUiThread {
                versionPref.summary = version?.toString() ?: "Internet Helper not available"
            }
        }.start()
    }

    private fun sendRequest() {
        val url = findPreference<EditTextPreference>(PREF_URL)?.text?.trim()
        if (url.isNullOrEmpty()) {
            GB.toast(requireContext(), "URL not set", Toast.LENGTH_LONG, GB.ERROR)
            return
        }

        var httpService = InternetHelperSingleton.getHttpService()
        if (httpService == null) {
            InternetHelperSingleton.ensureInternetHelperBoundBlocking()
            httpService = InternetHelperSingleton.getHttpService()
        }
        if (httpService == null) {
            GB.toast(requireContext(), "HTTP Service not found", Toast.LENGTH_LONG, GB.ERROR)
            return
        }

        val methodName = findPreference<ListPreference>(PREF_METHOD)?.value ?: HttpRequest.Method.GET.name
        val method = runCatching { HttpRequest.Method.valueOf(methodName) }.getOrElse { HttpRequest.Method.GET }

        val sendPref = findPreference<Preference>(PREF_SEND)
        val activity = requireActivity()
        sendPref?.summary = "Sending..."

        val callback = object : IHttpCallback.Stub() {
            override fun onResponse(response: HttpResponse) {
                try {
                    val bodyText = response.body?.let { readBody(it) }?.toString(Charsets.UTF_8)
                    activity.runOnUiThread {
                        val status = "Status: ${response.status}"
                        sendPref?.summary = if (bodyText.isNullOrBlank()) {
                            status
                        } else {
                            "$status\n${bodyText.take(MAX_BODY_CHARS)}"
                        }
                        GB.toast(activity.applicationContext, status, Toast.LENGTH_LONG, GB.INFO)
                    }
                } catch (e: IOException) {
                    activity.runOnUiThread {
                        sendPref?.summary = e.localizedMessage
                        GB.toast(activity.applicationContext, e.localizedMessage, Toast.LENGTH_LONG, GB.ERROR)
                    }
                }
            }

            override fun onException(message: String?) {
                val msg = message ?: "Request failed"
                activity.runOnUiThread {
                    sendPref?.summary = msg
                    GB.toast(activity.applicationContext, msg, Toast.LENGTH_LONG, GB.ERROR)
                }
            }
        }

        Thread {
            try {
                httpService.send(HttpRequest(url, method), callback)
            } catch (e: RemoteException) {
                activity.runOnUiThread {
                    sendPref?.summary = e.localizedMessage
                    GB.toast(activity.applicationContext, e.localizedMessage, Toast.LENGTH_LONG, GB.ERROR)
                }
            }
        }.start()
    }

    private fun readBody(fd: ParcelFileDescriptor): ByteArray {
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            val buffer = ByteArray(1024)
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read == -1) break
                offset += read
            }
            return buffer.copyOf(offset)
        }
    }

    companion object {
        private const val MAX_BODY_CHARS = 512

        private const val PREF_VERSION = "pref_debug_internethelper_version"
        private const val PREF_URL = "pref_debug_internethelper_url"
        private const val PREF_METHOD = "pref_debug_internethelper_method"
        private const val PREF_SEND = "pref_debug_internethelper_send"
    }
}
