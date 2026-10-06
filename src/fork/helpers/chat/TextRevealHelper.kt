package desu.inugram.helpers.chat

import android.graphics.BlurMaskFilter
import android.os.SystemClock
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.text.style.UpdateAppearance
import android.view.View
import android.widget.TextView
import desu.inugram.InuConfig
import desu.inugram.helpers.theme.M3Motion
import org.telegram.messenger.AndroidUtilities
import java.util.WeakHashMap

/**
 * Fades freshly typed characters in, out of a blur.
 *
 * Text in an EditText is drawn by the framework from its spans, so the reveal is a span: a
 * [CharacterStyle] that lowers alpha and attaches a [BlurMaskFilter] whose radius falls to zero.
 * One frame loop per field drives every live span and invalidates the field once per frame, rather
 * than an animator per character - typing fast would otherwise mean dozens of animators at once.
 *
 * Spans are removed as soon as they finish, so an idle field holds none and costs nothing.
 *
 * The blur is why this needs care: [BlurMaskFilter] is ignored under hardware acceleration, so the
 * field has to fall back to a software layer while anything is animating, and back to none when it
 * settles. Setting the blur radius to 0 in settings skips that entirely and leaves a plain fade,
 * which is the cheap way out if the software layer costs too much on a given phone.
 */
object TextRevealHelper {
    private val controllers = WeakHashMap<TextView, Controller>()

    @JvmStatic
    fun isEnabled(): Boolean = InuConfig.TEXT_REVEAL.value

    private var pendingStart = -1
    private var pendingCount = 0

    /**
     * Records the inserted range. Call from `onTextChanged`, which is where the range is known but
     * where the Editable must not be touched yet.
     */
    @JvmStatic
    fun onTextChanged(start: Int, before: Int, count: Int) {
        if (!isEnabled()) return
        pendingStart = start
        pendingCount = count
    }

    /**
     * Spans the range recorded by [onTextChanged]. Call from `afterTextChanged`: setting a span is
     * an edit to the Editable, and the one callback where that is allowed is this one.
     */
    @JvmStatic
    fun afterTextChanged(view: TextView) {
        val start = pendingStart
        val count = pendingCount
        pendingStart = -1
        pendingCount = 0

        if (!isEnabled() || count <= 0 || start < 0) return
        val text = view.text as? Spannable ?: return
        if (start + count > text.length) return

        val controller = controllers.getOrPut(view) { Controller(view) }
        controller.add(text, start, start + count)
    }

    /** Drops every live span and stops the loop - for a field that is being reused or cleared. */
    @JvmStatic
    fun reset(view: TextView) {
        controllers.remove(view)?.finish()
    }

    private class RevealSpan : CharacterStyle(), UpdateAppearance {
        var progress = 0f

        override fun updateDrawState(tp: TextPaint) {
            if (progress >= 1f) return
            tp.alpha = (tp.alpha * progress).toInt().coerceIn(0, 255)

            val radius = AndroidUtilities.dpf2(InuConfig.TEXT_REVEAL_BLUR.value) * (1f - progress)
            // BlurMaskFilter rejects a radius of 0, and a sub-pixel one buys nothing.
            if (radius > 0.2f) {
                tp.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    private class Controller(private val view: TextView) : Runnable {
        private val live = ArrayList<Entry>()
        private var running = false
        private var softwareLayer = false

        private class Entry(val span: RevealSpan, val startedAt: Long)

        fun add(text: Spannable, start: Int, end: Int) {
            // A replacement span draws itself and ignores the paint we would be colouring, so a
            // custom emoji would pop in at full strength mid-fade. Leave those ranges alone.
            if (text.getSpans(start, end, ReplacementSpan::class.java).isNotEmpty()) return

            val span = RevealSpan()
            text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            live.add(Entry(span, SystemClock.elapsedRealtime()))
            start()
        }

        private fun start() {
            if (running) return
            running = true
            if (InuConfig.TEXT_REVEAL_BLUR.value > 0f && !softwareLayer) {
                softwareLayer = true
                view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            view.postOnAnimation(this)
        }

        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val duration = InuConfig.TEXT_REVEAL_DURATION.value.coerceAtLeast(1).toFloat()
            val curve = M3Motion.DEFAULT_EFFECTS.interpolator

            val iterator = live.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val linear = ((now - entry.startedAt) / duration).coerceIn(0f, 1f)
                entry.span.progress = curve.getInterpolation(linear)
                if (linear >= 1f) {
                    remove(entry.span)
                    iterator.remove()
                }
            }

            view.invalidate()

            if (live.isEmpty()) {
                finish()
            } else {
                view.postOnAnimation(this)
            }
        }

        private fun remove(span: RevealSpan) {
            (view.text as? Spannable)?.removeSpan(span)
        }

        fun finish() {
            for (entry in live) remove(entry.span)
            live.clear()
            running = false
            view.removeCallbacks(this)
            if (softwareLayer) {
                softwareLayer = false
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            view.invalidate()
        }
    }
}
