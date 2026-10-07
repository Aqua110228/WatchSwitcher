/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.ui.authkey.MiAccountActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.HuaweiIdActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.applyHuaweiAccountToDevice
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.getHuaweiAccount
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.setHuaweiAccount
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme
import nodomain.freeyourgadget.gadgetbridge.util.BondingInterface
import nodomain.freeyourgadget.gadgetbridge.util.BondingUtil
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper

/**
 * Compose discovery flow: pick brand -> (Huawei: set account ID) -> pick model -> scan and pair.
 * Scanning reuses Gadgetbridge's GBScanEventProcessor; pairing reuses BondingUtil.
 */
class DiscoveryActivity : ComponentActivity(), BondingInterface {

    private companion object {
        const val CHILD_RESULT = 0x826983
    }

    private enum class Step { BRAND, HUAWEI_ID, MODEL, SCAN }

    private val viewModel: DiscoveryViewModel by viewModels()

    private val step = mutableStateOf(Step.BRAND)
    private val brand = mutableStateOf<BrandOption?>(null)
    private val model = mutableStateOf<ModelOption?>(null)

    private val huaweiAccountState = mutableStateOf("")
    private val huaweiPrompt = mutableStateOf<GBDeviceCandidate?>(null)
    private val huaweiAccountInput = mutableStateOf("")

    private var deviceTarget: GBDeviceCandidate? = null
    private var bondReceiver: BroadcastReceiver? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.startScan() }

    private val authKeyPrompt = mutableStateOf<Pair<GBDeviceCandidate, DeviceCoordinator>?>(null)
    private val authKeyPrimary = mutableStateOf("")
    private val authKeySecondary = mutableStateOf("")

    private val miAccountLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val key = result.data?.getStringExtra(MiAccountActivity.EXTRA_AUTH_KEY)
        if (!key.isNullOrBlank()) authKeyPrimary.value = key
    }

    private val huaweiIdLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val account = getHuaweiAccount()
        huaweiAccountState.value = account
        if (account.isNotBlank()) huaweiAccountInput.value = account
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        huaweiAccountState.value = getHuaweiAccount()

        setContent {
            WatchSwitcherTheme {
                val brands = remember { loadBrands(this) }
                when (step.value) {
                    Step.BRAND -> BrandListScreen(
                        brands = brands,
                        onBack = { finish() },
                        onSelectBrand = { selected ->
                            brand.value = selected
                            step.value = if (selected.isHuawei()) Step.HUAWEI_ID else Step.MODEL
                        },
                    )

                    Step.HUAWEI_ID -> HuaweiIdStepScreen(
                        isHuawei = true,
                        account = huaweiAccountState.value,
                        onBack = { step.value = Step.BRAND },
                        onOpenHuaweiId = {
                            huaweiIdLauncher.launch(HuaweiIdActivity.newIntent(this))
                        },
                        onContinue = { step.value = Step.MODEL },
                    )

                    Step.MODEL -> {
                        val selectedBrand = brand.value
                        if (selectedBrand == null) {
                            step.value = Step.BRAND
                        } else {
                            ModelListScreen(
                                brand = selectedBrand,
                                onBack = {
                                    step.value = if (selectedBrand.isHuawei()) {
                                        Step.HUAWEI_ID
                                    } else {
                                        Step.BRAND
                                    }
                                },
                                onSelectModel = { selectedModel ->
                                    model.value = selectedModel
                                    viewModel.setTypeFilter(selectedModel?.type)
                                    step.value = Step.SCAN
                                    ensurePermissionsAndScan()
                                },
                            )
                        }
                    }

                    Step.SCAN -> {
                        val devices = viewModel.devices.collectAsStateWithLifecycle().value
                        val scanning = viewModel.scanning.collectAsStateWithLifecycle().value
                        DiscoveryScreen(
                            devices = devices,
                            scanning = scanning,
                            filterLabel = model.value?.name,
                            onBack = {
                                viewModel.stopScan()
                                step.value = Step.MODEL
                            },
                            onToggleScan = { toggleScan() },
                            onShowAll = {
                                model.value = null
                                viewModel.setTypeFilter(null)
                            },
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

                        val ak = authKeyPrompt.value
                        if (ak != null) {
                            val (candidate, coordinator) = ak
                            val secondaryPref = coordinator.secondaryAuthKeyPref
                            AuthKeyPromptDialog(
                                deviceName = candidate.name ?: candidate.macAddress,
                                secondaryHintRes = coordinator.secondaryAuthKeyHint,
                                primary = authKeyPrimary.value,
                                secondary = authKeySecondary.value,
                                onPrimaryChange = { authKeyPrimary.value = it },
                                onSecondaryChange = { authKeySecondary.value = it },
                                onDismiss = { authKeyPrompt.value = null },
                                validate = { primary, secondary ->
                                    coordinator.validateAuthKey(primary) &&
                                        (secondaryPref == null || secondary.isNotBlank())
                                },
                                onConfirm = {
                                    val prefs = GBApplication
                                        .getDeviceSpecificSharedPrefs(candidate.macAddress)
                                    prefs.edit()
                                        .putString(
                                            DeviceSettingsPreferenceConst.PREF_AUTH_KEY,
                                            authKeyPrimary.value.trim(),
                                        )
                                        .apply()
                                    if (secondaryPref != null) {
                                        prefs.edit()
                                            .putString(secondaryPref, authKeySecondary.value.trim())
                                            .apply()
                                    }
                                    authKeyPrompt.value = null
                                    startPair(candidate, coordinator)
                                },
                                onAutoFetch = {
                                    miAccountLauncher.launch(MiAccountActivity.newIntent(this))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        huaweiAccountState.value = getHuaweiAccount()
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
            authKeyPrimary.value = ""
            authKeySecondary.value = ""
            authKeyPrompt.value = candidate to coordinator
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
