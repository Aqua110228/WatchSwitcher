/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.ui.discovery.HUAWEI_ACCOUNT_HELP_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    state: DeviceUiState,
    onBack: () -> Unit,
    onToggleConnection: () -> Unit,
    onSync: () -> Unit,
    onFindDevice: () -> Unit,
    onOpenActivityData: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onSaveHuaweiAccount: (String) -> Unit,
    onUnbind: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("状态", state.stateString)
                    if (state.model.isNotBlank()) InfoRow("型号", state.model)
                    if (state.firmware.isNotBlank()) InfoRow("固件", state.firmware)
                    if (state.address.isNotBlank()) InfoRow("地址", state.address)
                    if (state.battery in 0..100) InfoRow("电量", "${state.battery}%")
                }
            }

            FilledTonalButton(onClick = onToggleConnection, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.connected) "断开连接" else "连接")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSync, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("同步数据")
                }
                OutlinedButton(onClick = onFindDevice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("查找设备")
                }
            }

            Text("功能", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(onClick = onOpenActivityData, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.List, contentDescription = null)
                Text("活动数据（步数 / 睡眠 / 心率）")
            }
            OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Text("通知管理")
            }
            Button(onClick = onOpenFullSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text("全部设置")
            }

            if (state.isHuawei) {
                HuaweiAccountCard(
                    currentAccount = state.huaweiAccount,
                    onSave = onSaveHuaweiAccount,
                )
            }

            OutlinedButton(onClick = onUnbind, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("解绑设备")
            }
        }
    }
}

@Composable
private fun HuaweiAccountCard(
    currentAccount: String,
    onSave: (String) -> Unit,
) {
    var account by remember { mutableStateOf(currentAccount) }
    LaunchedEffect(currentAccount) { account = currentAccount }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("华为账号 ID", style = MaterialTheme.typography.titleMedium)
            Text(
                "设备若已绑定华为账号，连接时会要求恢复出厂。填入最初配对手表时用的华为账号 ID（17 位数字，不是手机号），保存后重新连接即可免恢复出厂。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("华为账号 ID") },
            )
            Button(onClick = { onSave(account) }, modifier = Modifier.fillMaxWidth()) {
                Text("保存并重连")
            }
            TextButton(onClick = { uriHandler.openUri(HUAWEI_ACCOUNT_HELP_URL) }) {
                Text("如何获取账号 ID？（打开官方说明网页）")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
