package com.neoarama.app.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoarama.app.MainActivity
import com.neoarama.app.R
import com.neoarama.app.data.ContactsRepo
import com.neoarama.app.data.Dialer
import com.neoarama.app.data.PhoneUtil
import com.neoarama.app.ui.AppTheme
import com.neoarama.app.ui.Avatar
import com.neoarama.app.ui.Green
import com.neoarama.app.ui.Red
import com.neoarama.app.ui.RoundButton
import com.neoarama.app.ui.clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gelen ve giden aramaların tam ekran arayüzü (kilit ekranının üstünde de açılır). */
class InCallActivity : ComponentActivity() {

    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "NeoArama:proximity")
        }

        handleIntent(intent)
        addOnNewIntentListener { handleIntent(it) }

        setContent {
            AppTheme(dark = true) {
                InCallScreen(
                    onFinish = { finish() },
                    onProximity = { setProximity(it) },
                    onAddCall = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .setAction(MainActivity.ACTION_ADD_CALL)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
        }
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallManager.answer()
            i.removeExtra(EXTRA_ANSWER)
        }
    }

    /** Telefon kulağa yaklaşınca ekranı kapatır (ahize modunda). */
    private fun setProximity(on: Boolean) {
        val wl = proximity ?: return
        if (on && !wl.isHeld) wl.acquire(3 * 60 * 60 * 1000L)
        else if (!on && wl.isHeld) wl.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ANSWER = "answer"

        fun start(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // Arka plan kısıtı varsa tam ekran bildirim devreye girer.
            }
        }
    }
}

private val QUICK_REPLIES = listOf(
    "Şu an konuşamıyorum, sonra ararım.",
    "Araç kullanıyorum, sonra ararım.",
    "Toplantıdayım, mesaj atar mısın?",
    "5 dakika içinde dönüyorum."
)

private val White = Color.White
private val Glass = Color.White.copy(alpha = 0.12f)

@Composable
fun InCallScreen(onFinish: () -> Unit, onProximity: (Boolean) -> Unit, onAddCall: () -> Unit) {
    val calls by CallManager.calls.collectAsState()
    val tick by CallManager.tick.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val ended by CallManager.ended.collectAsState()
    val call = remember(calls, tick) { CallManager.primary() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF132037), Color(0xFF070A10), Color(0xFF05070A))))
            .systemBarsPadding()
    ) {
        if (call == null) {
            LaunchedEffect(Unit) {
                onProximity(false)
                delay(1600)
                onFinish()
            }
            EndedView(ended)
        } else {
            ActiveCall(call, tick, calls, audio, onProximity, onAddCall)
        }
    }
}

@Composable
private fun EndedView(ended: CallManager.Ended?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Arama sona erdi", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (ended != null && ended.seconds > 0) {
            Spacer(Modifier.height(8.dp))
            Text(clock(ended.seconds), color = White.copy(alpha = 0.7f), fontSize = 20.sp)
        }
        ended?.cause?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = White.copy(alpha = 0.6f), fontSize = 16.sp)
        }
    }
}

