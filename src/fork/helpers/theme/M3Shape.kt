package desu.inugram.helpers.theme

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import kotlin.math.min

/**
 * Material 3 Expressive shape tokens, plus the press-time shape morph that is the system's most
 * recognisable gesture: a container's corners tighten while it is held and spring back on release.
 *
 * Expressive's own morphs run on `androidx.graphics.shapes` polygons. Everything inugram needs so
 * far is a round rect, so [MorphingSelectorDrawable] animates the corner radius instead - no new
 * dependency, and it still works as a plain [Drawable] background on stock views, including their
 * elevation shadow (see [MorphingSelectorDrawable.getOutline]).
 */
object M3Shape {
    // Corner tokens, in dp. `FULL` is a pill: half the shorter side, resolved per-bounds.
    const val NONE = 0f
    const val EXTRA_SMALL = 4f
    const val SMALL = 8f
    const val MEDIUM = 12f
    const val LARGE = 16f
    const val LARGE_INCREASED = 20f
    const val EXTRA_LARGE = 28f
    const val EXTRA_LARGE_INCREASED = 32f
    const val EXTRA_EXTRA_LARGE = 48f
    const val FULL = -1f

    /**
     * Background that blends [restColor] to [pressedColor] and morphs its corners from
     * [restRadiusDp] to [pressedRadiusDp] while held. Radii accept the tokens above, [FULL]
     * included.
     */
    @JvmStatic
    fun pressMorph(
        restColor: Int,
        pressedColor: Int,
        restRadiusDp: Float,
        pressedRadiusDp: Float,
    ): Drawable = MorphingSelectorDrawable(restColor, pressedColor, restRadiusDp, pressedRadiusDp)

    class MorphingSelectorDrawable(
        private val restColor: Int,
        private val pressedColor: Int,
        private val restRadiusDp: Float,
        private val pressedRadiusDp: Float,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        /** 0 = resting, 1 = fully held. Driven by [onStateChange], read by [draw]. */
        private var pressProgress = 0f

        /** The state we are animating towards, so a state change that is not a press is a no-op. */
        private var pressTarget = 0f

        /** Whatever [setAlpha] was handed; folded into the fill in [draw]. */
        private var alphaFactor = 255

        private var animator: ValueAnimator? = null

        override fun isStateful() = true

        override fun onStateChange(state: IntArray): Boolean {
            val pressed = state.any { it == android.R.attr.state_pressed }
            animateTo(if (pressed) 1f else 0f)
            return true
        }

        private fun animateTo(target: Float) {
            if (pressTarget == target) return
            pressTarget = target
            animator?.cancel()

            val token = M3Motion.FAST_SPATIAL
            val from = pressProgress
            animator = ValueAnimator.ofFloat(from, target).apply {
                interpolator = token.interpolator
                duration = token.duration()
                addUpdateListener {
                    pressProgress = it.animatedValue as Float
                    invalidateSelf()
                    // invalidateSelf() redraws but does not rebuild the host view's outline, and a
                    // View only rebuilds it on a size change or an explicit call - without this the
                    // elevation shadow would keep the resting corner radius while the fill morphs.
                    (this@MorphingSelectorDrawable.callback as? View)?.invalidateOutline()
                }
                start()
            }
        }

        private fun radiusPx(): Float {
            val rest = resolve(restRadiusDp)
            val pressed = resolve(pressedRadiusDp)
            // The spatial spring overshoots past 1, which is the point - let the radius overshoot
            // with it rather than clamping the progress.
            return rest + (pressed - rest) * pressProgress
        }

        private fun resolve(radiusDp: Float): Float =
            if (radiusDp == FULL) {
                min(bounds.width(), bounds.height()) / 2f
            } else {
                AndroidUtilities.dpf2(radiusDp)
            }

        override fun draw(canvas: Canvas) {
            rect.set(bounds)
            // Assigning Paint.color overwrites its alpha, so the drawable-level alpha has to be
            // folded back in afterwards, not set before.
            val color = ColorUtils.blendARGB(restColor, pressedColor, pressProgress.coerceIn(0f, 1f))
            paint.color = color
            paint.alpha = Color.alpha(color) * alphaFactor / 255
            val r = radiusPx()
            canvas.drawRoundRect(rect, r, r, paint)
        }

        /** Keeps the elevation shadow in step with the morph. */
        override fun getOutline(outline: Outline) {
            outline.setRoundRect(bounds, radiusPx())
        }

        override fun setAlpha(alpha: Int) {
            alphaFactor = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
