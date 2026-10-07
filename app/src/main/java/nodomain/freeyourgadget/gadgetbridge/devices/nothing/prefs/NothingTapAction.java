package nodomain.freeyourgadget.gadgetbridge.devices.nothing.prefs;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry;

public enum NothingTapAction implements LabeledEntry {
    OFF(0x01, R.string.sony_button_mode_off),
    PLAY_PAUSE(0x02, R.string.pref_media_playpause),
    PREVIOUS_TRACK(0x08, R.string.pref_media_previous),
    NEXT_TRACK(0x09, R.string.pref_media_next),
    VOICE_ASSISTANT(0x0b, R.string.pref_title_touch_voice_assistant),
    VOLUME_UP(0x12, R.string.pref_media_volumeup),
    VOLUME_DOWN(0x13, R.string.pref_media_volumedown),
    ANC_MODE__ANC_TRANSPARENCY_OFF(0x0a, R.string.redmi_buds_5_pro_combo_all),
    ANC_MODE__ANC_TRANSPARENCY(0x16, R.string.redmi_buds_5_pro_combo_anc_transparency),
    ANC_MODE__ANC_OFF(0x14, R.string.redmi_buds_5_pro_combo_anc_off),
    ANC_MODE__TRANSPARENCY_OFF(0x15, R.string.redmi_buds_5_pro_combo_transparency_off),
    ;

    private final int code;
    @StringRes
    private final int label;

    NothingTapAction(final int code, @StringRes final int label) {
        this.code = code;
        this.label = label;
    }

    @Nullable
    public static NothingTapAction fromCode(final int code) {
        for (NothingTapAction type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    public static NothingTapAction fromPreferences(final SharedPreferences prefs, final String key) {
        return NothingTapAction.valueOf(prefs.getString(key, NothingTapAction.OFF.name()).toUpperCase(Locale.ROOT));
    }

    @Override
    public int getLabel() {
        return label;
    }

    public int getCode() {
        return code;
    }

    public Map<String, Object> toPreferences(String key) {
        return new HashMap<>() {{
            put(key, name().toLowerCase(Locale.ROOT));
        }};
    }

    public enum NothingTapType {
        TAP_2(0x02),
        TAP_3(0x03),
        TAP_1_HOLD(0x07),
        TAP_2_HOLD(0x09),
        ;

        private final int code;

        NothingTapType(final int code) {
            this.code = code;
        }

        @Nullable
        public static NothingTapType fromCode(final int code) {
            for (NothingTapType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }

        public int getCode() {
            return code;
        }
    }
}
