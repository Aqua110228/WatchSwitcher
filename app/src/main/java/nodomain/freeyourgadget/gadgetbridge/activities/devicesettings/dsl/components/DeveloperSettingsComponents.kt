package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope

fun DeviceSettingsScope.keepActivityDataOnDevice() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_KEEP_ACTIVITY_DATA_ON_DEVICE,
        title = R.string.pref_title_keep_data_on_device,
        summary = R.string.pref_summary_keep_data_on_device,
        icon = R.drawable.ic_keep,
        defaultValue = false,
        connectedOnly = false,
    )
}

fun DeviceSettingsScope.fetchUnknownFiles() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_FETCH_UNKNOWN_FILES,
        title = R.string.pref_fetch_unknown_files_title,
        summary = R.string.pref_fetch_unknown_files_summary,
        icon = R.drawable.ic_downloading,
        defaultValue = false,
        connectedOnly = false,
    )
}

fun DeviceSettingsScope.installUnsupportedFiles() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_INSTALL_UNSUPPORTED_FILES,
        title = R.string.pref_install_unsupported_files_title,
        summary = R.string.pref_install_unsupported_files_summary,
        icon = R.drawable.ic_arrow_upload_progress,
        defaultValue = false,
        connectedOnly = false,
    )
}

fun DeviceSettingsScope.newSyncProtocol() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_NEW_SYNC_PROTOCOL,
        title = R.string.pref_new_sync_protocol_title,
        summary = R.string.pref_new_sync_protocol_summary,
        icon = R.drawable.ic_refresh,
        defaultValue = false,
        connectedOnly = false,
    )
}
