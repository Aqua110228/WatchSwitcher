/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes

/**
 * Exposes the list of paired devices and mirrors Gadgetbridge's local device-change broadcasts
 * so the Compose UI stays in sync while a device connects/disconnects or new data arrives.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _devices = MutableStateFlow<List<GBDevice>>(emptyList())
    val devices: StateFlow<List<GBDevice>> = _devices.asStateFlow()

    private var registered = false
    private val syncedDevices = mutableSetOf<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(DeviceManager.ACTION_DEVICES_CHANGED)
                addAction(GBDevice.ACTION_DEVICE_CHANGED)
                addAction(GBApplication.ACTION_NEW_DATA)
            }
            LocalBroadcastManager.getInstance(getApplication()).registerReceiver(receiver, filter)
            registered = true
        }
        refresh()
    }

    fun stop() {
        if (registered) {
            LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(receiver)
            registered = false
        }
    }

    fun refresh() {
        val devices = GBApplication.app().deviceManager.devices
        _devices.value = devices.toList()
        devices.forEach { device ->
            if (device.isInitialized) {
                // Pull recorded data once per connection, so the UI is not empty after pairing.
                if (syncedDevices.add(device.address)) {
                    GBApplication.deviceService(device)
                        .onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
                }
            } else {
                syncedDevices.remove(device.address)
            }
        }
    }

    fun toggleConnection(device: GBDevice) {
        val service = GBApplication.deviceService(device)
        if (device.isConnected) {
            service.disconnect()
        } else {
            service.connect()
        }
        refresh()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
