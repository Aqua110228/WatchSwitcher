/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.PermissionsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.welcome.WelcomeActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.ui.device.DeviceDetailActivity
import nodomain.freeyourgadget.gadgetbridge.ui.discovery.DiscoveryActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.HuaweiIdActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.isHuaweiIdOnboarded
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.setHuaweiIdOnboarded
import nodomain.freeyourgadget.gadgetbridge.ui.settings.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils

/**
 * Main entry point. Material 3 Expressive, Compose-based UI on top of Gadgetbridge's device service.
 */
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleFirstRunAndPermissions()
        GBApplication.deviceService().requestDeviceInfo()

        setContent {
            WatchSwitcherTheme {
                val devices = viewModel.devices.collectAsStateWithLifecycle().value
                HomeScreen(
                    devices = devices,
                    onAddDevice = {
                        startActivity(Intent(this, DiscoveryActivity::class.java))
                    },
                    onOpenDevice = { device: GBDevice ->
                        startActivity(
                            Intent(this, DeviceDetailActivity::class.java)
                                .putExtra(DeviceDetailActivity.EXTRA_DEVICE_ADDRESS, device.address)
                        )
                    },
                    onToggleConnection = { device -> viewModel.toggleConnection(device) },
                    onRefresh = { viewModel.refresh() },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    override fun onResume() {
        super.onResume()
        maybeShowHuaweiIdOnboarding()
    }

    /**
     * Right after the initial welcome flow, offer to set the Huawei account ID once, so pairing
     * a Huawei/Honor watch later works without a factory reset. Shown at most once.
     */
    private fun maybeShowHuaweiIdOnboarding() {
        val prefs = GBApplication.getPrefs()
        if (prefs.getBoolean("first_run", true)) return
        if (isHuaweiIdOnboarded()) return
        setHuaweiIdOnboarded()
        startActivity(HuaweiIdActivity.newIntent(this))
    }

    private fun handleFirstRunAndPermissions() {
        val prefs = GBApplication.getPrefs()
        if (prefs.getBoolean("first_run", true)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
        } else if (prefs.getBoolean("permission_pestering", true) &&
            !PermissionsUtils.checkAllPermissions(this)
        ) {
            startActivity(
                Intent(this, PermissionsActivity::class.java)
                    .putExtra(PermissionsActivity.ARG_SHOW_DO_NOT_ASK_BUTTON, true)
            )
        }
    }
}
