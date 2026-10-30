package com.neoarama.app.call

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.neoarama.app.R
import com.neoarama.app.data.ContactsRepo
import com.neoarama.app.data.PhoneUtil

object CallNotifications {
    private const val CH_INCOMING = "incoming_calls"
    private const val CH_ONGOING = "ongoing_call"
    private const val ID = 42

    const val ACTION_DECLINE = "com.neoarama.app.DECLINE"
    const val ACTION_HANGUP = "com.neoarama.app.HANGUP"

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, "Gelen aramalar", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // zil sesini sistem çalıyor
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Devam eden arama", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        val call = CallManager.primary()
        if (call == null) {
            cancel(ctx)
            return
        }
        ensureChannels(ctx)
        val number = call.number()
        val name = ContactsRepo.lookup(ctx, number).first
            ?: if (number.isBlank()) "Gizli numara" else PhoneUtil.format(number)
        val state = call.stateCompat()
        val ringing = state == Call.STATE_RINGING

        val openIntent = Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = PendingIntent.getActivity(
            ctx, 1, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val b = NotificationCompat.Builder(ctx, if (ringing) CH_INCOMING else CH_ONGOING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(name)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (ringing) {
            val answerPi = PendingIntent.getActivity(
                ctx, 2,
                Intent(ctx, InCallActivity::class.java)
                    .putExtra(InCallActivity.EXTRA_ANSWER, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.setContentText("Gelen arama")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(openPi, true)
                .addAction(R.drawable.ic_call_end, "Reddet", broadcast(ctx, ACTION_DECLINE, 3))
                .addAction(R.drawable.ic_call, "Cevapla", answerPi)
        } else {
            val active = state == Call.STATE_ACTIVE
            val connect = call.details.connectTimeMillis
            b.setContentText(
                when (state) {
                    Call.STATE_ACTIVE -> "Devam eden arama"
                    Call.STATE_HOLDING -> "Beklemede"
                    else -> "Aranıyor…"
                }
            )
                .setOnlyAlertOnce(true)
                .setUsesChronometer(active && connect > 0)
                .setWhen(if (connect > 0) connect else System.currentTimeMillis())
                .addAction(R.drawable.ic_call_end, "Kapat", broadcast(ctx, ACTION_HANGUP, 4))
        }

        try {
            val nmc = NotificationManagerCompat.from(ctx)
            if (nmc.areNotificationsEnabled()) nmc.notify(ID, b.build())
        } catch (e: SecurityException) {
            // Bildirim izni yoksa arama ekranı yine açılır.
        }
    }

    fun cancel(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID)
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifications.ACTION_DECLINE -> CallManager.reject()
            CallNotifications.ACTION_HANGUP -> CallManager.hangup()
        }
    }
}
