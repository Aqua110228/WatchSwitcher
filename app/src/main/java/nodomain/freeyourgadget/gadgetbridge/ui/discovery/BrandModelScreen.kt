/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

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
private fun PickerScreen(
    title: String,
    subtitle: String?,
    names: List<String>,
    onBack: () -> Unit,
    onSelectIndex: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (subtitle != null) {
                item {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(names.size) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectIndex(index) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(names[index], modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun BrandListScreen(
    brands: List<BrandOption>,
    onBack: () -> Unit,
    onSelectBrand: (BrandOption) -> Unit,
) {
    PickerScreen(
        title = "选择品牌",
        subtitle = "先选择手表 / 手环的品牌，再选择型号，最后扫描连接。",
        names = brands.map { it.name },
        onBack = onBack,
        onSelectIndex = { onSelectBrand(brands[it]) },
    )
}

@Composable
fun ModelListScreen(
    brand: BrandOption,
    onBack: () -> Unit,
    onSelectModel: (ModelOption?) -> Unit,
) {
    val names = listOf("全部型号（不筛选）") + brand.models.map { it.name }
    PickerScreen(
        title = brand.name,
        subtitle = "选择型号；选「全部型号」则扫描时不做筛选。",
        names = names,
        onBack = onBack,
        onSelectIndex = { index ->
            onSelectModel(if (index == 0) null else brand.models[index - 1])
        },
    )
}

@Composable
fun HuaweiIdStepScreen(
    isHuawei: Boolean,
    account: String,
    onBack: () -> Unit,
    onOpenHuaweiId: () -> Unit,
    onContinue: () -> Unit,
) {
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "华为 / 荣耀手表若已绑定华为账号，连接时需要同一个账号 ID（17 位数字），" +
                    "否则会被要求恢复出厂。建议先在这里设置好，再选择型号并扫描。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (account.isBlank()) "当前：未设置" else "当前：已设置（${account.take(4)}…）",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenHuaweiId, modifier = Modifier.fillMaxWidth()) {
                Text(if (account.isBlank()) "设置华为账号 ID" else "修改华为账号 ID")
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("继续（选择型号）")
            }
        }
    }
}
