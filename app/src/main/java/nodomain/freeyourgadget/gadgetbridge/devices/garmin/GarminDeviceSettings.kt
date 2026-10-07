package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.fetchUnknownFiles
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.highMtu
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.installUnsupportedFiles
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.keepActivityDataOnDevice
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.newSyncProtocol
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.screen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.sendAppNotifications
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.timeSync
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.workoutSendGpsToBand
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.WorkoutTemplateListActivity
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.actions.GarminSendWaypointActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HydrationContainer
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitAsyncProcessor
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.notifications.GBProgressNotification
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToLong

private val LOG = LoggerFactory.getLogger("GarminDeviceSettings")

fun garminDeviceSettings(
    device: GBDevice,
    coordinator: GarminCoordinator,
): DeviceSettingsSpec = deviceSettings {
    if (coordinator.supports(device, GarminCapability.REALTIME_SETTINGS)) {
        externalSettings(
            key = GarminPreferences.PREF_GARMIN_REALTIME_SETTINGS,
            title = R.string.realtime_settings,
            icon = R.drawable.ic_settings,
            activityClass = GarminRealtimeSettingsActivity::class.java,
        )
    }

    if (coordinator.supports(device, GarminCapability.WORKOUT_DOWNLOAD)) {
        externalSettings(
            key = DeviceSettingsPreferenceConst.PREF_WORKOUT_TEMPLATES,
            title = R.string.pref_workout_templates_title,
            summary = R.string.pref_workout_templates_summary,
            icon = R.drawable.ic_activity_unknown_small,
            activityClass = WorkoutTemplateListActivity::class.java,
        )
    }

    if (coordinator.supportsHydration(device)) {
        garminHydration()
    }

    if (coordinator.supportsCalendarEvents(device)) {
        xmlScreen(
            DeviceSpecificSettingsScreen.CALENDAR,
            R.xml.devicesettings_header_calendar,
            R.xml.devicesettings_sync_calendar,
            connectedOnly = false,
        )
    }

    if (coordinator.supportsSendWaypoint(device)) {
        action(
            key = GarminPreferences.PREF_GARMIN_SEND_WAYPOINT,
            title = R.string.activity_send_waypoint_titel,
            icon = R.drawable.ic_add_location_alt_24px,
            connectedOnly = false,
        ) { context, gbDevice ->
            GarminSendWaypointActivity.handlePreferenceClick(context, gbDevice)
        }
    }

    val notifications = mutableListOf(R.xml.devicesettings_transliteration)
    if (coordinator.getCannedRepliesSlotCount(device) > 0) {
        notifications.add(R.xml.devicesettings_canned_reply_16)
        notifications.add(R.xml.devicesettings_canned_dismisscall_16)
    }
    if (coordinator.getContactsSlotCount(device) > 0) {
        notifications.add(R.xml.devicesettings_contacts)
    }
    if (coordinator.supportsNavigation(device)) {
        notifications.add(R.xml.devicesettings_navigation_notifications)
    }
    screen(
        DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
        icon = R.drawable.ic_notifications,
        xmlSubScreens = notifications,
    ) {
        sendAppNotifications()
    }

    screen(DeviceSpecificSettingsScreen.LOCATION, icon = R.drawable.ic_gps_location) {
        workoutSendGpsToBand()
        if (coordinator.supportsAgpsUpdates(device)) {
            garminAgps(device)
        }
    }

    screen(DeviceSpecificSettingsScreen.DATE_TIME, icon = R.drawable.ic_timer) {
        timeSync()
    }

    screen(DeviceSpecificSettingsScreen.CONNECTION, icon = R.drawable.ic_mtu) {
        highMtu()
    }

    if (GBApplication.hasInternetAccess()) {
        xmlScreen(
            DeviceSpecificSettingsScreen.INTERNET,
            R.xml.devicesettings_device_internet_access,
            R.xml.devicesettings_device_internet_firewall,
            R.xml.devicesettings_device_internet_firewall_blacklisted_domains,
            connectedOnly = false,
        )
    }

    screen(DeviceSpecificSettingsScreen.DEVELOPER, icon = R.drawable.ic_developer_mode) {
        filePicker(
            key = DeviceSettingsPreferenceConst.PREF_IMPORT_ACTIVITY_FILES,
            title = R.string.pref_import_activity_files_title,
            summary = R.string.pref_import_activity_files_summary,
            icon = R.drawable.ic_file_upload,
            connectedOnly = false,
        ) { context, _, uris ->
            importActivityFiles(context, device, uris)
        }

        action(
            key = DeviceSettingsPreferenceConst.PREF_REPROCESS_ACTIVITY_FILES,
            title = R.string.pref_reprocess_activity_files_title,
            summary = R.string.pref_reprocess_activity_files_summary,
            icon = R.drawable.ic_refresh,
            connectedOnly = false,
        ) { context, _ ->
            parseAllFitFilesFromStorage(context, device)
            true
        }

        keepActivityDataOnDevice()
        fetchUnknownFiles()
        installUnsupportedFiles()
        newSyncProtocol()

        switchSetting(
            key = GarminPreferences.PREF_GARMIN_LEGACY_SYNC_FLUSH,
            title = R.string.garmin_legacy_sync_flush_title,
            summary = R.string.garmin_legacy_sync_flush_summary,
            icon = R.drawable.ic_download,
            defaultValue = true,
            dependency = DeviceSettingsPreferenceConst.PREF_NEW_SYNC_PROTOCOL,
            connectedOnly = false,
        )

        switchSetting(
            key = GarminPreferences.PREF_GARMIN_MLR,
            title = R.string.garmin_mlr_protocol,
            icon = R.drawable.ic_bolt,
            defaultValue = true,
            connectedOnly = false,
        )

        switchSetting(
            key = GarminPreferences.PREF_GARMIN_EXPLORE_SYNC,
            title = R.string.feature_exploresync_title,
            summary = R.string.feature_exploresync_summary,
            icon = R.drawable.ic_bolt,
            defaultValue = false,
            connectedOnly = false,
        )
    }
}

