package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;

import nodomain.freeyourgadget.gadgetbridge.R;

public final class BarShade {
    private static final long FADE_MILLIS = 150;

    private BarShade() {
    }

    public static boolean continuesToolbar(final Context context, @AttrRes final int rowColorAttr) {
        return MaterialColors.getColor(context, rowColorAttr, Color.TRANSPARENT)
            == MaterialColors.getColor(context, R.attr.toolbar_bg, Color.TRANSPARENT);
    }

    /**
     * Fades a shade view in and out. Holds the state of the shade, so that a repeated request
     * does not restart the animation.
     */
    public static final class Fader {
        private final View shade;
        private boolean shown;

        public Fader(final View shade) {
            this.shade = shade;
            shade.setAlpha(0f);
            shade.setVisibility(View.VISIBLE);
        }

        public void set(final boolean show) {
            if (show == shown) {
                return;
            }
            shown = show;
            shade.animate().alpha(show ? 1f : 0f).setDuration(FADE_MILLIS).start();
        }
    }

    /**
     * Shows a shade at the top and at the bottom of a screen, while the content scrolls past that
     * edge. Looks for the view that scrolls below {@code root} again after every layout, so that
     * it follows the page of a pager and content that loads later. Moves each shade to the edge
     * of that view, so that a row which stays in place, such as a row of tabs, remains above the
     * shade.
     */
    public static final class ScrollListener implements ViewTreeObserver.OnScrollChangedListener,
        ViewTreeObserver.OnGlobalLayoutListener, Runnable {

        private final View root;
        private View topShadeView;
        private View bottomShadeView;
        private Fader topShade;
        private Fader bottomShade;
        private View scrolling;
        private boolean updatePosted;

        public ScrollListener(final View root) {
            this.root = root;
            root.getViewTreeObserver().addOnScrollChangedListener(this);
            root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }

        public void detach() {
            root.getViewTreeObserver().removeOnScrollChangedListener(this);
            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            root.removeCallbacks(this);
        }

        public void setTopShade(@Nullable final View shade) {
            if (shade == topShadeView) {
                return;
            }
            topShadeView = shade;
            topShade = replace(topShade, shade);
            postUpdate();
        }

        public void setBottomShade(@Nullable final View shade) {
            if (shade == bottomShadeView) {
                return;
            }
            bottomShadeView = shade;
            bottomShade = replace(bottomShade, shade);
            postUpdate();
        }

        @Override
        public void onScrollChanged() {
            postUpdate();
        }

        @Override
        public void onGlobalLayout() {
            // The page of a pager, and the content of a list, change with the layout
            scrolling = null;
            postUpdate();
        }

        @Override
        public void run() {
            updatePosted = false;
            if (scrolling == null) {
                scrolling = findScrolling(root);
            }
            final boolean atTop = scrolling == null || !scrolling.canScrollVertically(-1);
            final boolean atBottom = scrolling == null || !scrolling.canScrollVertically(1);
            if (topShade != null) {
                if (scrolling != null) {
                    align(topShadeView, scrolling, true);
                }
                topShade.set(!atTop);
            }
            if (bottomShade != null) {
                if (scrolling != null) {
                    align(bottomShadeView, scrolling, false);
                }
                bottomShade.set(!atBottom);
            }
        }

        /**
         * Moves a shade to the top or the bottom edge of the scrolling view, from the place the
         * layout gives it. The shade only moves inwards.
         */
        private static void align(final View shade, final View scrolling, final boolean top) {
            final int[] parentLocation = new int[2];
            final int[] scrollingLocation = new int[2];
            // The place the layout gives the shade, which its own rotation does not change
            ((View) shade.getParent()).getLocationInWindow(parentLocation);
            scrolling.getLocationInWindow(scrollingLocation);
            final float shadeEdge = parentLocation[1] + shade.getTop() + (top ? 0 : shade.getHeight());
            final float scrollingEdge = scrollingLocation[1] + (top ? 0 : scrolling.getHeight());
            final float offset = scrollingEdge - shadeEdge;
            shade.setTranslationY(top ? Math.max(0, offset) : Math.min(0, offset));
        }

        /**
         * Runs the update once the layout and the scroll of this frame are complete, so that a
         * state the content passes through on the way does not show as a flash of the shade.
         */
        private void postUpdate() {
            if (!updatePosted) {
                updatePosted = true;
                root.post(this);
            }
        }

        private static Fader replace(@Nullable final Fader current, @Nullable final View shade) {
            if (current != null) {
                current.set(false);
            }
            return shade != null ? new Fader(shade) : null;
        }

        @Nullable
        private static View findScrolling(final View view) {
            if (view.getVisibility() != View.VISIBLE) {
                return null;
            }
            if (view instanceof ViewPager2) {
                final View page = currentPage((ViewPager2) view);
                return page != null ? findScrolling(page) : null;
            }
            if (view.canScrollVertically(-1) || view.canScrollVertically(1)) {
                return view;
            }
            if (view instanceof ViewGroup) {
                final ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    final View found = findScrolling(group.getChildAt(i));
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        @Nullable
        private static View currentPage(final ViewPager2 pager) {
            final View child = pager.getChildAt(0);
            if (!(child instanceof RecyclerView)) {
                return null;
            }
            final RecyclerView.ViewHolder holder =
                ((RecyclerView) child).findViewHolderForAdapterPosition(pager.getCurrentItem());
            return holder != null ? holder.itemView : null;
        }
    }
}
