package nodomain.freeyourgadget.gadgetbridge.prefs;

import android.content.SharedPreferences;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;

public abstract class AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPreferenceMigrator.class);

    protected static final String TAG = "GBPrefMigration";

    private static final String PREF_CHARTS_TABS = "charts_tabs";

    public abstract void migrate(int oldVersion, SharedPreferences sharedPrefs, SharedPreferences.Editor editor);

    protected void migrateChartsPreference(final int version, final String newChart) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            final List<Device> activeDevices = DBHelper.getActiveDevices(daoSession);

            for (final Device dbDevice : activeDevices) {
                final SharedPreferences deviceSharedPrefs = GBApplication.getDeviceSpecificSharedPrefs(dbDevice.getIdentifier());
                final String chartsTabsValue = deviceSharedPrefs.getString(PREF_CHARTS_TABS, null);
                if (chartsTabsValue == null) {
                    continue;
                }

                final String newPrefValue;
                if (StringUtils.isBlank(chartsTabsValue)) {
                    newPrefValue = newChart;
                } else if (chartsTabsValue.contains(newChart)) {
                    newPrefValue = chartsTabsValue;
                } else {
                    newPrefValue = chartsTabsValue + "," + newChart;
                }

                deviceSharedPrefs.edit()
                        .putString(PREF_CHARTS_TABS, newPrefValue)
                        .apply();
            }
        } catch (final Exception e) {
            LOG.error("Failed to add new chart preference {} on version {}", newChart, version, e);
        }
    }
}
