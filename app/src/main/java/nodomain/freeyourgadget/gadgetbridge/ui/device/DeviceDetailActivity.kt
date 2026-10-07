/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.device

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.DeviceDeleteActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.ui.design.AppTheme
import nodomain.freeyourgadget.gadgetbridge.ui.design.DesignSystem
import nodomain.freeyourgadget.gadgetbridge.ui.design.loadDesignSystem

/**
 * Compose device detail screen (Material 3 Expressive / MiUIX) with quick actions, replacing
 * Gadgetbridge's per-device entry point. The full device settings are still reachable from here.
 */
class DeviceDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }

    private val viewModel: DeviceDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (address == null) {
            finish()
            return
        }
        viewModel.attach(address)
        val design = loadDesignSystem()

        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            AppTheme(design) {
                when (design) {
                    DesignSystem.MATERIAL3 -> DeviceDetailScreen(
                        state = state,
                        onBack = { finish() },
                        onToggleConnection = { viewModel.toggleConnection() },
                        onSync = { viewModel.syncNow() },
                        onFindDevice = { viewModel.findDevice(true) },
                        onOpenFullSettings = { openFullSettings(address) },
                        onUnbind = { unbind(address) },
                    )

                    DesignSystem.MIUIX -> DeviceDetailScreenMiuix(
                        state = state,
                        onBack = { finish() },
                        onToggleConnection = { viewModel.toggleConnection() },
                        onSync = { viewModel.syncNow() },
                        onFindDevice = { viewModel.findDevice(true) },
                        onOpenFullSettings = { openFullSettings(address) },
                        onUnbind = { unbind(address) },
                    )
                }
            }
        }
    }

    private fun currentDevice(address: String): GBDevice? =
        GBApplication.app().deviceManager.getDeviceByAddress(address)

    private fun openFullSettings(address: String) {
        val device = currentDevice(address) ?: return
        startActivity(
            Intent(this, DeviceSettingsActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
        )
    }

    private fun unbind(address: String) {
        val device = currentDevice(address) ?: return
        startActivity(
            Intent(this, DeviceDeleteActivity::class.java)
                .putExtra(DeviceDeleteActivity.EXTRA_DEVICE, device)
                .putExtra(DeviceDeleteActivity.EXTRA_DELETE_FILES, true)
        )
    }
}
