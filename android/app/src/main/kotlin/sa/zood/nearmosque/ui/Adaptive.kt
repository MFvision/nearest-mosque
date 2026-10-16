package sa.zood.nearmosque.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Large screens (tablets, an unfolded Galaxy Z Fold): text columns keep a readable width in the middle, and a
 * wide window held sideways shows two panes side by side where a screen has a natural split.
 */
object Adaptive {
    /** The widest a column of cards, settings rows or answer text gets. */
    val contentWidth = 720.dp

    /** Side padding that centres a [contentWidth] column in a window [width] wide (at least [min]). */
    fun sidePadding(width: Dp, min: Dp = 16.dp, content: Dp = contentWidth): Dp = max(min, (width - content) / 2)

    /** Two panes: a window at least 600 dp wide and wider than it is tall (a tablet or unfolded Fold sideways). */
    fun twoPane(width: Dp, height: Dp): Boolean = width >= 600.dp && width > height * 1.05f
}
