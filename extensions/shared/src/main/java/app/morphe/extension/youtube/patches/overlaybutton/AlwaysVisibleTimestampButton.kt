package app.morphe.extension.youtube.patches.overlaybutton

import android.view.View
import app.morphe.extension.shared.utils.Logger
import app.morphe.extension.youtube.settings.Settings
import app.morphe.extension.youtube.shared.PlayerControlButton

@Suppress("unused")
object AlwaysVisibleTimestampButton {
    private val alwaysVisibleTimestampEnabled = Settings.ALWAYS_VISIBLE_TIMESTAMP_ENABLED
    private var instance: PlayerControlButton? = null

    @JvmStatic
    var isLongPressFormatActive = false
        private set

    @JvmStatic
    fun initializeButton(controlsView: View) {
        try {
            instance = PlayerControlButton(
                controlsViewGroup = controlsView,
                imageViewButtonId = "revanced_always_visible_timestamp_button",
                buttonVisibility = { Settings.OVERLAY_BUTTON_ALWAYS_VISIBLE_TIMESTAMP.get() },
                onClickListener = { onClick() },
                onLongClickListener = {
                    isLongPressFormatActive = !isLongPressFormatActive
                    true
                }
            )
            instance?.changeSelected(selected = alwaysVisibleTimestampEnabled.get())
        } catch (ex: Exception) {
            Logger.printException({ "initializeButton failure" }, ex)
        }
    }

    @JvmStatic
    fun setVisibilityNegatedImmediate() {
        instance?.setVisibilityNegatedImmediate()
    }

    @JvmStatic
    fun setVisibilityImmediate(visible: Boolean) {
        instance?.setVisibilityImmediate(visible)
    }

    @JvmStatic
    fun setVisibility(visible: Boolean, animated: Boolean) {
        instance?.setVisibility(visible, animated)
    }

    private fun onClick() {
        val newState = !alwaysVisibleTimestampEnabled.get()
        alwaysVisibleTimestampEnabled.save(newState)
        instance?.changeSelected(newState)
    }

    private fun PlayerControlButton.changeSelected(selected: Boolean) {
        val imageView = imageView() ?: return
        imageView.isSelected = selected
    }
}
