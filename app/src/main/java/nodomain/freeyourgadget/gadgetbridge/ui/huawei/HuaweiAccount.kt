/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.huawei

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants

/**
 * The Huawei account ID is a 17-digit number bound to the watch. Setting it once lets
 * Gadgetbridge authenticate against that account instead of the watch demanding a factory reset.
 *
 * It is stored globally (so it can be entered during onboarding, before any device is paired) and
 * copied into a device's own preferences right before pairing.
 */
private const val KEY_HUAWEI_ACCOUNT = "huaweiswitcher_huawei_account"
private const val KEY_ONBOARDED = "huaweiswitcher_huawei_id_onboarded"

fun getHuaweiAccount(): String =
    GBApplication.getPrefs().getString(KEY_HUAWEI_ACCOUNT, "").orEmpty()

fun setHuaweiAccount(value: String) {
    GBApplication.getPrefs().getPreferences()
        .edit()
        .putString(KEY_HUAWEI_ACCOUNT, value.trim())
        .apply()
}

fun isHuaweiIdOnboarded(): Boolean =
    GBApplication.getPrefs().getBoolean(KEY_ONBOARDED, false)

fun setHuaweiIdOnboarded() {
    GBApplication.getPrefs().getPreferences()
        .edit()
        .putBoolean(KEY_ONBOARDED, true)
        .apply()
}

/** Copies the global account ID into the given device's preferences, where the Huawei code reads it. */
fun applyHuaweiAccountToDevice(deviceAddress: String, value: String = getHuaweiAccount()) {
    if (value.isBlank()) return
    GBApplication.getDeviceSpecificSharedPrefs(deviceAddress)
        .edit()
        .putString(HuaweiConstants.PREF_HUAWEI_ACCOUNT, value.trim())
        .apply()
}
