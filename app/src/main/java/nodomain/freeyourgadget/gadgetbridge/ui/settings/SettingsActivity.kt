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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.ui.design.AppTheme
import nodomain.freeyourgadget.gadgetbridge.ui.design.DesignSystem
import nodomain.freeyourgadget.gadgetbridge.ui.design.loadDesignSystem
import nodomain.freeyourgadget.gadgetbridge.ui.design.saveDesignSystem
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity as GbSettingsActivity

/**
 * Compose settings screen (Material 3 Expressive / MiUIX). The design system can be switched
 * live here; the rest of Gadgetbridge's settings remain reachable through the full settings screen.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var design by remember { mutableStateOf(loadDesignSystem()) }
            AppTheme(design) {
                val onSelectDesign: (DesignSystem) -> Unit = {
                    saveDesignSystem(it)
                    design = it
                }
                val onOpenFullSettings: () -> Unit = {
                    startActivity(Intent(this, GbSettingsActivity::class.java))
                }
                when (design) {
                    DesignSystem.MATERIAL3 -> SettingsScreen(
                        design = design,
                        versionName = BuildConfig.VERSION_NAME,
                        onBack = { finish() },
                        onSelectDesign = onSelectDesign,
                        onOpenFullSettings = onOpenFullSettings,
                    )

                    DesignSystem.MIUIX -> SettingsScreenMiuix(
                        design = design,
                        versionName = BuildConfig.VERSION_NAME,
                        onBack = { finish() },
                        onSelectDesign = onSelectDesign,
                        onOpenFullSettings = onOpenFullSettings,
                    )
                }
            }
        }
    }
}
