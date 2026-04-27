package app.morphe.extension.youtube.patches.overlaybutton

import android.content.pm.ActivityInfo
import android.view.View
import app.morphe.extension.shared.utils.Logger
import app.morphe.extension.shared.utils.PackageUtils
import app.morphe.extension.shared.utils.Utils
import app.morphe.extension.youtube.settings.Settings
import app.morphe.extension.youtube.shared.PlayerControlButton
import app.morphe.extension.youtube.shared.RootView.isAdProgressTextVisible

@Suppress("unused")
object RotateFullScreenButton {
    private var instance: PlayerControlButton? = null

    /**
     * Injection point.
     */
    @JvmStatic
    fun initializeButton(controlsView: View) {
        try {
            instance = PlayerControlButton(
                controlsViewGroup = controlsView,
                imageViewButtonId = "revanced_rotate_fullscreen_button",
                buttonVisibility = { isButtonEnabled() },
                onClickListener = { view: View -> onClick(view, controlsView) },
                onLongClickListener = { view: View -> onLongClick(view, controlsView) },
            )
        } catch (ex: Exception) {
            Logger.printException({ "RotateFullScreenButton initializeButton failure" }, ex)
        }
    }

    /**
     * injection point
     */
    @JvmStatic
    fun setVisibilityNegatedImmediate() {
        instance?.setVisibilityNegatedImmediate()
    }

    /**
     * injection point
     */
    @JvmStatic
    fun setVisibilityImmediate(visible: Boolean) {
        instance?.setVisibilityImmediate(visible)
    }

    /**
     * injection point
     */
    @JvmStatic
    fun setVisibility(visible: Boolean, animated: Boolean) {
        instance?.setVisibility(visible, animated)
    }

    private fun isButtonEnabled(): Boolean {
        // Only show if setting is enabled
        if (!Settings.OVERLAY_BUTTON_ROTATE_FULLSCREEN.get()) return false
        if (isAdProgressTextVisible()) return false

        // Show only on genuine tablets.
        return PackageUtils.isTablet()
    }

    private fun onClick(view: View, controlsView: View) {
        animateRotation(view)

        // 1. Toggle Screen Orientation
        toggleOrientation()

        // 2. Toggle Fullscreen State
        toggleFullscreen(controlsView)
    }

    private fun onLongClick(view: View, controlsView: View): Boolean {
        animateRotation(view, true)

        // 1. Toggle Screen Orientation (Reverse)
        toggleOrientation(true)

        // 2. Toggle Fullscreen State
        toggleFullscreen(controlsView)

        return true
    }

    private fun toggleOrientation(isReverse: Boolean = false) {
        val activity = Utils.getActivity() ?: return
        val currentOrientation = activity.requestedOrientation

        val isLandscape = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

        if (isLandscape) {
            if (isReverse) {
                // Toggle between landscapes
                activity.requestedOrientation = if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            activity.requestedOrientation = if (isReverse) {
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    private fun toggleFullscreen(controlsView: View) {
        // Search for the YouTube fullscreen button and trigger a click.
        // The ID is usually "fullscreen_button" or "youtube_controls_fullscreen_button_stub".
        val fullscreenButton = Utils.getChildViewByResourceName<View>(controlsView, "fullscreen_button")
            ?: Utils.getChildViewByResourceName<View>(controlsView, "youtube_controls_fullscreen_button_stub")

        if (fullscreenButton != null) {
            fullscreenButton.performClick()
        } else {
            Logger.printDebug { "RotateFullScreenButton: Could not find fullscreen button to toggle" }
        }
    }

    private fun animateRotation(view: View, isReverse: Boolean = false) {
        view.animate()
            .rotationBy(if (isReverse) -360f else 360f)
            .setDuration(500)
            .start()
    }
}
