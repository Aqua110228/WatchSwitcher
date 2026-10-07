package nodomain.freeyourgadget.gadgetbridge.activities.files

import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.wififtp.WifiFtpSessionScreen
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityFtpFileBrowserBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSession
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSessionRegistry
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.internethelper.aidl.ftp.FtpEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lists the files of a device over its [WifiFtpSession].
 */
class FtpFileBrowserActivity : AbstractGBActivity(), MenuProvider {
    private lateinit var binding: ActivityFtpFileBrowserBinding
    private lateinit var device: GBDevice
    private lateinit var session: WifiFtpSession
    private lateinit var adapter: FtpFileBrowserAdapter

    private lateinit var sessionScreen: WifiFtpSessionScreen

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var path: String

    private val parentDirCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            list(path.substringBeforeLast('/').ifEmpty { "/" })
        }
    }

    // The remote path to download, while the document picker is open
    private var pendingDownload: String? = null

    private val downloadLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        sessionScreen.releasePickerHold()
        val remotePath = pendingDownload
        pendingDownload = null
        if (uri != null && remotePath != null) {
            download(remotePath, uri)
        }
    }

    private val uploadLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        sessionScreen.releasePickerHold()
        if (uri != null) {
            upload(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device: GBDevice? = intent.getParcelableCompat(GBDevice.EXTRA_DEVICE)
        val session = device?.let { WifiFtpSessionRegistry.get(it.address) }
        if (device == null || session == null) {
            GB.toast(this, getString(R.string.fwapp_install_device_not_ready), Toast.LENGTH_LONG, GB.ERROR)
            finish()
            return
        }
        this.device = device
        this.session = session
        path = session.rootDir
        sessionScreen = WifiFtpSessionScreen(this, device)

        binding = ActivityFtpFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addMenuProvider(this)
        onBackPressedDispatcher.addCallback(this, parentDirCallback)

        adapter = FtpFileBrowserAdapter(
            onOpen = { entry ->
                if (entry.type == FtpEntry.Type.DIRECTORY) {
                    list(childPath(entry))
                }
            },
            onDownload = { entry ->
                pendingDownload = childPath(entry)
                sessionScreen.holdForPicker()
                downloadLauncher.launch(entry.name)
            },
            onShare = { entry -> share(entry) },
            onDelete = { entry -> confirmDelete(entry) }
        )
        binding.ftpFileBrowserList.layoutManager = LinearLayoutManager(this)
        binding.ftpFileBrowserList.adapter = adapter
        binding.ftpFileBrowserRefresh.setOnRefreshListener { list(path) }
    }

    override fun onStart() {
        super.onStart()
        sessionScreen.runWhenReady({ list(path) }, { finish() })
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_ftp_file_browser, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.ftp_file_browser_upload -> {
                sessionScreen.holdForPicker()
                uploadLauncher.launch(arrayOf("*/*"))
                true
            }

            R.id.ftp_file_browser_refresh -> {
                list(path)
                true
            }

            else -> false
        }
    }

    private fun childPath(entry: FtpEntry): String = childPath(entry.name.orEmpty())

    private fun childPath(name: String): String {
        return if (path == "/") "/$name" else "$path/$name"
    }

    /**
     * Runs the operation on the executor, with the FTP client of the session.
     */
    private fun runFtp(operation: (InternetHelperFtpClient) -> Unit) {
        binding.ftpFileBrowserRefresh.isRefreshing = true
        executor.execute {
            try {
                val ftpClient = session.acquire()
                try {
                    operation(ftpClient)
                } finally {
                    session.release()
                }
            } catch (e: Exception) {
                LOG.error("FTP operation failed", e)
                runOnUiThread {
                    GB.toast(this, e.localizedMessage ?: e.javaClass.simpleName, Toast.LENGTH_LONG, GB.ERROR)
                }
            } finally {
                runOnUiThread { binding.ftpFileBrowserRefresh.isRefreshing = false }
            }
        }
    }

    private fun list(newPath: String) {
        runFtp { ftpClient ->
            val entries = ftpClient.list(newPath)
            LOG.debug("Got {} entries in {}", entries.size, newPath)
            runOnUiThread {
                path = newPath
                parentDirCallback.isEnabled = path != session.rootDir
                supportActionBar?.subtitle = path
                adapter.setEntries(entries)
            }
        }
    }

    private fun share(entry: FtpEntry) {
        val remotePath = childPath(entry)
        runFtp { ftpClient ->
            val exportDir = device.deviceCoordinator.getWritableExportDirectory(device, true)
            val targetFile = File(File(exportDir, "ftp"), remotePath.trimStart('/'))
            targetFile.parentFile?.mkdirs()
            LOG.debug("Downloading {} to {} for sharing", remotePath, targetFile)
            ftpClient.download(
                remotePath,
                ParcelFileDescriptor.open(
                    targetFile,
                    ParcelFileDescriptor.MODE_WRITE_ONLY or
                        ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE
                ),
                null
            )
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(targetFile.extension.lowercase()) ?: "*/*"
            runOnUiThread { AndroidUtils.shareFile(this, targetFile, mimeType) }
        }
    }

    private fun download(remotePath: String, uri: Uri) {
        runFtp { ftpClient ->
            val pfd = contentResolver.openFileDescriptor(uri, "wt")
                ?: throw IOException("Failed to open $uri")
            LOG.debug("Downloading {} to {}", remotePath, uri)
            ftpClient.download(remotePath, pfd, null)
            runOnUiThread {
                GB.toast(this, getString(R.string.ftp_file_browser_download_complete), Toast.LENGTH_SHORT, GB.INFO)
            }
        }
    }

    private fun confirmDelete(entry: FtpEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.music_delete_confirm_description, entry.name))
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val remotePath = childPath(entry)
                runFtp { ftpClient ->
                    LOG.debug("Deleting {}", remotePath)
                    ftpClient.delete(remotePath)
                    runOnUiThread { list(path) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun upload(uri: Uri) {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    size = cursor.getLong(1)
                }
            }
        val remotePath = childPath(FileUtils.makeValidFileName(name))
        runFtp { ftpClient ->
            LOG.debug("Uploading {} to {}", uri, remotePath)
            ftpClient.uploadUri(uri, remotePath, size, null)
            runOnUiThread { list(path) }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(FtpFileBrowserActivity::class.java)
    }
}
