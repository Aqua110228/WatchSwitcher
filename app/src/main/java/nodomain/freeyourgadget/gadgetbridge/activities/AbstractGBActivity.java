/*  Copyright (C) 2017-2024 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Daniele Gobbetti, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils;
import nodomain.freeyourgadget.gadgetbridge.util.BarShade;


public abstract class AbstractGBActivity extends AppCompatActivity implements GBActivity {
    private boolean isLanguageInvalid = false;
    private BarShade.ScrollListener barShades;
    private View actionBarShade;

    public static final int NONE = 0;
    public static final int NO_ACTIONBAR = 1;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case GBApplication.ACTION_LANGUAGE_CHANGE:
                    setLanguage(GBApplication.getLanguage(), true);
                    break;
                case GBApplication.ACTION_THEME_CHANGE:
                    recreate();
                    break;
                case GBApplication.ACTION_QUIT:
                    finish();
                    break;
            }
        }
    };

    @Override
    public void setLanguage(Locale language, boolean invalidateLanguage) {
        if (invalidateLanguage) {
            isLanguageInvalid = true;
        }
        AndroidUtils.setLanguage(this, language);
    }

    public static void init(GBActivity activity) {
        init(activity, NONE);
    }

    public static void init(GBActivity activity, int flags) {
        if (GBApplication.areDynamicColorsEnabled()) {
            if (GBApplication.isDarkThemeEnabled()) {
                if ((flags & NO_ACTIONBAR) != 0) {
                    if (GBApplication.isAmoledBlackEnabled())
                        activity.setTheme(R.style.GadgetbridgeThemeDynamicDarkAmoled_NoActionBar);
                    else
                        activity.setTheme(R.style.GadgetbridgeThemeDynamicDark_NoActionBar);
                } else {
                    if (GBApplication.isAmoledBlackEnabled())
                        activity.setTheme(R.style.GadgetbridgeThemeDynamicDarkAmoled);
                    else
                        activity.setTheme(R.style.GadgetbridgeThemeDynamicDark);
                }
            } else {
                if ((flags & NO_ACTIONBAR) != 0) {
                    activity.setTheme(R.style.GadgetbridgeThemeDynamicLight_NoActionBar);
                } else {
                    activity.setTheme(R.style.GadgetbridgeThemeDynamicLight);
                }
            }
        } else if (GBApplication.isDarkThemeEnabled()) {
            if ((flags & NO_ACTIONBAR) != 0) {
                if (GBApplication.isAmoledBlackEnabled())
                    activity.setTheme(R.style.GadgetbridgeThemeBlack_NoActionBar);
                else
                    activity.setTheme(R.style.GadgetbridgeThemeDark_NoActionBar);
            } else {
                if (GBApplication.isAmoledBlackEnabled())
                    activity.setTheme(R.style.GadgetbridgeThemeBlack);
                else
                    activity.setTheme(R.style.GadgetbridgeThemeDark);
            }
        } else {
            if ((flags & NO_ACTIONBAR) != 0) {
                activity.setTheme(R.style.GadgetbridgeTheme_NoActionBar);
            } else {
                activity.setTheme(R.style.GadgetbridgeTheme);
            }
        }

        // Dynamic Color already ties tab/button/chip colors to the wallpaper palette, so the
        // user's chosen accent preset only applies to the static Light/Dark themes above.
        if (!GBApplication.areDynamicColorsEnabled() && activity instanceof Activity) {
            ((Activity) activity).getTheme().applyStyle(GBApplication.getAccentColorOverlay(), true);
        }

        activity.setLanguage(GBApplication.getLanguage(), false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        IntentFilter filterLocal = new IntentFilter();
        filterLocal.addAction(GBApplication.ACTION_QUIT);
        filterLocal.addAction(GBApplication.ACTION_LANGUAGE_CHANGE);
        filterLocal.addAction(GBApplication.ACTION_THEME_CHANGE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filterLocal);

        init(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isLanguageInvalid) {
            isLanguageInvalid = false;
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public void setContentView(final int layoutResID) {
        super.setContentView(layoutResID);
        applyEdgeToEdgeInsets();
        applyShades();
    }

    @Override
    public void setContentView(final View view) {
        super.setContentView(view);
        applyEdgeToEdgeInsets();
        applyShades();
    }

    /**
     * Applies the shade below the action bar, and follows the content that scrolls under it.
     */
    private void applyShades() {
        final ViewGroup content = findViewById(android.R.id.content);
        if (actionBarShade != null) {
            content.removeView(actionBarShade);
            actionBarShade = null;
        }
        if (barShades != null) {
            barShades.detach();
        }
        barShades = new BarShade.ScrollListener(content);
        if (getSupportActionBar() == null) {
            return;
        }
        actionBarShade = getLayoutInflater().inflate(R.layout.view_bar_shade, content, false);
        content.addView(actionBarShade);
        barShades.setTopShade(actionBarShade);
    }

    /**
     * Shows {@code shade} instead of the shade below the action bar, for screens that have a top row
     * of their own below the action bar.
     */
    public void setTopShade(@Nullable final View shade) {
        if (actionBarShade != null) {
            ((ViewGroup) actionBarShade.getParent()).removeView(actionBarShade);
            actionBarShade = null;
        }
        barShades.setTopShade(shade);
    }

    /**
     * Shows {@code shade} above a sticky bar at the bottom of the screen.
     */
    public void setBottomShade(@Nullable final View shade) {
        barShades.setBottomShade(shade);
    }

    /**
     * targetSdk 35 forces edge-to-edge, so android:fitsSystemWindows no longer pads content.
     */
    private void applyEdgeToEdgeInsets() {
        final View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
    }
}
