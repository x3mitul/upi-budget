package dev.mitul.upibudget.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.mitul.upibudget.budget.DayBar
import dev.mitul.upibudget.core.Paise

/** One slim bar per day of the month. Moss = at or under the daily pace, brick = over it, the dashed line is the pace. */
@Composable
fun DayStrip(bars: List<DayBar>, pace: Paise, modifier: Modifier = Modifier) {
    val p = pal
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800)) }
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        if (bars.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        val foot = 10.dp.toPx()                       // room under the baseline for the "today" dot
        val base = size.height - foot
        val minH = 3.dp.toPx()
        val cap = maxOf(pace * 2, bars.maxOf { it.spent }, 1L).toFloat()
        bars.forEachIndexed { i, b ->
            val x = i * (w + gap)
            val h = if (b.isFuture) minH else maxOf(minH, b.spent / cap * base * grow.value)
            val color = when {
                b.isFuture -> p.mist
                b.spent == 0L -> p.moss.copy(alpha = 0.35f)
                pace > 0 && b.spent > pace -> p.brick
                else -> p.moss
            }
            drawRoundRect(color, Offset(x, base - h), Size(w, h), CornerRadius(w / 2))
            if (b.isToday) drawCircle(p.ink, 2.5.dp.toPx(), Offset(x + w / 2, base + 6.dp.toPx()))
        }
        if (pace > 0) {
            val y = base - pace / cap * base
            drawLine(p.inkSoft.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        }
    }
}
