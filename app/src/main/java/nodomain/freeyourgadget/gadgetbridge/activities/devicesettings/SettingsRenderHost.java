package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings;

import android.content.Context;

import androidx.activity.result.ActivityResultCaller;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * The host a {@link nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingRenderer}
 * renders a {@link nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec}
 * into.
 */
public interface SettingsRenderHost extends ActivityResultCaller {
    /**
     * Finds a preference with the given key. Returns null if the preference is not found.
     *
     * @param preferenceKey the preference key.
     * @return the preference, if found.
     */
    @Nullable
    <T extends Preference> T findPreference(@NonNull CharSequence preferenceKey);

    /**
     * Adds a preference handler for a preference key. This handler sends the preference to the device on change.
     *
     * @param preferenceKey the preference key.
     */
    void addPreferenceHandlerFor(final String preferenceKey);

    /**
     * Notify the device that a preference changed.
     *
     * @param preferenceKey the preference key.
     */
    void notifyPreferenceChanged(final String preferenceKey);

    /**
     * Adds a preference handler for a preference key. On change, this handler calls the provided extra listener, and then sends the preference to the device.
     *
     * @param preferenceKey the preference key.
     * @param extraListener the extra listener.
     */
    void addPreferenceHandlerFor(final String preferenceKey, Preference.OnPreferenceChangeListener extraListener);

    /**
     * Sets the input type flags for an EditText preference.
     *
     * @param preferenceKey the preference key.
     * @param editTypeFlags the edit type {@link android.text.InputType} flags.
     */
    void setInputTypeFor(final String preferenceKey, final int editTypeFlags);

    /**
     * Get the device associated with this host, if any. Non-device hosts (e.g. widget instance
     * settings) return null.
     *
     * @return the {@link GBDevice}, or null if this host is not bound to a device.
     */
    @Nullable
    default GBDevice getDevice() {
        return null;
    }

    /**
     * Get the current {@link Context}.
     *
     * @return the {@link Context}.
     */
    Context getContext();

    /**
     * Navigate to the given {@link PreferenceScreen}. Used by the programmatic settings renderer to
     * trigger sub-screen navigation for model-defined screens.
     *
     * @param screen the screen to navigate to.
     */
    void navigateToScreen(@NonNull PreferenceScreen screen);

    /**
     * Inflate preferences from an XML resource and add them to the current preference screen at
     * the current position. Used by the programmatic settings renderer to insert
     * {@link nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.XmlScreenSetting}
     * entries at the position they appear in the DSL rather than appending them at the end.
     *
     * @param resId the XML resource ID.
     */
    void addXmlPreferences(@XmlRes int resId);
}
