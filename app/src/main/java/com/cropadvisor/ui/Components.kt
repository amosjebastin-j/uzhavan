package com.cropadvisor.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    accent: Color = Palette.Forest,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().premiumCard(24.dp).padding(18.dp)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            }
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

/** Big tappable action button. */
@Composable
fun BigButton(
    text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    bg: Color = Palette.Forest, fg: Color = Palette.OnForest, enabled: Boolean = true,
    brush: Brush? = null,
    shimmer: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    // a soft light sweeping across premium buttons every few seconds
    val sweep = if (shimmer) rememberInfiniteTransition(label = "shine").animateFloat(
        -1f, 2.5f, infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)), label = "x").value else -2f
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(18.dp))
            .then(if (brush != null && enabled) Modifier.background(brush) else Modifier.background(if (enabled) bg else bg.copy(alpha = 0.4f)))
            .drawWithContent {
                drawContent()
                if (shimmer) {
                    val x = size.width * sweep
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(x - size.width * 0.35f, 0f), end = Offset(x, size.height)))
                }
            }
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RowScope.ActionChip(text: String, icon: ImageVector, onClick: () -> Unit, active: Boolean = false) {
    Column(
        Modifier.weight(1f).premiumCard(20.dp, if (active) Palette.Forest else Palette.Card)
            .clickable(onClick = onClick).padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (active) Palette.OnForest else Palette.Forest, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) Palette.OnForest else Palette.Ink,
            maxLines = 1)
    }
}

/** Animated ring showing model confidence. */
@Composable
fun ConfidenceRing(value: Float, color: Color, size: Dp = 92.dp, label: String) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value, tween(1100, easing = FastOutSlowInEasing)) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Palette.Line, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * anim.value, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(anim.value * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Palette.Ink)
            Text(label, fontSize = 10.sp, color = Palette.InkSoft, maxLines = 1)
        }
    }
}

/** Three-segment severity meter with an animated marker. */
@Composable
fun SeverityGauge(pct: Float, labels: List<String>) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(pct) { anim.animateTo((pct / 40f).coerceIn(0f, 1f), tween(1200, easing = FastOutSlowInEasing)) }
    val colors = listOf(Palette.Mint, Palette.Amber, Palette.Terracotta)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val h = 10.dp.toPx(); val y = (size.height - h) / 2; val gap = 4.dp.toPx()
            // segment widths proportional to 0–10, 10–25, 25–40 %
            val bounds = listOf(0f, 0.25f, 0.625f, 1f)
            for (i in 0..2) {
                val x0 = size.width * bounds[i] + if (i > 0) gap / 2 else 0f
                val x1 = size.width * bounds[i + 1] - if (i < 2) gap / 2 else 0f
                drawRoundRect(colors[i], Offset(x0, y), Size(x1 - x0, h),
                    androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
            }
            val mx = size.width * anim.value
            drawCircle(Color.White, 11.dp.toPx(), Offset(mx.coerceIn(11.dp.toPx(), size.width - 11.dp.toPx()), size.height / 2))
            drawCircle(Palette.Ink, 6.dp.toPx(), Offset(mx.coerceIn(11.dp.toPx(), size.width - 11.dp.toPx()), size.height / 2))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(labels[0], Modifier.weight(0.25f), fontSize = 11.sp, color = Palette.InkSoft)
            Text(labels[1], Modifier.weight(0.375f), fontSize = 11.sp, color = Palette.InkSoft)
            Text(labels[2], Modifier.weight(0.375f), fontSize = 11.sp, color = Palette.InkSoft)
        }
    }
}

@Composable
fun BitmapImage(bmp: Bitmap?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (bmp == null) Box(modifier.background(Palette.MintSoft))
    else Image(bmp.asImageBitmap(), null, modifier, contentScale = contentScale)
}

@Composable
fun NumberedStep(n: Int, text: String, color: Color = Palette.Forest) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text("$n", color = Palette.OnForest, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun LabeledText(label: String, text: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Leaf)
        Spacer(Modifier.height(3.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
    }
}


/** Soft shadow + hairline border: the base surface for every card in the app. */
@Composable
fun Modifier.premiumCard(radius: Dp = 24.dp, bg: Color = Palette.Card): Modifier {
    val shape = RoundedCornerShape(radius)
    val shadowColor = if (Palette.dark) Color.Black else Color(0xFF0B3D2E)
    return this.shadow(if (Palette.dark) 0.dp else 10.dp, shape, ambientColor = shadowColor.copy(alpha = 0.06f),
        spotColor = shadowColor.copy(alpha = 0.10f))
        .clip(shape).background(bg).border(1.dp, Palette.Line.copy(alpha = if (Palette.dark) 1f else 0.7f), shape)
}

/** Small uppercase gold label used above sections. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Palette.GoldText)
}
