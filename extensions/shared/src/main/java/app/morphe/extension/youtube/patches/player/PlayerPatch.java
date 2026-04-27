package app.morphe.extension.youtube.patches.player;

import static app.morphe.extension.shared.utils.Utils.hideViewBy0dpUnderCondition;
import static app.morphe.extension.shared.utils.Utils.hideViewByRemovingFromParentUnderCondition;
import static app.morphe.extension.shared.utils.Utils.hideViewUnderCondition;
import static app.morphe.extension.shared.utils.Utils.validateValue;

import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.libraries.youtube.innertube.model.media.VideoQuality;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.utils.Logger;
import app.morphe.extension.shared.utils.ResourceUtils;
import app.morphe.extension.shared.utils.Utils;
import app.morphe.extension.youtube.patches.utils.InitializationPatch;
import app.morphe.extension.youtube.patches.utils.PatchStatus;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.EngagementPanel;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.shared.RootView;
import app.morphe.extension.youtube.shared.VideoInformation;
import app.morphe.extension.youtube.utils.VideoUtils;

@SuppressWarnings({"unused", "deprecation"})
public class PlayerPatch {
    private static final IntegerSetting quickActionsMarginTopSetting = Settings.QUICK_ACTIONS_TOP_MARGIN;

    private static final int PLAYER_OVERLAY_OPACITY_LEVEL;
    private static final int QUICK_ACTIONS_MARGIN_TOP;
    private static final float SPEED_OVERLAY_VALUE;

    static {
        final int opacity = validateValue(
                Settings.CUSTOM_PLAYER_OVERLAY_OPACITY,
                0,
                100,
                "revanced_custom_player_overlay_opacity_invalid_toast"
        );
        PLAYER_OVERLAY_OPACITY_LEVEL = (opacity * 255) / 100;

        SPEED_OVERLAY_VALUE = validateValue(
                Settings.SPEED_OVERLAY_VALUE,
                0.0f,
                8.0f,
                "revanced_speed_overlay_value_invalid_toast"
        );

        final int topMargin = validateValue(
                Settings.QUICK_ACTIONS_TOP_MARGIN,
                0,
                32,
                "revanced_quick_actions_top_margin_invalid_toast"
        );

        QUICK_ACTIONS_MARGIN_TOP = Utils.dipToPixels(topMargin);
    }

    // region [Ambient mode control] patch

    /**
     * Constant found in: androidx.window.embedding.DividerAttributes
     */
    private static final int DIVIDER_ATTRIBUTES_COLOR_SYSTEM_DEFAULT = -16777216;

    public static boolean bypassAmbientModeRestrictions(boolean original) {
        return (!Settings.BYPASS_AMBIENT_MODE_RESTRICTIONS.get() && original) || Settings.DISABLE_AMBIENT_MODE.get();
    }

    public static boolean disableAmbientModeInFullscreen() {
        return !Settings.DISABLE_AMBIENT_MODE_IN_FULLSCREEN.get();
    }

    public static int getFullScreenBackgroundColor(int originalColor) {
        if (Settings.DISABLE_AMBIENT_MODE_IN_FULLSCREEN.get()) {
            return DIVIDER_ATTRIBUTES_COLOR_SYSTEM_DEFAULT;
        }

        return originalColor;
    }

    // endregion

    // region [Change player flyout menu toggles] patch

    public static boolean changeSwitchToggle(boolean original) {
        return !Settings.CHANGE_PLAYER_FLYOUT_MENU_TOGGLE.get() && original;
    }

    public static String getToggleString(String str) {
        return ResourceUtils.getString(str);
    }

    // endregion

    // region [Description components] patch

    public static boolean disableRollingNumberAnimations() {
        return Settings.DISABLE_ROLLING_NUMBER_ANIMATIONS.get();
    }

    /**
     * view id R.id.content
     */
    private static final int contentId = ResourceUtils.getIdIdentifier("content");
    private static final boolean EXPAND_VIDEO_DESCRIPTION = Settings.EXPAND_VIDEO_DESCRIPTION.get();

    /**
     * The last time the clickDescriptionView method was called.
     */
    private static long lastTimeDescriptionViewInvoked;

