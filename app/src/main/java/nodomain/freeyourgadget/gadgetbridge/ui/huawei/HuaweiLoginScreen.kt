/*  Copyright (C) 2026 HuaweiSwitcher contributors

    This file is part of HuaweiSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.huawei

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** Huawei Cloud login page — the account ID is stored as the `userId` cookie for this domain. */
const val HUAWEI_CLOUD_URL = "https://cloud.huawei.com/"

/**
 * In-app browser used to obtain the Huawei account ID: the user logs into Huawei Cloud, and the
 * caller then reads the `userId` cookie via [CookieManager]. This replaces the manual
 * "open browser devtools and copy userId" step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuaweiLoginScreen(
    onBack: () -> Unit,
    onExtract: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录华为云获取账号 ID") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onExtract) { Text("提取") }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "在下方网页登录你的华为账号，登录成功后点右上角「提取」，会自动读取账号 ID（userId）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = WebViewClient()
                        loadUrl(HUAWEI_CLOUD_URL)
                    }
                },
            )
        }
    }
}
