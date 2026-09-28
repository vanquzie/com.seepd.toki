package com.seepd.toki;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Installs view-level component and global navigation purification hooks. */
final class PurificationHooks extends HookFeature {
    private static final String MAIN_ACTIVITY = "com.ss.android.ugc.aweme.main.MainActivity";
    private final AtomicBoolean visibilityLogged = new AtomicBoolean(false);
    private final AtomicBoolean globalVisibilityLogged = new AtomicBoolean(false);
    private final AtomicBoolean topHiddenLogged = new AtomicBoolean(false);
    private final AtomicBoolean bottomHiddenLogged = new AtomicBoolean(false);
    private final AtomicBoolean bottomBarLogged = new AtomicBoolean(false);
    private final AtomicBoolean searchEntryLogged = new AtomicBoolean(false);
    private final AtomicBoolean bottomLabelsLogged = new AtomicBoolean(false);
    private final AtomicBoolean barCollapsedLogged = new AtomicBoolean(false);
    private final AtomicBoolean topIconsLogged = new AtomicBoolean(false);
    private final AtomicBoolean topSweepLogged = new AtomicBoolean(false);
    private final WeakHashMap<View, Boolean> observedRoots = new WeakHashMap<>();
    private final AtomicBoolean commentFirstHideLogged = new AtomicBoolean(false);
    private final AtomicBoolean followFirstHideLogged = new AtomicBoolean(false);

    /** eq5 id is stable across videos, so one cached id per observer suffices. */
    private static final int COMMENT_WALK_CAP = 4000;
    private static final int NAV_WALK_CAP = 12000;
    private static final int BAR_CHILD_CAP = 12;
    private static final int BAR_WALK_LEVELS = 3;
    private static final int HALF_DEN = 2;
    private static final int TOP_ZONE_DEN = 4;
    private static final int SHORT_DEN = 2;
    private static final int BOTTOM_ZONE_NUM = 17;
    private static final int BOTTOM_ZONE_DEN = 20;
    private static final String[] TAB_TEXTS = {
            "For You", "Following", "Shop", "Series", "Community", "STEM", "Friends", "Drama"
    };
    private static final String[] BOTTOM_TEXTS = {
            "Home", "Friends", "Shop", "Inbox", "Profile", "+"
    };
    private static final String SEARCH_DESC = "search";

    private static final class CommentObserver {
        final ViewTreeObserver observer;
        final ViewTreeObserver.OnGlobalLayoutListener listener;

        CommentObserver(ViewTreeObserver observer,
                ViewTreeObserver.OnGlobalLayoutListener listener) {
            this.observer = observer;
            this.listener = listener;
        }
    }

    private final WeakHashMap<View, CommentObserver> commentObservers =
            new WeakHashMap<>();
    private final WeakHashMap<View, CommentObserver> followObservers =
            new WeakHashMap<>();

    PurificationHooks(XposedModule module) {
        super(module);
    }

    int installComponents(ClassLoader classLoader, ModuleConfig config) {
        int installed = 0;
        if (config.hideAuthorAvatar) {
            installed += installComponentVisibilityHooks(classLoader, "author-avatar",
                    "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarAssemWrap",
                    "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarDefaultAssem");
        }
        if (config.hideAuthorInfo) {
            installed += installComponentVisibilityHooks(classLoader, "author-info",
                    "com.ss.android.ugc.aweme.feed.assem.videoauthorinfo.VideoAuthorInfoRelationAssem");
        }
        if (config.hideFollowButton) {
            installed += installComponentVisibilityHooks(classLoader, "follow-button",
                    "com.ss.android.ugc.aweme.feed.assem.relationbtn.VideoRelationBtnAssem",
                    "com.ss.android.ugc.aweme.feed.assem.relationbtn.VideoRelationBtnAssemV2");
            installed += installFollowButtonIdBackup();
        }
        if (config.hideVideoDescription) {
            installed += installComponentVisibilityHooks(classLoader, "video-description",
                    "com.ss.android.ugc.aweme.feed.assem.desc.VideoDescAssem");
        }
        if (config.hideVideoTags) {
            installed += installComponentVisibilityHooks(classLoader, "video-tags",
                    "com.ss.android.ugc.aweme.feed.assem.desc.VideoDescTagAssem");
        }
        if (config.hideMusicTitle) {
            installed += installMusicTitleVisibilityHook(classLoader);
        }
        if (config.hideMusicCover) {
            installed += installComponentVisibilityHooks(classLoader, "music-cover",
                    "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicCoverAssem");
        }
        if (config.hideLikeButton) {
            installed += installComponentVisibilityHooks(classLoader, "like-button",
                    "com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem");
        }
        if (config.hideCommentButton) {
            installed += installComponentVisibilityHooks(classLoader, "comment-button",
                    "com.ss.android.ugc.aweme.feed.assem.videocomment.VideoCommentAssem");
            installed += installCommentButtonIdBackup();
        }
        if (config.hideFavoriteButton) {
            installed += installComponentVisibilityHooks(classLoader, "favorite-button",
                    "com.ss.android.ugc.aweme.feed.assem.favorite.VideoFavoriteAssem",
                    "com.ss.android.ugc.aweme.feed.favorite.VideoFavoriteAssem");
        }
        if (config.hideShareButton) {
            installed += installComponentVisibilityHooks(classLoader, "share-button",
                    "com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem");
        }
        if (config.hideDuetButton) {
            installed += installComponentVisibilityHooks(classLoader, "duet-button",
                    "com.ss.android.ugc.aweme.feed.assem.duetbutton.VideoDuetButtonAssem");
        }
        if (config.hideStitchButton) {
            installed += installComponentVisibilityHooks(classLoader, "stitch-button",
                    "com.ss.android.ugc.aweme.feed.assem.stitchbutton.VideoStitchButtonAssem");
        }
        if (config.hideQuickDm) {
            installed += installComponentVisibilityHooks(classLoader, "quick-dm",
                    "com.ss.android.ugc.aweme.feed.assem.quickreply.MUFQuickDMBoxAssem",
                    "com.ss.android.ugc.aweme.feed.assem.quickreply.MUFQuickDMBoxAssemV2",
                    "com.ss.android.ugc.aweme.feed.assem.story.QuickDMEntranceAssem",
                    "com.ss.android.ugc.aweme.feed.assem.story.QuickDMEntranceAssemV2");
        }
        if (config.hideStoryTags) {
            installed += installComponentVisibilityHooks(classLoader, "story-tags",
                    "com.ss.android.ugc.aweme.feed.assem.story.FeedStoryTagAssem",
                    "com.ss.android.ugc.aweme.feed.assem.story.FeedStoryTagAssemV2");
        }
        if (config.hideCollabLabel) {
            installed += installComponentVisibilityHooks(classLoader, "collab-label",
                    "com.ss.android.ugc.aweme.feed.assem.collab.CollabInFeedLabelAssem");
        }
        if (config.hideTako) {
            installed += installComponentVisibilityHooks(classLoader, "tako",
                    "com.ss.android.ugc.aweme.feed.assem.tikbot.TakoAssem");
        }
        if (config.hideTranslationControls) {
            installed += installComponentVisibilityHooks(classLoader, "translation-controls",
                    "com.ss.android.ugc.aweme.translation.ui.TranslationControlsAssem");
        }
        return installed;
    }

