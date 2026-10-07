/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import nodomain.freeyourgadget.gadgetbridge.ui.theme.HuaweiSwitcherTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Wraps the content in the currently selected design system, so a screen can be written once
 * against the shared components and rendered either as Material 3 Expressive or MiUIX.
 */
@Composable
fun AppTheme(design: DesignSystem, content: @Composable () -> Unit) {
    when (design) {
        DesignSystem.MATERIAL3 -> HuaweiSwitcherTheme(content = content)
        DesignSystem.MIUIX -> {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            MiuixTheme(controller = controller, content = content)
        }
    }
}
