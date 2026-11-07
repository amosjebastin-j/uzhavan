package com.cropadvisor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Uzhavan mark: an emerald badge holding a leaf with a gold midrib, framed by four gold
 * "viewfinder" corners — a crop leaf being scanned by on-device AI.
 * The launcher icon (res/drawable/ic_leaf_fg.xml) draws the same shapes.
 */
@Composable
fun UzhavanLogo(size: Dp, modifier: Modifier = Modifier, animated: Boolean = false, badge: Boolean = true) {
    val intro = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(animated) {
        if (animated) intro.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val loop = rememberInfiniteTransition(label = "logo")
    val scan by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "scan")
    val breathe by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing),
        RepeatMode.Reverse), label = "breathe")

    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val k = intro.value

        if (badge) {
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF13563F), Color(0xFF0B3D2E), Color(0xFF06231A)),
                start = Offset.Zero, end = Offset(s, s)), cornerRadius = CornerRadius(s * 0.30f))
            drawRoundRect(Palette.Gold.copy(alpha = 0.45f), cornerRadius = CornerRadius(s * 0.30f),
                style = Stroke(s * 0.018f))
        }

        // viewfinder corners (slide in, then "breathe" while animated)
        val inset = 0.16f + (1f - k) * 0.10f - (if (animated) breathe * 0.015f else 0f)
        val arm = 0.12f
        val bw = s * 0.035f
        val gold = Palette.Gold.copy(alpha = k.coerceIn(0f, 1f))
        listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (dx, dy) ->
            val cx = if (dx > 0) inset else 1f - inset
            val cy = if (dy > 0) inset else 1f - inset
            drawLine(gold, p(cx, cy), p(cx + dx * arm, cy), bw, StrokeCap.Round)
            drawLine(gold, p(cx, cy), p(cx, cy + dy * arm), bw, StrokeCap.Round)
        }

        // leaf
        val leaf = Path().apply {
            moveTo(0.30f * s, 0.72f * s)
            cubicTo(0.26f * s, 0.48f * s, 0.48f * s, 0.26f * s, 0.74f * s, 0.28f * s)
            cubicTo(0.76f * s, 0.52f * s, 0.54f * s, 0.74f * s, 0.30f * s, 0.72f * s)
            close()
        }
        scale(0.6f + 0.4f * k, pivot = p(0.5f, 0.5f)) {
            drawPath(leaf, Brush.linearGradient(listOf(Color(0xFFA8E6C1), Color(0xFF5CC08E), Color(0xFF2E9466)),
                start = p(0.30f, 0.30f), end = p(0.70f, 0.72f)), alpha = k.coerceIn(0f, 1f))
            if (animated) clipPath(leaf) {          // scanning light sweeping over the leaf
                val y = (0.20f + 0.62f * scan) * s
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.GoldLight.copy(alpha = 0.55f), Color.Transparent),
                    startY = y - s * 0.08f, endY = y + s * 0.02f), Offset(0f, y - s * 0.08f), Size(s, s * 0.10f))
            }
            val vein = Palette.GoldLight.copy(alpha = 0.95f * k.coerceIn(0f, 1f))
            drawLine(vein, p(0.33f, 0.69f), p(0.70f, 0.32f), s * 0.026f, StrokeCap.Round)
            drawLine(vein, p(0.4595f, 0.5605f), p(0.4455f, 0.4615f), s * 0.018f, StrokeCap.Round)
            drawLine(vein, p(0.4595f, 0.5605f), p(0.5585f, 0.5745f), s * 0.018f, StrokeCap.Round)
            drawLine(vein, p(0.552f, 0.468f), p(0.541f, 0.389f), s * 0.016f, StrokeCap.Round)
            drawLine(vein, p(0.552f, 0.468f), p(0.631f, 0.479f), s * 0.016f, StrokeCap.Round)
            val stem = Path().apply {
                moveTo(0.30f * s, 0.72f * s); quadraticBezierTo(0.27f * s, 0.78f * s, 0.23f * s, 0.80f * s)
            }
            drawPath(stem, vein, style = Stroke(s * 0.03f, cap = StrokeCap.Round))
        }
    }
}

/** Fades + slides a block up when it first appears; `index` staggers a list of blocks. */
@Composable
fun Modifier.enterUp(index: Int = 0, distance: Dp = 28.dp): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        a.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * distance.toPx()
    }
}

/** Horizontal shake each time `trigger` changes (used for wrong password). */
@Composable
fun Modifier.shakeOn(trigger: Int): Modifier {
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        for (v in listOf(18f, -14f, 10f, -6f, 3f, 0f)) x.animateTo(v, tween(45))
    }
    return this.graphicsLayer { translationX = x.value * density }
}

/** Premium text field: soft card fill, gold focus ring, optional show/hide for passwords. */
@Composable
fun AuthField(
    value: String, onChange: (String) -> Unit, label: String, icon: ImageVector,
    modifier: Modifier = Modifier, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    hint: String? = null, prefix: String? = null, isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, isError = isError,
        leadingIcon = { Icon(icon, null) },
        prefix = if (prefix != null) { { Text(prefix) } } else null,
        trailingIcon = if (password) {
            {
                IconButton({ visible = !visible }) {
                    Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                }
            }
        } else null,
        supportingText = if (hint != null) { { Text(hint) } } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        textStyle = MaterialTheme.typography.titleMedium,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Gold, unfocusedBorderColor = Palette.Line,
            focusedLabelColor = Palette.GoldText, unfocusedLabelColor = Palette.InkSoft,
            focusedLeadingIconColor = Palette.Forest, unfocusedLeadingIconColor = Palette.InkSoft,
            focusedTrailingIconColor = Palette.Forest, unfocusedTrailingIconColor = Palette.InkSoft,
            focusedTextColor = Palette.Ink, unfocusedTextColor = Palette.Ink, cursorColor = Palette.Forest,
            focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
            errorBorderColor = Palette.Terracotta, errorContainerColor = Palette.Card,
            focusedPrefixColor = Palette.Ink, unfocusedPrefixColor = Palette.Ink,
            focusedSupportingTextColor = Palette.InkSoft, unfocusedSupportingTextColor = Palette.InkSoft,
        ),
    )
}
