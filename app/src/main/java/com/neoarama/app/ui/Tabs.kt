package com.neoarama.app.ui

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoarama.app.R
import com.neoarama.app.data.CallEntry
import com.neoarama.app.data.Contact
import com.neoarama.app.data.PhoneUtil
import com.neoarama.app.data.T9
import com.neoarama.app.data.TR

/* =====================  FAVORİLER  ===================== */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesTab(
    favorites: List<Contact>,
    frequent: List<Contact>,
    onCall: (String) -> Unit,
    onOpen: (Contact) -> Unit
) {
    if (favorites.isEmpty() && frequent.isEmpty()) {
        EmptyState(
            "Henüz favori yok",
            "Kişiler sekmesinden bir kişiye girip yıldıza dokunun. İlk 9 favori, tuş takımında 1–9'a basılı tutularak hızlıca aranır."
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (favorites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GridTitle("Favoriler") }
            itemsIndexed(favorites, key = { _, c -> "f${c.id}" }) { i, c ->
                PersonCard(c, if (i < 9) i + 1 else null, onCall, onOpen)
            }
        }
        if (frequent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GridTitle("Sık arananlar") }
            itemsIndexed(frequent, key = { _, c -> "r${c.id}" }) { _, c ->
                PersonCard(c, null, onCall, onOpen)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Dokun: ara  ·  Basılı tut: kişi detayı",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun GridTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonCard(c: Contact, speedDial: Int?, onCall: (String) -> Unit, onOpen: (Contact) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = { c.primary?.let(onCall) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpen(c)
                }
            )
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Avatar(c.name, c.photo, 72.dp)
            if (speedDial != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) { Text("$speedDial", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/* =====================  SON ARAMALAR  ===================== */

@Composable
fun RecentsTab(
    entries: List<CallEntry>,
    byNumber: Map<String, Contact>,
    onCall: (String) -> Unit,
    onOpen: (CallEntry) -> Unit
) {
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val list = remember(entries, missedOnly) { if (missedOnly) entries.filter { isMissed(it.type) } else entries }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !missedOnly, onClick = { missedOnly = false }, label = { Text("Tümü") })
            FilterChip(selected = missedOnly, onClick = { missedOnly = true }, label = { Text("Cevapsız") })
        }
        if (list.isNotEmpty()) SwipeHint()
        if (list.isEmpty()) {
            EmptyState(if (missedOnly) "Cevapsız arama yok" else "Arama geçmişi boş", "Yaptığınız ve aldığınız aramalar burada görünür.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            var lastHeader: String? = null
            list.forEachIndexed { i, e ->
                val header = dayHeader(e.date)
                if (header != lastHeader) {
                    lastHeader = header
                    item(key = "h$i") { SectionHeader(header) }
                }
                item(key = "e$i") { RecentRow(e, byNumber[PhoneUtil.normalize(e.number)], onCall, onOpen) }
            }
        }
    }
}

@Composable
private fun RecentRow(e: CallEntry, contact: Contact?, onCall: (String) -> Unit, onOpen: (CallEntry) -> Unit) {
    val name = contact?.name ?: e.name ?: if (e.number.isBlank()) "Gizli numara" else PhoneUtil.format(e.number)
    val missed = isMissed(e.type)
    val ctx = LocalContext.current
    val hasNumber = e.number.isNotBlank()
    SwipeRow(
        onSwipeRight = if (hasNumber) ({ onCall(e.number) }) else null,
        onSwipeLeft = if (hasNumber) ({ openWhatsApp(ctx, contact?.let { whatsappNumber(it) } ?: e.number) }) else null
    ) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, contact?.photo ?: e.photo, 50.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name + if (e.count > 1) "  (${e.count})" else "",
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = if (missed) Red else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(callTypeIcon(e.type)), null, tint = callTypeColor(e.type), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                val dur = durationText(e.duration)
                Text(
                    callTypeLabel(e.type) + " · " + timeOf(e.date) + if (dur.isNotEmpty()) " · $dur" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1
                )
            }
        }
        if (e.number.isNotBlank()) {
            IconButton(onClick = { onCall(e.number) }) {
                Icon(painterResource(R.drawable.ic_call), "Ara", tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
    }
}

/* =====================  KİŞİLER  ===================== */

@Composable
fun ContactsTab(contacts: List<Contact>, onOpen: (Contact) -> Unit, onCall: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, contacts) {
        if (query.isBlank()) contacts
        else {
            val q = query.trim().lowercase(TR)
            val digits = query.filter { it.isDigit() }
            contacts.filter { c ->
                c.name.lowercase(TR).contains(q) ||
                    (digits.length >= 3 && c.numbers.any { n -> n.number.filter { it.isDigit() }.contains(digits) })
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("${contacts.size} kişide ara") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), "Temizle") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        if (filtered.isNotEmpty()) SwipeHint()
        if (filtered.isEmpty()) {
            EmptyState(if (contacts.isEmpty()) "Rehber boş" else "Sonuç yok", if (contacts.isEmpty()) "Rehber izni verildiğinde kişileriniz burada görünür." else "Farklı bir ad ya da numara deneyin.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            var last: String? = null
            var headerIndex = 0
            filtered.forEach { c ->
                val letter = c.name.firstOrNull()?.uppercase(TR)?.takeIf { it.first().isLetter() } ?: "#"
                if (letter != last) {
                    last = letter
                    headerIndex++
                    item(key = "L$headerIndex") { SectionHeader(letter) }
                }
                item(key = "c${c.id}") { ContactRow(c, onOpen, onCall) }
            }
        }
    }
}

@Composable
private fun ContactRow(c: Contact, onOpen: (Contact) -> Unit, onCall: (String) -> Unit) {
    val ctx = LocalContext.current
    SwipeRow(
        onSwipeRight = c.primary?.let { n -> { onCall(n) } },
        onSwipeLeft = whatsappNumber(c)?.let { n -> { openWhatsApp(ctx, n) } }
    ) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(c) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.name, c.photo, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            c.numbers.firstOrNull()?.let {
                Text(
                    it.label + " · " + PhoneUtil.format(it.number) + if (c.numbers.size > 1) "  +${c.numbers.size - 1}" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1
                )
            }
        }
        c.primary?.let { n ->
            IconButton(onClick = { onCall(n) }) {
                Icon(painterResource(R.drawable.ic_call), "Ara", tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
    }
}

@Composable
private fun SwipeHint() {
    Text(
        "Sağa kaydır: ara   ·   Sola kaydır: WhatsApp",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
    )
}

/* =====================  TUŞ TAKIMI  ===================== */

private val KEYS = listOf(
    "1" to " ", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to " ", "0" to "+", "#" to " "
)

private fun dtmfTone(d: String): Int = when (d) {
    "1" -> ToneGenerator.TONE_DTMF_1
    "2" -> ToneGenerator.TONE_DTMF_2
    "3" -> ToneGenerator.TONE_DTMF_3
    "4" -> ToneGenerator.TONE_DTMF_4
    "5" -> ToneGenerator.TONE_DTMF_5
    "6" -> ToneGenerator.TONE_DTMF_6
    "7" -> ToneGenerator.TONE_DTMF_7
    "8" -> ToneGenerator.TONE_DTMF_8
    "9" -> ToneGenerator.TONE_DTMF_9
    "*" -> ToneGenerator.TONE_DTMF_S
    "#" -> ToneGenerator.TONE_DTMF_P
    else -> ToneGenerator.TONE_DTMF_0
}

@Composable
private fun rememberKeyTone(): (String) -> Unit {
    val tone = remember { try { ToneGenerator(AudioManager.STREAM_DTMF, 70) } catch (e: Exception) { null } }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    return remember(tone) {
        val play: (String) -> Unit = { d -> tone?.startTone(dtmfTone(d), 120) }
        play
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadTab(
    number: String,
    onNumberChange: (String) -> Unit,
    contacts: List<Contact>,
    favorites: List<Contact>,
    onCall: (String) -> Unit
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playTone = rememberKeyTone()
    val digits = number.filter { it.isDigit() }
    val matches = remember(digits, contacts) {
        if (digits.length < 2) emptyList() else contacts.filter { T9.matches(it, digits) }.take(4)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            matches.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { c.primary?.let(onCall) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(c.name, c.photo, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PhoneUtil.format(c.primary.orEmpty()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Icon(painterResource(R.drawable.ic_call), null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (matches.isEmpty() && digits.length >= 3) {
                TextButton(
                    onClick = {
                        val i = Intent(ContactsContract.Intents.Insert.ACTION)
                            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                        try { ctx.startActivity(i) } catch (e: Exception) { }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(painterResource(R.drawable.ic_person_add), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rehbere ekle")
                }
            }
        }

        Text(
            if (number.isEmpty()) " " else PhoneUtil.format(number),
            fontSize = if (number.length > 13) 30.sp else 38.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )

        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (d, letters) ->
                    Column(
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = {
                                    playTone(d)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNumberChange(number + d)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when (d) {
                                        "0" -> onNumberChange("$number+")
                                        "*", "#" -> onNumberChange(number + d)
                                        else -> {
                                            val fav = favorites.getOrNull(d.toInt() - 1)
                                            val n = fav?.primary
                                            if (n != null) onCall(n)
                                            else Toast.makeText(ctx, "$d. sırada favori yok", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(d, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        Text(letters, fontSize = 11.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(78.dp))
            RoundButton(R.drawable.ic_call, null, Green, Color.White, 78.dp) {
                if (number.isNotBlank()) onCall(number)
            }
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = number.isNotEmpty(),
                        onClick = { onNumberChange(number.dropLast(1)) },
                        onLongClick = { onNumberChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_backspace), "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
