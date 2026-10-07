/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.ui.theme.HuaweiSwitcherTheme
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.HuaweiIdActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.getHuaweiAccount
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity as GbSettingsActivity

/**
 * Compose settings screen. The rest of Gadgetbridge's settings remain reachable through the
 * full settings screen.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HuaweiSwitcherTheme {
                SettingsScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    huaweiAccount = getHuaweiAccount(),
                    onBack = { finish() },
                    onOpenFullSettings = {
                        startActivity(Intent(this, GbSettingsActivity::class.java))
                    },
                    onOpenHuaweiId = {
                        startActivity(HuaweiIdActivity.newIntent(this))
                    },
                )
            }
        }
    }
}
