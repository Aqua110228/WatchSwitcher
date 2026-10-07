/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.huawei

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme

/**
 * Lets the user log into Huawei Cloud in an embedded browser so the app can read the `userId`
 * cookie (the Huawei account ID) automatically, instead of the manual devtools step.
 */
class HuaweiLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"

        fun newIntent(context: Context): Intent = Intent(context, HuaweiLoginActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchSwitcherTheme {
                HuaweiLoginScreen(
                    onBack = { finish() },
                    onExtract = { extractAccountId() },
                )
            }
        }
    }

    private fun extractAccountId() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val cookie = cookieManager.getCookie(HUAWEI_CLOUD_URL)
        val accountId = cookie
            ?.split(";")
            ?.asSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("userId=") }
            ?.substringAfter("=")
            ?.trim()

        if (!accountId.isNullOrBlank()) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_ACCOUNT_ID, accountId))
            finish()
        } else {
            Toast.makeText(this, "未找到 userId，请确认已在网页中成功登录华为账号", Toast.LENGTH_LONG).show()
        }
    }
}
