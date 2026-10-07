/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.discovery

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.GBScanEvent
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.GBScanEventProcessor
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils

/**
 * Bluetooth scanning for the discovery screen, reusing Gadgetbridge's [GBScanEventProcessor]
 * so the candidate/device-type resolution matches the classic discovery screen.
 */
class DiscoveryViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val SCAN_DURATION_MS = 30_000L
    }

    private val _devices = MutableStateFlow<List<GBDeviceCandidate>>(emptyList())
    val devices: StateFlow<List<GBDeviceCandidate>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopScan() }

    private val processorCallback = object : GBScanEventProcessor.Callback {
        override fun onDeviceChanged() {
            _devices.value = processor.devices.toList()
        }
    }
    private val processor: GBScanEventProcessor = GBScanEventProcessor(processorCallback)

    private var adapter: BluetoothAdapter? = null
    private var receiver: BroadcastReceiver? = null

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val uuids: Array<ParcelUuid>? = record.serviceUuids?.toTypedArray()
            processor.scheduleProcessing(
                GBScanEvent(result.device, result.rssi.toShort(), uuids, record.manufacturerSpecificData)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val context = getApplication<Application>()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return
        this.adapter = adapter

        _devices.value = emptyList()
        processor.clear()
        _scanning.value = true

        registerClassicReceiver(context)
        adapter.bluetoothLeScanner?.startScan(null, scanSettings(), bleScanCallback)
        adapter.startDiscovery()

        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        handler.removeCallbacks(stopRunnable)
        _scanning.value = false

        val adapter = adapter ?: return
        try {
            adapter.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (ignored: Exception) {
        }
        try {
            adapter.cancelDiscovery()
        } catch (ignored: Exception) {
        }
        unregisterClassicReceiver()
        processor.stop()
        _devices.value = processor.devices.toList()
    }

    @SuppressLint("MissingPermission")
    private fun registerClassicReceiver(context: Context) {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_UUID)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device == null) return
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, GBDevice.RSSI_UNKNOWN)
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND ->
                        processor.scheduleProcessing(GBScanEvent(device, rssi, device.uuids, null))

                    BluetoothDevice.ACTION_UUID -> {
                        val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                        processor.scheduleProcessing(
                            GBScanEvent(device, rssi, AndroidUtils.toParcelUuids(uuids), null)
                        )
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = r
    }

    private fun unregisterClassicReceiver() {
        receiver?.let {
            try {
                getApplication<Application>().unregisterReceiver(it)
            } catch (ignored: Exception) {
            }
        }
        receiver = null
    }

    private fun scanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
        }
        return builder.build()
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
