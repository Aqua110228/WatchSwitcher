package nodomain.freeyourgadget.gadgetbridge.util.internethelper

import android.os.RemoteException
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.internethelper.aidl.wifi.IWifiCallback
import nodomain.freeyourgadget.internethelper.aidl.wifi.IWifiService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InternetHelperWifiConnection private constructor(
    private val binding: InternetHelperBinding<IWifiService>,
    private val onLost: Runnable
) {
    @Volatile
    var networkRequestId: String? = null
        private set

    private val connectLatch = CountDownLatch(1)

    @Volatile
    private var error: String? = null

    private val callback = object : IWifiCallback.Stub() {
        override fun onAvailable(networkRequestId: String) {
            LOG.info("Wi-Fi network {} available", networkRequestId)
            connectLatch.countDown()
        }

        override fun onUnavailable(networkRequestId: String, msg: String?) {
            LOG.warn("Wi-Fi network {} unavailable: {}", networkRequestId, msg)
            error = msg ?: "Wi-Fi network not available"
            connectLatch.countDown()
        }

        override fun onLost(networkRequestId: String) {
            LOG.warn("Wi-Fi network {} lost", networkRequestId)
            onLost.run()
        }
    }

    /**
     * Connects to the Wi-Fi network and blocks until it is available.
     */
    @Throws(InternetHelperException::class)
    fun connect(ssid: String, password: String, timeoutMs: Long) {
        val start = System.currentTimeMillis()
        try {
            networkRequestId = binding.service.connect(ssid, password, callback)
            LOG.info("Requested Wi-Fi network {}: {}", ssid, networkRequestId)
        } catch (e: RemoteException) {
            LOG.error("Failed to request Wi-Fi network {}", ssid, e)
            throw InternetHelperException(e.message ?: "Failed to connect to $ssid")
        }

        if (!connectLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            LOG.error("Timeout after {} ms while connecting to {}", timeoutMs, ssid)
            throw InternetHelperException("Timeout while connecting to $ssid")
        }
        error?.let {
            LOG.error("Failed to connect to {}: {}", ssid, it)
            throw InternetHelperException(it)
        }
        LOG.info("Connected to {} after {} ms", ssid, System.currentTimeMillis() - start)
    }

    fun disconnect() {
        val id = networkRequestId
        networkRequestId = null
        LOG.debug("Disconnecting from Wi-Fi network {}", id)
        if (id != null) {
            try {
                binding.service.disconnect(id)
            } catch (e: RemoteException) {
                LOG.warn("Failed to disconnect from Wi-Fi network {}", id, e)
            }
        }
        binding.unbind()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InternetHelperWifiConnection::class.java)

        private const val ACTION = "nodomain.freeyourgadget.internethelper.WifiService"
        private const val MIN_VERSION = 2

        /**
         * Binds to the Internet Helper Wi-Fi service. Fails if Wi-Fi is off.
         */
        @JvmStatic
        @Throws(InternetHelperException::class)
        fun open(onLost: Runnable): InternetHelperWifiConnection {
            val binding = InternetHelperBinding.bind(ACTION, { IWifiService.Stub.asInterface(it) })
            val connection = InternetHelperWifiConnection(binding, onLost)
            try {
                val version = binding.service.version()
                LOG.debug("Internet Helper Wi-Fi service version {}", version)
                if (version < MIN_VERSION) {
                    LOG.error("Internet Helper Wi-Fi service version {} is older than {}", version, MIN_VERSION)
                    throw InternetHelperException(
                        GBApplication.getContext().getString(R.string.internet_helper_error_old_version)
                    )
                }
                if (!binding.service.isWifiEnabled) {
                    LOG.warn("Wi-Fi is off")
                    throw WifiDisabledException(
                        GBApplication.getContext().getString(R.string.wifi_ftp_error_wifi_disabled)
                    )
                }
            } catch (e: Exception) {
                LOG.error("Internet Helper Wi-Fi service is not available", e)
                connection.disconnect()
                throw e as? InternetHelperException ?: InternetHelperException(e.message ?: e.javaClass.simpleName)
            }
            return connection
        }

        /**
         * Checks that the Internet Helper can connect to a Wi-Fi network.
         */
        @JvmStatic
        @Throws(InternetHelperException::class)
        fun check() {
            open {}.disconnect()
        }
    }
}
