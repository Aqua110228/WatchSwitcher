/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.ui.design.DesignSystem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreenMiuix(
    design: DesignSystem,
    versionName: String,
    onBack: () -> Unit,
    onSelectDesign: (DesignSystem) -> Unit,
    onOpenFullSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("设计风格", color = MiuixTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (design == DesignSystem.MATERIAL3) {
                        Button(onClick = { onSelectDesign(DesignSystem.MATERIAL3) }) {
                            Text("Material 3", color = MiuixTheme.colorScheme.onPrimary)
                        }
                    } else {
                        TextButton(text = "Material 3", onClick = { onSelectDesign(DesignSystem.MATERIAL3) })
                    }
                    if (design == DesignSystem.MIUIX) {
                        Button(onClick = { onSelectDesign(DesignSystem.MIUIX) }) {
                            Text("MiUIX", color = MiuixTheme.colorScheme.onPrimary)
                        }
                    } else {
                        TextButton(text = "MiUIX", onClick = { onSelectDesign(DesignSystem.MIUIX) })
                    }
                }
            }

            Button(onClick = onOpenFullSettings, modifier = Modifier.fillMaxWidth()) {
                Text("完整设置（Gadgetbridge）", color = MiuixTheme.colorScheme.onPrimary)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Text("关于", color = MiuixTheme.colorScheme.onSurface)
                Text(
                    "HuaweiSwitcher $versionName",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    "基于 Gadgetbridge，以 AGPL-3.0 发布。绕开华为运动健康直连手表。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
