package com.mason.bricktracking.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.mason.bricktracking.R;

/**
 * Utility to animate banners cascading down from the top of the screen,
 * like they're being "built" and attaching one by one.
 */
public class BannerAnimHelper {

    private static final long STAGGER_DELAY = 120; // ms between each banner

    /**
     * Animates all direct children of the given parent with a staggered
     * slide-down-from-top effect. Each child starts invisible and slides in
     * after an incremental delay, creating a "building" cascade.
     *
     * @param parent The root LinearLayout containing the banners/content sections
     */
    public static void animateBanners(ViewGroup parent) {
        if (parent == null) return;

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            child.setVisibility(View.INVISIBLE);

            final int index = i;
            child.postDelayed(() -> {
                child.setVisibility(View.VISIBLE);
                Animation anim = AnimationUtils.loadAnimation(child.getContext(), R.anim.banner_slide_down);
                anim.setDuration(350 + (index * 30L)); // slightly longer for lower items
                child.startAnimation(anim);
            }, index * STAGGER_DELAY);
        }
    }
}
