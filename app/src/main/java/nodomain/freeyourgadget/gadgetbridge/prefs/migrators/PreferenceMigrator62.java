package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator62 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        migrateChartsPreference(62, "solarcharging");
    }
}
