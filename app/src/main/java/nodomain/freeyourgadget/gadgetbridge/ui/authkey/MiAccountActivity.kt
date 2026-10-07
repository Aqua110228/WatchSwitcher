/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.authkey

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme

/**
 * Logs into the Xiaomi account and lists the auth keys of the bound devices, so the user does not
 * have to dig the key out of the vendor app's log files.
 */
class MiAccountActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTH_KEY = "auth_key"
        const val EXTRA_MAC = "mac"

        fun newIntent(context: Context): Intent = Intent(context, MiAccountActivity::class.java)
    }

    private val email = mutableStateOf("")
    private val password = mutableStateOf("")
    private val region = mutableStateOf("cn")
    private val loading = mutableStateOf(false)
    private val errorState = mutableStateOf<String?>(null)
    private val devices = mutableStateOf<List<MiDevice>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchSwitcherTheme {
                MiAccountScreen(
                    email = email.value,
                    password = password.value,
                    region = region.value,
                    loading = loading.value,
                    error = errorState.value,
                    devices = devices.value,
                    onEmailChange = { email.value = it },
                    onPasswordChange = { password.value = it },
                    onRegionChange = { region.value = it },
                    onFetch = { fetch() },
                    onPickDevice = { device ->
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_AUTH_KEY, device.authKey)
                                .putExtra(EXTRA_MAC, device.mac),
                        )
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    private fun fetch() {
        if (loading.value) return
        loading.value = true
        errorState.value = null
        devices.value = emptyList()

        val emailValue = email.value.trim()
        val passwordValue = password.value
        val regionValue = region.value.trim().ifBlank { "cn" }

        Thread {
            val result = runCatching { MiFitnessKeyFetcher(regionValue).fetch(emailValue, passwordValue) }
            runOnUiThread {
                loading.value = false
                result.onSuccess { list ->
                    devices.value = list
                    if (list.isEmpty()) {
                        errorState.value = "未找到已绑定的设备（账号/区域是否正确？）"
                    }
                }.onFailure { throwable ->
                    errorState.value = throwable.message ?: "获取失败"
                }
            }
        }.start()
    }
}
