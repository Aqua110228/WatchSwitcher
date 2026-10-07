/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.discovery

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType

data class ModelOption(val type: DeviceType, val name: String)

data class BrandOption(val name: String, val models: List<ModelOption>)

private val brandCache = HashMap<String, List<BrandOption>>()

/** All supported device types grouped by manufacturer / brand, for the picker. */
fun loadBrands(context: Context): List<BrandOption> {
    val key = context.resources.configuration.locales[0].language
    brandCache[key]?.let { return it }

    val grouped = LinkedHashMap<String, MutableList<ModelOption>>()
    for (type in DeviceType.values()) {
        if (type == DeviceType.UNKNOWN) continue
        val coordinator = runCatching { type.deviceCoordinator }.getOrNull() ?: continue
        val brand = runCatching { coordinator.manufacturer }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: continue
        val nameRes = runCatching { coordinator.deviceNameResource }.getOrDefault(0)
        val modelName = if (nameRes != 0) {
            runCatching { context.getString(nameRes) }.getOrDefault(type.name)
        } else {
            type.name
        }
        grouped.getOrPut(brand) { mutableListOf() }.add(ModelOption(type, modelName))
    }

    val brands = grouped.entries
        .map { BrandOption(it.key, it.value.sortedBy { model -> model.name }) }
        .sortedBy { it.name }
    brandCache[key] = brands
    return brands
}

fun BrandOption.isHuawei(): Boolean = models.any {
    runCatching { it.type.deviceCoordinator is nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator }
        .getOrDefault(false)
}
