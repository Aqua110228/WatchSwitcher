package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityChartsActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB

/**
 * Shared click helpers for widgets.
 */
object WidgetActions {
    /**
     * Opens [ActivityChartsActivity] on [chart] for whichever of [config]'s resolved devices the
     * user picks (or the only one, if there's just one). No-op if none are supported.
     */
    fun openChart(
        context: Context,
        config: WidgetConfig,
        widget: GBWidget<*>,
        chart: String,
        @StringRes label: Int,
        mode: String,
        timestamp: Int,
    ) {
        chooseDevice(context, config, widget) { device ->
            val intent = Intent(context, ActivityChartsActivity::class.java)
            intent.putExtra(GBDevice.EXTRA_DEVICE, device)
            intent.putExtra(ActivityChartsActivity.EXTRA_SINGLE_FRAGMENT_NAME, chart)
            intent.putExtra(ActivityChartsActivity.EXTRA_ACTIONBAR_TITLE, label)
            intent.putExtra(ActivityChartsActivity.EXTRA_TIMESTAMP, timestamp)
            intent.putExtra(ActivityChartsActivity.EXTRA_MODE, mode)
            context.startActivity(intent)
        }
    }

    /**
     * Picks a device from [config]'s resolved devices, prompting if there's more than one.
     */
    fun chooseDevice(
        context: Context,
        config: WidgetConfig,
        widget: GBWidget<*>,
        onChosen: (GBDevice) -> Unit,
    ) {
        val devices = config.resolveDevices(widget)

        if (devices.size == 1) {
            onChosen(devices[0])
            return
        }

        if (devices.isEmpty()) {
            GB.toast(GBApplication.getContext(), R.string.no_supported_devices_found, Toast.LENGTH_LONG, GB.WARN)
            return
        }

        val deviceNames = devices.map { it.aliasOrName }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setCancelable(true)
            .setTitle(R.string.choose_device)
            .setItems(deviceNames) { _, which -> onChosen(devices[which]) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }
}
