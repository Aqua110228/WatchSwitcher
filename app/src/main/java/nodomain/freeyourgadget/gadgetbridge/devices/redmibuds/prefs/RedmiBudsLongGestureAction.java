package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsLongGestureAction implements RedmiBudsPreferenceEntry {
    NONE((byte) 0x08, R.string.none),
    VOICE_ASSISTANT((byte) 0x00, R.string.pref_title_touch_voice_assistant),
    AMBIENT_SOUND_CONTROL((byte) 0x06, R.string.pref_header_sony_ambient_sound_control),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsLongGestureAction(final byte code, @StringRes final int label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Override
    public int getLabel() {
        return label;
    }
}
