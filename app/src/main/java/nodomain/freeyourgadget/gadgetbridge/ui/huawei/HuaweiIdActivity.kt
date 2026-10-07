/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.huawei

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme

/**
 * Obtains and stores the global Huawei account ID. Shown during onboarding (before any device is
 * paired) and reachable from settings / the discovery prompt.
 */
class HuaweiIdActivity : ComponentActivity() {

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, HuaweiIdActivity::class.java)
    }

    private val input = mutableStateOf("")

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val id = result.data?.getStringExtra(HuaweiLoginActivity.EXTRA_ACCOUNT_ID)
        if (!id.isNullOrBlank()) {
            input.value = id
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        input.value = getHuaweiAccount()

        setContent {
            WatchSwitcherTheme {
                HuaweiIdScreen(
                    value = input.value,
                    onValueChange = { input.value = it },
                    onBack = {
                        setHuaweiIdOnboarded()
                        finish()
                    },
                    onSave = {
                        setHuaweiAccount(input.value)
                        setHuaweiIdOnboarded()
                        finish()
                    },
                    onOpenWebLogin = {
                        loginLauncher.launch(HuaweiLoginActivity.newIntent(this))
                    },
                )
            }
        }
    }
}
