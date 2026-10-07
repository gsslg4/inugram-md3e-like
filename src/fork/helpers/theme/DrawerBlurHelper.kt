package desu.inugram.helpers.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import desu.inugram.InuConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LiteMode

/**
 * Blurs what the side drawer is sliding over, in step with how far it is open.
 *
 * Unlike [TransitionBlurHelper] this tracks rather than peaks: the drawer can sit open indefinitely,
 * and a blur that faded back out while it stayed open would look like a bug. The cost is that the
 * content keeps an offscreen layer for as long as the drawer is open - acceptable because nothing
 * animates underneath it meanwhile.
 *
 * Android 12+ and [LiteMode.FLAG_CHAT_BLUR], same as the rest of the fork's blur.
 */
object DrawerBlurHelper {
    private const val MIN_RADIUS_PX = 0.6f

    private var blurredView: View? = null

    @JvmStatic
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && InuConfig.DRAWER_BLUR.value
            && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)

    /** [progress] is 0 closed, 1 fully open - the drawer's own scrim fraction. */
    @JvmStatic
    fun apply(view: View?, progress: Float) {
        if (view == null) return
        if (!isEnabled()) {
            clear()
            return
        }

        val radius = AndroidUtilities.dpf2(InuConfig.DRAWER_BLUR_RADIUS.value) * progress.coerceIn(0f, 1f)
        if (radius < MIN_RADIUS_PX) {
            clear()
            return
        }
        setEffect(view, radius)
    }

    private fun clear() {
        val view = blurredView ?: return
        blurredView = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.setRenderEffect(null)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setEffect(view: View, radius: Float) {
        view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        blurredView = view
    }
}
