/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

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
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2
import nodomain.freeyourgadget.gadgetbridge.activities.welcome.WelcomeActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.ui.design.AppTheme
import nodomain.freeyourgadget.gadgetbridge.ui.design.DesignSystem
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils

/**
 * Main entry point. Replaces the classic Gadgetbridge ControlCenter with a Material 3
 * Expressive, Compose-based UI while reusing Gadgetbridge's device service underneath.
 */
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleFirstRunAndPermissions()
        GBApplication.deviceService().requestDeviceInfo()

        setContent {
            val design = viewModel.design.collectAsStateWithLifecycle().value
            AppTheme(design) {
                val devices = viewModel.devices.collectAsStateWithLifecycle().value
                val onAddDevice: () -> Unit = {
                    startActivity(Intent(this, DiscoveryActivityV2::class.java))
                }
                val onOpenDevice: (GBDevice) -> Unit = { device ->
                    startActivity(
                        Intent(this, DeviceSettingsActivity::class.java)
                            .putExtra(GBDevice.EXTRA_DEVICE, device)
                    )
                }
                val onToggleConnection: (GBDevice) -> Unit = { device ->
                    viewModel.toggleConnection(device)
                }
                val onRefresh: () -> Unit = { viewModel.refresh() }
                val onSettings: () -> Unit = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                val onToggleDesign: () -> Unit = { viewModel.toggleDesign() }

                when (design) {
                    DesignSystem.MATERIAL3 -> HomeScreen(
                        devices = devices,
                        onAddDevice = onAddDevice,
                        onOpenDevice = onOpenDevice,
                        onToggleConnection = onToggleConnection,
                        onRefresh = onRefresh,
                        onSettings = onSettings,
                        onToggleDesign = onToggleDesign,
                    )

                    DesignSystem.MIUIX -> HomeScreenMiuix(
                        devices = devices,
                        onAddDevice = onAddDevice,
                        onOpenDevice = onOpenDevice,
                        onToggleConnection = onToggleConnection,
                        onRefresh = onRefresh,
                        onSettings = onSettings,
                        onToggleDesign = onToggleDesign,
                    )
                }
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
