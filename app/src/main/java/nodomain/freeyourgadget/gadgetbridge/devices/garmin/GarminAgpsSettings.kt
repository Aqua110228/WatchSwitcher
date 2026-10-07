package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.agps.GarminAgpsStatus
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import androidx.core.content.edit

@SuppressLint("ConstantLocale")
private val SDF = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/**
 * Adds the AGPS settings: the folder that holds the downloaded files, and one group per URL the
 * device asked for, with the local file, its status and the time of the last update.
 */
fun DeviceSettingsScope.garminAgps(device: GBDevice) {
    val prefs = Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.address))
    val urls = prefs.getList(GarminPreferences.PREF_AGPS_KNOWN_URLS, emptyList<String>(), "\n")
    if (urls.isEmpty()) {
        return
    }

    category(
        key = DeviceSettingsPreferenceConst.PREF_HEADER_AGPS,
        title = R.string.pref_agps_header,
        icon = R.drawable.ic_gps_edit,
    ) {
        info(
            key = "pref_garmin_agps_help",
            summary = R.string.pref_garmin_agps_help,
            iconSpaceReserved = false,
            connectedOnly = false,
        )

        folderPicker(
            key = GarminPreferences.PREF_GARMIN_AGPS_FOLDER,
            title = R.string.folder,
            icon = R.drawable.ic_folder,
            connectedOnly = false,
        )
    }

    urls.forEachIndexed { index, url -> agpsUrl(device, index + 1, url) }
}

private fun DeviceSettingsScope.agpsUrl(device: GBDevice, index: Int, url: String) {
    category(
        key = "pref_agps_url_header_$index",
        titleText = GBApplication.getContext().getString(R.string.garmin_agps_url_i, index),
        iconSpaceReserved = false,
    ) {
        action(
            key = "pref_garmin_agps_url_$index",
            title = R.string.url,
            icon = R.drawable.ic_link,
            summaryProvider = { _, _ -> url },
            connectedOnly = false,
        ) { context, _ ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.url), url))
            GB.toast(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT, GB.INFO)
            true
        }

        action(
            key = GarminPreferences.agpsFilename(url),
            title = R.string.garmin_agps_local_file,
            icon = R.drawable.ic_file_open,
            summaryProvider = { _, prefs -> prefs.getString(GarminPreferences.agpsFilename(url), "") },
            connectedOnly = false,
        ) { context, _ ->
            selectAgpsFile(context, device, url)
            true
        }

        info(
            key = GarminPreferences.agpsStatus(url),
            title = R.string.status,
            icon = R.drawable.ic_health,
            summaryProvider = { context, prefs -> agpsStatus(context, prefs, url) },
            connectedOnly = false,
        )

        info(
            key = GarminPreferences.agpsUpdateTime(url),
            title = R.string.pref_agps_update_time,
            icon = R.drawable.ic_calendar_today,
            summaryProvider = { context, prefs -> agpsUpdateTime(context, prefs, url) },
            connectedOnly = false,
        )
    }
}

private fun agpsStatus(context: Context, prefs: Prefs, url: String): String {
    val filename = prefs.getString(GarminPreferences.agpsFilename(url), "")
    if (filename.isNullOrEmpty()) {
        return ""
    }
    val folderUri = prefs.getString(GarminPreferences.PREF_GARMIN_AGPS_FOLDER, "")
    if (folderUri.isNullOrEmpty()) {
        return ""
    }
    val folder = DocumentFile.fromTreeUri(context, folderUri.toUri()) ?: return ""

    val localFile = folder.findFile(filename)
    val status = if (localFile != null && localFile.isFile && localFile.canRead()) {
        if (localFile.lastModified() < prefs.getLong(GarminPreferences.agpsUpdateTime(url), 0L)) {
            GarminAgpsStatus.CURRENT
        } else {
            GarminAgpsStatus.PENDING
        }
    } else {
        GarminAgpsStatus.MISSING
    }
    return context.getString(status.text)
}

private fun agpsUpdateTime(context: Context, prefs: Prefs, url: String): String {
    val ts = prefs.getLong(GarminPreferences.agpsUpdateTime(url), 0L)
    if (ts <= 0) {
        return context.getString(R.string.unknown)
    }
    return String.format(
        "%s (%s)",
        SDF.format(Date(ts)),
        DateTimeUtils.formatDurationHoursMinutes(System.currentTimeMillis() - ts, TimeUnit.MILLISECONDS)
    )
}

private fun selectAgpsFile(context: Context, device: GBDevice, url: String) {
    val sharedPrefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
    val prefs = Prefs(sharedPrefs)

    val folderUri = prefs.getString(GarminPreferences.PREF_GARMIN_AGPS_FOLDER, "")
    if (folderUri.isNullOrEmpty()) {
        GB.toast(context.getString(R.string.no_folder_selected), Toast.LENGTH_SHORT, GB.INFO)
        return
    }

    val folder = DocumentFile.fromTreeUri(context, folderUri.toUri())
    val documentFiles = folder?.listFiles() ?: emptyArray()
    if (documentFiles.isEmpty()) {
        GB.toast(context.getString(R.string.folder_is_empty), Toast.LENGTH_SHORT, GB.INFO)
        return
    }

    val files = arrayOfNulls<String>(documentFiles.size + 1)
    files[0] = context.getString(R.string.none)
    val selectedFile = prefs.getString(GarminPreferences.agpsFilename(url), "")
    var checkedItem = 0
    for (j in documentFiles.indices) {
        files[j + 1] = documentFiles[j].name
        if (selectedFile == files[j + 1]) {
            checkedItem = j + 1
        }
    }

    var selectedIdx = 0
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.garmin_agps_local_file)
        .setSingleChoiceItems(files, checkedItem) { _, which -> selectedIdx = which }
        .setPositiveButton(android.R.string.ok) { _, _ ->
            sharedPrefs.edit {
                putString(GarminPreferences.agpsFilename(url), if (selectedIdx > 0) files[selectedIdx] else null)
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
