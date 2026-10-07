/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.design

import nodomain.freeyourgadget.gadgetbridge.GBApplication

/** The UI design system the app shells itself in. */
enum class DesignSystem { MATERIAL3, MIUIX }

private const val PREF_KEY_DESIGN_SYSTEM = "huaweiswitcher_design_system"

fun loadDesignSystem(): DesignSystem {
    val value = GBApplication.getPrefs()
        .getString(PREF_KEY_DESIGN_SYSTEM, DesignSystem.MATERIAL3.name)
    return DesignSystem.entries.firstOrNull { it.name == value } ?: DesignSystem.MATERIAL3
}

fun saveDesignSystem(design: DesignSystem) {
    GBApplication.getPrefs().getPreferences()
        .edit()
        .putString(PREF_KEY_DESIGN_SYSTEM, design.name)
        .apply()
}
