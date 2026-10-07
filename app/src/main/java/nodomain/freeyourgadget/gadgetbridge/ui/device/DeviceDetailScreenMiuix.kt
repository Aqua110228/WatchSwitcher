/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.device

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceDetailScreenMiuix(
    state: DeviceUiState,
    onBack: () -> Unit,
    onToggleConnection: () -> Unit,
    onSync: () -> Unit,
    onFindDevice: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onUnbind: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = state.name,
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
                MiuixInfoRow("状态", state.stateString)
                if (state.model.isNotBlank()) MiuixInfoRow("型号", state.model)
                if (state.firmware.isNotBlank()) MiuixInfoRow("固件", state.firmware)
                if (state.address.isNotBlank()) MiuixInfoRow("地址", state.address)
                if (state.battery in 0..100) MiuixInfoRow("电量", "${state.battery}%")
            }

            Button(onClick = onToggleConnection, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (state.connected) "断开连接" else "连接",
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }

            TextButton(
                text = "同步数据",
                onClick = onSync,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = "查找设备",
                onClick = onFindDevice,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onOpenFullSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "完整设置", color = MiuixTheme.colorScheme.onPrimary)
            }
            TextButton(
                text = "解绑设备",
                onClick = onUnbind,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MiuixInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
