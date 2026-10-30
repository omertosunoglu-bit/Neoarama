package com.neoarama.app.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Uygulama varsayılan telefon uygulaması olduğunda Android bütün aramaları buraya bildirir.
 * Zil sesini ve titreşimi sistem çalar; biz ekranı ve bildirimi gösteririz.
 */
class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onCallAdded(call: Call) {
        CallManager.service = this
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
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }
}
