package nodomain.freeyourgadget.gadgetbridge.util.internethelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.InternetUtils
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A binding to one service of the Internet Helper, made with a blocking call.
 */
class InternetHelperBinding<T> private constructor(
    private val connection: ServiceConnection,
    val service: T,
) {
    fun unbind() {
        LOG.debug("Unbinding from {}", service)
        try {
            GBApplication.getContext().unbindService(connection)
        } catch (e: IllegalArgumentException) {
            LOG.warn("Service was not bound", e)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InternetHelperBinding::class.java)

        @Throws(InternetHelperException::class)
        fun <T> bind(action: String, asInterface: (IBinder) -> T, timeoutMs: Long = 5000): InternetHelperBinding<T> {
            val context = GBApplication.getContext()
            val installed = AndroidUtils.isPackageInstalled(PermissionsUtils.PACKAGE_INTERNET_HELPER)
            val hasPermission = PermissionsUtils.checkPermission(context, PermissionsUtils.CUSTOM_PERM_INTERNET_HELPER)
            if (!installed || !hasPermission) {
                LOG.error("Cannot bind to {}: installed={}, hasPermission={}", action, installed, hasPermission)
                throw InternetHelperException(InternetUtils.noInternetAccessReason(context))
            }

            val latch = CountDownLatch(1)
            var service: T? = null
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                    LOG.debug("Bound to {}", name)
                    service = asInterface(binder)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    LOG.warn("Disconnected from {}", name)
                }
            }

            val intent = Intent(action).setPackage(PermissionsUtils.PACKAGE_INTERNET_HELPER)
            val bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                LOG.error("Not allowed to bind to {}", action, e)
                false
            }
            if (!bound) {
                LOG.error("Failed to bind to {}", action)
                throw InternetHelperException(InternetUtils.noInternetAccessReason(context))
            }

            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS) || service == null) {
                LOG.error("Timeout after {} ms while binding to {}", timeoutMs, action)
                context.unbindService(connection)
                throw InternetHelperException(InternetUtils.noInternetAccessReason(context))
            }

            return InternetHelperBinding(connection, service!!)
        }
    }
}
