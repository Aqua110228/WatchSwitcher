/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.ui.device.DeviceDetailActivity
import nodomain.freeyourgadget.gadgetbridge.ui.discovery.DiscoveryActivity
import nodomain.freeyourgadget.gadgetbridge.ui.settings.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme

/**
 * Main entry point. Material 3 Expressive, Compose-based UI on top of Gadgetbridge's device service.
 */
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private var pendingConnect: GBDevice? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingConnect?.let { device -> viewModel.toggleConnection(device) }
        pendingConnect = null
    }

    private fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Permissions are requested only when a connection is actually started. */
    private fun toggleConnection(device: GBDevice) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.toggleConnection(device)
        } else {
            pendingConnect = device
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    onToggleConnection = { device -> toggleConnection(device) },
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
}
