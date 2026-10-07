/*  Copyright (C) 2026 Daniele Gobbetti

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.nothing.prefs;

import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry;

public enum NothingAudioMode implements LabeledEntry {
        OFF((byte) 0x05, R.string.off),
        ANC((byte) 0x01, R.string.prefs_active_noise_cancelling),
        ANCMEDIUM((byte) 0x02, R.string.prefs_active_noise_cancelling_medium),
        ANCLIGHT((byte) 0x03, R.string.prefs_active_noise_cancelling_light),
        ANCADAPTIVE((byte) 0x04, R.string.prefs_active_noise_cancelling_adaptive),
        TRANSPARENCY((byte) 0x07, R.string.prefs_active_noise_cancelling_transparency);

        private final byte code;
        @StringRes
        private final int label;

        NothingAudioMode(final byte code, @StringRes final int label) {
            this.code = code;
            this.label = label;
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
                put(DeviceSettingsPreferenceConst.PREF_NOTHING_EAR1_AUDIOMODE, name().toLowerCase(Locale.ROOT));
            }};
        }

    public static NothingAudioMode fromCode(final byte code) {
        for (NothingAudioMode value : NothingAudioMode.values()) {
            if (value.getCode() == code) {
                return value;
            }
        }

        return null;
    }

    public static NothingAudioMode fromPreferences(final SharedPreferences prefs) {
        return NothingAudioMode.valueOf(prefs.getString(DeviceSettingsPreferenceConst.PREF_NOTHING_EAR1_AUDIOMODE, "off").toUpperCase(Locale.ROOT));
    }

}
