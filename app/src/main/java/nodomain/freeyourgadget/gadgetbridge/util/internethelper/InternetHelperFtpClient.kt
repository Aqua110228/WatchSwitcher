package nodomain.freeyourgadget.gadgetbridge.util.internethelper

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.internethelper.aidl.ftp.FtpEntry
import nodomain.freeyourgadget.internethelper.aidl.ftp.IFtpCallback
import nodomain.freeyourgadget.internethelper.aidl.ftp.IFtpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An FTP client from the Internet Helper, blocking. Operations are executed sequentially, one at a time.
 */
class InternetHelperFtpClient private constructor(
    private val binding: InternetHelperBinding<IFtpService>
) {
    fun interface ProgressListener {
        fun onProgress(bytes: Long, total: Long)
    }

    private class Pending(val path: String?) {
        val latch = CountDownLatch(1)

        @Volatile
        var lastActivity = System.currentTimeMillis()

        @Volatile
        var error: String? = null

        @Volatile
        var entries: List<FtpEntry> = emptyList()

        @Volatile
        var progressListener: ProgressListener? = null

        fun finish(success: Boolean, msg: String?) {
            if (!success) {
                error = msg ?: "Unknown error"
            }
            latch.countDown()
        }
    }

    @Volatile
    private var pending: Pending? = null

    private val callback = object : IFtpCallback.Stub() {
        override fun onConnect(success: Boolean, msg: String?) {
            finishPending("connect", null, success, msg)
        }

        override fun onLogin(success: Boolean, msg: String?) {
            finishPending("login", null, success, msg)
        }

        override fun onList(path: String, success: Boolean, entries: List<FtpEntry>?, msg: String?) {
            pending?.entries = entries ?: emptyList()
            finishPending("list", path, success, msg)
        }

        override fun onProgress(path: String, bytes: Long, total: Long) {
            val p = pending ?: return
            p.lastActivity = System.currentTimeMillis()
            p.progressListener?.onProgress(bytes, total)
        }

        override fun onUpload(path: String, success: Boolean, msg: String?) {
            finishPending("upload", path, success, msg)
        }

        override fun onDownload(path: String, success: Boolean, msg: String?) {
            finishPending("download", path, success, msg)
        }

        override fun onDelete(path: String, success: Boolean, msg: String?) {
            finishPending("delete", path, success, msg)
        }

        override fun onMkdirs(path: String, success: Boolean, msg: String?) {
            finishPending("mkdirs", path, success, msg)
        }

        override fun onDisconnect(msg: String?) {
            if (pending == null) {
                LOG.warn("FTP client {} disconnected: {}", clientId, msg)
                return
            }
            finishPending("disconnect", null, true, null)
        }
    }

    private fun finishPending(name: String, path: String?, success: Boolean, msg: String?) {
        val p = pending
        if (p == null) {
            // The operation timed out before its callback was called
            LOG.warn("Ignoring late FTP {} callback for {}: success={}, msg={}", name, path, success, msg)
            return
        }
        p.finish(success, msg)
    }

    private val clientId: String = binding.service.createClient(callback).also {
        LOG.debug("Created FTP client {}", it)
    }

    /**
     * Runs the request and blocks until its callback arrives. The timeout restarts on each progress update.
     */
    @Synchronized
    @Throws(IOException::class)
    private fun run(
        name: String,
        path: String?,
        progressListener: ProgressListener? = null,
        request: () -> Unit,
    ): Pending {
        val p = Pending(path)
        p.progressListener = progressListener
        pending = p
        val start = System.currentTimeMillis()
        LOG.debug("FTP {} {} started", name, path)
        try {
            request()
            while (!p.latch.await(1, TimeUnit.SECONDS)) {
                if (System.currentTimeMillis() - p.lastActivity > TIMEOUT_MS) {
                    LOG.error("FTP {} {} timed out after {} ms", name, path, System.currentTimeMillis() - start)
                    throw IOException("Timeout during FTP $name ${path.orEmpty()}")
                }
            }
        } catch (e: RemoteException) {
            LOG.error("FTP {} {} failed in the Internet Helper", name, path, e)
            throw IOException("FTP $name failed: ${e.message}", e)
        } finally {
            pending = null
        }
        p.error?.let {
            LOG.error("FTP {} {} failed after {} ms: {}", name, path, System.currentTimeMillis() - start, it)
            throw IOException("FTP $name ${path.orEmpty()} failed: $it")
        }
        LOG.debug("FTP {} {} finished after {} ms", name, path, System.currentTimeMillis() - start)
        return p
    }

    @Throws(IOException::class)
    fun connect(host: String, port: Int, networkRequestId: String?) {
        run("connect", "$host:$port") { binding.service.connect(clientId, host, port, networkRequestId) }
    }

    @Throws(IOException::class)
    fun login(username: String, password: String) {
        run("login", username) { binding.service.login(clientId, username, password) }
    }

    @Throws(IOException::class)
    fun list(path: String): List<FtpEntry> {
        val entries = run("list", path) { binding.service.list(clientId, path) }.entries
        LOG.debug("Got {} entries in {}", entries.size, path)
        return entries
    }

    @Throws(IOException::class)
    fun delete(path: String) {
        run("delete", path) { binding.service.delete(clientId, path) }
    }

    @Throws(IOException::class)
    fun mkdirs(path: String) {
        run("mkdirs", path) { binding.service.mkdirs(clientId, path) }
    }

    @Throws(IOException::class)
    fun upload(src: ParcelFileDescriptor, remoteDest: String, size: Long, progressListener: ProgressListener?) {
        src.use {
            run("upload", remoteDest, progressListener) { binding.service.upload(clientId, src, remoteDest, size) }
        }
    }

    @Throws(IOException::class)
    fun uploadUri(uri: Uri, remoteDest: String, size: Long, progressListener: ProgressListener?) {
        val pfd = GBApplication.getContext().contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Failed to open $uri")
        upload(pfd, remoteDest, size, progressListener)
    }

    /**
     * Uploads the stream through a pipe. The stream is not closed.
     */
    @Throws(IOException::class)
    fun uploadStream(inputStream: InputStream, remoteDest: String, size: Long, progressListener: ProgressListener?) {
        val (pipeRead, pipeWrite) = ParcelFileDescriptor.createPipe()
        val writer = Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipeWrite).use { inputStream.copyTo(it) }
            } catch (e: IOException) {
                LOG.warn("Failed to write {} to pipe", remoteDest, e)
            }
        }, "ftp-upload-pipe")
        writer.start()
        try {
            upload(pipeRead, remoteDest, size, progressListener)
        } finally {
            writer.join()
        }
    }

    @Throws(IOException::class)
    fun download(remoteSrc: String, dest: ParcelFileDescriptor, progressListener: ProgressListener?) {
        dest.use {
            run("download", remoteSrc, progressListener) { binding.service.download(clientId, remoteSrc, dest) }
        }
    }

    @Throws(IOException::class)
    fun downloadBytes(remoteSrc: String): ByteArray {
        val (pipeRead, pipeWrite) = ParcelFileDescriptor.createPipe()
        val baos = ByteArrayOutputStream()
        val reader = Thread({
            try {
                ParcelFileDescriptor.AutoCloseInputStream(pipeRead).use { it.copyTo(baos) }
            } catch (e: IOException) {
                LOG.warn("Failed to read {} from pipe", remoteSrc, e)
            }
        }, "ftp-download-pipe")
        reader.start()
        try {
            download(remoteSrc, pipeWrite, null)
        } finally {
            reader.join()
        }
        LOG.debug("Downloaded {} bytes from {}", baos.size(), remoteSrc)
        return baos.toByteArray()
    }

    fun close() {
        LOG.debug("Closing FTP client {}", clientId)
        try {
            run("disconnect", null) { binding.service.disconnect(clientId) }
        } catch (e: IOException) {
            LOG.warn("Failed to disconnect", e)
        }
        try {
            binding.service.destroyClient(clientId)
        } catch (e: RemoteException) {
            LOG.warn("Failed to destroy client {}", clientId, e)
        }
        binding.unbind()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InternetHelperFtpClient::class.java)

        private const val ACTION = "nodomain.freeyourgadget.internethelper.FtpService"
        private const val MIN_VERSION = 2
        private const val TIMEOUT_MS = 30_000L

        /**
         * Connects and logs in to the FTP server. The network request id selects the Wi-Fi network from
         * [InternetHelperWifiConnection]. A null network request id selects the default network.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun connect(
            host: String,
            port: Int,
            networkRequestId: String?,
            username: String,
            password: String,
        ): InternetHelperFtpClient {
            val binding = InternetHelperBinding.bind(ACTION, { IFtpService.Stub.asInterface(it) })
            val client = try {
                val version = binding.service.version()
                LOG.debug("Internet Helper FTP service version {}", version)
                if (version < MIN_VERSION) {
                    LOG.error("Internet Helper FTP service version {} is older than {}", version, MIN_VERSION)
                    throw InternetHelperException(
                        GBApplication.getContext().getString(R.string.internet_helper_error_old_version)
                    )
                }
                InternetHelperFtpClient(binding)
            } catch (e: RemoteException) {
                LOG.error("Failed to create FTP client", e)
                binding.unbind()
                throw InternetHelperException(e.message ?: "Failed to create FTP client")
            } catch (e: InternetHelperException) {
                binding.unbind()
                throw e
            }

            try {
                client.connect(host, port, networkRequestId)
                client.login(username, password)
            } catch (e: IOException) {
                client.close()
                throw e
            }
            return client
        }
    }
}
