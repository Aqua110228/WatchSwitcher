package nodomain.freeyourgadget.gadgetbridge.service.ftp

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The [WifiFtpSession] of each connected device, by device address.
 */
object WifiFtpSessionRegistry {
    private val LOG: Logger = LoggerFactory.getLogger(WifiFtpSessionRegistry::class.java)

    private val sessions: MutableMap<String, WifiFtpSession> = ConcurrentHashMap()

    @JvmStatic
    fun register(address: String, session: WifiFtpSession) {
        LOG.debug("Registering session for {}", address)
        sessions[address] = session
    }

    @JvmStatic
    fun unregister(address: String, session: WifiFtpSession) {
        LOG.debug("Unregistering session for {}", address)
        sessions.remove(address, session)
    }

    @JvmStatic
    fun get(address: String): WifiFtpSession? = sessions[address]
}
