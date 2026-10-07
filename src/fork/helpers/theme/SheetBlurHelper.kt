package desu.inugram.helpers.theme

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import desu.inugram.InuConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LiteMode

/**
 * Blurs what sits behind a bottom sheet's own window, so the screen under it reads as frosted
 * rather than merely dimmed.
 *
 * This is the compositor's blur, not ours: `FLAG_BLUR_BEHIND` asks the system to blur everything
 * behind the window, which is both cheaper and more correct than blurring a view tree we do not own
 * - the sheet lives in its own window, so there is nothing of the activity inside it to apply a
 * [android.graphics.RenderEffect] to.
 *
 * Android 12+, and only when the device actually offers cross-window blur: it is a capability the
 * system withdraws on weaker GPUs and under battery saver, which is also why
 * [WindowManager.isCrossWindowBlurEnabled] is checked per call rather than cached. We additionally
 * follow [LiteMode.FLAG_CHAT_BLUR] so this goes away with the rest of the app's blur.
 *
 * Stock already zeroes `dimAmount` and draws its own scrim over the blur, so the two stack: expect
 * to want a smaller radius than a blur on its own would need.
 */
object SheetBlurHelper {
    @JvmStatic
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && InuConfig.SHEET_BLUR.value
            && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)

    /**
     * Call with the params about to be handed to [Window.setAttributes]; mutates them in place.
     * Safe to call on any version - it no-ops where the capability is missing.
     */
    @JvmStatic
    fun apply(window: Window?, params: WindowManager.LayoutParams?) {
        if (window == null || params == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        applyImpl(window, params)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyImpl(window: Window, params: WindowManager.LayoutParams) {
        val wm = window.context.getSystemService(WindowManager::class.java)
        if (!isEnabled() || wm?.isCrossWindowBlurEnabled != true) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.blurBehindRadius = 0
            return
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = AndroidUtilities.dp(InuConfig.SHEET_BLUR_RADIUS.value)
    }
}
