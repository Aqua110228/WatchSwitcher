package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.DrawableRes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;

public class NavigationUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NavigationUtils.class);

    /**
     * Prefix of the fake package names used for navigation icons. Devices usually cache icons
     * by package name - change the version if the drawable changes.
     */
    private static final String ICON_PACKAGE_PREFIX = "gadgetbridge.nav.v1.";

    public static boolean shouldSendNavigation(final Context context, final String appName) {
        final Prefs prefs = GBApplication.getPrefs();

        final boolean navigationForward = prefs.getBoolean("navigation_forward", true);
        final boolean navigationApp = prefs.getBoolean("navigation_app_" + appName, true);
        if (!navigationForward || !navigationApp) {
            LOG.info("Not forwarding navigation instruction for {}, user preferences do not allow this", appName);
            return false;
        }

        final boolean navigationScreenOn = prefs.getBoolean("nagivation_screen_on", true);
        if (!navigationScreenOn) {
            final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isScreenOn()) {
                LOG.info("Not forwarding navigation instructions, screen seems to be on and settings do not allow this");
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the fake package name for the icon of a navigation action.
     */
    public static String getIconPackageName(final int action) {
        return ICON_PACKAGE_PREFIX + action;
    }

    /**
     * Returns the drawable for a package name that matches {@link #getIconPackageName}, or 0 if not recognized.
     */
    @DrawableRes
    public static int getIconResource(final String packageName) {
        if (packageName == null || !packageName.startsWith(ICON_PACKAGE_PREFIX)) {
            return 0;
        }

        final int action;
        try {
            action = Integer.parseInt(packageName.substring(ICON_PACKAGE_PREFIX.length()));
        } catch (final NumberFormatException e) {
            return 0;
        }

        return switch (action) {
            case NavigationInfoSpec.ACTION_CONTINUE -> R.drawable.ic_turn_straight;
            case NavigationInfoSpec.ACTION_TURN_LEFT -> R.drawable.ic_turn_left;
            case NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY, NavigationInfoSpec.ACTION_KEEP_LEFT ->
                R.drawable.ic_turn_left_slight;
            case NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY -> R.drawable.ic_turn_left_sharp;
            case NavigationInfoSpec.ACTION_TURN_RIGHT -> R.drawable.ic_turn_right;
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY, NavigationInfoSpec.ACTION_KEEP_RIGHT ->
                R.drawable.ic_turn_right_slight;
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY -> R.drawable.ic_turn_right_sharp;
            case NavigationInfoSpec.ACTION_UTURN_LEFT -> R.drawable.ic_turn_uleft;
            case NavigationInfoSpec.ACTION_UTURN_RIGHT -> R.drawable.ic_turn_uright;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT -> R.drawable.ic_turn_round_left;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT -> R.drawable.ic_turn_round_right;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_STRAIGHT -> R.drawable.ic_turn_round_straight;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_UTURN -> R.drawable.ic_turn_round_uturn;
            case NavigationInfoSpec.ACTION_FINISH -> R.drawable.ic_turn_finish;
            default -> 0;
        };
    }
}
