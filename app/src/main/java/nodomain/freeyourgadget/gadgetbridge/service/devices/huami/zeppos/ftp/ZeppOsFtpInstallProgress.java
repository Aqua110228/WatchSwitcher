package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ftp;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.StringRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * The progress of an FTP upload, for both the notification and the activity.
 */
class ZeppOsFtpInstallProgress {
    private final Context context;
    private final int inProgressRes;
    private final int completeRes;
    private final int failedRes;
    private int lastPercent = -1;

    ZeppOsFtpInstallProgress(final Context context,
                             @StringRes final int inProgressRes,
                             @StringRes final int completeRes,
                             @StringRes final int failedRes) {
        this.context = context;
        this.inProgressRes = inProgressRes;
        this.completeRes = completeRes;
        this.failedRes = failedRes;
    }

    void update(final long bytes, final long total) {
        final int percent = total > 0 ? (int) Math.min(100, bytes * 100 / total) : 0;
        if (percent == lastPercent) {
            return;
        }
        lastPercent = percent;

        GB.updateInstallNotification(context.getString(inProgressRes), true, percent, context);
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            new Intent(GB.ACTION_SET_PROGRESS_BAR).putExtra(GB.PROGRESS_BAR_PROGRESS, percent)
        );
    }

    void finish(final boolean success, final String error) {
        String message = context.getString(success ? completeRes : failedRes);
        if (!success && error != null) {
            message = message + ": " + error;
        }

        GB.updateInstallNotification(message, false, 100, context);
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        broadcastManager.sendBroadcast(new Intent(GB.ACTION_SET_INFO_TEXT).putExtra(GB.DISPLAY_MESSAGE_MESSAGE, ""));
        broadcastManager.sendBroadcast(new Intent(GB.ACTION_SET_PROGRESS_TEXT).putExtra(GB.DISPLAY_MESSAGE_MESSAGE, message));
        broadcastManager.sendBroadcast(new Intent(GB.ACTION_SET_FINISHED));
    }
}
