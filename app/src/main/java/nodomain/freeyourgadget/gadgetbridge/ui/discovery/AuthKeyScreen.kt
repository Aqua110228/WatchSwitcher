/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Docs about obtaining the auth key for Xiaomi/Huami/Zepp (and other key-protected) devices. */
const val AUTH_KEY_HELP_URL = "https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/"

/**
 * Asks for the vendor auth key of a key-protected device (Xiaomi/Huami/Zepp, CMF, …). The key is
 * a per-device secret created when pairing with the vendor app, so it must be extracted from there.
 */
@Composable
fun AuthKeyPromptDialog(
    deviceName: String,
    secondaryHintRes: Int,
    onDismiss: () -> Unit,
    onConfirm: (primary: String, secondary: String) -> Unit,
    validate: (primary: String, secondary: String) -> Boolean,
) {
    var primary by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val secondaryLabel = if (secondaryHintRes != 0) {
        runCatching { stringResource(secondaryHintRes) }.getOrDefault("第二密钥")
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要授权密钥") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "「$deviceName」使用加密协议，需要授权密钥才能连接。请先用官方 App 配对该设备，" +
                        "再从它的日志里取出密钥（例如小米穿戴 / Mi Fitness：" +
                        "Android/data/com.xiaomi.wearable/files/log/XiaomiFit.device.log 中 " +
                        "搜 token 或 encryptKey，32 位十六进制，可能带 0x 前缀）。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("授权密钥") },
                )
                if (secondaryLabel != null) {
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it; error = null },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(secondaryLabel) },
                    )
                }
                TextButton(onClick = { uriHandler.openUri(AUTH_KEY_HELP_URL) }) {
                    Text("如何获取授权密钥（打开说明网页）")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (validate(primary, secondary)) {
                    onConfirm(primary, secondary)
                } else {
                    error = "密钥格式不正确（或第二密钥为空）"
                }
            }) { Text("保存并连接") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
