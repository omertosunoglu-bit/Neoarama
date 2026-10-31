package com.neoarama.app.call

import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Uygulama varsayılan telefon uygulaması olduğunda Android bütün aramaları buraya bildirir.
 * Zil sesini ve titreşimi sistem çalar; biz ekranı ve bildirimi gösteririz.
 *
 * Akıllı saat: Galaxy Watch, Wear OS saatler ve bileklikler gelen aramayı telefonun arama
 * altyapısından kendileri gösterir. Saatten cevaplama/reddetme/kapatma Android üzerinden
 * bu servise ulaşır ve telefondaki ekran anında güncellenir.
 */
class CallService : InCallService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onCallAdded(call: Call) {
        CallManager.service = this

        // Telefon kullanılırken gelen aramada yalnızca tam ekran açılsın, üstten küçük bildirim çıkmasın.
        if (call.stateCompat() == Call.STATE_RINGING && screenInUse()) {
            CallManager.expectingUi = true
            handler.postDelayed({
                // Tam ekran bir sebeple açılamadıysa normal (üstten) bildirime dön
                if (CallManager.expectingUi && !CallManager.uiVisible) {
                    CallManager.expectingUi = false
                    CallNotifications.show(this, repost = true)
                }
            }, 2500)
        }

        CallManager.add(call)
        @Suppress("DEPRECATION")
        CallManager.updateAudio(callAudioState)
        CallNotifications.show(this)
        InCallActivity.start(this)
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        if (CallManager.calls.value.isEmpty()) CallNotifications.cancel(this)
        else CallNotifications.show(this)
    }

    @Deprecated("Android 14 öncesi cihazlar için")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.updateAudio(audioState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    /** Ekran açık ve kilitsiz mi (kullanıcı telefonu kullanıyor mu)? */
    private fun screenInUse(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        return pm?.isInteractive == true && km?.isKeyguardLocked == false
    }
}
