package desu.inugram.helpers.theme

import android.view.animation.Interpolator
import desu.inugram.InuConfig
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Material 3 Expressive motion physics, expressed as plain [Interpolator]s so stock call sites can
 * keep using [android.animation.ObjectAnimator] / [android.animation.ValueAnimator] unchanged.
 *
 * Expressive replaces M3's cubic-bezier easing with springs. Instead of pulling in
 * `androidx.dynamicanimation` (and rewriting every animator into a `SpringAnimation`), each token
 * here analytically solves its spring once and exposes the result as an interpolator plus the
 * duration the spring takes to settle. A stock site becomes a two-line change:
 *
 * ```java
 * animator.setInterpolator(M3Motion.DEFAULT_SPATIAL.interpolator);
 * animator.setDuration(M3Motion.DEFAULT_SPATIAL.duration());
 * ```
 *
 * Spatial tokens are under-damped - they overshoot slightly, which is what reads as "expressive".
 * Use them for anything that moves or resizes. Effects tokens are critically damped (no overshoot);
 * use them for colour, alpha and elevation, where an overshoot looks like a bug.
 *
 * One caveat of expressing a spring as an interpolator: the normalised curve depends only on
 * `damping`, so two tokens with equal damping (e.g. [DEFAULT_SPATIAL] and [SLOW_SPATIAL]) differ
 * only in [SpringToken.duration]. A call site that keeps its own duration therefore gets the right
 * shape but not the right pace - pass [SpringToken.duration] along wherever the duration is ours to
 * set.
 *
 * Damping/stiffness values follow the Expressive motion scheme; treat them as the tuning surface for
 * this fork rather than as spec constants, and verify against the current spec before quoting them.
 */
object M3Motion {
    /**
     * Residual at which a spring counts as settled; sets each token's duration.
     *
     * 1%, not something tighter: the tail of an under-damped spring is visually over long before it
     * is mathematically over, and a stricter threshold stretches the tokens well past the durations
     * M3 publishes (at 0.2% the fast spatial token runs 366ms instead of 271ms).
     */
    private const val SETTLE_EPSILON = 0.01

    private const val MIN_DURATION_MS = 50L
    private const val MAX_DURATION_MS = 1200L

    class SpringToken internal constructor(
        @JvmField val damping: Float,
        @JvmField val stiffness: Float,
    ) {
        private val omega = sqrt(stiffness.toDouble())

        /** Settling time at 1x speed, in ms. Prefer [duration], which honours the speed multiplier. */
        @JvmField
        val baseDurationMs: Long = (-ln(SETTLE_EPSILON) / (damping * omega) * 1000.0)
            .toLong()
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

        @JvmField
        val interpolator: Interpolator = Interpolator { input -> fraction(input.toDouble()).toFloat() }

        /** [baseDurationMs] scaled by [InuConfig.ANIMATION_SPEED], like the stock animator sites. */
        fun duration(): Long {
            val speed = InuConfig.ANIMATION_SPEED.value
            if (speed <= 0f) return 0L
            return (baseDurationMs / speed).toLong().coerceAtLeast(0L)
        }

        /**
         * Spring displacement mapped to 0..1 progress. The input fraction is rescaled onto
         * [baseDurationMs] of real time, so the curve keeps its physical shape no matter what
         * duration the caller ends up using.
         */
        private fun fraction(input: Double): Double {
            if (input <= 0.0) return 0.0
            if (input >= 1.0) return 1.0

            val t = input * baseDurationMs / 1000.0
            val z = damping.toDouble()
            val envelope = exp(-z * omega * t)
            val residual = if (z < 1.0) {
                val damped = omega * sqrt(1.0 - z * z)
                envelope * (cos(damped * t) + z * omega / damped * sin(damped * t))
            } else {
                // Critically damped closed form. Over-damped tokens (z > 1) settle slightly slower
                // than this approximation; none of the tokens below rely on that difference.
                envelope * (1.0 + omega * t)
            }
            return 1.0 - residual
        }
    }

    // Spatial: position, size, shape. Under-damped, overshoots.
    @JvmField val FAST_SPATIAL = SpringToken(damping = 0.6f, stiffness = 800f)
    @JvmField val DEFAULT_SPATIAL = SpringToken(damping = 0.8f, stiffness = 380f)
    @JvmField val SLOW_SPATIAL = SpringToken(damping = 0.8f, stiffness = 200f)

    // Effects: colour, alpha, elevation. Critically damped, no overshoot.
    @JvmField val FAST_EFFECTS = SpringToken(damping = 1f, stiffness = 3800f)
    @JvmField val DEFAULT_EFFECTS = SpringToken(damping = 1f, stiffness = 1600f)
    @JvmField val SLOW_EFFECTS = SpringToken(damping = 1f, stiffness = 800f)

    /**
     * The spatial token to use when [InuConfig.M3_EXPRESSIVE_MOTION] is on, and `null` when it is
     * off - so a call site can stay a one-liner that falls back to the stock interpolator:
     *
     * ```java
     * M3Motion.spatial(M3Motion.DEFAULT_SPATIAL, animator, CubicBezierInterpolator.DEFAULT);
     * ```
     */
    @JvmStatic
    fun apply(
        token: SpringToken,
        animator: android.animation.ValueAnimator,
        fallback: Interpolator?,
        fallbackDurationMs: Long,
    ) {
        if (InuConfig.M3_EXPRESSIVE_MOTION.value) {
            animator.interpolator = token.interpolator
            animator.duration = token.duration()
        } else {
            animator.interpolator = fallback
            animator.duration = fallbackDurationMs
        }
    }

    /** Interpolator-only variant, for sites that own their duration. */
    @JvmStatic
    fun interpolator(token: SpringToken, fallback: Interpolator?): Interpolator? =
        if (InuConfig.M3_EXPRESSIVE_MOTION.value) token.interpolator else fallback
}
