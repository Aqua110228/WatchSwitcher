package nodomain.freeyourgadget.gadgetbridge.service.ftp

import android.content.Context
import android.os.Handler
import android.os.Looper
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A reference-counted FTP connection to a device, over a Wi-Fi network of the device.
 */
abstract class WifiFtpSession(
    protected val context: Context,
    protected val device: GBDevice
) {
    enum class State {
        STOPPED,
        STARTING_HOTSPOT,
        CONNECTING_WIFI,
        STARTING_FTP_SERVER,
        CONNECTED,
    }

    fun interface Listener {
        fun onStateChanged(state: State)
    }

    /**
     * Keeps the session open until it is released or until it expires. It does not start the session.
     */
    interface Hold {
        fun release()
    }

    /**
     * The start directory on the device that the FTP server makes available.
     */
    abstract val rootDir: String

    @Volatile
    var state: State = State.STOPPED
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Guards the reference count and the idle timer. It is never held for long, so the UI thread can use it.
    private val refLock = Any()
    private var refCount = 0
    private var idleTeardown: ScheduledFuture<*>? = null

    // Guards the connection. It is held while the connection starts.
    private val connectionLock = Any()
    private var client: InternetHelperFtpClient? = null

    @Volatile
    private var startingThread: Thread? = null

    @Volatile
    protected var disposed = false
        private set

    /**
     * Opens the connection, and calls [setState] for each step.
     */
    @Throws(Exception::class)
    protected abstract fun open(): InternetHelperFtpClient

    /**
     * Stops the services on the device that [open] started. It must not send commands when [disposed] is true.
     */
    protected abstract fun shutdown()

    /**
     * Opens the connection if necessary, and blocks until it is ready. Each call must have one [release] call.
     */
    @Throws(IOException::class)
    fun acquire(): InternetHelperFtpClient {
        addRef()
        try {
            synchronized(connectionLock) {
                return client ?: start()
            }
        } catch (e: IOException) {
            release()
            throw e
        }
    }

    fun release() {
        synchronized(refLock) {
            refCount = maxOf(0, refCount - 1)
            LOG.debug("Released session for {}, {} references left", device.address, refCount)
            if (refCount == 0 && !scheduler.isShutdown) {
                cancelIdleTeardown()
                idleTeardown = scheduler.schedule({ closeIfIdle() }, IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun hold(maxDurationMs: Long = MAX_HOLD_MS): Hold {
        addRef()
        LOG.debug("Hold on session for {} for up to {} ms", device.address, maxDurationMs)
        val released = AtomicBoolean(false)
        val hold = object : Hold {
            override fun release() {
                if (released.compareAndSet(false, true)) {
                    LOG.debug("Hold on session for {} released", device.address)
                    this@WifiFtpSession.release()
                }
            }
        }
        if (!scheduler.isShutdown) {
            scheduler.schedule({
                if (!released.get()) {
                    LOG.warn("Hold on {} expired", device.address)
                }
                hold.release()
            }, maxDurationMs, TimeUnit.MILLISECONDS)
        }
        return hold
    }

    /**
     * Closes the connection now - also if it is in use or starting. Does not block.
     */
    fun close() {
        val starting = startingThread
        LOG.info("Close requested for session of {}, state={}, starting={}", device.address, state, starting != null)
        starting?.interrupt()
        if (!scheduler.isShutdown) {
            scheduler.execute {
                closeNow()
            }
        }
    }

    /**
     * Closes the connection after the device disconnected.
     */
    fun dispose() {
        LOG.info("Disposing session for {}", device.address)
        disposed = true
        close()
        scheduler.shutdown()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    protected fun setState(newState: State) {
        if (state == newState) {
            return
        }
        LOG.debug("Session state for {}: {}", device.address, newState)
        state = newState
        WifiFtpSessionNotification.update(context, device, newState)
        mainHandler.post {
            for (listener in listeners) {
                listener.onStateChanged(newState)
            }
        }
    }

    private fun start(): InternetHelperFtpClient {
        startingThread = Thread.currentThread()
        val start = System.currentTimeMillis()
        LOG.info("Opening session for {}", device.address)
        try {
            val opened = open()
            client = opened
            setState(State.CONNECTED)
            LOG.debug("Opened session for {} in {} ms", device.address, System.currentTimeMillis() - start)
            return opened
        } catch (e: Exception) {
            LOG.error(
                "Failed to open session for {} after {} ms in state {}",
                device.address,
                System.currentTimeMillis() - start,
                state,
                e
            )
            teardown()
            if (e is InterruptedException || Thread.interrupted()) {
                throw IOException(context.getString(R.string.wifi_ftp_error_closed), e)
            }
            throw e as? IOException ?: IOException(e.message ?: e.javaClass.simpleName, e)
        } finally {
            startingThread = null
        }
    }

    private fun addRef() {
        synchronized(refLock) {
            cancelIdleTeardown()
            refCount++
            LOG.debug("Acquired session for {}, {} references", device.address, refCount)
        }
    }

    private fun closeNow() {
        synchronized(refLock) {
            cancelIdleTeardown()
        }
        synchronized(connectionLock) {
            teardown()
        }
    }

    private fun closeIfIdle() {
        synchronized(refLock) {
            if (refCount > 0) {
                return
            }
        }
        synchronized(connectionLock) {
            if (client != null) {
                LOG.info("Closing idle session for {}", device.address)
                teardown()
            }
        }
    }

    private fun teardown() {
        if (state != State.STOPPED) {
            LOG.info("Stopping session for {} in state {}", device.address, state)
        }
        client?.close()
        client = null
        if (state != State.STOPPED) {
            shutdown()
        }
        setState(State.STOPPED)
    }

    private fun cancelIdleTeardown() {
        idleTeardown?.cancel(false)
        idleTeardown = null
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WifiFtpSession::class.java)

        private const val IDLE_TIMEOUT_MS = 10_000L
        const val MAX_HOLD_MS = 5 * 60_000L
    }
}
