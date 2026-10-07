package nodomain.freeyourgadget.gadgetbridge.util

import android.content.Context
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.automations.AbstractAutoExportSettingsFragment.Companion.LOG

object UriUtils {
    /**
     * Either returns the file path of the selected document, or the display name, or an error string
     */
    fun resolveLocationSummary(context: Context, uriString: String): String {
        if (uriString == "") {
            return ""
        }
        val uri = uriString.toUri()

        // Handle tree URIs (from ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && DocumentsContract.isTreeUri(uri)) {
            try {
                val treeDocId = DocumentsContract.getTreeDocumentId(uri)
                if ("com.android.externalstorage.documents" == uri.authority) {
                    val split = treeDocId.split(":", limit = 2)
                    if (split.size >= 2) {
                        return if (split[0] == "primary") {
                            "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
                        } else {
                            "/storage/${split[0]}/${split[1]}"
                        }
                    }
                }

                // For other providers, query the document URI built from the tree
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    }
                }
                return treeDocId
            } catch (e: Exception) {
                LOG.warn("getAutoExportLocationSummary tree", e)
            }
            return context.getString(R.string.auto_export_invalid_location, uriString)
        }

        try {
            return AndroidUtils.getFilePath(context.applicationContext, uri)
        } catch (e: IllegalArgumentException) {
            LOG.warn("getAutoExportLocationSummary 1", e)
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    }
                }
            } catch (e2: Exception) {
                LOG.warn("getAutoExportLocationSummary 2", e2)
            }
        }
        return context.getString(R.string.auto_export_invalid_location, uriString)
    }
}
