package com.userhub.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

private const val SWEEP_DURATION_MILLIS = 1_150

@Composable
private fun sweepBrush(): Brush {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val travel by rememberInfiniteTransition().animateFloat(
        initialValue = -700f,
        targetValue = 1_400f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = SWEEP_DURATION_MILLIS, easing = LinearEasing)
        )
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(travel, 0f),
        end = Offset(travel + 340f, 0f)
    )
}

@Composable
private fun Bar(widthFraction: Float, height: Int, brush: Brush) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(brush)
    )
}

@Composable
fun LoadingSkeleton() {
    val brush = sweepBrush()
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(8) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Bar(widthFraction = if (index % 2 == 0) 0.48f else 0.38f, height = 15, brush = brush)
                    Bar(widthFraction = if (index % 3 == 0) 0.74f else 0.62f, height = 11, brush = brush)
                }
                Box(
                    Modifier
                        .width(52.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(brush)
                )
            }
        }
    }
}
