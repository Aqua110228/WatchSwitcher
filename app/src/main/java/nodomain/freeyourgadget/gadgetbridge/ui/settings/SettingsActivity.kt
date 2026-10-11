/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.Logging
import nodomain.freeyourgadget.gadgetbridge.activities.ControlCenterv2
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.HuaweiIdActivity
import nodomain.freeyourgadget.gadgetbridge.ui.huawei.getHuaweiAccount
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity as GbSettingsActivity
import java.io.File

/**
 * Compose settings screen. The rest of Gadgetbridge's settings remain reachable through the
 * full settings screen. Also exposes a simple log on/off + share, for troubleshooting.
 */
class SettingsActivity : ComponentActivity() {

    private val loggingEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loggingEnabled.value = Logging.getInstance().logPath != null

        setContent {
            WatchSwitcherTheme {
                SettingsScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    huaweiAccount = getHuaweiAccount(),
                    loggingEnabled = loggingEnabled.value,
                    onBack = { finish() },
                    onOpenFullSettings = {
                        startActivity(Intent(this, GbSettingsActivity::class.java))
                    },
                    onOpenHuaweiId = {
                        startActivity(HuaweiIdActivity.newIntent(this))
                    },
                    onOpenClassicUi = {
                        startActivity(Intent(this, ControlCenterv2::class.java))
                    },
                    onToggleLogging = { toggleLogging() },
                    onShareLogs = { shareLogs() },
                )
            }
        }
    }

    private fun toggleLogging() {
        val enable = !loggingEnabled.value
        runCatching { Logging.getInstance().setFileLoggingEnabled(enable) }
        GBApplication.getPrefs().preferences.edit().putBoolean("log_to_file", enable).apply()
        loggingEnabled.value = enable
        Toast.makeText(
            this,
            if (enable) "已开启日志记录，复现问题后再点「分享日志文件」" else "已关闭日志记录",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun shareLogs() {
        val logging = Logging.getInstance()
        logging.flush()
        val path = logging.logPath
        if (path.isNullOrEmpty()) {
            Toast.makeText(this, "还没有日志文件，请先开启日志记录并复现问题", Toast.LENGTH_LONG).show()
            return
        }
        val logFile = File(path)
        if (!logFile.exists()) {
            Toast.makeText(this, "日志文件不存在", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.screenshot_provider", logFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "WatchSwitcher log")
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(intent, "分享日志"))
    }
}
