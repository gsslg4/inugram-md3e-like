package desu.inugram.helpers.theme

import android.graphics.drawable.Drawable
import android.view.ViewOutlineProvider
import desu.inugram.InuConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.utils.ViewOutlineProviderImpl
import org.telegram.ui.ActionBar.Theme

object M3FabHelper {
    // MD3 standard FAB is 16dp on 56dp; scaled proportionally to TG's 48dp FABs.
    private const val RADIUS_DP = 14f

    // Expressive morphs a held FAB toward a tighter corner; MD3 pairs the 16dp rest shape with a
    // 28dp pressed one on a 56dp FAB, scaled here the same way RADIUS_DP is.
    private const val PRESSED_RADIUS_DP = 24f

    @JvmStatic
    fun outlineProvider(): ViewOutlineProvider =
        if (InuConfig.MATERIAL3_FABS.value) {
            // The morphing background supplies its own outline, so the shadow follows the morph.
            // Without it, fall back to the static round rect.
            if (InuConfig.M3_EXPRESSIVE_MOTION.value) {
                ViewOutlineProvider.BACKGROUND
            } else {
                ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, AndroidUtilities.dpf2(RADIUS_DP))
            }
        } else {
            ViewOutlineProviderImpl.BOUNDS_OVAL
        }

    @JvmStatic
    fun makeSelectorBackground(sizeDp: Int, accent: Int, pressed: Int): Drawable = when {
        !InuConfig.MATERIAL3_FABS.value ->
            Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(sizeDp.toFloat()), accent, pressed)

        InuConfig.M3_EXPRESSIVE_MOTION.value ->
            M3Shape.pressMorph(accent, pressed, RADIUS_DP, PRESSED_RADIUS_DP)

        else ->
            Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(RADIUS_DP), accent, pressed)
    }
}