    public static void onVideoDescriptionCreate(RecyclerView recyclerView) {
        if (!EXPAND_VIDEO_DESCRIPTION)
            return;

        recyclerView.getViewTreeObserver().addOnDrawListener(() -> {
            try {
                // Video description panel is only open when the player is active.
                if (!RootView.isPlayerActive()) {
                    return;
                }
                // Video description's recyclerView is a child view of [contentId].
                if (!(recyclerView.getParent().getParent() instanceof View contentView)) {
                    return;
                }
                if (contentView.getId() != contentId) {
                    return;
                }
                // Check description panel opened.
                if (!EngagementPanel.isDescription()) {
                    return;
                }
                // The first view group contains information such as the video's title, like count, and number of views.
                if (!(recyclerView.getChildAt(0) instanceof ViewGroup primaryViewGroup)) {
                    return;
                }
                if (primaryViewGroup.getChildCount() < 2) {
                    return;
                }
                // Typically, descriptionView is placed as the second child of recyclerView.
                if (recyclerView.getChildAt(1) instanceof ViewGroup viewGroup) {
                    clickDescriptionView(viewGroup);
                }
                // In some videos, descriptionView is placed as the third child of recyclerView.
                if (recyclerView.getChildAt(2) instanceof ViewGroup viewGroup) {
                    clickDescriptionView(viewGroup);
                }
                // Even if both methods are performed, there is no major issue with the operation of the patch.
            } catch (Exception ex) {
                Logger.printException(() -> "onVideoDescriptionCreate failed.", ex);
            }
        });
    }

    private static void clickDescriptionView(@NonNull ViewGroup descriptionViewGroup) {
        final View descriptionView = descriptionViewGroup.getChildAt(0);
        if (descriptionView == null) {
            return;
        }
        // This method is sometimes used multiple times.
        // To prevent this, ignore method reuse within 1 second.
        final long now = System.currentTimeMillis();
        if (now - lastTimeDescriptionViewInvoked < 1000) {
            return;
        }
        lastTimeDescriptionViewInvoked = now;

        // The type of descriptionView can be either ViewGroup or TextView. (A/B tests)
        // If the type of descriptionView is TextView, longer delay is required.
        final long delayMillis = descriptionView instanceof TextView
                ? 750
                : 200;

        Utils.runOnMainThreadDelayed(() -> Utils.clickView(descriptionView), delayMillis);
    }

    /**
     * This method is invoked only when the view type of descriptionView is {@link TextView}. (A/B tests)
     *
     * @param textView descriptionView.
     * @param original Whether to apply {@link TextView#setTextIsSelectable}.
     *                 Patch replaces the {@link TextView#setTextIsSelectable} method invoke.
     */
    public static void disableVideoDescriptionInteraction(TextView textView, boolean original) {
        if (textView != null) {
            textView.setTextIsSelectable(
                    !Settings.DISABLE_VIDEO_DESCRIPTION_INTERACTION.get() && original
            );
        }
    }

    // endregion

    // region [Disable haptic feedback] patch

    public static boolean disableChapterVibrate() {
        return Settings.DISABLE_HAPTIC_FEEDBACK_CHAPTERS.get();
    }

    public static boolean disablePreciseSeekingVibrate() {
        return Settings.DISABLE_HAPTIC_FEEDBACK_PRECISE_SEEKING.get();
    }

    public static boolean disableSeekUndoVibrate() {
        return Settings.DISABLE_HAPTIC_FEEDBACK_SEEK_UNDO.get();
    }

    public static Object disableTapAndHoldVibrate(Object vibrator) {
        return Settings.DISABLE_HAPTIC_FEEDBACK_TAP_AND_HOLD.get()
                ? null
                : vibrator;
    }

    public static boolean disableZoomVibrate() {
        return Settings.DISABLE_HAPTIC_FEEDBACK_ZOOM.get();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void vibrate(Vibrator vibrator, VibrationEffect vibrationEffect) {
        if (disableVibrate()) return;
        vibrator.vibrate(vibrationEffect);
    }

    public static void vibrate(Vibrator vibrator, long milliseconds) {
        if (disableVibrate()) return;
        vibrator.vibrate(milliseconds);
    }

    private static boolean disableVibrate() {
        return Settings.DISABLE_HAPTIC_FEEDBACK_CHAPTERS.get()
                && Settings.DISABLE_HAPTIC_FEEDBACK_PRECISE_SEEKING.get()
                && Settings.DISABLE_HAPTIC_FEEDBACK_SEEK_UNDO.get()
                && Settings.DISABLE_HAPTIC_FEEDBACK_TAP_AND_HOLD.get()
                && Settings.DISABLE_HAPTIC_FEEDBACK_ZOOM.get();
    }

    // endregion

    // region [Fullscreen components] patch

    public static void disableEngagementPanels(CoordinatorLayout coordinatorLayout) {
        if (!Settings.DISABLE_ENGAGEMENT_PANEL.get()) return;
        coordinatorLayout.setVisibility(View.GONE);
    }

    public static void showVideoTitleSection(FrameLayout frameLayout, View view) {
        final boolean isEnabled = Settings.SHOW_VIDEO_TITLE_SECTION.get() || !Settings.DISABLE_ENGAGEMENT_PANEL.get();

        if (isEnabled) {
            frameLayout.addView(view);
        }
    }

    public static boolean hideAutoPlayPreview() {
        return Settings.HIDE_AUTOPLAY_PREVIEW.get();
    }

    public static boolean hideRelatedVideoOverlay() {
        return Settings.HIDE_RELATED_VIDEOS_OVERLAY.get();
    }

    public static void hideQuickActions(View view) {
        final boolean isEnabled = Settings.DISABLE_ENGAGEMENT_PANEL.get() || Settings.HIDE_QUICK_ACTIONS.get();

        Utils.hideViewBy0dpUnderCondition(
                isEnabled,
                view
        );
    }

    public static void setQuickActionMargin(View view) {
        int topMarginPx = getQuickActionsTopMargin();
        if (topMarginPx == 0) {
            return;
        }

        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp))
            return;

