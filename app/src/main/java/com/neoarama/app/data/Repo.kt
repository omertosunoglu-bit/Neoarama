package com.neoarama.app.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.Collator
import java.util.Calendar
import java.util.Locale
import android.provider.ContactsContract.CommonDataKinds.Phone as CdkPhone

val TR: Locale = Locale.forLanguageTag("tr-TR")

data class PhoneNumber(val number: String, val label: String)

data class Contact(
    val id: Long,
    val name: String,
    val photo: Uri?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>
) {
    val primary: String? get() = numbers.firstOrNull()?.number
}

data class CallEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val photo: Uri?,
    val count: Int = 1
)

fun Context.has(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

object PhoneUtil {
    /** Karşılaştırma için: yalnızca rakamlar, son 10 hane (0532… ile +90532… aynı sayılır). */
    fun normalize(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun format(n: String): String =
        try { PhoneNumberUtils.formatNumber(n, "TR") ?: n } catch (e: Exception) { n }

    fun whatsapp(n: String): Uri {
        var d = n.filter { it.isDigit() }
        d = when {
            d.startsWith("90") && d.length == 12 -> d
            d.startsWith("0") && d.length == 11 -> "9$d"
            d.length == 10 && d.startsWith("5") -> "90$d"
            else -> d
        }
        return Uri.parse("https://wa.me/$d")
    }
}

object ContactsRepo {

    fun loadContacts(ctx: Context): List<Contact> {
        if (!ctx.has(Manifest.permission.READ_CONTACTS)) return emptyList()
        class Builder(val id: Long, val name: String, val photo: Uri?, val starred: Boolean) {
            val numbers = ArrayList<PhoneNumber>()
            val seen = HashSet<String>()
        }
        val map = LinkedHashMap<Long, Builder>()
        val projection = arrayOf(
            CdkPhone.CONTACT_ID,
            CdkPhone.DISPLAY_NAME_PRIMARY,
            CdkPhone.NUMBER,
            CdkPhone.TYPE,
            CdkPhone.LABEL,
            CdkPhone.PHOTO_THUMBNAIL_URI,
            CdkPhone.STARRED
        )
        try {
            ctx.contentResolver.query(CdkPhone.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)
                    val number = c.getString(2)
                    if (name != null && !number.isNullOrBlank()) {
                        val b = map.getOrPut(id) {
                            Builder(id, name, c.getString(5)?.let { Uri.parse(it) }, c.getInt(6) == 1)
                        }
                        val key = PhoneUtil.normalize(number)
                        if (b.seen.add(key)) {
                            val label = CdkPhone.getTypeLabel(ctx.resources, c.getInt(3), c.getString(4)).toString()
                            b.numbers += PhoneNumber(number, label)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        val collator = Collator.getInstance(TR)
        return map.values
            .map { Contact(it.id, it.name, it.photo, it.starred, it.numbers) }
            .sortedWith(compareBy<Contact, String>(collator) { it.name })
    }

    /** Numaradan rehberdeki adı ve fotoğrafı bulur. */
    fun lookup(ctx: Context, number: String): Pair<String?, Uri?> {
        if (number.isBlank() || !ctx.has(Manifest.permission.READ_CONTACTS)) return Pair(null, null)
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) Pair(c.getString(0), c.getString(1)?.let { Uri.parse(it) }) else Pair(null, null)
            } ?: Pair(null, null)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    fun loadCallLog(ctx: Context): List<CallEntry> {
        if (!ctx.has(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<CallEntry>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_PHOTO_URI
        )
        try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                var read = 0
                while (read < 500 && c.moveToNext()) {
                    read++
                    val number = c.getString(0) ?: ""
                    val entry = CallEntry(
                        number = number,
                        name = c.getString(1)?.takeIf { it.isNotBlank() },
                        type = c.getInt(2),
                        date = c.getLong(3),
                        duration = c.getLong(4),
                        photo = c.getString(5)?.let { Uri.parse(it) }
                    )
                    val last = out.lastOrNull()
                    // Aynı gün, aynı numara, aynı tür arka arkaya ise tek satırda topla: "Ahmet (3)"
                    if (last != null && last.type == entry.type &&
                        PhoneUtil.normalize(last.number) == PhoneUtil.normalize(number) &&
                        sameDay(last.date, entry.date)
                    ) {
                        out[out.size - 1] = last.copy(count = last.count + 1)
                    } else {
                        out += entry
                    }
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    /** Numarayı sistem genelinde engeller (yalnızca varsayılan telefon uygulaması yapabilir). */
    fun block(ctx: Context, number: String): Boolean = try {
        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        }
        ctx.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
    } catch (e: Exception) {
        false
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}

/** Uygulamaya özel favori listesi (sıra = hızlı arama tuşu 1–9). */
class Favorites(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("neoarama", Context.MODE_PRIVATE)

    fun ids(): List<Long> =
        prefs.getString("fav", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun toggle(id: Long): List<Long> {
        val list = ids().toMutableList()
        if (!list.remove(id)) list.add(id)
        prefs.edit().putString("fav", list.joinToString(",")).apply()
        return list
    }

    var lastSeenMissed: Long
        get() = prefs.getLong("seen_missed", 0L)
        set(v) { prefs.edit().putLong("seen_missed", v).apply() }
}

/** Tuş takımından isimle arama (Türkçe harfler dahil). */
object T9 {
    private val keys: Map<Char, Char> = buildMap {
        "abcç".forEach { put(it, '2') }
        "def".forEach { put(it, '3') }
        "gğhıi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }
        "mnoö".forEach { put(it, '6') }
        "pqrsş".forEach { put(it, '7') }
        "tuüv".forEach { put(it, '8') }
        "wxyz".forEach { put(it, '9') }
    }

    private fun digitsOf(s: String): String =
        s.lowercase(TR).map { ch -> keys[ch] ?: if (ch.isDigit()) ch else ' ' }.joinToString("")

    fun matches(c: Contact, query: String): Boolean {
        if (query.isEmpty()) return false
        val d = digitsOf(c.name)
        if (d.split(' ').any { it.isNotEmpty() && it.startsWith(query) }) return true
        if (d.replace(" ", "").startsWith(query)) return true
        return c.numbers.any { it.number.filter { ch -> ch.isDigit() }.contains(query) }
    }
}
