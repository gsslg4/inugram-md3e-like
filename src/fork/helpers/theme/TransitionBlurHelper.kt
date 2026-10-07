package desu.inugram.helpers.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import desu.inugram.InuConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LiteMode
import java.util.WeakHashMap
import kotlin.math.sin

/**
 * A short defocus across a screen transition: the moving container blurs as it leaves, peaks
 * halfway, and is sharp again by the time it stops.
 *
 * The peak matters. A blur held flat for the whole transition reads as "this has not finished
 * drawing", not as polish - what looks deliberate is a defocus that is already clearing by the time
 * the eye catches up. Hence [peak], zero at both ends.
 *
 * Only on Android 12+, where [RenderEffect] blurs on the GPU; there is no cheap path below that, so
 * the effect is simply absent rather than emulated. It also follows [LiteMode.FLAG_CHAT_BLUR], which
 * the system clears under battery saver - so the blur goes away when the device is conserving,
 * without any logic of our own.
 *
 * Caveat worth knowing: a render effect pushes the view into an offscreen layer, and SurfaceView /
 * TextureView content (video, the camera, round video messages, maps) does not render into one. A
 * transition out of a chat with a video playing is the case to watch.
 *
 * This hangs off [Material3NavigationAnimation], so it needs that animation turned on; with the
 * stock transition there is no per-frame progress of ours to drive it from.
 */
object TransitionBlurHelper {
    /** Below this the blur is invisible but still costs a layer, so drop the effect instead. */
    private const val MIN_RADIUS_PX = 0.6f

    /** Views currently carrying an effect, so a no-op frame does not re-set one. */
    private val blurred = WeakHashMap<View, Boolean>()

    @JvmStatic
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && InuConfig.TRANSITION_BLUR.value
            && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)

    /** Zero at both ends, one in the middle. */
    private fun peak(progress: Float): Float {
        if (progress <= 0f || progress >= 1f) return 0f
        return sin(progress * Math.PI).toFloat()
    }

    @JvmStatic
    fun apply(view: View?, progress: Float) {
        if (view == null) return
        if (!isEnabled()) {
            clear(view)
            return
        }

        val radius = AndroidUtilities.dpf2(InuConfig.TRANSITION_BLUR_RADIUS.value) * peak(progress)
        if (radius < MIN_RADIUS_PX) {
            clear(view)
            return
        }
        setEffect(view, radius)
    }

    @JvmStatic
    fun clear(view: View?) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (blurred.remove(view) == null) return
        view.setRenderEffect(null)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setEffect(view: View, radius: Float) {
        view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        blurred[view] = true
    }
}