    /**
     * Prevents TikTok from restoring the top-left LIVE entry during feed transitions.
     * 47.1.3 exact (LiveIconGenerator#LJIIL) with 46.4.3 legacy (LJIIIZ) fallback.
     */
    private int installLiveEntryVisibilityHook(ClassLoader classLoader) {
        try {
            Class<?> generatorType = Class.forName(
                    "com.bytedance.tiktok.homepage.mainfragment.toolbar.LiveIconGenerator",
                    false,
                    classLoader);
            if (isTikTok4713()) {
                // 47.1.3 exact: force the LJIIL(boolean) gate false.
                Method liveEntry = generatorType.getDeclaredMethod("LJIIL", boolean.class);
                if (liveEntry.getReturnType() != void.class
                        && liveEntry.getReturnType() != boolean.class) {
                    throw new NoSuchMethodException(
                            "LiveIconGenerator#LJIIL(boolean)");
                }
                liveEntry.setAccessible(true);
                hook(liveEntry)
                        .setId("toki-purify-live-entry-generator-4713")
                        .intercept(chain -> chain.proceed(new Object[]{false}));
                logInfo("LIVE entry exact 47.1.3: LiveIconGenerator#LJIIL"
                        + " [TikTok " + hostTikTokVersion() + "]");
                return 1;
            }
            Method visibilityMethod = generatorType.getDeclaredMethod("LJIIIZ", boolean.class);
            if (visibilityMethod.getReturnType() != void.class) {
                throw new NoSuchMethodException("LiveIconGenerator#LJIIIZ(boolean): void");
            }
            visibilityMethod.setAccessible(true);
            hook(visibilityMethod)
                    .setId("toki-purify-live-entry-generator-4643")
                    .intercept(chain -> chain.proceed(new Object[]{false}));
            return 1;
        } catch (ClassNotFoundException ignored) {
            // The verified 46.4.3 live-entry controller is absent in this process.
            return 0;
        } catch (Throwable error) {
            logError("Unable to prevent 46.4.3 LIVE entry restoration", error);
            return 0;
        }
    }

