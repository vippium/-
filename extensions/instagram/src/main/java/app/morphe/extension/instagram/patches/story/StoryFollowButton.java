/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * The approach (hide the story header's Follow button just before a frame is drawn) is adapted
 * from HushGram's StoryFollowButton, Copyright 2026 HushGram contributors, GPL-3.0:
 * https://github.com/SysAdminDoc/HushGram
 */

package app.morphe.extension.instagram.patches.story;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Keeps the Follow button out of the story viewer's header.
 *
 * <p>The button sits in the row of buttons next to the three-dot menu and is inflated from a
 * ViewStub. The story header's view holder is recycled between stories and Instagram decides on
 * every bind whether the button shows, so {@link #bind} runs first thing in the header bind. While
 * the setting is on, the row gets a pre-draw listener that sets the button GONE before a frame
 * shows it, so the row closes up with no gap, and a later show from Instagram is undone the same
 * way. Nothing here taps the button or calls anything on it except {@link View#setVisibility}.
 */
public final class StoryFollowButton {
    private static final String CONTAINER = "reel_header_extras_container";
    private static final String FOLLOW = "reel_header_unconnected_follow_button_stub";

    /** Header rows that have a keeper. A row Instagram drops takes its entry with it. Main thread only. */
    private static final Map<View, Keeper> KEEPERS = new WeakHashMap<>();

    /** Whether a keeper was ever made, so a switch that was never on costs nothing per story. */
    private static boolean kept;

    private StoryFollowButton() {
    }

    private static boolean enabled() {
        return SettingsStatus.hideStoryFollowButton && Pref.hideStoryFollowButton();
    }

    /**
     * Injected at the start of the story header bind with the story view holder's root view.
     * Puts back what the row's keeper hid, then hides the Follow button again while the setting is on.
     */
    public static void bind(View root) {
        try {
            boolean hiding = enabled();
            if (!hiding && !kept) return;
            if (root == null) return;

            View found = findView(root, CONTAINER);
            if (!(found instanceof ViewGroup)) return;
            ViewGroup row = (ViewGroup) found;

            Keeper keeper = KEEPERS.get(row);
            if (keeper != null) keeper.restore();

            if (!hiding) {
                if (keeper != null) keeper.release(row);
                return;
            }

            if (keeper == null) {
                int follow = ResourceUtils.getIdentifier(ResourceType.ID, FOLLOW);
                if (follow == 0) return;
                keeper = new Keeper(row, follow);
                KEEPERS.put(row, keeper);
                row.addOnAttachStateChangeListener(keeper);
                if (row.isAttachedToWindow()) keeper.listen(row);
                kept = true;
            }
        } catch (Throwable ignored) {
            // Never break Instagram's own header bind.
        }
    }

    private static View findView(View root, String name) {
        int id = ResourceUtils.getIdentifier(ResourceType.ID, name);
        return id == 0 ? null : root.findViewById(id);
    }

    /**
     * Keeps one header row's Follow button gone while the row is on screen. It listens to the
     * window's pre-draw only while the row is attached, and stops on detach.
     */
    private static final class Keeper
            implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final WeakReference<ViewGroup> row;
        private final int follow;
        private ViewTreeObserver observer;
        private WeakReference<View> hidden;
        private int before;

        Keeper(ViewGroup row, int follow) {
            this.row = new WeakReference<>(row);
            this.follow = follow;
        }

        void listen(View view) {
            if (observer != null) return;
            ViewTreeObserver tree = view.getViewTreeObserver();
            tree.addOnPreDrawListener(this);
            observer = tree;
        }

        void stop() {
            ViewTreeObserver tree = observer;
            observer = null;
            if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
        }

        /** Stops for good: the setting went off, so the row needs no keeper until it is back on. */
        void release(View view) {
            stop();
            view.removeOnAttachStateChangeListener(this);
            KEEPERS.remove(view);
        }

        /** Puts back the button this keeper hid, unless something else has shown it since. */
        void restore() {
            View view = hidden == null ? null : hidden.get();
            hidden = null;
            if (view != null && view.getVisibility() == View.GONE) view.setVisibility(before);
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            listen(view);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            stop();
        }

        /** Hides the Follow button before the frame that would show it, and cancels that frame. */
        @Override
        public boolean onPreDraw() {
            try {
                ViewGroup group = row.get();
                if (group == null || !enabled()) return true;
                View button = group.findViewById(follow);
                if (button == null || button instanceof ViewStub
                        || button.getVisibility() == View.GONE) return true;
                before = button.getVisibility();
                hidden = new WeakReference<>(button);
                button.setVisibility(View.GONE);
                // The frame is drawn again once the row has closed up, so the button never shows.
                return false;
            } catch (Throwable ignored) {
                return true;
            }
        }
    }
}
