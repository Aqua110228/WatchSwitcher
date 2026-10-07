package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundCycle;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerBand;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerBandLevel;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerPreset;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsNoiseCancellingStrength;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsPreferenceEntry;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTransparencyStrength;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;

/**
 * Migrate the Redmi Buds preferences from the per-model keys and numeric protocol values to the
 * shared keys and the named values used by the new settings DSL.
 */
public class PreferenceMigrator64 extends AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(PreferenceMigrator64.class);

    /**
     * The key prefixes the models used.
     */
    private static final List<String> OLD_PREFIXES = List.of(
        "pref_redmi_buds_5_pro_",
        "pref_redmi_buds_6_active_",
        "pref_redmi_buds_8_active_"
    );

    private static final String NEW_PREFIX = "pref_redmi_buds_";

    /**
     * Every preference whose value changed from a protocol value number to a name, mapped to the values that
     * preference can hold.
     */
    private static final Map<String, Map<String, String>> NAMED_SETTINGS = namedSettings();

    /**
     * Every preference that only changed key, and so keeps its value.
     */
    private static final List<String> RENAMED_SETTINGS = List.of(
        "adaptive_noise_cancelling",
        "adaptive_sound",
        "auto_reply_phonecall",
        "double_connection",
        "wearing_detection"
    );

    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        try (DBHandler db = GBApplication.acquireDB()) {
            for (final Device dbDevice : DBHelper.getActiveDevices(db.getDaoSession())) {
                migrateDevice(GBApplication.getDeviceSpecificSharedPrefs(dbDevice.getIdentifier()));
            }
        } catch (final Exception e) {
            LOG.error("Failed to migrate the Redmi Buds preferences to version 64", e);
        }
    }

    private void migrateDevice(final SharedPreferences devicePrefs) {
        final SharedPreferences.Editor editor = devicePrefs.edit();

        for (final String oldPrefix : OLD_PREFIXES) {
            for (final String suffix : RENAMED_SETTINGS) {
                rename(devicePrefs, editor, oldPrefix + suffix, NEW_PREFIX + suffix);
            }

            for (final Map.Entry<String, Map<String, String>> setting : NAMED_SETTINGS.entrySet()) {
                final String oldKey = oldPrefix + setting.getKey();
                final String oldValue = devicePrefs.getString(oldKey, null);
                if (oldValue == null) {
                    continue;
                }

                editor.remove(oldKey);

                final String newValue = setting.getValue().get(oldValue);
                if (newValue == null) {
                    LOG.warn("Dropping unknown old value '{}' from {}", oldValue, oldKey);
                    continue;
                }

                editor.putString(NEW_PREFIX + setting.getKey(), newValue);
            }
        }

        editor.apply();
    }

    private void rename(final SharedPreferences devicePrefs, final SharedPreferences.Editor editor,
                        final String oldKey, final String newKey) {
        if (!devicePrefs.contains(oldKey)) {
            return;
        }

        editor.putBoolean(newKey, devicePrefs.getBoolean(oldKey, false));
        editor.remove(oldKey);
    }

    private static Map<String, Map<String, String>> namedSettings() {
        final Map<String, Map<String, String>> settings = new HashMap<>();

        settings.put("ambient_sound_control", valuesOf(RedmiBudsAmbientSoundMode.values()));
        settings.put("noise_cancelling_strength", valuesOf(RedmiBudsNoiseCancellingStrength.values()));
        settings.put("transparency_strength", valuesOf(RedmiBudsTransparencyStrength.values()));
        settings.put("equalizer_preset", valuesOf(RedmiBudsEqualizerPreset.values()));

        final Map<String, String> gestures = valuesOf(RedmiBudsGestureAction.values());
        for (final String suffix : List.of("single", "double", "triple")) {
            settings.put("control_" + suffix + "_tap_left", gestures);
            settings.put("control_" + suffix + "_tap_right", gestures);
        }

        final Map<String, String> longGestures = valuesOf(RedmiBudsLongGestureAction.values());
        settings.put("control_long_tap_mode_left", longGestures);
        settings.put("control_long_tap_mode_right", longGestures);

        final Map<String, String> cycles = valuesOf(RedmiBudsAmbientSoundCycle.values());
        settings.put("control_long_tap_settings_left", cycles);
        settings.put("control_long_tap_settings_right", cycles);

        final Map<String, String> bandLevels = valuesOf(RedmiBudsEqualizerBandLevel.values());
        for (final RedmiBudsEqualizerBand band : RedmiBudsEqualizerBand.values()) {
            settings.put(band.getPreferenceKey().substring(NEW_PREFIX.length()), bandLevels);
        }

        return settings;
    }

    /**
     * Maps the protocol numbers a preference used to hold to the names it holds now. Some preferences
     * were written as a signed byte and others as an unsigned one, so both are accepted.
     *
     * @param entries the values the preference can hold.
     * @return the numbers, mapped to names.
     */
    private static <T extends Enum<T> & RedmiBudsPreferenceEntry> Map<String, String> valuesOf(final T[] entries) {
        final Map<String, String> values = new HashMap<>();
        for (final T entry : entries) {
            final String name = entry.name().toLowerCase(Locale.ROOT);
            values.put(Integer.toString(entry.getCode()), name);
            values.put(Integer.toString(entry.getCode() & 0xFF), name);
        }
        return values;
    }
}
