/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.device

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

data class DeviceUiState(
    val name: String = "",
    val model: String = "",
    val firmware: String = "",
    val address: String = "",
    val stateString: String = "",
    val connected: Boolean = false,
    val battery: Int = -1,
)

class DeviceDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private var device: GBDevice? = null

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    fun attach(address: String) {
        device = GBApplication.app().deviceManager.getDeviceByAddress(address)
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(DeviceManager.ACTION_DEVICES_CHANGED)
                addAction(GBDevice.ACTION_DEVICE_CHANGED)
            }
            LocalBroadcastManager.getInstance(getApplication()).registerReceiver(receiver, filter)
            registered = true
        }
        refresh()
    }

    fun detach() {
        if (registered) {
            LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(receiver)
            registered = false
        }
    }

    fun refresh() {
        val device = device ?: return
        val context = getApplication<Application>()
        _state.value = DeviceUiState(
            name = device.aliasOrName,
            model = device.model.orEmpty(),
            firmware = device.firmwareVersion.orEmpty(),
            address = device.address,
            stateString = device.getStateString(context),
            connected = device.isConnected,
            battery = device.getBatteryLevel(0),
        )
    }

    fun toggleConnection() {
        val device = device ?: return
        val service = GBApplication.deviceService(device)
        if (device.isConnected) service.disconnect() else service.connect()
        refresh()
    }

    fun syncNow() {
        val device = device ?: return
        GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
    }

    fun findDevice(start: Boolean) {
        val device = device ?: return
        GBApplication.deviceService(device).onFindDevice(start)
    }

    override fun onCleared() {
        detach()
        super.onCleared()
    }
}