@Composable
private fun ActiveCall(
    call: Call,
    tick: Int,
    calls: List<Call>,
    audio: CallAudioState?,
    onProximity: (Boolean) -> Unit,
    onAddCall: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(call, tick) { call.stateCompat() }
    val number = call.number()
    val info by produceState(Pair<String?, Uri?>(null, null), number) {
        value = withContext(Dispatchers.IO) { ContactsRepo.lookup(ctx, number) }
    }
    val name = info.first
        ?: call.details.callerDisplayName?.takeIf { it.isNotBlank() }
        ?: if (number.isBlank()) "Gizli numara" else PhoneUtil.format(number)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state == Call.STATE_ACTIVE) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    LaunchedEffect(state, route) {
        onProximity((state == Call.STATE_ACTIVE || state == Call.STATE_DIALING) && route == CallAudioState.ROUTE_EARPIECE)
    }

    var showKeypad by remember { mutableStateOf(false) }
    var showReplies by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }

    val status = when (state) {
        Call.STATE_RINGING -> "Gelen arama"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Aranıyor…"
        Call.STATE_ACTIVE -> {
            val c = call.details.connectTimeMillis
            if (c > 0) clock(((now - c) / 1000).coerceAtLeast(0)) else "Bağlandı"
        }
        Call.STATE_HOLDING -> "Beklemede"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "SIM kart seçin"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED ->
            call.details.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() } ?: "Arama sonlanıyor"
        else -> ""
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val other = calls.firstOrNull { it != call }
        if (other != null) {
            OtherCall(other, onSwap = {
                if (call.stateCompat() == Call.STATE_ACTIVE) call.hold()
                other.unhold()
            })
        }

        Spacer(Modifier.height(36.dp))
        Text(status, color = White.copy(alpha = 0.75f), fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        Avatar(name, info.second, 132.dp)
        Spacer(Modifier.height(20.dp))
        Text(name, color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.first != null && number.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(PhoneUtil.format(number), color = White.copy(alpha = 0.6f), fontSize = 18.sp)
        }
        if (dtmf.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(dtmf, color = White, fontSize = 26.sp, letterSpacing = 3.sp, maxLines = 1)
        }

        Spacer(Modifier.weight(1f))

        when {
            state == Call.STATE_SELECT_PHONE_ACCOUNT -> SimPicker(call)
            state == Call.STATE_RINGING && showReplies -> Replies(
                onPick = { call.reject(true, it) },
                onBack = { showReplies = false }
            )
            state == Call.STATE_RINGING -> Incoming(
                onAnswer = { call.answer(VideoProfile.STATE_AUDIO_ONLY) },
                onReject = { call.reject(false, null) },
                onReplies = { showReplies = true }
            )
            showKeypad -> InCallKeypad(
                onKey = { d ->
                    dtmf += d
                    call.playDtmfTone(d)
                    scope.launch {
                        delay(180)
                        call.stopDtmfTone()
                    }
                },
                onHide = { showKeypad = false },
                onEnd = { call.disconnect() }
            )
            else -> Controls(call, state, audio, onKeypad = { showKeypad = true }, onAddCall = onAddCall)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OtherCall(other: Call, onSwap: () -> Unit) {
    val n = other.number()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Glass).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Beklemede: " + (if (n.isBlank()) "Gizli numara" else PhoneUtil.format(n)),
            color = White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onSwap) { Text("Geç", color = Green, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Incoming(onAnswer: () -> Unit, onReject: () -> Unit, onReplies: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(R.drawable.ic_call_end, "Reddet", Red, White, 84.dp, onClick = onReject)
            RoundButton(R.drawable.ic_call, "Cevapla", Green, White, 84.dp, modifier = Modifier.scale(scale), onClick = onAnswer)
        }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onReplies) {
            Icon(painterResource(R.drawable.ic_message), null, tint = White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mesajla reddet", color = White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Replies(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mesaj gönder ve reddet", color = White.copy(alpha = 0.7f), fontSize = 15.sp)
        QUICK_REPLIES.forEach { r ->
            Text(
                r, color = White, fontSize = 17.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Glass)
                    .clickable { onPick(r) }.padding(horizontal = 18.dp, vertical = 16.dp)
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Geri", color = White)
        }
    }
}

@Composable
private fun SimPicker(call: Call) {
    val ctx = LocalContext.current
    val sims = remember { Dialer.sims(ctx) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hangi SIM ile aransın?", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        sims.forEach { sim ->
            Button(
                onClick = { call.phoneAccountSelected(sim.handle, false) },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Glass, contentColor = White)
            ) { Text(sim.label, fontSize = 18.sp) }
        }
        TextButton(onClick = { call.disconnect() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Vazgeç", color = Red, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Toggle(icon: Int, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    RoundButton(
        icon, label,
        background = if (active) White else Glass,
        tint = if (active) Color(0xFF0B1220) else White,
        size = 72.dp, enabled = enabled, onClick = onClick
    )
}

@Composable
private fun Controls(call: Call, state: Int, audio: CallAudioState?, onKeypad: () -> Unit, onAddCall: () -> Unit) {
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val mask = audio?.supportedRouteMask ?: 0
    val bluetooth = (mask and CallAudioState.ROUTE_BLUETOOTH) != 0
    val holding = state == Call.STATE_HOLDING
    val canHold = holding || call.details.can(Call.Details.CAPABILITY_HOLD)
    val live = state == Call.STATE_ACTIVE || holding

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(R.drawable.ic_mic_off, "Sessiz", muted) { CallManager.setMuted(!muted) }
            Toggle(R.drawable.ic_dialpad, "Tuş takımı", false, onClick = onKeypad)
            Toggle(R.drawable.ic_speaker, "Hoparlör", route == CallAudioState.ROUTE_SPEAKER) {
                CallManager.setRoute(
                    if (route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    else CallAudioState.ROUTE_SPEAKER
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(R.drawable.ic_pause, if (holding) "Devam" else "Beklet", holding, enabled = canHold && live) {
                if (holding) call.unhold() else call.hold()
            }
            Toggle(R.drawable.ic_add, "Arama ekle", false, enabled = live, onClick = onAddCall)
            Toggle(R.drawable.ic_bluetooth, "Bluetooth", route == CallAudioState.ROUTE_BLUETOOTH, enabled = bluetooth) {
                CallManager.setRoute(
                    if (route == CallAudioState.ROUTE_BLUETOOTH) CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    else CallAudioState.ROUTE_BLUETOOTH
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoundButton(R.drawable.ic_call_end, null, Red, White, 80.dp) { call.disconnect() }
        }
    }
}

@Composable
private fun InCallKeypad(onKey: (Char) -> Unit, onHide: () -> Unit, onEnd: () -> Unit) {
    val keys = listOf("123", "456", "789", "*0#")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { k ->
                    Box(
                        Modifier.size(70.dp).clip(CircleShape).background(Glass).clickable { onKey(k) },
                        contentAlignment = Alignment.Center
                    ) { Text(k.toString(), color = White, fontSize = 30.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(70.dp))
            RoundButton(R.drawable.ic_call_end, null, Red, White, 72.dp, onClick = onEnd)
            TextButton(onClick = onHide, modifier = Modifier.width(70.dp)) { Text("Gizle", color = White) }
        }
    }
}
