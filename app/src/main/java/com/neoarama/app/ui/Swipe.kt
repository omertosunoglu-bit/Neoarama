package com.neoarama.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoarama.app.R
import com.neoarama.app.data.Contact
import com.neoarama.app.data.PhoneUtil
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

val WhatsAppGreen = Color(0xFF25D366)

/** WhatsApp için tercihen cep numarası (5xx ile başlayan), yoksa ilk numara. */
fun whatsappNumber(c: Contact): String? =
    c.numbers.firstOrNull { PhoneUtil.normalize(it.number).startsWith("5") }?.number ?: c.primary

fun openWhatsApp(ctx: Context, number: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, PhoneUtil.whatsapp(number)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "WhatsApp açılamadı", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Efektli kaydırma:
 *  - Sağa: yeşil "Ara", sola: WhatsApp yeşili "WhatsApp".
 *  - Kaydırdıkça ikon büyür, yazı belirir, satır hafifçe havalanır (gölge).
 *  - Eşik geçilince titreşim + renk ikondan dairesel olarak tüm satıra yayılır.
 *  - Bırakınca satır o yöne fırlar, işlem başlar, satır yaylanarak geri gelir.
 *  - Hızlı fiske (fling) de eşiği yarı yolda geçse bile işlemi başlatır.
 */
@Composable
fun SwipeRow(
    onSwipeRight: (() -> Unit)?,
    onSwipeLeft: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 88.dp.toPx() }
    val iconCenter = with(density) { 40.dp.toPx() }

    var width by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }

    val dragState = rememberDraggableState { delta ->
        if (flying) return@rememberDraggableState
        // Eşikten sonra lastik gibi direnç
        val resistance = if (abs(offsetX) > threshold) 0.5f else 1f
        val max = if (onSwipeRight != null) width else 0f
        val min = if (onSwipeLeft != null) -width else 0f
        offsetX = (offsetX + delta * resistance).coerceIn(min, max)
        val nowArmed = abs(offsetX) >= threshold
        if (nowArmed != armed) {
            armed = nowArmed
            haptic.performHapticFeedback(
                if (nowArmed) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
    }

    val progress = if (threshold > 0f) (abs(offsetX) / threshold).coerceIn(0f, 1f) else 0f
    val reveal by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "reveal"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (armed) 1.3f else 0.6f + 0.4f * progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale"
    )

    val toRight = offsetX > 0
    val color = if (toRight) Green else WhatsAppGreen

    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { width = it.width.toFloat() }
    ) {
        if (offsetX != 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(color.copy(alpha = 0.15f + 0.25f * progress))
                        if (reveal > 0f) {
                            val cx = if (toRight) iconCenter else size.width - iconCenter
                            drawCircle(
                                color = color,
                                radius = hypot(size.width, size.height) * reveal,
                                center = Offset(cx, size.height / 2f)
                            )
                        }
                    }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End
            ) {
                val tint = if (armed || reveal > 0.5f) Color.White else color
                if (toRight) {
                    Icon(
                        painterResource(R.drawable.ic_call), null, tint = tint,
                        modifier = Modifier.size(26.dp).scale(iconScale)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Ara", color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.alpha(progress)
                    )
                } else {
                    Text(
                        "WhatsApp", color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.alpha(progress)
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        painterResource(R.drawable.ic_chat), null, tint = tint,
                        modifier = Modifier.size(26.dp).scale(iconScale)
                    )
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .graphicsLayer {
                    shadowElevation = 12.dp.toPx() * progress
                    val s = 1f - 0.02f * progress
                    scaleX = s
                    scaleY = s
                }
                .background(MaterialTheme.colorScheme.background)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val x = offsetX
                        val fling = abs(velocity) > 1800f && abs(x) > threshold * 0.5f && velocity.sign == x.sign
                        val action = if (x > 0) onSwipeRight else onSwipeLeft
                        if ((abs(x) >= threshold || fling) && action != null) {
                            flying = true
                            armed = true
                            // Satır o yöne fırlar
                            animate(x, x.sign * width, animationSpec = tween(170, easing = FastOutLinearInEasing)) { v, _ ->
                                offsetX = v
                            }
                            action()
                            delay(120)
                            armed = false
                            // Yaylanarak geri gelir
                            animate(
                                offsetX, 0f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                            ) { v, _ -> offsetX = v }
                            flying = false
                        } else {
                            armed = false
                            animate(
                                x, 0f, initialVelocity = velocity,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                            ) { v, _ -> offsetX = v }
                        }
                    }
                )
        ) {
            content()
        }
    }
}
