/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.huawei

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.ui.discovery.HUAWEI_ACCOUNT_HELP_URL

/**
 * Lets the user obtain and store the Huawei account ID up front (during onboarding or from
 * settings), so pairing a Huawei/Honor watch works without a factory reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuaweiIdScreen(
    value: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenWebLogin: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("华为账号 ID") },
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
                    Text("为什么需要它", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "华为手表配对时会把华为账号 ID（17 位数字）写进去。如果手表之前用华为运动健康配过，" +
                            "不填这个 ID 直接连会被要求恢复出厂（清空手表）。现在填一次、全局保存，" +
                            "之后配对手表会自动套用，不需要先连上再找。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("华为账号 ID（17 位数字）") },
            )

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存")
            }

            OutlinedButton(onClick = onOpenWebLogin, modifier = Modifier.fillMaxWidth()) {
                Text("在应用内登录华为云，自动获取")
            }

            TextButton(onClick = { uriHandler.openUri(HUAWEI_ACCOUNT_HELP_URL) }) {
                Text("手动获取方法（打开官方说明）")
            }
        }
    }
}
