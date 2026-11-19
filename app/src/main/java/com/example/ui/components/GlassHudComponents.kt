package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.PendingSensitiveAction
import com.example.ui.theme.GlassCardBg

@Composable
fun JarvisHudBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val bg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .background(bg)
            .drawWithCache {
                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        secondary.copy(alpha = 0.14f),
                        bg,
                        primary.copy(alpha = 0.08f)
                    )
                )
                val gridPath = Path().apply {
                    val step = 44.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        moveTo(x, 0f)
                        lineTo(x, size.height)
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        moveTo(0f, y)
                        lineTo(size.width, y)
                        y += step
                    }
                }
                val gridColor = primary.copy(alpha = 0.035f)
                onDrawBehind {
                    drawRect(brush = gradient)
                    drawPath(path = gridPath, color = gridColor)
                }
            }
    ) {
        content()
    }
}

@Composable
fun GlassHudCard(
    modifier: Modifier = Modifier,
    accentBorder: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = GlassCardBg,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, accentBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun SensitiveActionConfirmationDialog(
    action: PendingSensitiveAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Authorization required",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = action.title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "JARVIS Security Protocol: Explicit authorization required before executing.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_sensitive_action_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Authorize")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_sensitive_action_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SectionHeaderRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun VoiceWaveformBar(
    rmsDb: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 18
) {
    val normalized = (rmsDb / 12f).coerceIn(0.08f, 1f)
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val centerDist = kotlin.math.abs(i - (barCount - 1) / 2f) / (barCount / 2f)
            val envelope = 1f - 0.55f * centerDist
            val waveOffset = 0.65f + 0.35f * kotlin.math.sin(i * 1.15f + rmsDb * 0.9f)
            val barFraction = (normalized * envelope * waveOffset).coerceIn(0.14f, 1f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(26.dp * barFraction)
                    .background(barColor, RoundedCornerShape(50))
            )
        }
    }
}
