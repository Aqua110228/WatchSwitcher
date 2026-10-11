/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    huaweiAccount: String,
    loggingEnabled: Boolean,
    onBack: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onOpenHuaweiId: () -> Unit,
    onToggleLogging: () -> Unit,
    onShareLogs: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            Button(onClick = onOpenHuaweiId, modifier = Modifier.fillMaxWidth()) {
                Text("华为账号 ID" + if (huaweiAccount.isNotBlank()) "（已设置）" else "（未设置）")
            }

            Button(onClick = onOpenFullSettings, modifier = Modifier.fillMaxWidth()) {
                Text("全部设置")
            }

            Text("排障", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onToggleLogging, modifier = Modifier.fillMaxWidth()) {
                Text(if (loggingEnabled) "关闭日志记录" else "开启日志记录")
            }
            OutlinedButton(onClick = onShareLogs, modifier = Modifier.fillMaxWidth()) {
                Text("分享日志文件")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "WatchSwitcher $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "以 AGPL-3.0 开源发布。绕开华为运动健康直连手表。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
