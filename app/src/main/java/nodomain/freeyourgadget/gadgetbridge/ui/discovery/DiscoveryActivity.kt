/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import nodomain.freeyourgadget.gadgetbridge.activities.AuthKeyActivity
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.HuaweiIdActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.applyHuaweiAccountToDevice
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.getHuaweiAccount
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.setHuaweiAccount
import nodomain.freeyourgadget.gadgetbridge.ui.theme.HuaweiSwitcherTheme
import nodomain.freeyourgadget.gadgetbridge.util.BondingInterface
import nodomain.freeyourgadget.gadgetbridge.util.BondingUtil
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper

/**
 * Compose discovery/scan screen. Scanning reuses Gadgetbridge's GBScanEventProcessor; pairing
 * reuses BondingUtil (including the auth-key step for devices that require one).
 */
class DiscoveryActivity : ComponentActivity(), BondingInterface {

    private companion object {
        const val CHILD_RESULT = 0x826983
    }

    private val viewModel: DiscoveryViewModel by viewModels()

    private var deviceTarget: GBDeviceCandidate? = null
    private var bondReceiver: BroadcastReceiver? = null
    private val huaweiPrompt = mutableStateOf<GBDeviceCandidate?>(null)
    private val huaweiAccountInput = mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.startScan() }

    private val authKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        @Suppress("DEPRECATION")
        val candidate: GBDeviceCandidate? =
            data.getParcelableExtra(AuthKeyActivity.EXTRA_DEVICE_CANDIDATE_RESULT)
        if (candidate != null) {
            val deviceType = DeviceHelper.getInstance().resolveDeviceType(candidate)
            startPair(candidate, deviceType.deviceCoordinator)
        }
    }

    private val huaweiIdLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val account = getHuaweiAccount()
        if (account.isNotBlank()) huaweiAccountInput.value = account
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HuaweiSwitcherTheme {
                val devices = viewModel.devices.collectAsStateWithLifecycle().value
                val scanning = viewModel.scanning.collectAsStateWithLifecycle().value
                DiscoveryScreen(
                    devices = devices,
                    scanning = scanning,
                    onBack = { finish() },
                    onToggleScan = { toggleScan() },
                    onSelect = { onCandidateSelected(it) },
                )
                val prompt = huaweiPrompt.value
                if (prompt != null) {
                    HuaweiAccountPromptDialog(
                        deviceName = prompt.name ?: prompt.macAddress,
                        account = huaweiAccountInput.value,
                        onAccountChange = { huaweiAccountInput.value = it },
                        onDismiss = { huaweiPrompt.value = null },
                        onAutoGet = {
                            huaweiIdLauncher.launch(HuaweiIdActivity.newIntent(this))
                        },
                        onConfirm = { account ->
                            if (account.isNotBlank()) {
                                setHuaweiAccount(account)
                                applyHuaweiAccountToDevice(prompt.macAddress, account)
                            }
                            huaweiPrompt.value = null
                            pair(prompt)
                        },
                    )
                }
            }
        }

        ensurePermissionsAndScan()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopScan()
    }

    private fun toggleScan() {
        if (viewModel.scanning.value) viewModel.stopScan() else ensurePermissionsAndScan()
    }

    private fun ensurePermissionsAndScan() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.startScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions
    }

    private fun onCandidateSelected(candidate: GBDeviceCandidate) {
        val coordinator = DeviceHelper.getInstance().resolveDeviceType(candidate).deviceCoordinator
        if (coordinator is HuaweiCoordinator) {
            huaweiAccountInput.value = getHuaweiAccount()
            huaweiPrompt.value = candidate
        } else {
            pair(candidate)
        }
    }

    private fun pair(candidate: GBDeviceCandidate) {
        DeviceHelper.getInstance().clearForcedDeviceTypes()
        val deviceType: DeviceType = DeviceHelper.getInstance().resolveDeviceType(candidate)
        if (!deviceType.isSupported) {
            Toast.makeText(this, "不支持的设备", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.stopScan()
        deviceTarget = candidate

        val coordinator: DeviceCoordinator = deviceType.deviceCoordinator
        if (coordinator.requiresAuthKey()) {
            authKeyLauncher.launch(AuthKeyActivity.newIntent(this, candidate))
        } else {
            startPair(candidate, coordinator)
        }
    }

    private fun startPair(candidate: GBDeviceCandidate, coordinator: DeviceCoordinator) {
        val pairingActivity = coordinator.pairingActivity
        if (pairingActivity != null) {
            val intent = Intent(this, pairingActivity)
            intent.putExtra(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE, candidate)
            intent.putParcelableArrayListExtra(
                DeviceCoordinator.EXTRA_DEVICE_ALL_CANDIDATES,
                ArrayList(viewModel.devices.value),
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, CHILD_RESULT)
        } else {
            BondingUtil.initiateCorrectBonding(this, candidate, coordinator)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHILD_RESULT) {
            if (resultCode == RESULT_OK) finish()
        } else {
            BondingUtil.handleActivityResult(this, requestCode, resultCode, data)
        }
    }

    // --- BondingInterface ---

    override fun onBondingComplete(success: Boolean) {
        finish()
    }

    override fun getCurrentTarget(): GBDeviceCandidate? = deviceTarget

    override fun getAttemptToConnect(): Boolean = true

    override fun getContext(): Context = this

    @SuppressLint("MissingPermission")
    override fun registerBroadcastReceivers() {
        if (bondReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device == null) return
                val mac = getMacAddress()
                if (mac == null || !mac.equals(device.address, ignoreCase = true)) return
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    val target = deviceTarget
                    if (target != null) {
                        BondingUtil.handleDeviceBonded(this@DiscoveryActivity, target)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        bondReceiver = receiver
    }

    override fun unregisterBroadcastReceivers() {
        bondReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {
            }
        }
        bondReceiver = null
    }
}
