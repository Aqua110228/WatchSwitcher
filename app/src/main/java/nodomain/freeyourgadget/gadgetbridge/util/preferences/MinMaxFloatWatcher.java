/*  Copyright (C) 2024-2026 José Rebelo, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.util.preferences;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;

import nodomain.freeyourgadget.gadgetbridge.R;

public class MinMaxFloatWatcher implements TextWatcher {
    private final EditText editText;
    private final float min;
    private final float max;
    private final boolean allowEmpty;

    public MinMaxFloatWatcher(final EditText editText, final float min, final float max) {
        this(editText, min, max, false);
    }

    public MinMaxFloatWatcher(final EditText editText, final float min, final float max, final boolean allowEmpty) {
        this.editText = editText;
        this.min = min;
        this.max = max;
        this.allowEmpty = allowEmpty;
    }

    @Override
    public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
    }

    @Override
    public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
    }

    @Override
    public void afterTextChanged(final Editable editable) {
        if (TextUtils.isEmpty(editable.toString()) && allowEmpty) {
            editText.getRootView().findViewById(android.R.id.button1)
                    .setEnabled(true);
            return;
        }

        try {
            final float val = Float.parseFloat(editable.toString());
            editText.getRootView().findViewById(android.R.id.button1)
                    .setEnabled(val >= min && val <= max);
            if (val < min) {
                editText.setError(editText.getContext().getString(R.string.min_val, min));
            } else if (val > max) {
                editText.setError(editText.getContext().getString(R.string.max_val, max));
            } else {
                editText.setError(null);
            }
        } catch (final NumberFormatException e) {
            editText.getRootView().findViewById(android.R.id.button1)
                    .setEnabled(false);
        }
    }
}
