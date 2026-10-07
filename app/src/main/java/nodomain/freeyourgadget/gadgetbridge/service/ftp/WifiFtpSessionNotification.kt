package nodomain.freeyourgadget.gadgetbridge.service.ftp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The ongoing notification of a live [WifiFtpSession].
 */
object WifiFtpSessionNotification {
    private val LOG: Logger = LoggerFactory.getLogger(WifiFtpSessionNotification::class.java)

    fun update(context: Context, device: GBDevice, state: WifiFtpSession.State) {
        if (state == WifiFtpSession.State.STOPPED) {
            cancel(context, device.address)
            return
        }

        val stopIntent = Intent(context, WifiFtpSessionStopReceiver::class.java)
            .putExtra(WifiFtpSessionStopReceiver.EXTRA_DEVICE_ADDRESS, device.address)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            device.address.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setSmallIcon(R.drawable.ic_wifi_tethering)
            .setContentTitle(context.getString(R.string.wifi_ftp_notification_title, device.aliasOrName))
            .setContentText(context.getString(stateText(state)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, state != WifiFtpSession.State.CONNECTED)
            .addAction(
                R.drawable.ic_notification_disconnected,
                context.getString(R.string.controlcenter_disconnect),
                stopPendingIntent
            )
            .build()

        GB.createNotificationChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(device.address, GB.NOTIFICATION_ID_WIFI_FTP, notification)
        } catch (e: SecurityException) {
            LOG.warn("No permission to show the notification", e)
        }
    }

    fun cancel(context: Context, address: String) {
        NotificationManagerCompat.from(context).cancel(address, GB.NOTIFICATION_ID_WIFI_FTP)
    }

    fun stateText(state: WifiFtpSession.State): Int {
        return when (state) {
            WifiFtpSession.State.STOPPED -> R.string.wifi_ftp_state_stopped
            WifiFtpSession.State.STARTING_HOTSPOT -> R.string.wifi_ftp_state_starting_hotspot
            WifiFtpSession.State.CONNECTING_WIFI -> R.string.wifi_ftp_state_connecting_wifi
            WifiFtpSession.State.STARTING_FTP_SERVER -> R.string.wifi_ftp_state_starting_ftp_server
            WifiFtpSession.State.CONNECTED -> R.string.wifi_ftp_state_connected
        }
    }
}
