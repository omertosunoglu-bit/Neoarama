package com.neoarama.app.ui

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoarama.app.R
import com.neoarama.app.data.CallEntry
import com.neoarama.app.data.Contact
import com.neoarama.app.data.ContactsRepo
import com.neoarama.app.data.PhoneNumber
import com.neoarama.app.data.PhoneUtil

/** Kişi ya da rehberde olmayan bir numara için detay ekranı. */
data class DetailTarget(val contact: Contact?, val number: String)

@Composable
fun ContactDetail(
    target: DetailTarget,
    recents: List<CallEntry>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCall: (String) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val c = target.contact
    val name = c?.name ?: if (target.number.isBlank()) "Gizli numara" else PhoneUtil.format(target.number)
    val numbers = c?.numbers ?: listOf(PhoneNumber(target.number, "Numara"))
    val keys = remember(numbers) { numbers.map { PhoneUtil.normalize(it.number) }.toSet() }
    val history = remember(recents, keys) { recents.filter { PhoneUtil.normalize(it.number) in keys }.take(15) }
    var blockTarget by remember { mutableStateOf<String?>(null) }

    fun open(intent: Intent) {
        try { ctx.startActivity(intent) } catch (e: Exception) {
            Toast.makeText(ctx, "Uygun uygulama bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Geri") }
                Spacer(Modifier.weight(1f))
                if (c != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                            if (isFavorite) "Favoriden çıkar" else "Favorilere ekle",
                            tint = if (isFavorite) Gold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, c?.photo, 120.dp)
                Spacer(Modifier.height(16.dp))
                Text(name, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (c == null && target.number.isNotBlank()) {
                    Text("Rehberde kayıtlı değil", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (target.number.isNotBlank() || c != null) {
            items(numbers) { n ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(PhoneUtil.format(n.number), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text(n.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Ara", R.drawable.ic_call, Green, Modifier.weight(1f)) { onCall(n.number) }
                        Pill("Mesaj", R.drawable.ic_message, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + n.number)))
                        }
                        Pill("WhatsApp", R.drawable.ic_chat, Color(0xFF25D366), Modifier.weight(1f)) {
                            open(Intent(Intent.ACTION_VIEW, PhoneUtil.whatsapp(n.number)))
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 10.dp)) {
                    if (c != null) {
                        ActionRow(R.drawable.ic_edit, "Rehberde düzenle") {
                            open(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id)))
                        }
                    } else {
                        ActionRow(R.drawable.ic_person_add, "Rehbere ekle") {
                            open(
                                Intent(ContactsContract.Intents.Insert.ACTION)
                                    .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                                    .putExtra(ContactsContract.Intents.Insert.PHONE, target.number)
                            )
                        }
                    }
                    ActionRow(R.drawable.ic_copy, "Numarayı kopyala") {
                        clipboard.setText(AnnotatedString(numbers.first().number))
                        Toast.makeText(ctx, "Kopyalandı", Toast.LENGTH_SHORT).show()
                    }
                    ActionRow(R.drawable.ic_block, "Numarayı engelle", Red) { blockTarget = numbers.first().number }
                }
            }
        }

        if (history.isNotEmpty()) {
            item { SectionHeader("Son aramalar") }
            items(history) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(callTypeIcon(e.type)), null, tint = callTypeColor(e.type), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(callTypeLabel(e.type) + if (e.count > 1) " (${e.count})" else "", fontWeight = FontWeight.Medium)
                        Text(dayHeader(e.date) + " · " + timeOf(e.date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Text(durationText(e.duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }

    blockTarget?.let { num ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Numara engellensin mi?") },
            text = { Text("${PhoneUtil.format(num)} numarasından gelen aramalar ve mesajlar engellenecek.") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = ContactsRepo.block(ctx, num)
                    Toast.makeText(
                        ctx,
                        if (ok) "Numara engellendi" else "Engellemek için uygulamayı varsayılan telefon uygulaması yapın",
                        Toast.LENGTH_LONG
                    ).show()
                    blockTarget = null
                }) { Text("Engelle", color = Red) }
            },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun Pill(label: String, icon: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionRow(icon: Int, label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
