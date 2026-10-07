package nodomain.freeyourgadget.gadgetbridge.activities;

import android.view.View;

import androidx.annotation.Nullable;

/**
 * A that has a sticky header of its own, below the top toolbar.
 */
public interface HeaderShadePage {
    /**
     * The shade below the header, which replaces the shade below the top toolbar. Null when the page
     * has none, or while the page has no view.
     */
    @Nullable
    View getHeaderShade();
}