    int installGlobalNavigation(ClassLoader classLoader, ModuleConfig config) {
        int installed = config.hideLiveEntry
                ? installLiveEntryVisibilityHook(classLoader)
                : 0;
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            hook(onResume)
                    .setId("toki-global-navigation-purification")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object activity = chain.getThisObject();
                        if (activity instanceof Activity) {
                            observeGlobalNavigation((Activity) activity, config);
                        }
                        return result;
                    });
            return installed + 1;
        } catch (Throwable error) {
            logError("Unable to install global navigation purification", error);
            return installed;
        }
    }

    private int installComponentVisibilityHooks(
            ClassLoader classLoader,
            String targetName,
            String... classNames
    ) {
        int installed = 0;
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                Method contentViewMethod = findComponentContentViewMethod(type);
                for (Method method : type.getDeclaredMethods()) {
                    boolean viewCreated = "onViewCreated".equals(method.getName())
                            && method.getParameterCount() == 1
                            && View.class.isAssignableFrom(method.getParameterTypes()[0]);
                    boolean binding = "onBind".equals(method.getName())
                            && method.getParameterCount() == 1
                            && !method.isBridge();
                    if (!viewCreated && !binding) {
                        continue;
                    }
                    method.setAccessible(true);
                    final Method viewMethod = contentViewMethod;
                    final String source = className + "#" + method.getName();
                    hook(method)
                            .setId("toki-purify-" + targetName + "-" + installed)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                hideComponentView(chain.getThisObject(), chain.getArg(0), viewMethod);
                                if (visibilityLogged.compareAndSet(false, true)) {
                                    logInfo("Page purification active via " + source);
                                }
                                return result;
                            });
                    installed++;
                }
            } catch (ClassNotFoundException ignored) {
                // TikTok changes some optional component variants between releases.
            } catch (Throwable error) {
                logError("Unable to hide page purification target " + className, error);
            }
        }
        return installed;
    }

    /**
     * ID-based backup for the 47.1.3 native comment Button (eq5), which lives
     * outside the VideoCommentAssem subtree. Same hide_comment_button gate as
     * the Assem hook above; resolves per resume, so id 0 (46.4.3 or renamed)
     * skips silently with no version branch.
     */
    private int installCommentButtonIdBackup() {
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            hook(onResume)
                    .setId("toki-purify-comment-button-eq5-4713")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object activity = chain.getThisObject();
                        if (activity instanceof Activity) {
                            Activity resumedActivity = (Activity) activity;
                            hideCommentButtonId(resumedActivity);
                            observeCommentButtonId(resumedActivity);
                        }
                        return result;
                    });
            logInfo("comment-button ID backup installed for eq5");
            return 1;
        } catch (Throwable error) {
            logError("Unable to install comment button id backup", error);
            return 0;
        }
    }

    /** Follow button by id (ix9 on 47.1.3), mirroring the comment backup below. */
    private int installFollowButtonIdBackup() {
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            hook(onResume)
                    .setId("toki-purify-follow-button-ix9-4713")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object activity = chain.getThisObject();
                        if (activity instanceof Activity) {
                            Activity resumedActivity = (Activity) activity;
                            hideFollowButtonId(resumedActivity);
                            observeFollowButtonId(resumedActivity);
                        }
                        return result;
                    });
            logInfo("follow-button ID backup installed for ix9");
            return 1;
        } catch (Throwable error) {
            logError("Unable to hide follow button by id", error);
            return 0;
        }
    }

    private static void hideFollowButtonId(Activity activity) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null) {
            return;
        }
        int followId;
        try {
            followId = decorView.getResources().getIdentifier(
                    "ix9", "id", ModuleConfig.TARGET_PACKAGE);
        } catch (RuntimeException ignored) {
            return;
        }
        if (followId == 0) {
            return;
        }
        hideViewByIdAfterLayout(decorView, followId);
    }

    private void observeFollowButtonId(Activity activity) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null) {
            return;
        }
        boolean healthy = false;
        CommentObserver current = null;
        try {
            current = followObservers.get(decorView);
            healthy = current != null && current.observer.isAlive();
        } catch (Throwable ignored) {
            healthy = false;
        }
        if (healthy) {
            return;
        }
        followObservers.remove(decorView);
        if (current != null) {
            try {
                current.observer.removeOnGlobalLayoutListener(current.listener);
            } catch (Throwable ignored) {
                // Dead observer; the framework already dropped its listeners.
            }
        }
        final int followId;
        try {
            followId = decorView.getResources().getIdentifier(
                    "ix9", "id", ModuleConfig.TARGET_PACKAGE);
        } catch (RuntimeException ignored) {
            return;
        }
        if (followId == 0) {
            return;
        }
        final View rootView = decorView;
        final String activityName = activity.getClass().getSimpleName();
        final String windowTag =
                Integer.toHexString(System.identityHashCode(decorView));
        try {
            ViewTreeObserver observer = decorView.getViewTreeObserver();
            if (observer.isAlive()) {
                final ViewTreeObserver.OnGlobalLayoutListener listener = () ->
                        reapplyFollowButtonIdHide(rootView, followId);
                observer.addOnGlobalLayoutListener(listener);
                followObservers.put(decorView, new CommentObserver(observer, listener));
            }
        } catch (Throwable error) {
            logError("follow observer registration failed: " + activityName, error);
            return;
        }
        logInfo("follow observer registered: " + activityName + " " + windowTag);
    }

    private void reapplyFollowButtonIdHide(View root, int followId) {
        final java.util.List<View> matches = new java.util.ArrayList<>(2);
        final int[] visited = new int[1];
        try {
            collectViewsById(root, followId, matches, visited);
        } catch (Throwable ignored) {
            return;
        }
        if (matches.isEmpty()) {
            return;
        }
        String firstHiddenClass = null;
        for (View button : matches) {
            if (button == null) {
                continue;
            }
            boolean gone;
            try {
                gone = button.getVisibility() == View.GONE;
            } catch (RuntimeException ignored) {
                continue;
            }
            if (gone) {
                continue;
            }
            try {
                button.setVisibility(View.GONE);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (firstHiddenClass == null) {
                try {
                    firstHiddenClass = button.getClass().getSimpleName();
                } catch (RuntimeException ignored) {
                    firstHiddenClass = "View";
                }
            }
        }
        if (firstHiddenClass != null
                && followFirstHideLogged.compareAndSet(false, true)) {
            logInfo("follow observer first hide: " + firstHiddenClass);
        }
    }

    private static void hideCommentButtonId(Activity activity) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null) {
            return;
        }
        int commentId;
        try {
            commentId = decorView.getResources().getIdentifier(
                    "eq5", "id", ModuleConfig.TARGET_PACKAGE);
        } catch (RuntimeException ignored) {
            return;
        }
        if (commentId == 0) {
            return;
        }
        hideViewByIdAfterLayout(decorView, commentId);
        hideCommentCountSibling(decorView, commentId);
    }

    /**
     * Persistent re-application for rebinding cells: dedupe per decorView
     * with self-healing (a dead stored observer is dropped and re-registered
     * fresh on the next foregrounding, so observer death can never
     * permanently disable the hide). The eq5 id resolves ONCE per observer
     * and is captured; id 0 registers nothing.
     */
    private void observeCommentButtonId(Activity activity) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null) {
            return;
        }
        boolean healthy = false;
        CommentObserver current = null;
        try {
            current = commentObservers.get(decorView);
            healthy = current != null && current.observer.isAlive();
        } catch (Throwable ignored) {
            healthy = false;
        }
        if (healthy) {
            return;
        }
        commentObservers.remove(decorView);
        if (current != null) {
            try {
                current.observer.removeOnGlobalLayoutListener(current.listener);
            } catch (Throwable ignored) {
                // Dead observer; the framework already dropped its listeners.
            }
        }
        final int commentId;
        try {
            commentId = decorView.getResources().getIdentifier(
                    "eq5", "id", ModuleConfig.TARGET_PACKAGE);
        } catch (RuntimeException ignored) {
            return;
        }
        if (commentId == 0) {
            return;
        }
        final View rootView = decorView;
        final String activityName = activity.getClass().getSimpleName();
        final String windowTag =
                Integer.toHexString(System.identityHashCode(decorView));
        try {
            ViewTreeObserver observer = decorView.getViewTreeObserver();
            if (observer.isAlive()) {
                final ViewTreeObserver.OnGlobalLayoutListener listener = () ->
                        reapplyCommentButtonIdHide(rootView, commentId);
                observer.addOnGlobalLayoutListener(listener);
                commentObservers.put(decorView, new CommentObserver(observer, listener));
            }
        } catch (Throwable error) {
            logError("comment observer registration failed: " + activityName, error);
            return;
        }
        logInfo("comment observer registered: " + activityName + " " + windowTag);
    }

    /**
     * Per-pass cost: one bounded walk (GONE subtrees pruned, hard-capped).
     * Idle/steady passes only visit visible nodes and hide nothing; a VISIBLE
     * rebound button pays for the hide plus the bounded (<=6 children)
     * count-sibling scan.
     */
    private void reapplyCommentButtonIdHide(View root, int commentId) {
        final java.util.List<View> matches = new java.util.ArrayList<>(2);
        final int[] visited = new int[1];
        try {
            collectViewsById(root, commentId, matches, visited);
        } catch (Throwable ignored) {
            return;
        }
        if (matches.isEmpty()) {
            return;
        }
        String firstHiddenClass = null;
        for (View button : matches) {
            if (button == null || button.getVisibility() == View.GONE) {
                continue;
            }
            try {
                button.setVisibility(View.GONE);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (firstHiddenClass == null) {
                firstHiddenClass = button.getClass().getSimpleName();
            }
            hideCommentCountSibling(root, commentId);
        }
        if (firstHiddenClass != null
                && commentFirstHideLogged.compareAndSet(false, true)) {
            logInfo("comment observer first hide: " + firstHiddenClass);
        }
    }

    /**
     * Depth-first collection of every view with the given id. Prunes GONE
     * subtrees (a rendered VISIBLE button cannot hide inside one; it is
     * caught on a later pass once shown) and stops after COMMENT_WALK_CAP
     * visited nodes so a pathological hierarchy cannot stall layout.
     */
    private static void collectViewsById(
            View node, int commentId, java.util.List<View> out, int[] visited) {
        if (node == null || visited[0] >= COMMENT_WALK_CAP) {
            return;
        }
        visited[0]++;
        try {
            if (node.getId() == commentId) {
                out.add(node);
            }
        } catch (RuntimeException ignored) {
            return;
        }
        if (!(node instanceof ViewGroup) || node.getVisibility() == View.GONE) {
            return;
        }
        ViewGroup group = (ViewGroup) node;
        final int count;
        try {
            count = group.getChildCount();
        } catch (RuntimeException ignored) {
            return;
        }
        for (int index = 0; index < count && visited[0] < COMMENT_WALK_CAP; index++) {
            View child;
            try {
                child = group.getChildAt(index);
            } catch (RuntimeException ignored) {
                continue;
            }
            collectViewsById(child, commentId, out, visited);
        }
    }

    /**
     * Hides the "42"-style count TextView next to the eq5 Button when it is
     * clearly identifiable: same parent, TextView, short numeric text
     * (compact counts like 42 / 1.2K / 3M). First match only; anything
     * ambiguous is left visible rather than risking the wrong view.
     */
    private static void hideCommentCountSibling(View root, int commentId) {
        final View button;
        try {
            button = root.findViewById(commentId);
        } catch (RuntimeException ignored) {
            return;
        }
        if (button == null) {
            return;
        }
        ViewParent parent = button.getParent();
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        ViewGroup container = (ViewGroup) parent;
        if (container.getChildCount() > 6) {
            return;
        }
        for (int index = 0; index < container.getChildCount(); index++) {
            View sibling = container.getChildAt(index);
            if (sibling == null || sibling == button || !(sibling instanceof TextView)
                    || sibling.getVisibility() == View.GONE) {
                continue;
            }
            CharSequence text;
            try {
                text = ((TextView) sibling).getText();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (text == null || !isCompactCount(text.toString().trim())) {
                continue;
            }
            final View countView = sibling;
            countView.setVisibility(View.GONE);
            root.post(() -> countView.setVisibility(View.GONE));
            return;
        }
    }

    private static boolean isCompactCount(String text) {
        if (text.isEmpty() || text.length() > 8) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            boolean digit = c >= '0' && c <= '9';
            boolean separator = c == '.' || c == ',' || c == ' ';
            boolean suffix = c == 'K' || c == 'k' || c == 'M' || c == 'm'
                    || c == 'B' || c == 'b';
            if (!digit && !separator && !suffix) {
                return false;
            }
        }
        return Character.isDigit(text.charAt(0));
    }

    /** Forces the 46.4.3 music-title controller to keep its root hidden. */
    private int installMusicTitleVisibilityHook(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicTitleAssem",
                    false,
                    classLoader);
            // JADX renders this obfuscated method as m47347mr; the runtime name is mr.
            Method visibility = type.getDeclaredMethod("mr", int.class);
            if (visibility.getReturnType() != void.class
                    || Modifier.isStatic(visibility.getModifiers())) {
                throw new NoSuchMethodException(
                        "VideoMusicTitleAssem#mr(int): void");
            }
            visibility.setAccessible(true);
            hook(visibility)
                    .setId("toki-purify-music-title-visibility-4643")
                    .intercept(chain -> chain.proceed(new Object[]{8}));
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to hide 46.4.3 music title", error);
            return 0;
        }
    }

    private static void hideComponentView(
            Object component,
            Object lifecycleArgument,
            Method contentViewMethod
    ) {
        View lifecycleView = lifecycleArgument instanceof View ? (View) lifecycleArgument : null;
        View contentView = null;
        if (contentViewMethod != null) {
            try {
                Object value = contentViewMethod.invoke(component);
                if (value instanceof View) {
                    contentView = (View) value;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The lifecycle view remains a safe fallback when the base component changes.
            }
        }
        if (contentView != null) {
            contentView.setVisibility(View.GONE);
        }
        if (lifecycleView != null && lifecycleView != contentView) {
            lifecycleView.setVisibility(View.GONE);
        }
    }

    private static Method findComponentContentViewMethod(Class<?> type) {
        for (String name : new String[]{"getContentView", "LJJIJLIJ"}) {
            Method method = findInheritedNoArgMethod(type, name);
            if (method != null && View.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == 0
                        && !Modifier.isStatic(method.getModifiers())
                        && View.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findInheritedNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue to the base component where getContentView is declared.
            }
        }
        return null;
    }

    private void observeGlobalNavigation(Activity activity, ModuleConfig config) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null || observedRoots.put(decorView, Boolean.TRUE) != null) {
            return;
        }

        GlobalNavigationViewIds viewIds = GlobalNavigationViewIds.from(decorView);
        applyGlobalNavigationPurification(activity, decorView, config, viewIds);
        ViewTreeObserver observer = decorView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnGlobalLayoutListener(() ->
                    applyGlobalNavigationPurification(activity, decorView, config, viewIds));
        }
    }

    private void applyGlobalNavigationPurification(
            Activity activity,
            View root,
            ModuleConfig config,
            GlobalNavigationViewIds viewIds
    ) {
        if (config.hideStatusBar && MAIN_ACTIVITY.equals(activity.getClass().getName())) {
            hideStatusBar(activity);
        }
        if (config.hideLiveEntry) {
            hideViewById(root, viewIds.liveEntry);
        }
        if (config.hideTopNavigation) {
            if (isTikTok4713()) {
                // Text rows first; legacy u3t hunt stays as backup (no-op when absent).
                hideTabRowByText(root);
            }
            hideTopNavigation(root, viewIds.topNavigationHost);
        }
        if (config.hideSearchEntry) {
            if (isTikTok4713()) {
                // jz0/kap carry no live view on 47.1.3: hide all id copies
                // plus the magnifier by content description.
                hideSearchEntry(root, viewIds.searchEntry);
            } else {
                hideViewByIdAfterLayout(root, viewIds.searchEntry);
            }
        }
        if (config.hideBottomNavigation) {
            if (isTikTok4713()) {
                // Text rows first; legacy o7b id stays as backup (no-op when absent).
                hideBottomBarByText(root);
            }
            hideViewById(root, viewIds.bottomNavigation);
        }
        if (config.hideVideoProgressBar) {
            hideViewById(root, viewIds.videoProgressBar);
        }
        if (globalVisibilityLogged.compareAndSet(false, true)) {
            logInfo("Global navigation purification active");
        }
    }

    private static void hideStatusBar(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
            return;
        }

        int flags = decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(flags);
    }

    private static void hideViewById(View root, int resourceId) {
        if (resourceId == 0) {
            return;
        }
        View target = root.findViewById(resourceId);
        if (target != null) {
            target.setVisibility(View.GONE);
        }
    }

    private void hideSearchEntry(View root, int searchId) {
        int idCopies = hideAllViewsById(root, searchId);
        int icons = hideTopBarIconsByDescription(root);
        if (idCopies + icons > 0 && searchEntryLogged.compareAndSet(false, true)) {
            logInfo("search entry hidden: " + idCopies + " id-copies + "
                    + icons + " icons [TikTok " + hostTikTokVersion() + "]");
        }
    }

    /** Hides every live copy of a resource id, reusing the shared collector. */
    private static int hideAllViewsById(View root, int targetId) {
        if (targetId == 0) {
            return 0;
        }
        final java.util.List<View> matches = new java.util.ArrayList<>(2);
        collectViewsById(root, targetId, matches, new int[1]);
        for (View match : matches) {
            if (match != null && match.getVisibility() != View.GONE) {
                match.setVisibility(View.GONE);
                final View gone = match;
                match.post(() -> gone.setVisibility(View.GONE));
            }
        }
        return matches.size();
    }

    private int hideTopBarIconsByDescription(View root) {
        int rootHeight = root.getHeight();
        if (rootHeight <= 0) {
            return 0;
        }
        final java.util.List<View> icons = new java.util.ArrayList<>();
        collectTopIcons(root, icons, new int[1], new int[2], rootHeight);
        int hidden = 0;
        StringBuilder inventory = new StringBuilder();
        for (View icon : icons) {
            CharSequence desc = icon.getContentDescription();
            if (inventory.length() > 0) {
                inventory.append(", ");
            }
            inventory.append(simpleClass(icon)).append(":");
            inventory.append(desc == null ? "null" : desc);
            if (desc != null && desc.toString()
                    .toLowerCase(java.util.Locale.US).contains(SEARCH_DESC)) {
                icon.setVisibility(View.GONE);
                final View gone = icon;
                icon.post(() -> gone.setVisibility(View.GONE));
                hidden++;
            }
        }
        if (!icons.isEmpty() && topIconsLogged.compareAndSet(false, true)) {
            logInfo("top bar icons: [" + inventory + "] [TikTok " + hostTikTokVersion() + "]");
        }
        return hidden;
    }

    private static void collectTopIcons(
            View node, java.util.List<View> out, int[] visited, int[] loc,
            int rootHeight) {
        if (node == null || visited[0] >= NAV_WALK_CAP) {
            return;
        }
        visited[0]++;
        boolean visible;
        try {
            visible = node.getVisibility() == View.VISIBLE;
        } catch (RuntimeException ignored) {
            return;
        }
        if (!visible) {
            return;
        }
        if (node instanceof android.widget.ImageView) {
            try {
                node.getLocationOnScreen(loc);
            } catch (RuntimeException ignored) {
                return;
            }
            if (loc[1] + node.getHeight() <= rootHeight / TOP_ZONE_DEN) {
                out.add(node);
            }
            return;
        }
        if (!(node instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) node;
        final int count;
        try {
            count = group.getChildCount();
        } catch (RuntimeException ignored) {
            return;
        }
        for (int i = 0; i < count && visited[0] < NAV_WALK_CAP; i++) {
            View child;
            try {
                child = group.getChildAt(i);
            } catch (RuntimeException ignored) {
                continue;
            }
            collectTopIcons(child, out, visited, loc, rootHeight);
        }
    }

    private static void hideViewByIdAfterLayout(View root, int resourceId) {
        hideViewById(root, resourceId);
        if (resourceId != 0) {
            root.post(() -> hideViewById(root, resourceId));
        }
    }

    /** Collects visible navigation labels; reversed finds bottom items before the feed eats the budget. */
    private static void collectNavTexts(
            View node, String[] targets, java.util.List<TextView> out,
            int[] visited, boolean reverse) {
        if (node == null || visited[0] >= NAV_WALK_CAP) {
            return;
        }
        visited[0]++;
        boolean visible;
        try {
            visible = node.getVisibility() == View.VISIBLE;
        } catch (RuntimeException ignored) {
            return;
        }
        if (!visible) {
            return;
        }
        if (node instanceof TextView) {
            CharSequence text;
            try {
                text = ((TextView) node).getText();
            } catch (RuntimeException ignored) {
                return;
            }
            if (text != null) {
                for (String target : targets) {
                    if (target.contentEquals(text)) {
                        out.add((TextView) node);
                        break;
                    }
                }
            }
            return;
        }
        if (!(node instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) node;
        final int count;
        try {
            count = group.getChildCount();
        } catch (RuntimeException ignored) {
            return;
        }
        for (int k = 0; k < count && visited[0] < NAV_WALK_CAP; k++) {
            final int index = reverse ? count - 1 - k : k;
            View child;
            try {
                child = group.getChildAt(index);
            } catch (RuntimeException ignored) {
                continue;
            }
            collectNavTexts(child, targets, out, visited, reverse);
        }
    }

    private static String simpleClass(View view) {
        String name = view.getClass().getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** 47.1.3 dropped the u3t tab host, so hide each row holding a tab label. */
    private boolean hideTabRowByText(View root) {
        final java.util.List<TextView> matches = new java.util.ArrayList<>();
        collectNavTexts(root, TAB_TEXTS, matches, new int[1], false);
        keepZoneMatches(root, matches, true);
        if (matches.isEmpty()) {
            return false;
        }
        final java.util.Set<View> hidden = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<View, Boolean>());
        for (TextView tab : matches) {
            View parent = (View) tab.getParent();
            if (parent == null || !hidden.add(parent)) {
                continue;
            }
            parent.setVisibility(View.GONE);
            final View row = parent;
            row.post(() -> row.setVisibility(View.GONE));
        }
        if (hidden.isEmpty()) {
            return false;
        }
        // Overflow chevron has no text/desc: sweep text-less short top siblings.
        // Null descs only count right (spares LIVE/back on the left).
        int rootHeight = root.getHeight();
        int rootWidth = root.getWidth();
        final int[] loc = new int[2];
        StringBuilder inventory = new StringBuilder();
        int swept = 0;
        for (View row : hidden) {
            if (!(row.getParent() instanceof ViewGroup)) {
                continue;
            }
            ViewGroup bar = (ViewGroup) row.getParent();
            if (bar.getChildCount() > BAR_CHILD_CAP) {
                continue;
            }
            for (int i = 0; i < bar.getChildCount(); i++) {
                View sib = bar.getChildAt(i);
                if (sib == null || sib.getVisibility() != View.VISIBLE) {
                    continue;
                }
                sib.getLocationOnScreen(loc);
                int sibBottom = loc[1] + sib.getHeight();
                boolean topZone = rootHeight > 0 && sibBottom <= rootHeight / TOP_ZONE_DEN;
                boolean shortView = rootHeight > 0 && sib.getHeight() < rootHeight / SHORT_DEN;
                CharSequence sibText = sib instanceof TextView ? ((TextView) sib).getText() : null;
                CharSequence sibDesc = sib.getContentDescription();
                if (topZone && inventory.length() < 600) {
                    if (inventory.length() > 0) {
                        inventory.append(", ");
                    }
                    inventory.append(simpleClass(sib));
                    if (sibText != null && sibText.length() > 0) {
                        inventory.append("\"").append(sibText.subSequence(0, Math.min(14, sibText.length()))).append("\"");
                    } else if (sibDesc != null) {
                        inventory.append("(").append(sibDesc.subSequence(0, Math.min(14, sibDesc.length()))).append(")");
                    } else {
                        inventory.append("(.)");
                    }
                    inventory.append(loc[0] + sib.getWidth() / 2 < rootWidth / 2 ? "[L]" : "[R]");
                }
                boolean noText = sibText == null || sibText.length() == 0;
                boolean rightHalf = rootWidth > 0 && loc[0] + sib.getWidth() / 2 >= rootWidth / 2;
                if (topZone && shortView && noText && !hidden.contains(sib)
                        && sibDesc == null && rightHalf) {
                    sib.setVisibility(View.GONE);
                    final View gone = sib;
                    sib.post(() -> gone.setVisibility(View.GONE));
                    swept++;
                }
            }
        }
        if (topSweepLogged.compareAndSet(false, true)) {
            logInfo("top bar sweep: " + swept + " siblings [" + inventory + "] [TikTok " + hostTikTokVersion() + "]");
        }
        if (topHiddenLogged.compareAndSet(false, true)) {
            logInfo("top navigation tab rows hidden: " + hidden.size()
                    + " [TikTok " + hostTikTokVersion() + "]");
        }
        return true;
    }

    private static boolean isChromeOnly(
            ViewGroup bar, int[] loc, int rootHeight) {
        final int count;
        try {
            count = bar.getChildCount();
        } catch (RuntimeException ignored) {
            return false;
        }
        if (count > BAR_CHILD_CAP) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            View c;
            try {
                c = bar.getChildAt(i);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (c == null) {
                continue;
            }
            boolean visible;
            try {
                visible = c.getVisibility() == View.VISIBLE;
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!visible) {
                continue;
            }
            boolean noText = !(c instanceof TextView);
            if (!noText) {
                CharSequence text;
                try {
                    text = ((TextView) c).getText();
                } catch (RuntimeException ignored) {
                    continue;
                }
                noText = text == null || text.length() == 0;
            }
            int height;
            try {
                c.getLocationOnScreen(loc);
                height = c.getHeight();
            } catch (RuntimeException ignored) {
                continue;
            }
            int bottom = loc[1] + height;
            if (!noText || height >= rootHeight / SHORT_DEN
                    || rootHeight <= 0 || bottom < rootHeight * BOTTOM_ZONE_NUM / BOTTOM_ZONE_DEN) {
                return false;
            }
        }
        return true;
    }

    /** 47.1.3 dropped the o7b bar, so hide label rows, then sweep and climb while it's only bar chrome. */
    /** Drops matches living in the wrong half; shared labels exist top and bottom. */
    private static void keepZoneMatches(View root, java.util.List<TextView> matches, boolean topHalf) {
        int rootHeight;
        try {
            rootHeight = root.getHeight();
        } catch (RuntimeException ignored) {
            return;
        }
        if (rootHeight <= 0 || matches.isEmpty()) {
            return;
        }
        final int[] loc = new int[2];
        for (int i = matches.size() - 1; i >= 0; i--) {
            TextView tab = matches.get(i);
            try {
                tab.getLocationOnScreen(loc);
            } catch (RuntimeException ignored) {
                continue;
            }
            int centerY;
            try {
                centerY = loc[1] + tab.getHeight() / 2;
            } catch (RuntimeException ignored) {
                continue;
            }
            if ((centerY < rootHeight / HALF_DEN) != topHalf) {
                matches.remove(i);
            }
        }
    }

    /** Collapses emptied chrome shells to height 0 so the feed reclaims the slot. */
    private void collapseEmptiedBars(View root, java.util.Set<View> hidden) {
        final java.util.Set<ViewGroup> shells = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<ViewGroup, Boolean>());
        for (View row : hidden) {
            ViewParent parent;
            try {
                parent = row.getParent();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (parent instanceof ViewGroup) {
                shells.add((ViewGroup) parent);
            }
        }
        for (int up = 0; up < BAR_WALK_LEVELS && !shells.isEmpty(); up++) {
            final java.util.Set<ViewGroup> next = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<ViewGroup, Boolean>());
            for (ViewGroup shell : shells) {
                if (shell != root) {
                    boolean emptied = true;
                    int count;
                    try {
                        count = shell.getChildCount();
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    for (int i = 0; i < count; i++) {
                        View c;
                        try {
                            c = shell.getChildAt(i);
                        } catch (RuntimeException ignored) {
                            continue;
                        }
                        boolean visible;
                        try {
                            visible = c != null && c.getVisibility() == View.VISIBLE;
                        } catch (RuntimeException ignored) {
                            continue;
                        }
                        if (visible) {
                            emptied = false;
                            break;
                        }
                    }
                    if (emptied) {
                        try {
                            ViewGroup.LayoutParams lp = shell.getLayoutParams();
                            if (lp != null && lp.height != 0) {
                                lp.height = 0;
                                shell.setLayoutParams(lp);
                            }
                            shell.setVisibility(View.GONE);
                            final View gone = shell;
                            shell.post(() -> gone.setVisibility(View.GONE));
                            shell.requestLayout();
                        } catch (RuntimeException ignored) {
                            // Hide stands without the collapse.
                        }
                        if (barCollapsedLogged.compareAndSet(false, true)) {
                            logInfo("bottom bar slot collapsed (" + simpleClass(shell)
                                    + ") [TikTok " + hostTikTokVersion() + "]");
                        }
                    }
                }
                ViewParent parent;
                try {
                    parent = shell.getParent();
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (parent instanceof ViewGroup && parent != root) {
                    next.add((ViewGroup) parent);
                }
            }
            shells.clear();
            shells.addAll(next);
        }
    }

    private boolean hideBottomBarByText(View root) {
        final java.util.List<TextView> matches = new java.util.ArrayList<>();
        collectNavTexts(root, BOTTOM_TEXTS, matches, new int[1], true);
        keepZoneMatches(root, matches, false);
        if (matches.isEmpty()) {
            if (bottomLabelsLogged.compareAndSet(false, true)) {
                logInfo("bottom labels found: 0 [TikTok " + hostTikTokVersion() + "]");
            }
            return false;
        }
        final java.util.Set<View> hidden = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<View, Boolean>());
        for (TextView tab : matches) {
            View parent = (View) tab.getParent();
            if (parent == null || !hidden.add(parent)) {
                continue;
            }
            parent.setVisibility(View.GONE);
            final View row = parent;
            row.post(() -> row.setVisibility(View.GONE));
        }
        if (hidden.isEmpty()) {
            return false;
        }
        int rootHeight = root.getHeight();
        final int[] loc = new int[2];
        for (View row : hidden) {
            if (!(row.getParent() instanceof ViewGroup)) {
                continue;
            }
            ViewGroup bar = (ViewGroup) row.getParent();
            if (bar.getChildCount() > BAR_CHILD_CAP) {
                continue;
            }
            for (int i = 0; i < bar.getChildCount(); i++) {
                View sib = bar.getChildAt(i);
                if (sib == null || sib.getVisibility() != View.VISIBLE) {
                    continue;
                }
                CharSequence sibText = sib instanceof TextView
                        ? ((TextView) sib).getText() : null;
                boolean noText = sibText == null || sibText.length() == 0;
                sib.getLocationOnScreen(loc);
                int sibBottom = loc[1] + sib.getHeight();
                if (noText && sib.getHeight() < rootHeight / SHORT_DEN && rootHeight > 0
                        && sibBottom >= rootHeight * BOTTOM_ZONE_NUM / BOTTOM_ZONE_DEN) {
                    sib.setVisibility(View.GONE);
                    final View gone = sib;
                    sib.post(() -> gone.setVisibility(View.GONE));
                }
            }
        }
        java.util.Set<ViewGroup> level = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<ViewGroup, Boolean>());
        for (View row : hidden) {
            if (row.getParent() instanceof ViewGroup) {
                level.add((ViewGroup) row.getParent());
            }
        }
        StringBuilder barChain = new StringBuilder();
        for (int up = 0; up < BAR_WALK_LEVELS && !level.isEmpty(); up++) {
            java.util.Set<ViewGroup> next = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<ViewGroup, Boolean>());
            for (ViewGroup bar : level) {
                if (bar == root || !isChromeOnly(bar, loc, rootHeight)) {
                    continue;
                }
                bar.setVisibility(View.GONE);
                final View whole = bar;
                bar.post(() -> whole.setVisibility(View.GONE));
                if (barChain.length() > 0) {
                    barChain.append(">");
                }
                barChain.append(simpleClass(bar));
                if (bar.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) bar.getParent();
                    if (parent != root && parent.getChildCount() <= BAR_CHILD_CAP) {
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            View sib = parent.getChildAt(i);
                            if (sib instanceof ViewGroup && sib != bar
                                    && sib.getVisibility() == View.VISIBLE
                                    && isChromeOnly((ViewGroup) sib, loc, rootHeight)) {
                                ((ViewGroup) sib).setVisibility(View.GONE);
                                final View s = sib;
                                sib.post(() -> s.setVisibility(View.GONE));
                                barChain.append("+").append(simpleClass(sib));
                            }
                        }
                    }
                }
                if (bar.getParent() instanceof ViewGroup
                        && bar.getParent() != root) {
                    next.add((ViewGroup) bar.getParent());
                }
            }
            level = next;
        }
        collapseEmptiedBars(root, hidden);
        if (bottomHiddenLogged.compareAndSet(false, true)) {
            logInfo("bottom navigation hidden: " + hidden.size() + " row(s)"
                    + " [TikTok " + hostTikTokVersion() + "]");
        }
        if (barChain.length() > 0 && bottomBarLogged.compareAndSet(false, true)) {
            logInfo("bottom bar fully hidden (" + barChain + ") [TikTok "
                    + hostTikTokVersion() + "]");
        }
        return true;
    }

    private static void hideTopNavigation(View root, int hostId) {
        View host = hostId == 0 ? null : root.findViewById(hostId);
        if (!(host instanceof ViewGroup)) {
            return;
        }
        ViewGroup hostGroup = (ViewGroup) host;
        for (int index = 0; index < hostGroup.getChildCount(); index++) {
            View child = hostGroup.getChildAt(index);
            if (containsHorizontalScrollView(child)) {
                child.setVisibility(View.GONE);
                return;
            }
        }
    }

    private static boolean containsHorizontalScrollView(View view) {
        if (view instanceof HorizontalScrollView) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsHorizontalScrollView(group.getChildAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static final class GlobalNavigationViewIds {
        final int liveEntry;
        final int topNavigationHost;
        final int searchEntry;
        final int bottomNavigation;
        final int videoProgressBar;

        private GlobalNavigationViewIds(
                int liveEntry,
                int topNavigationHost,
                int searchEntry,
                int bottomNavigation,
                int videoProgressBar
        ) {
            this.liveEntry = liveEntry;
            this.topNavigationHost = topNavigationHost;
            this.searchEntry = searchEntry;
            this.bottomNavigation = bottomNavigation;
            this.videoProgressBar = videoProgressBar;
        }

        static GlobalNavigationViewIds from(View root) {
            return new GlobalNavigationViewIds(
                    viewId(root, "jyx"),
                    viewId(root, "u3t"),
                    viewId(root, "jz0"),
                    viewId(root, "o7b"),
                    viewId(root, "video_seek_bar"));
        }

        private static int viewId(View root, String resourceName) {
            return root.getResources().getIdentifier(
                    resourceName, "id", ModuleConfig.TARGET_PACKAGE);
        }
    }


}