private fun DeviceSettingsScope.garminHydration() {
    screen(
        key = "pref_screen_garmin_hydration",
        title = R.string.pref_header_hydration,
        icon = R.drawable.ic_drink,
    ) {
        enumList<HydrationUnit>(
            key = DeviceSettingsPreferenceConst.PREF_HYDRATION_UNIT,
            title = R.string.pref_title_unit_system,
            icon = R.drawable.ic_straighten,
            defaultValue = HydrationUnit.MILLILITER,
            connectedOnly = false,
        )

        for (container in 1..HydrationContainer.COUNT) {
            category(
                key = "pref_header_hydration_container_$container",
                titleText = GBApplication.getContext().getString(R.string.pref_hydration_container_i, container),
                iconSpaceReserved = false,
            ) {
                text(
                    key = HydrationContainer.volumeKey(container),
                    title = R.string.pref_hydration_container_volume,
                    icon = R.drawable.ic_drink,
                    defaultValue = HydrationContainer.defaultVolume(container).toString(),
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
                    connectedOnly = false,
                )

                enumList<HydrationUnit>(
                    key = HydrationContainer.unitKey(container),
                    title = R.string.workout_field_unit,
                    icon = R.drawable.ic_straighten,
                    defaultValue = HydrationUnit.MILLILITER,
                    connectedOnly = false,
                    onValueChange = { preference, oldUnit, newUnit ->
                        convertContainerVolume(preference, container, oldUnit, newUnit)
                    },
                )
            }
        }
    }
}

private fun convertContainerVolume(
    preference: Preference,
    container: Int,
    oldUnit: HydrationUnit,
    newUnit: HydrationUnit
) {
    val volumePreference = preference.preferenceManager.findPreference<EditTextPreference>(
        HydrationContainer.volumeKey(container)
    ) ?: return
    val volume = volumePreference.text?.toDoubleOrNull() ?: return
    val sharedPreferences = preference.sharedPreferences ?: return

    // The stored volume in mL is only valid if it still rounds to the current volume.
    val storedMl = sharedPreferences.getString(HydrationContainer.volumeMlKey(container), null)?.toDoubleOrNull()
    val volumeMl = if (storedMl != null && roundVolume(storedMl / oldUnit.ml) == volume) {
        storedMl
    } else {
        volume * oldUnit.ml
    }
    val converted = roundVolume(volumeMl / newUnit.ml)

    LOG.debug(
        "Converting container {} volume from {} {} ({} mL) to {} {}",
        container,
        volume,
        oldUnit,
        volumeMl,
        converted,
        newUnit
    )

    sharedPreferences.edit {
        putString(HydrationContainer.volumeMlKey(container), volumeMl.toString())
    }
    volumePreference.text = converted.toLong().toString()
}

