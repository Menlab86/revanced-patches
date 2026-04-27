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
object RotateButton {
    private var instance: PlayerControlButton? = null

    /**
     * Injection point.
     */
    @JvmStatic
    fun initializeButton(controlsView: View) {
        try {
            instance = PlayerControlButton(
                controlsViewGroup = controlsView,
                imageViewButtonId = "revanced_rotate_button",
                buttonVisibility = { isButtonEnabled() },
                onClickListener = { view: View -> onClick(view) },
                onLongClickListener = { view: View -> onLongClick(view) },
            )
        } catch (ex: Exception) {
            Logger.printException({ "initializeButton failure" }, ex)
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
        return Settings.OVERLAY_BUTTON_ROTATE.get()
                && PackageUtils.isTablet()
                && !isAdProgressTextVisible()
    }

    private fun onClick(view: View) {
        animateRotation(view)

        toggleOrientation()
    }

    private fun onLongClick(view: View): Boolean {
        animateRotation(view, true)

        toggleOrientation(true)

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

    private fun animateRotation(view: View, isReverse: Boolean = false) {
        view.animate()
            .rotationBy(if (isReverse) -360f else 360f)
            .setDuration(500)
            .start()
    }
}
