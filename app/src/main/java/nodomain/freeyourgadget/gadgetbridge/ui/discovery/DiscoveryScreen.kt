/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.isValidHuaweiAccountId

/** Gadgetbridge documentation on pairing Huawei/Honor devices without a factory reset. */
const val HUAWEI_ACCOUNT_HELP_URL =
    "https://gadgetbridge.org/basics/pairing/huawei-honor-pairing/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    devices: List<GBDeviceCandidate>,
    scanning: Boolean,
    onBack: () -> Unit,
    onToggleScan: () -> Unit,
    onSelect: (GBDeviceCandidate) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onToggleScan) {
                        Text(if (scanning) "停止" else "扫描")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (scanning) "正在扫描附近的设备…" else "点击右上角「扫描」查找设备",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = devices, key = { it.macAddress }) { candidate ->
                    CandidateCard(candidate = candidate, onClick = { onSelect(candidate) })
                }
            }
        }
    }
}

/**
 * Asks for the Huawei account ID before connecting a Huawei/Honor device. The watch checks it
 * during authentication, so providing the account it is already bound to avoids the
 * "factory reset required" prompt.
 */
@Composable
fun HuaweiAccountPromptDialog(
    deviceName: String,
    account: String,
    onAccountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onAutoGet: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要华为账号 ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "要连接「${deviceName}」，需要填入配对手表时用的华为账号 ID。" +
                        "如果该手表已绑定华为账号，不填的话连接时会被要求恢复出厂（清空手表数据）。" +
                        "填错会报「认证密钥协商失败 / 密钥不正确」。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = account,
                    onValueChange = {
                        onAccountChange(it)
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("华为账号 ID（17 位数字）") },
                )
                TextButton(onClick = onAutoGet) {
                    Text("登录华为云自动获取（推荐）")
                }
                TextButton(onClick = { uriHandler.openUri(HUAWEI_ACCOUNT_HELP_URL) }) {
                    Text("手动获取方法（打开官方说明）")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (account.isNotBlank() && !isValidHuaweiAccountId(account)) {
                    error = "请输入 15-19 位数字的华为账号 ID（不是手机号）"
                } else {
                    onConfirm(account)
                }
            }) { Text("保存并连接") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CandidateCard(candidate: GBDeviceCandidate, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = candidate.name ?: "未知设备",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = candidate.macAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
