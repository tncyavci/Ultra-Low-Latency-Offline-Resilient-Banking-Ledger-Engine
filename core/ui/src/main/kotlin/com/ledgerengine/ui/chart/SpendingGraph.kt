package com.ledgerengine.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class SpendingPoint(val label: String, val amountMinorUnits: Long)

/**
 * Canvas-drawn spending line chart. Two deliberate choices keep this smooth at
 * 120Hz on capable displays:
 *
 * 1. [points] is an [ImmutableList] of an `@Immutable` data class, so the
 *    Compose compiler treats it as stable and skips recomposing this
 *    composable whenever an unrelated part of the screen state changes.
 * 2. The entrance animation reads [Animatable.value] *inside the `Canvas`
 *    draw-phase lambda*, not in the composable body. Compose's `Canvas` draw
 *    lambda runs during the draw phase, so subscribing to the animated
 *    fraction there re-executes only drawing on each animation frame —
 *    composition and layout never re-run at all for the 600ms of the reveal.
 */
@Composable
fun SpendingGraph(
    points: ImmutableList<SpendingPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.15f),
) {
    val maxAmount by remember(points) {
        derivedStateOf { points.maxOfOrNull { it.amountMinorUnits }?.coerceAtLeast(1L) ?: 1L }
    }

    val revealProgress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        revealProgress.snapTo(0f)
        revealProgress.animateTo(1f, animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        if (points.size < 2) return@Canvas

        val stepX = size.width / (points.size - 1)
        val linePath = Path()
        val fillPath = Path()
        val progress = revealProgress.value // draw-phase read: see kdoc above.

        points.forEachIndexed { index, point ->
            val x = stepX * index
            val normalizedY = point.amountMinorUnits.toFloat() / maxAmount.toFloat()
            val y = size.height - (normalizedY * size.height * progress)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(stepX * (points.size - 1), size.height)
        fillPath.close()

        drawPath(fillPath, color = fillColor)
        drawPath(
            linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
