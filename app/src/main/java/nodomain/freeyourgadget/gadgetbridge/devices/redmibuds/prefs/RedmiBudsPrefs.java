package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public final class RedmiBudsPrefs {
    private static final Logger LOG = LoggerFactory.getLogger(RedmiBudsPrefs.class);

    private RedmiBudsPrefs() {
        // utility class
    }

    /**
     * Reads a preference and resolves it to its enum constant.
     *
     * @param prefs    the device preferences.
     * @param key      the preference key.
     * @param fallback the value to return if the preference is unset or unknown.
     * @return the stored value, or {@code fallback}.
     */
    @NonNull
    public static <T extends Enum<T> & RedmiBudsPreferenceEntry> T get(@NonNull final Prefs prefs,
                                                                       @NonNull final String key,
                                                                       @NonNull final T fallback) {
        final String value = prefs.getString(key, null);
        if (value == null) {
            return fallback;
        }

        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            LOG.warn("Unknown value '{}' for {}, falling back to {}", value, key, fallback);
            return fallback;
        }
    }

    /**
     * Reads a preference and returns the protocol byte code for it.
     *
     * @param prefs    the device preferences.
     * @param key      the preference key.
     * @param fallback the value to use if the preference is unset or unknown.
     * @return the byte code.
     */
    public static <T extends Enum<T> & RedmiBudsPreferenceEntry> byte getCode(@NonNull final Prefs prefs,
                                                                              @NonNull final String key,
                                                                              @NonNull final T fallback) {
        return get(prefs, key, fallback).getCode();
    }

    /**
     * Stores the value the device reported for a preference. Codes the app does not know about
     * are logged and dropped, and the preference keeps its last known value.
     *
     * @param editor the editor to write to.
     * @param key    the preference key.
     * @param type   the enum that maps the device codes for this preference.
     * @param code   the code reported by the device.
     */
    public static <T extends Enum<T> & RedmiBudsPreferenceEntry> void putCode(@NonNull final SharedPreferences.Editor editor,
                                                                              @NonNull final String key,
                                                                              @NonNull final Class<T> type,
                                                                              final byte code) {
        final T value = fromCode(type, code);
        if (value == null) {
            LOG.warn("Unknown code 0x{} for {}", String.format(Locale.ROOT, "%02X", code), key);
            return;
        }

        editor.putString(key, value.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Finds the constant of {@code type} with the given byte code.
     *
     * @param type the enum to search.
     * @param code the byte code.
     * @return the constant, or null if no constant has that code.
     */
    @Nullable
    public static <T extends Enum<T> & RedmiBudsPreferenceEntry> T fromCode(@NonNull final Class<T> type, final byte code) {
        for (final T candidate : Objects.requireNonNull(type.getEnumConstants())) {
            if (candidate.getCode() == code) {
                return candidate;
            }
        }

        return null;
    }
}
