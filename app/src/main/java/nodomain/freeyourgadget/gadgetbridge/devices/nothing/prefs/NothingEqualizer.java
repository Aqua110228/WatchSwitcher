package nodomain.freeyourgadget.gadgetbridge.devices.nothing.prefs;

import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry;

public enum NothingEqualizer implements LabeledEntry {
    ROCK((byte) 0x01, R.string.nothing_equalizer_rock),
    ELECTRONIC((byte) 0x02, R.string.nothing_equalizer_electronic),
    POP((byte) 0x03, R.string.nothing_equalizer_pop),
    ENHANCE_VOCALS((byte) 0x04, R.string.nothing_equalizer_enhance_vocals),
    CLASSICAL((byte) 0x05, R.string.nothing_equalizer_classical),
    CUSTOM((byte) 0x06, R.string.nothing_equalizer_custom),
    DIRAC((byte) 0x07, R.string.nothing_equalizer_dirac),
    ;

    private final byte code;
    @StringRes
    private final int label;

    NothingEqualizer(final byte code, @StringRes final int label) {
        this.code = code;
        this.label = label;
    }

    public static NothingEqualizer fromCode(final byte code) {
        for (NothingEqualizer value : NothingEqualizer.values()) {
            if (value.getCode() == code) {
                return value;
            }
        }

        return null;
    }

    public static NothingEqualizer fromPreferences(final SharedPreferences prefs) {
        return NothingEqualizer.valueOf(prefs.getString(DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER, NothingEqualizer.DIRAC.name().toUpperCase(Locale.ROOT)));
    }

    @Override
    public int getLabel() {
        return label;
    }

    public byte getCode() {
        return this.code;
    }

    public Map<String, Object> toPreferences() {
        return new HashMap<>() {{
            put(DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER, name().toLowerCase(Locale.ROOT));
        }};
    }

}
