package nodomain.freeyourgadget.gadgetbridge.util.internethelper

import java.io.IOException

/**
 * An error from a service of the Internet Helper. The message is user-readable.
 */
open class InternetHelperException(message: String) : IOException(message)

/**
 * The Wi-Fi of the phone is off.
 */
class WifiDisabledException(message: String) : InternetHelperException(message)