        mlp.setMargins(
                mlp.leftMargin,
                topMarginPx,
                mlp.rightMargin,
                mlp.bottomMargin
        );
    }

    public static boolean enableCompactControlsOverlay(boolean original) {
        return Settings.ENABLE_COMPACT_CONTROLS_OVERLAY.get() || original;
    }

    public static boolean disableLandScapeMode(boolean original) {
        return Settings.DISABLE_LANDSCAPE_MODE.get() || original;
    }

    private static volatile boolean isScreenOn;

    public static boolean keepFullscreen(boolean original) {
        if (!Settings.KEEP_LANDSCAPE_MODE.get())
            return original;

        return isScreenOn;
    }

    public static void setScreenOn() {
        if (!Settings.KEEP_LANDSCAPE_MODE.get())
            return;

        isScreenOn = true;
        Utils.runOnMainThreadDelayed(() -> isScreenOn = false, Settings.KEEP_LANDSCAPE_MODE_TIMEOUT.get());
    }

    // endregion

    // region [Hide comments component] patch

    public static void changeEmojiPickerOpacity(ImageView imageView) {
        if (!Settings.HIDE_COMMENTS_EMOJI_AND_TIMESTAMP_BUTTONS.get())
            return;

        imageView.setImageAlpha(0);
    }

    @Nullable
    public static Object disableEmojiPickerOnClickListener(@Nullable Object object) {
        return Settings.HIDE_COMMENTS_EMOJI_AND_TIMESTAMP_BUTTONS.get() ? null : object;
    }

    private static final String CHIP_BAR_PATH_PREFIX = "chip_bar.";

    public static void sanitizeCommentsCategoryBar(@NonNull List<Object> list, @NonNull String identifier) {
        try {
            if (Settings.SANITIZE_COMMENTS_CATEGORY_BAR.get() &&
                    identifier.startsWith(CHIP_BAR_PATH_PREFIX) &&
                    PlayerType.getCurrent().isMaximizedOrFullscreen()
            ) {
                int listSize = list.size();
                if (listSize > 2) {
                    list.subList(1, listSize - 1).clear();
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "sanitizeCommentsCategoryBar failure", ex);
        }
    }

    // endregion

    // region [Hide player buttons] patch

    public static boolean hideAutoPlayButton() {
        return Settings.HIDE_PLAYER_AUTOPLAY_BUTTON.get();
    }

    public static boolean hideCaptionsButton(boolean original) {
        return !Settings.HIDE_PLAYER_CAPTIONS_BUTTON.get() && original;
    }

    public static int hideCastButton(int original) {
        return Settings.HIDE_PLAYER_CAST_BUTTON.get()
                ? View.GONE
                : original;
    }

    public static void hideCaptionsButton(View view) {
        Utils.hideViewUnderCondition(Settings.HIDE_PLAYER_CAPTIONS_BUTTON, view);
    }

    public static void hideCollapseButton(ImageView imageView) {
        if (!Settings.HIDE_PLAYER_COLLAPSE_BUTTON.get())
            return;

        imageView.setImageResource(android.R.color.transparent);
        imageView.setImageAlpha(0);
        imageView.setEnabled(false);

        var layoutParams = imageView.getLayoutParams();
        if (layoutParams instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(0, 0);
            imageView.setLayoutParams(lp);
        } else {
            Logger.printDebug(() -> "Unknown collapse button layout params: " + layoutParams);
        }
    }

    public static void setTitleAnchorStartMargin(View titleAnchorView) {
        if (!Settings.HIDE_PLAYER_COLLAPSE_BUTTON.get())
            return;

        var layoutParams = titleAnchorView.getLayoutParams();
        if (titleAnchorView.getLayoutParams() instanceof RelativeLayout.LayoutParams lp) {
            lp.setMarginStart(0);
        } else {
            Logger.printDebug(() -> "Unknown title anchor layout params: " + layoutParams);
        }
    }

    public static ImageView hideFullscreenButton(ImageView imageView) {
        final boolean hideView = Settings.HIDE_PLAYER_FULLSCREEN_BUTTON.get();

        Utils.hideViewUnderCondition(hideView, imageView);
        return hideView ? null : imageView;
    }

    public static boolean hidePreviousNextButton(boolean previousOrNextButtonVisible) {
        return !Settings.HIDE_PLAYER_PREVIOUS_NEXT_BUTTON.get() && previousOrNextButtonVisible;
    }

    private static final int playerControlPreviousButtonTouchAreaId =
            ResourceUtils.getIdIdentifier("player_control_previous_button_touch_area");
    private static final int playerControlNextButtonTouchAreaId =
            ResourceUtils.getIdIdentifier("player_control_next_button_touch_area");

    public static void hidePreviousNextButtons(View parentView) {
        if (!Settings.HIDE_PLAYER_PREVIOUS_NEXT_BUTTON.get()) {
            return;
        }

        // Must use a deferred call to main thread to hide the button.
        // Otherwise the layout crashes if set to hidden now.
        Utils.runOnMainThread(() -> {
            hideView(parentView, playerControlPreviousButtonTouchAreaId);
            hideView(parentView, playerControlNextButtonTouchAreaId);
        });
    }

    private static void hideView(View parentView, int resourceId) {
        View nextPreviousButton = parentView.findViewById(resourceId);

        if (nextPreviousButton == null) {
            Logger.printException(() -> "Could not find player previous / next button");
            return;
        }

        Utils.hideViewByRemovingFromParentUnderCondition(true, nextPreviousButton);
    }

    public static boolean hideMusicButton() {
        return Settings.HIDE_PLAYER_YOUTUBE_MUSIC_BUTTON.get();
    }

    /**
     * Injection point.
     */
    public static void hidePlayerControlButtonsBackground(View rootView) {
        try {
            if (!Settings.HIDE_PLAYER_CONTROL_BUTTONS_BACKGROUND.get()) {
                return;
            }

            // Each button is an ImageView with a background set to another drawable.
            removeImageViewsBackgroundRecursive(rootView);
        } catch (Exception ex) {
            Logger.printException(() -> "removePlayerControlButtonsBackground failure", ex);
        }
    }

    private static void removeImageViewsBackgroundRecursive(View currentView) {
        if (currentView instanceof ImageView imageView) {
            imageView.setBackground(null);
        }

        if (currentView instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                removeImageViewsBackgroundRecursive(viewGroup.getChildAt(i));
            }
        }
    }

    // endregion

    // region [Player components] patch

    public static void changeOpacity(ImageView imageView) {
        imageView.setImageAlpha(PLAYER_OVERLAY_OPACITY_LEVEL);
    }

    /**
     * Used in YouTube 18.29.38 ~ 20.04.46.
     */
    private static boolean isAutoPopupPanel;

    /**
     * Used in YouTube 18.29.38 ~ 20.04.46.
     */
    public static boolean disableAutoPlayerPopupPanels(boolean isLiveChatOrPlaylistPanel) {
        if (!Settings.DISABLE_AUTO_PLAYER_POPUP_PANELS.get()) {
            return false;
        }
        if (isLiveChatOrPlaylistPanel) {
            return true;
        }
        return isAutoPopupPanel && !RootView.isShortsActive();
    }

    /**
     * Used in YouTube 18.29.38 ~ 20.04.46.
     */
    public static void setInitVideoPanel(boolean initVideoPanel) {
        isAutoPopupPanel = initVideoPanel;
    }

    /**
     * Used in YouTube 20.05.46+.
     */
    @NonNull
    private static final AtomicBoolean newVideoStarted = new AtomicBoolean(false);

    /**
     * Used in YouTube 20.05.46+.
     */
    public static boolean disableAutoPlayerPopupPanels(boolean isLiveChatOrPlaylistPanel, String panelId) {
        if (Settings.DISABLE_AUTO_PLAYER_POPUP_PANELS.get()) {
            return isLiveChatOrPlaylistPanel || (panelId.equals("PAproduct_list") && newVideoStarted.get());
        }
        return false;
    }

    /**
     * Used in YouTube 20.05.46+.
     */
    public static void disableAutoPlayerPopupPanels(@NonNull String newlyLoadedChannelId, @NonNull String newlyLoadedChannelName,
                                                    @NonNull String newlyLoadedVideoId, @NonNull String newlyLoadedVideoTitle,
                                                    final long newlyLoadedVideoLength, boolean newlyLoadedLiveStreamValue) {
        if (Settings.DISABLE_AUTO_PLAYER_POPUP_PANELS.get() && newVideoStarted.compareAndSet(false, true)) {
            Utils.runOnMainThreadDelayed(() -> newVideoStarted.compareAndSet(true, false), 3000L);
        }
    }

    @NonNull
    public static String videoId = "";

    public static void disableAutoSwitchMixPlaylists(@NonNull String newlyLoadedChannelId, @NonNull String newlyLoadedChannelName,
                                                     @NonNull String newlyLoadedVideoId, @NonNull String newlyLoadedVideoTitle,
                                                     final long newlyLoadedVideoLength, boolean newlyLoadedLiveStreamValue) {
        if (!Settings.DISABLE_AUTO_SWITCH_MIX_PLAYLISTS.get()) {
            return;
        }
        if (PlayerType.getCurrent() == PlayerType.INLINE_MINIMAL) {
            return;
        }
        if (Objects.equals(newlyLoadedVideoId, videoId)) {
            return;
        }
        videoId = newlyLoadedVideoId;

        if (!VideoInformation.lastPlayerResponseIsAutoGeneratedMixPlaylist()) {
            return;
        }
        VideoUtils.pauseMedia();
        VideoUtils.openVideo(newlyLoadedVideoId);
    }

    public static boolean disableDoubleTapChapters(boolean original) {
        return !Settings.DISABLE_CHAPTER_SKIP_DOUBLE_TAP.get() && original;
    }

    public static boolean disableSpeedOverlay() {
        return disableSpeedOverlay(true);
    }

    public static boolean disableSpeedOverlay(boolean original) {
        return !Settings.DISABLE_SPEED_OVERLAY.get() && original;
    }

    public static CharSequence onCharSequenceLoaded(@NonNull Object conversionContext,
                                                    @NonNull CharSequence charSequence) {
        try {
            if (Settings.DISABLE_SPEED_OVERLAY.get() || SPEED_OVERLAY_VALUE == 2.0f) {
                return charSequence;
            }
            final String conversionContextString = conversionContext.toString();
            if (!conversionContextString.contains("identifierProperty=seek_edu_overlay_v2.")) {
                return charSequence;
            }
            if (!conversionContextString.contains("elementId=0,0,0,0,")) {
                return charSequence;
            }
            return VideoUtils.formatSpeedStringX(SPEED_OVERLAY_VALUE, 2) + ' ';
        } catch (Exception ex) {
            Logger.printException(() -> "onCharSequenceLoaded failed", ex);
        }
        return charSequence;
    }

    public static double speedOverlayValue() {
        return speedOverlayValue(2.0f);
    }

    public static float speedOverlayValue(float original) {
        return SPEED_OVERLAY_VALUE;
    }

    public static float speedOverlayRelativeValue(float original) {
        return SPEED_OVERLAY_VALUE != 2.0f
                ? 0f
                : original;
    }

    public static boolean hideChannelWatermark(boolean original) {
        return !Settings.HIDE_CHANNEL_WATERMARK.get() && original;
    }

    public static void hideCrowdfundingBox(View view) {
        hideViewBy0dpUnderCondition(Settings.HIDE_CROWDFUNDING_BOX.get(), view);
    }

    public static void hideDoubleTapOverlayFilter(View view) {
        hideViewByRemovingFromParentUnderCondition(Settings.HIDE_DOUBLE_TAP_OVERLAY_FILTER, view);
    }

    public static boolean hideEndScreenCards() {
        return Settings.HIDE_END_SCREEN_CARDS.get();
    }

    public static void hideEndScreenCards(View view) {
        if (Settings.HIDE_END_SCREEN_CARDS.get()) {
            view.setVisibility(View.GONE);
        }
    }

    public static boolean hideEndScreenSuggestedVideo() {
        return Settings.HIDE_END_SCREEN_SUGGESTED_VIDEO.get();
    }

    public static void skipAutoPlayCountdown(View view) {
        if (!hideEndScreenSuggestedVideo())
            return;
        if (!Settings.SKIP_AUTOPLAY_COUNTDOWN.get())
            return;

        Utils.clickView(view);
    }

    public static boolean hideFilmstripOverlay() {
        return Settings.HIDE_FILMSTRIP_OVERLAY.get();
    }

    public static boolean hideFilmstripOverlay(boolean original) {
        return !Settings.HIDE_FILMSTRIP_OVERLAY.get() && original;
    }

    public static boolean hideInfoCard(boolean original) {
        return !Settings.HIDE_INFO_CARDS.get() && original;
    }

    public static boolean hideSeekMessage() {
        return Settings.HIDE_SEEK_MESSAGE.get();
    }

    @Nullable
    public static ViewStub hideSeekMessage(ViewStub viewStub) {
        return Settings.HIDE_SEEK_MESSAGE.get()
                ? null
                : viewStub;
    }

    public static boolean hideSeekUndoMessage() {
        return Settings.HIDE_SEEK_UNDO_MESSAGE.get();
    }

    public static void hideSuggestedActions(View view) {
        hideViewUnderCondition(Settings.HIDE_SUGGESTED_ACTION.get(), view);
    }

    public static boolean hideZoomOverlay() {
        return Settings.HIDE_ZOOM_OVERLAY.get();
    }

    // endregion

    // region [Hide player flyout menu] patch

    public static VideoQuality[] hidePlayerFlyoutMenuEnhancedBitrate(VideoQuality[] videoQualities) {
        if (Settings.HIDE_PLAYER_FLYOUT_MENU_ENHANCED_BITRATE.get() &&
                ArrayUtils.isNotEmpty(videoQualities)) {
            try {
                return Arrays.stream(videoQualities)
                        .filter(quality -> !StringUtils.contains(quality.patch_getQualityName(), "Premium"))
                        .toArray(VideoQuality[]::new);
            } catch (Exception ex) {
                Logger.printException(() -> "hidePlayerFlyoutMenuEnhancedBitrate failure", ex);
            }
        }

        return videoQualities;
    }

    public static void hidePlayerFlyoutMenuCaptionsFooter(View view) {
        Utils.hideViewUnderCondition(
                Settings.HIDE_PLAYER_FLYOUT_MENU_CAPTIONS_FOOTER.get(),
                view
        );
    }

    public static void hidePlayerFlyoutMenuQualityFooter(View view) {
        Utils.hideViewUnderCondition(
                Settings.HIDE_PLAYER_FLYOUT_MENU_QUALITY_FOOTER.get(),
                view
        );
    }

    public static View hidePlayerFlyoutMenuQualityHeader(View view) {
        return Settings.HIDE_PLAYER_FLYOUT_MENU_QUALITY_HEADER.get()
                ? new View(view.getContext()) // empty view
                : view;
    }

    /**
     * Overriding this values is possible only after the litho component has been loaded.
     * Otherwise, crash will occur.
     * See {@link InitializationPatch#onCreate}.
     *
     * @param original original value.
     * @return whether to enable PiP Mode in the player flyout menu.
     */
    public static boolean hidePiPModeMenu(boolean original) {
        if (!BaseSettings.SETTINGS_INITIALIZED.get()) {
            return original;
        }

        return !Settings.HIDE_PLAYER_FLYOUT_MENU_PIP.get();
    }

    /**
     * Overriding this values is possible only after the litho component has been loaded.
     * Otherwise, crash will occur.
     * See {@link InitializationPatch#onCreate}.
     *
     * @param original original value.
     * @return whether to enable Sleep timer Mode in the player flyout menu.
     */
    public static boolean hideDeprecatedSleepTimerMenu(boolean original) {
        if (!BaseSettings.SETTINGS_INITIALIZED.get()) {
            return original;
        }

        return !Settings.HIDE_PLAYER_FLYOUT_MENU_SLEEP_TIMER.get();
    }

    // endregion

    // region [Seekbar components] patch

    private static final java.util.WeakHashMap<View, TextView> alwaysVisibleTimestampMap = new java.util.WeakHashMap<>();

    public static void updateAlwaysVisibleTimestamp(final TextView originalTimestampView, String timestampString) {
        TextView existingView = alwaysVisibleTimestampMap.get(originalTimestampView);
        
        if (existingView == null) {
            View parent = (View) originalTimestampView.getParent();
            ViewGroup playerView = null;
            while (parent != null) {
                if (parent.getClass().getSimpleName().contains("PlayerView") || parent.getClass().getSimpleName().contains("PlayerViewGroup")) {
                    playerView = (ViewGroup) parent;
                    break;
                }
                if (parent.getParent() instanceof View) {
                    parent = (View) parent.getParent();
                } else {
                    break;
                }
            }
            
            if (playerView == null && originalTimestampView.getRootView() instanceof ViewGroup) {
                playerView = (ViewGroup) originalTimestampView.getRootView().findViewById(android.R.id.content);
            }

            if (playerView != null) {
                final TextView newAlwaysVisibleTimestampView = new TextView(originalTimestampView.getContext());
                newAlwaysVisibleTimestampView.setTextColor(0xFFFFFFFF);
                newAlwaysVisibleTimestampView.setTextSize(14f);
                newAlwaysVisibleTimestampView.setShadowLayer(5f, 0f, 0f, 0xFF000000);
                
                if (playerView instanceof RelativeLayout) {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    params.addRule(RelativeLayout.ALIGN_PARENT_START);
                    params.bottomMargin = Utils.dipToPixels(24);
                    params.setMarginStart(Utils.dipToPixels(16));
                    playerView.addView(newAlwaysVisibleTimestampView, params);
                } else if (playerView instanceof FrameLayout) {
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                    params.bottomMargin = Utils.dipToPixels(24);
                    params.setMarginStart(Utils.dipToPixels(16));
                    playerView.addView(newAlwaysVisibleTimestampView, params);
                } else {
                    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    playerView.addView(newAlwaysVisibleTimestampView, params);
                }
                
                alwaysVisibleTimestampMap.put(originalTimestampView, newAlwaysVisibleTimestampView);
                
                // Start a periodic update to capture the ticking current time
                newAlwaysVisibleTimestampView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (newAlwaysVisibleTimestampView.getParent() == null) return;
                        
                        try {
                            long videoTime = app.morphe.extension.youtube.shared.VideoInformation.getVideoTime();
                            long videoLength = app.morphe.extension.youtube.shared.VideoInformation.getVideoLength();
                            float speed = app.morphe.extension.youtube.shared.VideoInformation.getPlaybackSpeed();
                            if (speed <= 0f) speed = 1.0f;
                            
                            if (videoTime >= 0) {
                                String formatPref = app.morphe.extension.youtube.patches.overlaybutton.AlwaysVisibleTimestampButton.isLongPressFormatActive() 
                                    ? app.morphe.extension.youtube.settings.Settings.ALWAYS_VISIBLE_TIMESTAMP_FORMAT_LONG_PRESS.get() 
                                    : app.morphe.extension.youtube.settings.Settings.ALWAYS_VISIBLE_TIMESTAMP_FORMAT_DEFAULT.get();
                                int formatMode = 0;
                                try { formatMode = Integer.parseInt(formatPref); } catch (Exception e) {}
                                
                                if (videoLength <= 0) {
                                    Object cachedTag = originalTimestampView.getTag(android.R.id.text1);
                                    if (cachedTag instanceof Long) {
                                        videoLength = (Long) cachedTag;
                                    } else {
                                        String originalText = originalTimestampView.getText().toString();
                                        if (originalText.contains(" / ")) {
                                            String totalStr = originalText.substring(originalText.indexOf(" / ") + 3).trim();
                                            String[] parts = totalStr.split(":");
                                            try {
                                                long parsedLength = 0;
                                                if (parts.length == 3) {
                                                    parsedLength = (Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2])) * 1000;
                                                } else if (parts.length == 2) {
                                                    parsedLength = (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000;
                                                } else if (parts.length == 1) {
                                                    parsedLength = Long.parseLong(parts[0]) * 1000;
                                                }
                                                if (parsedLength > 0) {
                                                    videoLength = parsedLength;
                                                    originalTimestampView.setTag(android.R.id.text1, parsedLength);
                                                }
                                            } catch (Exception e) {}
                                        }
                                    }
                                }
                                
                                String elapsed = formatTime(videoTime);
                                String total = videoLength > 0 ? formatTime(videoLength) : "";
                                
                                long remainingTime = videoLength > 0 ? (videoLength - videoTime) : 0;
                                long elapsedAdj = (long) (videoTime / speed);
                                long totalAdj = (long) (videoLength / speed);
                                long remainingAdj = (long) (remainingTime / speed);
                                
                                String elapsedAdjStr = formatTime(elapsedAdj);
                                String totalAdjStr = formatTime(totalAdj);
                                String remainingAdjStr = "-" + formatTime(remainingAdj);
                                
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
                                java.util.Date endTime = new java.util.Date(System.currentTimeMillis() + remainingAdj);
                                String endTimeStr = sdf.format(endTime);
                                
                                String finalText = elapsed + (total.isEmpty() ? "" : " / " + total);
                                
                                switch (formatMode) {
                                    case 1:
                                        if (videoLength > 0) {
                                            finalText += " (" + elapsedAdjStr + " / " + totalAdjStr + " " + remainingAdjStr + ")";
                                        }
                                        break;
                                    case 2:
                                        if (videoLength > 0) {
                                            finalText = remainingAdjStr;
                                        }
                                        break;
                                    case 3:
                                        if (videoLength > 0) {
                                            finalText = endTimeStr;
                                        }
                                        break;
                                    case 4:
                                        if (videoLength > 0) {
                                            finalText = remainingAdjStr + " (" + endTimeStr + ")";
                                        }
                                        break;
                                    case 5:
                                        finalText = elapsed;
                                        break;
                                    case 0:
                                    default:
                                        break;
                                }
                                newAlwaysVisibleTimestampView.setText(finalText);
                            }
                        } catch (Exception ignored) {
                        }
                        
                        newAlwaysVisibleTimestampView.postDelayed(this, 100);
                    }
                });
                
                existingView = newAlwaysVisibleTimestampView;
            }
        }
        
        if (existingView != null) {
            existingView.setVisibility(app.morphe.extension.youtube.settings.Settings.ALWAYS_VISIBLE_TIMESTAMP_ENABLED.get() ? View.VISIBLE : View.GONE);
        }
    }
    
    private static String formatTime(long ms) {
        String t = app.morphe.extension.shared.utils.Utils.getTimeStamp(ms);
        if (t.startsWith("00:")) t = t.substring(3);
        if (t.startsWith("0") && t.length() > 4) t = t.substring(1);
        else if (t.startsWith("0") && t.length() == 4) t = t.substring(1);
        return t;
    }

    public static String appendTimeStampInformation(String original) {
        if (!Settings.APPEND_TIME_STAMP_INFORMATION.get()) return original;

        String appendString = Settings.APPEND_TIME_STAMP_INFORMATION_TYPE.get()
                ? VideoUtils.getFormattedQualityString(null)
                : VideoUtils.getFormattedSpeedString(null);

        // Encapsulate the entire appendString with bidi control characters
        appendString = "\u2066" + appendString + "\u2069";

        // Format the original string with the appended timestamp information
        return String.format(
                "%s\u2009•\u2009%s", // Add the separator and the appended information
                original, appendString
        );
    }

    public static void setContainerClickListener(View view) {
        if (!Settings.APPEND_TIME_STAMP_INFORMATION.get())
            return;

        if (!(view.getParent() instanceof View containerView))
            return;

        final BooleanSetting appendTypeSetting = Settings.APPEND_TIME_STAMP_INFORMATION_TYPE;
        final boolean previousBoolean = appendTypeSetting.get();

        containerView.setOnLongClickListener(timeStampContainerView -> {
                    appendTypeSetting.save(!previousBoolean);
                    return true;
                }
        );

        if (Settings.REPLACE_TIME_STAMP_ACTION.get()) {
            View.OnClickListener listener = v -> {
                Context context = v.getContext();
                if (Settings.APPEND_TIME_STAMP_INFORMATION_TYPE.get()) {
                    if (Settings.APPEND_TIME_STAMP_INFORMATION_VIDEO_QUALITY_MENU_TYPE.get()) {
                        VideoUtils.showCustomVideoQualityFlyoutMenu(context);
                    } else {
                        VideoUtils.showYouTubeLegacyVideoQualityFlyoutMenu();
                    }
                } else {
                    switch (Settings.APPEND_TIME_STAMP_INFORMATION_PLAYBACK_SPEED_MENU_TYPE.get()) {
                        case YOUTUBE_LEGACY ->
                                VideoUtils.showYouTubeLegacyPlaybackSpeedFlyoutMenu();
                        case CUSTOM_NO_THEME ->
                                VideoUtils.showCustomNoThemePlaybackSpeedDialog(context);
                        case CUSTOM_LEGACY ->
                                VideoUtils.showCustomLegacyPlaybackSpeedDialog(context);
                        case CUSTOM_MODERN ->
                                VideoUtils.showCustomModernPlaybackSpeedDialog(context);
                    }
                }
            };
            containerView.setOnClickListener(listener);
        }
    }

    public static boolean enableSeekbarTapping() {
        return Settings.ENABLE_SEEKBAR_TAPPING.get();
    }

    public static boolean enableHighQualityFullscreenThumbnails() {
        return Settings.RESTORE_OLD_SEEKBAR_THUMBNAILS.get();
    }

    private static final int timeBarChapterViewId =
            ResourceUtils.getIdIdentifier("time_bar_chapter_title");

    public static boolean hideSeekbar() {
        return Settings.HIDE_SEEKBAR.get();
    }

    public static boolean disableSeekbarChapters() {
        return Settings.DISABLE_SEEKBAR_CHAPTERS.get();
    }

    public static boolean hideSeekbarChapterLabel(View view) {
        return Settings.HIDE_SEEKBAR_CHAPTER_LABEL.get() && view.getId() == timeBarChapterViewId;
    }

    public static boolean hideTimeStamp() {
        return Settings.HIDE_TIME_STAMP.get();
    }

    public static boolean restoreOldSeekbarThumbnails() {
        return !Settings.RESTORE_OLD_SEEKBAR_THUMBNAILS.get();
    }

    // endregion

    public static int getQuickActionsTopMargin() {
        if (!PatchStatus.QuickActions()) {
            return 0;
        }
        return QUICK_ACTIONS_MARGIN_TOP;
    }

}
