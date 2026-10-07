/*  Copyright (C) 2024 Jonathan Gobbo

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
package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundCycle;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerPreset;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsNoiseCancellingStrength;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTransparencyStrength;

public class RedmiBuds5ProCoordinator extends AbstractRedmiBudsCoordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_redmi_buds_5_pro;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Redmi Buds 5 Pro");
    }

    @NonNull
    @Override
    public List<RedmiBudsAmbientSoundMode> getAmbientSoundModes() {
        return List.of(RedmiBudsAmbientSoundMode.values());
    }

    @NonNull
    @Override
    public List<RedmiBudsNoiseCancellingStrength> getNoiseCancellingStrengths() {
        return List.of(
            RedmiBudsNoiseCancellingStrength.BALANCED,
            RedmiBudsNoiseCancellingStrength.LIGHT,
            RedmiBudsNoiseCancellingStrength.DEEP
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsTransparencyStrength> getTransparencyStrengths() {
        return List.of(RedmiBudsTransparencyStrength.values());
    }

    @Override
    public boolean getSupportsAdaptiveNoiseCancelling() {
        return true;
    }

    @NonNull
    @Override
    public List<RedmiBudsEqualizerPreset> getEqualizerPresets() {
        return List.of(
            RedmiBudsEqualizerPreset.STANDARD,
            RedmiBudsEqualizerPreset.TREBLE,
            RedmiBudsEqualizerPreset.BASS,
            RedmiBudsEqualizerPreset.VOICE,
            RedmiBudsEqualizerPreset.CUSTOM
        );
    }

    @Override
    public boolean getSupportsCustomEqualizer() {
        return true;
    }

    @Override
    public boolean getSupportsAdaptiveSound() {
        return true;
    }

    @NonNull
    @Override
    public List<RedmiBudsGestureAction> getSingleTapActions() {
        return List.of(
            RedmiBudsGestureAction.NONE,
            RedmiBudsGestureAction.PLAY_PAUSE,
            RedmiBudsGestureAction.PREVIOUS_TRACK,
            RedmiBudsGestureAction.NEXT_TRACK,
            RedmiBudsGestureAction.VOLUME_UP,
            RedmiBudsGestureAction.VOLUME_DOWN
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsGestureAction> getTapActions() {
        return List.of(
            RedmiBudsGestureAction.PLAY_PAUSE,
            RedmiBudsGestureAction.PREVIOUS_TRACK,
            RedmiBudsGestureAction.NEXT_TRACK,
            RedmiBudsGestureAction.VOLUME_UP,
            RedmiBudsGestureAction.VOLUME_DOWN
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsLongGestureAction> getLongPressActions() {
        return List.of(
            RedmiBudsLongGestureAction.VOICE_ASSISTANT,
            RedmiBudsLongGestureAction.AMBIENT_SOUND_CONTROL
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsAmbientSoundCycle> getAmbientSoundCycles() {
        return List.of(RedmiBudsAmbientSoundCycle.values());
    }

    @Override
    public boolean getSupportsWearingDetection() {
        return true;
    }

    @Override
    public boolean getSupportsAutoAnswer() {
        return true;
    }

    @Override
    public boolean getSupportsDoubleConnection() {
        return true;
    }
}
