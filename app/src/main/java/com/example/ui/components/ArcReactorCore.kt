package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.JarvisAiStatus
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorCore(
    status: JarvisAiStatus,
    rmsDb: Float,
    onCoreTap: () -> Unit,
    modifier: Modifier = Modifier,
    coreSize: Dp = 188.dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val statusColor by animateColorAsState(
        targetValue = when (status) {
            JarvisAiStatus.SYSTEM_ONLINE -> primary
            JarvisAiStatus.LISTENING -> StatusEmerald
            JarvisAiStatus.THINKING, JarvisAiStatus.PROCESSING -> StatusAmber
            JarvisAiStatus.SPEAKING -> primary
            JarvisAiStatus.TASK_COMPLETED -> StatusEmerald
        },
        animationSpec = tween(300),
        label = "coreStatusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "arcReactor")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    JarvisAiStatus.THINKING, JarvisAiStatus.PROCESSING -> 2600
                    JarvisAiStatus.LISTENING, JarvisAiStatus.SPEAKING -> 4200
                    else -> 9500
                },
                easing = LinearEasing
            )
        ),
        label = "outerRotation"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, easing = LinearEasing)
        ),
        label = "innerRotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val normalizedRms by animateFloatAsState(
        targetValue = when (status) {
            JarvisAiStatus.LISTENING -> (rmsDb / 10f).coerceIn(0.15f, 1.0f)
            JarvisAiStatus.SPEAKING -> (pulse - 0.85f) * 3.5f
            JarvisAiStatus.THINKING, JarvisAiStatus.PROCESSING -> 0.55f
            else -> 0.1f
        },
        animationSpec = tween(110),
        label = "rmsNormalized"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(CircleShape)
                .clickable(onClick = onCoreTap)
                .testTag("jarvis_arc_reactor")
                .semantics { contentDescription = "JARVIS AI Core, status ${status.label}" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f

                // Ambient radial plasma glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.36f * pulse),
                            secondary.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR
                    ),
                    radius = maxR,
                    center = Offset(cx, cy)
                )

                // Outer segmented HUD ring
                val outerRadius = maxR * 0.86f
                val segmentCount = 6
                for (i in 0 until segmentCount) {
                    val start = outerAngle + i * (360f / segmentCount)
                    drawArc(
                        color = statusColor.copy(alpha = 0.85f),
                        startAngle = start,
                        sweepAngle = 42f,
                        useCenter = false,
                        topLeft = Offset(cx - outerRadius, cy - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Precision degree ticks
                val tickOuter = maxR * 0.77f
                val tickInner = maxR * 0.72f
                for (i in 0 until 24) {
                    val angleRad = ((i * 15f) + innerAngle * 0.25f) * (PI / 180.0)
                    val sx = cx + (tickInner * cos(angleRad)).toFloat()
                    val sy = cy + (tickInner * sin(angleRad)).toFloat()
                    val ex = cx + (tickOuter * cos(angleRad)).toFloat()
                    val ey = cy + (tickOuter * sin(angleRad)).toFloat()
                    drawLine(
                        color = secondary.copy(alpha = if (i % 3 == 0) 0.75f else 0.35f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Voice waveform ring (reactive radial bars)
                val waveBase = maxR * 0.56f
                val barCount = 32
                for (i in 0 until barCount) {
                    val theta = (i * (360f / barCount) + outerAngle * 0.5f) * (PI / 180.0)
                    val harmonic = 0.45f + 0.55f * sin(i * 0.9f + outerAngle * 0.08f)
                    val barLen = maxR * (0.04f + 0.14f * normalizedRms * harmonic)
                    val sx = cx + (waveBase * cos(theta)).toFloat()
                    val sy = cy + (waveBase * sin(theta)).toFloat()
                    val ex = cx + ((waveBase + barLen) * cos(theta)).toFloat()
                    val ey = cy + ((waveBase + barLen) * sin(theta)).toFloat()
                    drawLine(
                        color = statusColor.copy(alpha = 0.8f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Inner counter-rotating reactor arcs
                val innerRadius = maxR * 0.44f
                for (i in 0 until 3) {
                    drawArc(
                        color = primary.copy(alpha = 0.9f),
                        startAngle = innerAngle + i * 120f,
                        sweepAngle = 82f,
                        useCenter = false,
                        topLeft = Offset(cx - innerRadius, cy - innerRadius),
                        size = Size(innerRadius * 2f, innerRadius * 2f),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Glowing reactor nucleus
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            statusColor.copy(alpha = 0.85f),
                            statusColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR * 0.34f * pulse
                    ),
                    radius = maxR * 0.34f * pulse,
                    center = Offset(cx, cy)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Futuristic Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(statusColor.copy(alpha = 0.14f))
                .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp)
                .testTag("ai_status_indicator"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
        }
    }
}
