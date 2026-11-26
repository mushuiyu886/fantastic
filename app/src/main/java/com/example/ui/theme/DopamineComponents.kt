package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tactile Neo-Pop card with crisp 2dp dark ink border and hard offset drop shadow.
 */
@Composable
fun PopCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = DeepInk,
    shadowColor: Color = DeepInk,
    cornerRadius: Dp = 20.dp,
    shadowOffset: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .drawBehind {
                val px = shadowOffset.toPx()
                val radiusPx = cornerRadius.toPx()
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(px, px),
                    size = size,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            }
            .clip(shape)
            .background(containerColor)
            .border(width = 2.dp, color = borderColor, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * High-contrast tactile Dopamine CTA button with guaranteed >= 48dp touch height.
 */
@Composable
fun PopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ElectricPink,
    contentColor: Color = PureWhite,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (enabled) containerColor else SoftBorder
    val fg = if (enabled) contentColor else MutedInk

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .drawBehind {
                val px = if (enabled) 3.dp.toPx() else 0f
                val r = 16.dp.toPx()
                if (enabled) {
                    drawRoundRect(
                        color = DeepInk,
                        topLeft = Offset(px, px),
                        size = size,
                        cornerRadius = CornerRadius(r, r)
                    )
                }
            }
            .clip(shape)
            .background(bg)
            .border(2.dp, DeepInk, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PopTag(
    text: String,
    containerColor: Color,
    contentColor: Color = DeepInk,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, DeepInk)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LatencySignalPill(
    label: String,
    rttMs: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = PureWhite,
        border = BorderStroke(1.5.dp, DeepInk)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(1.dp, DeepInk, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = DeepInk
            )
            if (rttMs > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${rttMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = CobaltBlue
                )
            }
        }
    }
}

@Composable
fun AvatarBadge(
    emoji: String,
    colorIndex: Int,
    size: Dp = 44.dp,
    isHost: Boolean = false
) {
    val bg = PlayerPalette[colorIndex.coerceAtLeast(0) % PlayerPalette.size]
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .border(2.dp, DeepInk, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = (size.value * 0.48f).sp)
        }
        if (isHost) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(SunburstYellow)
                    .border(1.5.dp, DeepInk, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "房主",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = DeepInk
                )
            }
        }
    }
}
