package com.neoarama.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CallLog
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoarama.app.R
import com.neoarama.app.data.TR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

val Green = Color(0xFF22C55E)
val Red = Color(0xFFEF4444)
val Blue = Color(0xFF4F8CFF)
val Gold = Color(0xFFFFC53D)

private val DarkScheme = darkColorScheme(
    primary = Blue, onPrimary = Color.White,
    background = Color(0xFF07090C), onBackground = Color(0xFFEEF1F6),
    surface = Color(0xFF07090C), onSurface = Color(0xFFEEF1F6),
    surfaceVariant = Color(0xFF151920), onSurfaceVariant = Color(0xFF8A92A2),
    secondaryContainer = Color(0xFF1E2A40), onSecondaryContainer = Color(0xFFDCE6FF),
    surfaceContainer = Color(0xFF10141A),
    outline = Color(0xFF2A303B), outlineVariant = Color(0xFF20252E),
    error = Red, tertiary = Green
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    background = Color(0xFFF4F5F8), onBackground = Color(0xFF12161D),
    surface = Color(0xFFF4F5F8), onSurface = Color(0xFF12161D),
    surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF5F6878),
    secondaryContainer = Color(0xFFDCE6FF), onSecondaryContainer = Color(0xFF0F2A66),
    surfaceContainer = Color.White,
    outline = Color(0xFFD5DAE2), outlineVariant = Color(0xFFE6E9EE),
    error = Red, tertiary = Color(0xFF16A34A)
)

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

/* ---------- Kişi fotoğrafı / baş harfler ---------- */

private object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(120)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

private val AvatarColors = listOf(
    Color(0xFFFF7A59), Color(0xFF4F8CFF), Color(0xFFC77DFF), Color(0xFF2FD18B),
    Color(0xFFFFB02E), Color(0xFF00B4D8), Color(0xFFFF4D6D), Color(0xFF7B61FF)
)

fun initials(name: String): String {
    val parts = name.split(' ').filter { it.firstOrNull()?.isLetter() == true }.take(2)
    return if (parts.isEmpty()) "#" else parts.joinToString("") { it.first().uppercase(TR) }
}

fun avatarColor(name: String): Color = AvatarColors[Math.floorMod(name.hashCode(), AvatarColors.size)]

@Composable
fun Avatar(name: String, photo: Uri?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bitmap by produceState(photo?.let { PhotoCache.get(it.toString()) }, photo) {
        if (photo != null && value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it) }
                        ?.asImageBitmap()
                        ?.also { PhotoCache.put(photo.toString(), it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    Box(
        modifier.size(size).clip(CircleShape).background(avatarColor(name)),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

/* ---------- Yuvarlak düğme ---------- */

@Composable
fun RoundButton(
    icon: Int,
    label: String?,
    background: Color,
    tint: Color,
    size: Dp = 72.dp,
    labelColor: Color = Color.White.copy(alpha = 0.85f),
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(if (enabled) background else background.copy(alpha = background.alpha * 0.35f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon), contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(size * 0.42f)
            )
        }
        if (label != null) {
            Spacer(Modifier.height(8.dp))
            Text(label, color = if (enabled) labelColor else labelColor.copy(alpha = 0.4f), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/* ---------- Arama türü ---------- */

fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "Gelen"
    CallLog.Calls.OUTGOING_TYPE -> "Giden"
    CallLog.Calls.MISSED_TYPE -> "Cevapsız"
    CallLog.Calls.REJECTED_TYPE -> "Reddedildi"
    CallLog.Calls.BLOCKED_TYPE -> "Engellendi"
    CallLog.Calls.VOICEMAIL_TYPE -> "Sesli mesaj"
    else -> "Arama"
}

fun callTypeIcon(type: Int): Int = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_made
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> R.drawable.ic_call_missed
    else -> R.drawable.ic_call_received
}

fun isMissed(type: Int): Boolean = type == CallLog.Calls.MISSED_TYPE

@Composable
fun callTypeColor(type: Int): Color = when (type) {
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Red
    CallLog.Calls.OUTGOING_TYPE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.tertiary
}

/* ---------- Zaman ---------- */

fun timeOf(ms: Long): String = SimpleDateFormat("HH:mm", TR).format(Date(ms))

fun dayHeader(ms: Long): String = when {
    DateUtils.isToday(ms) -> "Bugün"
    DateUtils.isToday(ms + DateUtils.DAY_IN_MILLIS) -> "Dün"
    else -> SimpleDateFormat("d MMMM EEEE", TR).format(Date(ms))
}

fun durationText(seconds: Long): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "$seconds sn"
    seconds < 3600 -> "${seconds / 60} dk ${seconds % 60} sn"
    else -> "${seconds / 3600} sa ${(seconds % 3600) / 60} dk"
}

fun clock(seconds: Long): String =
    if (seconds >= 3600) String.format(TR, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else String.format(TR, "%02d:%02d", seconds / 60, seconds % 60)
