package com.neoarama.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast

object Dialer {

    private fun telecom(ctx: Context): TelecomManager = ctx.getSystemService(TelecomManager::class.java)

    fun isDefault(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            telecom(ctx).defaultDialerPackage == ctx.packageName
        }

    fun roleRequestIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
        }

    data class Sim(val handle: PhoneAccountHandle, val label: String)

    @SuppressLint("MissingPermission")
    fun sims(ctx: Context): List<Sim> = try {
        val tm = telecom(ctx)
        tm.callCapablePhoneAccounts.mapIndexed { i, h ->
            val label = tm.getPhoneAccount(h)?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}"
            Sim(h, label)
        }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Çift SIM varsa ve varsayılan SIM seçilmemişse kullanıcıya sorulmalı. */
    @SuppressLint("MissingPermission")
    fun needsSimChoice(ctx: Context): Boolean = try {
        sims(ctx).size > 1 && telecom(ctx).getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) == null
    } catch (e: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun call(ctx: Context, number: String, sim: PhoneAccountHandle? = null) {
        if (number.isBlank()) return
        if (!ctx.has(Manifest.permission.CALL_PHONE)) {
            Toast.makeText(ctx, "Arama izni gerekli", Toast.LENGTH_LONG).show()
            return
        }
        val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
        val extras = Bundle()
        if (sim != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim)
        try {
            telecom(ctx).placeCall(uri, extras)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Arama başlatılamadı", Toast.LENGTH_LONG).show()
        }
    }
}
