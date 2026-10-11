/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.qr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nodomain.freeyourgadget.gadgetbridge.ui.theme.WatchSwitcherTheme

/**
 * Scans a QR code / barcode and returns its raw text, so a watch's pairing QR can be read.
 */
class QRScanActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONTENT = "content"

        fun newIntent(context: Context): Intent = Intent(context, QRScanActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchSwitcherTheme {
                QRScanScreen(
                    onBack = { finish() },
                    onResult = { content ->
                        runOnUiThread {
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_CONTENT, content))
                            finish()
                        }
                    },
                )
            }
        }
    }
}
