package nodomain.freeyourgadget.gadgetbridge.activities.wififtp

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSession
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSessionNotification
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSessionRegistry
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperException
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperWifiConnection
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.WifiDisabledException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps the [WifiFtpSession] of a device open while the screen is visible, and shows the progress while it starts.
 * It does nothing for a device without a session.
 */
class WifiFtpSessionScreen(
    private val activity: ComponentActivity,
    private val device: GBDevice,
) : DefaultLifecycleObserver {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var screenHold: WifiFtpSession.Hold? = null
    private var pickerHold: WifiFtpSession.Hold? = null
    private var listenedSession: WifiFtpSession? = null

    private var progressDialog: AlertDialog? = null
    private var progressText: TextView? = null

    // The action to run when the user comes back from the Wi-Fi panel
    private var pendingAction: Runnable? = null
    private var pendingCancel: Runnable? = null

    private val listener = WifiFtpSession.Listener { state -> onStateChanged(state) }

    private val wifiSettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        LOG.debug("Returned from the Wi-Fi settings")
        val action = pendingAction
        val cancel = pendingCancel
        pendingAction = null
        pendingCancel = null
        if (action != null) {
            runWhenReady(action, cancel)
        }
    }

    private val session: WifiFtpSession?
        get() = WifiFtpSessionRegistry.get(device.address)

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val current = session ?: return
        LOG.debug("{} holding the session for {}", activity.javaClass.simpleName, device.address)
        screenHold = current.hold()
        current.addListener(listener)
        listenedSession = current
        onStateChanged(current.state)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (screenHold != null) {
            LOG.debug("{} releasing the session for {}", activity.javaClass.simpleName, device.address)
        }
        screenHold?.release()
        screenHold = null
        listenedSession?.removeListener(listener)
        listenedSession = null
        dismissProgress()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releasePickerHold()
        executor.shutdown()
    }

    /**
     * Keeps the session open while a picker is open. Call [releasePickerHold] with the result.
     */
    fun holdForPicker() {
        LOG.debug("Holding the session for {} for a picker", device.address)
        pickerHold?.release()
        pickerHold = session?.hold()
    }

    fun releasePickerHold() {
        pickerHold?.release()
        pickerHold = null
    }

    /**
     * Runs the action when the session can start. Before the session starts, it explains the connection
     * process to the user. It checks that the Internet Helper is available and that Wi-Fi is on.
     */
    @JvmOverloads
    fun runWhenReady(action: Runnable, onCancel: Runnable? = null) {
        val current = session
        if (current == null || current.state != WifiFtpSession.State.STOPPED) {
            LOG.debug("Running action for {}, state={}", device.address, current?.state)
            action.run()
            return
        }

        val prefs = GBApplication.getPrefs()
        if (prefs.getBoolean(PREF_EXPLAINER_HIDDEN, false)) {
            checkPrerequisites(action, onCancel)
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_wifi_ftp_explainer, null)
        val doNotShowAgain = view.findViewById<CheckBox>(R.id.wifi_ftp_explainer_dont_show_again)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.wifi_ftp_explainer_title)
            .setMessage(R.string.wifi_ftp_explainer)
            .setIcon(R.drawable.ic_wifi_tethering)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (doNotShowAgain.isChecked) {
                    prefs.preferences.edit { putBoolean(PREF_EXPLAINER_HIDDEN, true) }
                }
                checkPrerequisites(action, onCancel)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.run() }
            .setOnCancelListener { onCancel?.run() }
            .show()
    }

    private fun checkPrerequisites(action: Runnable, onCancel: Runnable?) {
        executor.execute {
            val error = try {
                InternetHelperWifiConnection.check()
                null
            } catch (e: InternetHelperException) {
                e
            }

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                when (error) {
                    null -> action.run()
                    is WifiDisabledException -> MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.wifi_ftp_explainer_title)
                        .setMessage(error.message)
                        .setPositiveButton(R.string.wifi_ftp_turn_on_wifi) { _, _ ->
                            pendingAction = action
                            pendingCancel = onCancel
                            wifiSettingsLauncher.launch(wifiSettingsIntent())
                        }
                        .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.run() }
                        .setOnCancelListener { onCancel?.run() }
                        .show()

                    else -> MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.wifi_ftp_explainer_title)
                        .setMessage(error.message)
                        .setPositiveButton(R.string.ok, null)
                        .setOnDismissListener { onCancel?.run() }
                        .show()
                }
            }
        }
    }

    private fun onStateChanged(state: WifiFtpSession.State) {
        val step = when (state) {
            WifiFtpSession.State.STARTING_HOTSPOT -> 1
            WifiFtpSession.State.CONNECTING_WIFI -> 2
            WifiFtpSession.State.STARTING_FTP_SERVER -> 3
            else -> {
                dismissProgress()
                return
            }
        }

        if (progressDialog == null) {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_wifi_ftp_progress, null)
            progressText = view.findViewById(R.id.wifi_ftp_progress_text)
            progressDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.wifi_ftp_explainer_title)
                .setView(view)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel) { _, _ -> session?.close() }
                .show()
        }
        progressText?.text = activity.getString(
            R.string.wifi_ftp_progress_step,
            activity.getString(WifiFtpSessionNotification.stateText(state)),
            step,
            STEP_COUNT
        )
    }

    private fun dismissProgress() {
        progressDialog?.dismiss()
        progressDialog = null
        progressText = null
    }

    private fun wifiSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WifiFtpSessionScreen::class.java)

        private const val PREF_EXPLAINER_HIDDEN = "wifi_ftp_explainer_hidden"
        private const val STEP_COUNT = 3
    }
}
