package com.neoarama.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoarama.app.call.CallManager
import com.neoarama.app.call.InCallActivity
import com.neoarama.app.data.CallEntry
import com.neoarama.app.data.Contact
import com.neoarama.app.data.ContactsRepo
import com.neoarama.app.data.Dialer
import com.neoarama.app.data.Favorites
import com.neoarama.app.data.PhoneUtil
import com.neoarama.app.data.has
import com.neoarama.app.ui.AppTheme
import com.neoarama.app.ui.ContactDetail
import com.neoarama.app.ui.ContactsTab
import com.neoarama.app.ui.DetailTarget
import com.neoarama.app.ui.DialpadTab
import com.neoarama.app.ui.FavoritesTab
import com.neoarama.app.ui.RecentsTab
import com.neoarama.app.ui.isMissed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)
    private var dialRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        addOnNewIntentListener { handleIntent(it) }
        setContent {
            AppTheme {
                AramaApp(resumeTick, dialRequest, onDialConsumed = { dialRequest = null })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    /** Başka uygulamalardan gelen "şu numarayı çevir" isteklerini tuş takımına aktarır. */
    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> dialRequest = i.data?.schemeSpecificPart?.let { Uri.decode(it) } ?: ""
            ACTION_ADD_CALL -> dialRequest = ""
        }
    }

    companion object {
        const val ACTION_ADD_CALL = "com.neoarama.app.ADD_CALL"
    }
}

private val PERMISSIONS: Array<String> = buildList {
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private data class Tab(val title: String, val icon: Int)

private val TABS = listOf(
    Tab("Favoriler", R.drawable.ic_star),
    Tab("Son aramalar", R.drawable.ic_history),
    Tab("Kişiler", R.drawable.ic_person),
    Tab("Tuş takımı", R.drawable.ic_dialpad)
)

@Composable
fun AramaApp(resumeTick: Int, dialRequest: String?, onDialConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val favStore = remember { Favorites(ctx) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var recents by remember { mutableStateOf(emptyList<CallEntry>()) }
    var favIds by remember { mutableStateOf(favStore.ids()) }
    var permTick by remember { mutableIntStateOf(0) }
    var isDefault by remember { mutableStateOf(Dialer.isDefault(ctx)) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var dialNumber by rememberSaveable { mutableStateOf("") }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    var lastSeen by remember { mutableLongStateOf(favStore.lastSeenMissed) }
    val calls by CallManager.calls.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = Dialer.isDefault(ctx)
        permTick++
    }
    val missing = remember(permTick, resumeTick) { PERMISSIONS.filter { !ctx.has(it) } }

    LaunchedEffect(Unit) {
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
    LaunchedEffect(permTick, resumeTick) {
        isDefault = Dialer.isDefault(ctx)
        val loaded = withContext(Dispatchers.IO) { ContactsRepo.loadContacts(ctx) to ContactsRepo.loadCallLog(ctx) }
        contacts = loaded.first
        recents = loaded.second
    }
    LaunchedEffect(dialRequest) {
        if (dialRequest != null) {
            dialNumber = dialRequest
            tab = 3
            detail = null
            onDialConsumed()
        }
    }
    LaunchedEffect(tab, recents) {
        if (tab == 1 && recents.isNotEmpty()) {
            val newest = recents.first().date
            if (newest > lastSeen) {
                lastSeen = newest
                favStore.lastSeenMissed = newest
            }
        }
    }

    val byNumber = remember(contacts) {
        buildMap<String, Contact> { contacts.forEach { c -> c.numbers.forEach { n -> put(PhoneUtil.normalize(n.number), c) } } }
    }
    val favorites = remember(favIds, contacts) {
        val m = contacts.associateBy { it.id }
        if (favIds.isEmpty()) contacts.filter { it.starred } else favIds.mapNotNull { m[it] }
    }
    val frequent = remember(recents, byNumber, favorites) {
        val favSet = favorites.map { it.id }.toSet()
        recents.mapNotNull { e -> byNumber[PhoneUtil.normalize(e.number)]?.let { it to e.count } }
            .filter { it.first.id !in favSet }
            .groupBy({ it.first.id }, { it })
            .values
            .sortedByDescending { list -> list.sumOf { it.second } }
            .take(6)
            .map { it.first().first }
    }
    val missedCount = remember(recents, lastSeen) {
        recents.filter { isMissed(it.type) && it.date > lastSeen }.sumOf { it.count }
    }

    val placeCall: (String) -> Unit = { n ->
        if (n.isNotBlank()) {
            if (Dialer.needsSimChoice(ctx)) pendingCall = n else Dialer.call(ctx, n)
        }
    }

    BackHandler(enabled = detail != null) { detail = null }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                if (calls.isNotEmpty()) {
                    Text(
                        "Devam eden arama · dönmek için dokunun",
                        color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiary)
                            .clickable { InCallActivity.start(ctx) }
                            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else {
                    Spacer(Modifier.statusBarsPadding())
                }
                if (detail == null) {
                    Text(
                        TABS[tab].title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                    )
                    if (!isDefault) {
                        Banner(
                            "Arama ekranını kullanmak ve numara engellemek için bu uygulamayı varsayılan telefon uygulaması yapın.",
                            "Varsayılan yap"
                        ) { roleLauncher.launch(Dialer.roleRequestIntent(ctx)) }
                    } else if (missing.isNotEmpty()) {
                        Banner("Rehber ve arama geçmişi için izin gerekli.", "İzin ver") {
                            permLauncher.launch(missing.toTypedArray())
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (detail == null) {
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                if (i == 1 && missedCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$missedCount") } }) {
                                        Icon(painterResource(t.icon), t.title)
                                    }
                                } else {
                                    Icon(painterResource(t.icon), t.title)
                                }
                            },
                            label = { Text(t.title, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = detail
            if (d != null) {
                ContactDetail(
                    target = d,
                    recents = recents,
                    isFavorite = d.contact != null && favorites.any { it.id == d.contact.id },
                    onToggleFavorite = {
                        val c = d.contact
                        if (c != null) {
                            // Sistem yıldızlıları kullanılıyorsa önce onları uygulama listesine aktar
                            if (favIds.isEmpty() && favorites.isNotEmpty()) {
                                favorites.forEach { favStore.toggle(it.id) }
                            }
                            favIds = favStore.toggle(c.id)
                        }
                    },
                    onCall = placeCall,
                    onBack = { detail = null }
                )
            } else {
                when (tab) {
                    0 -> FavoritesTab(favorites, frequent, placeCall) { detail = DetailTarget(it, it.primary.orEmpty()) }
                    1 -> RecentsTab(recents, byNumber, placeCall) { e ->
                        detail = DetailTarget(byNumber[PhoneUtil.normalize(e.number)], e.number)
                    }
                    2 -> ContactsTab(contacts, onOpen = { detail = DetailTarget(it, it.primary.orEmpty()) }, onCall = placeCall)
                    else -> DialpadTab(dialNumber, { dialNumber = it }, contacts, favorites, placeCall)
                }
            }
        }
    }

    pendingCall?.let { number ->
        val sims = remember(number) { Dialer.sims(ctx) }
        AlertDialog(
            onDismissRequest = { pendingCall = null },
            title = { Text("Hangi SIM ile aransın?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(PhoneUtil.format(number), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sims.forEach { sim ->
                        Button(
                            onClick = {
                                pendingCall = null
                                Dialer.call(ctx, number, sim.handle)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(sim.label, fontSize = 17.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingCall = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.Bold) }
    }
}