private fun roundVolume(volume: Double): Double = max(1.0, volume.roundToLong().toDouble())

private fun importActivityFiles(context: Context, device: GBDevice, uris: List<Uri>) {
    LOG.info("Files to import: {}", uris)

    val filesToProcess = mutableListOf<File>()
    for (uri in uris) {
        try {
            val file = File.createTempFile("activity-files-import", ".bin", context.cacheDir)
            file.deleteOnExit()
            FileUtils.copyURItoFile(context, uri, file)
            filesToProcess.add(file)
        } catch (e: IOException) {
            LOG.error("Failed to create temp file for activity file", e)
        }
    }

    if (filesToProcess.isEmpty()) {
        return
    }

    val transferNotification = GBProgressNotification(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
    transferNotification.start(R.string.busy_task_processing_files, 0, filesToProcess.size.toLong())

    FitAsyncProcessor(context, device).process(filesToProcess, false, object : FitAsyncProcessor.Callback {
        override fun onProgress(i: Int) {
            transferNotification.setTotalProgress(i.toLong())
        }

        override fun onFinish() {
            transferNotification.finish()
            GB.toast("Parsed ${filesToProcess.size} files", Toast.LENGTH_SHORT, GB.INFO)
            device.sendDeviceUpdateIntent(context)
        }
    })
}

private val PARSING_FROM_STORAGE = AtomicBoolean(false)

private fun parseAllFitFilesFromStorage(context: Context, device: GBDevice) {
    if (!PARSING_FROM_STORAGE.compareAndSet(false, true)) {
        GB.toast(context, "Already parsing!", Toast.LENGTH_LONG, GB.ERROR)
        return
    }

    LOG.info("Parsing all fit files from storage")

    val fitFiles: List<File>
    try {
        val exportDir = device.deviceCoordinator.getWritableExportDirectory(device, true)

        if (!exportDir.exists() || !exportDir.isDirectory) {
            LOG.error("export directory {} not found", exportDir)
            GB.toast(context, "export directory $exportDir not found", Toast.LENGTH_LONG, GB.ERROR)
            PARSING_FROM_STORAGE.set(false)
            return
        }

        fitFiles = FileUtils.listRecursive(exportDir) { _, name -> name.endsWith(".fit") }
        if (fitFiles.isEmpty()) {
            LOG.error("No fit files found in {}", exportDir)
            GB.toast(context, "No fit files found in $exportDir", Toast.LENGTH_LONG, GB.ERROR)
            PARSING_FROM_STORAGE.set(false)
            return
        }
    } catch (e: Exception) {
        LOG.error("Failed to parse from storage", e)
        GB.toast(context, "Failed to parse from storage", Toast.LENGTH_LONG, GB.ERROR, e)
        PARSING_FROM_STORAGE.set(false)
        return
    }

    LOG.debug("Got {} fit files to parse", fitFiles.size)

    GB.toast(context, "Check notification for progress", Toast.LENGTH_LONG, GB.INFO)

    val transferNotification = GBProgressNotification(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
    transferNotification.start(R.string.busy_task_processing_files, 0, fitFiles.size.toLong())

    FitAsyncProcessor(context, device).process(fitFiles, true, object : FitAsyncProcessor.Callback {
        override fun onProgress(i: Int) {
            transferNotification.setTotalProgress(i.toLong())
        }

        override fun onFinish() {
            PARSING_FROM_STORAGE.set(false)
            transferNotification.finish()
            GB.signalActivityDataFinish(device)
        }
    })
}
