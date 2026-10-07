package desu.inugram.helpers.security

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import desu.inugram.InuConfig
import org.telegram.messenger.AndroidUtilities

/**
 * Blurs the window while the app is in the background, so the app switcher shows a frosted card
 * instead of your last open chat.
 *
 * This is privacy first and decoration second: the recents preview is the one place the app's
 * contents are shown to whoever is holding - or looking over - the phone, with the app locked or
 * not.
 *
 * The effect is applied in `onPause` and dropped in `onResume`, which is before the first frame of
 * the resumed activity, so the blur is never seen from inside the app.
 *
 * Two things to know. The system takes its snapshot around the same moment we apply this, so
 * whether the blur lands in the card is a race we do not control and have to confirm per device.
 * And a render effect puts the window in an offscreen layer, which SurfaceView and TextureView
 * content does not render into - a video playing when you leave could stay sharp underneath. If
 * either turns out to matter more than the look, the deterministic alternative is FLAG_SECURE,
 * which blanks the card completely rather than blurring it.
 */
object RecentsBlurHelper {
    @JvmStatic
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && InuConfig.RECENTS_BLUR.value

    @JvmStatic
    fun onPause(activity: Activity?) {
        val root = activity?.window?.decorView ?: return
        if (!isEnabled()) return
        applyBlur(root)
    }

    @JvmStatic
    fun onResume(activity: Activity?) {
        val root = activity?.window?.decorView ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        clearBlur(root)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlur(root: View) {
        val radius = AndroidUtilities.dpf2(InuConfig.RECENTS_BLUR_RADIUS.value).coerceAtLeast(1f)
        root.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun clearBlur(root: View) {
        root.setRenderEffect(null)
    }
}
