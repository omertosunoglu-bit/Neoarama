package com.neoarama.app.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Telefonun o anki aramalarını tutar; arama ekranı ve bildirim buradan beslenir. */
object CallManager {

    data class Ended(val number: String, val seconds: Long, val cause: String?)

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    /** Bir aramanın durumu her değiştiğinde artar; arayüzün yenilenmesini sağlar. */
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    private val _ended = MutableStateFlow<Ended?>(null)
    val ended: StateFlow<Ended?> = _ended.asStateFlow()

    var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
    }

    private fun changed() {
        _tick.value = _tick.value + 1
        service?.let { CallNotifications.show(it) }
    }

    fun add(call: Call) {
        call.registerCallback(callback)
        _calls.value = _calls.value + call
        changed()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        val connect = call.details.connectTimeMillis
        _ended.value = Ended(
            number = call.number(),
            seconds = if (connect > 0) (System.currentTimeMillis() - connect) / 1000 else 0,
            cause = call.details.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() }
        )
        _calls.value = _calls.value - call
        changed()
    }

    fun updateAudio(state: CallAudioState?) {
        _audio.value = state
    }

    /** Ekranda gösterilecek arama: önce çalan, sonra konuşulan/aranan, sonra diğerleri. */
    fun primary(): Call? {
        val list = _calls.value
        return list.firstOrNull { it.stateCompat() == Call.STATE_RINGING }
            ?: list.firstOrNull {
                it.stateCompat() in setOf(
                    Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING,
                    Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT
                )
            }
            ?: list.firstOrNull()
    }

    private fun ringing(): Call? = _calls.value.firstOrNull { it.stateCompat() == Call.STATE_RINGING }

    fun answer() {
        ringing()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        ringing()?.reject(false, null)
    }

    fun hangup() {
        primary()?.disconnect()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) {
        service?.setAudioRoute(route)
    }
}

fun Call.number(): String = details.handle?.schemeSpecificPart.orEmpty()

@Suppress("DEPRECATION")
fun Call.stateCompat(): Int = if (Build.VERSION.SDK_INT >= 31) details.state else state
