package com.cropadvisor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cropadvisor.AppState
import com.cropadvisor.Scan
import com.cropadvisor.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun decodeUri(ctx: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longSide = maxOf(info.size.width, info.size.height)
        if (longSide > 1280) d.setTargetSampleSize(longSide / 1280)
    }

/** Decodes a picked image; shows a message instead of crashing on unsupported files. */
fun safeDecode(ctx: Context, uri: Uri): Bitmap? = try { decodeUri(ctx, uri) } catch (e: Throwable) {
    android.widget.Toast.makeText(ctx, "This image could not be opened. Please choose another photo.",
        android.widget.Toast.LENGTH_LONG).show()
    null
}

fun cropEmoji(key: String) = when (key) {
    "Tomato" -> "🍅"; "Potato" -> "🥔"; "Pepper,_bell" -> "🫑"; "Corn_(maize)" -> "🌽"; else -> "🌾"
}

// =====================================================================================
//  Shell with bottom navigation
// =====================================================================================
@Composable
fun MainShell(
    state: AppState, tab: Int, onTab: (Int) -> Unit, onScan: () -> Unit,
    onPicked: (Bitmap) -> Unit, onOpenScan: (Scan) -> Unit, onAbout: () -> Unit,
) {
    val tabs = listOf(
        Triple("home", Icons.Rounded.Home, 0), Triple("history", Icons.Rounded.History, 1),
        Triple("device", Icons.Rounded.Speed, 2), Triple("profile", Icons.Rounded.Person, 3),
    )
    Scaffold(
        containerColor = Palette.Cream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Palette.Card, tonalElevation = 0.dp) {
                tabs.forEach { (key, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { onTab(i) },
                        icon = { Icon(icon, null) }, label = { Text(state.t(key), maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.Forest, selectedTextColor = Palette.Forest,
                            indicatorColor = Palette.GoldSoft, unselectedIconColor = Palette.InkSoft,
                            unselectedTextColor = Palette.InkSoft),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            AnimatedContent(tab, label = "tab", transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(320)) { h -> h / 24 }) togetherWith fadeOut(tween(160))
            }) { t ->
                when (t) {
                    0 -> HomeScreen(state, onScan, onPicked, onOpenScan, onSeeAll = { onTab(1) }, onProfile = { onTab(3) })
                    1 -> HistoryScreen(state, onOpenScan)
                    2 -> DeviceScreen(state)
                    else -> ProfileScreen(state, onAbout)
                }
            }
        }
    }
}

// =====================================================================================
//  Home
// =====================================================================================
@Composable
fun HomeScreen(state: AppState, onScan: () -> Unit, onPicked: (Bitmap) -> Unit,
               onOpenScan: (Scan) -> Unit, onSeeAll: () -> Unit, onProfile: () -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { safeDecode(ctx, it)?.let(onPicked) }
    }
    var langOpen by remember { mutableStateOf(false) }
    if (langOpen) LanguageSheet(state) { langOpen = false }
    val user = state.user

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---------- hero ----------
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Palette.hero).statusBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 56.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UzhavanLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${state.t("greet")}, ${user?.firstName ?: ""} 👋", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(SimpleDateFormat("EEEE, d MMMM", localeFor(state.lang)).format(Date()),
                        color = Palette.MintSoftFixed.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                        .clickable { langOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Translate, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Strings.LANGS.first { it.first == state.lang }.second, color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Avatar(user?.initials ?: "?", 38.dp, Modifier.clip(CircleShape).clickable(onClick = onProfile))
            }
            Spacer(Modifier.height(30.dp))
            Text(state.t("premium_tag").uppercase(), color = Palette.GoldLight, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.enterUp(0))
            Spacer(Modifier.height(6.dp))
            Text(state.t("scan_title"), color = Color.White, style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.enterUp(1))
            Spacer(Modifier.height(8.dp))
            Text(state.t("scan_sub"), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.enterUp(2))
            Spacer(Modifier.height(24.dp))
            BigButton(state.t("scan_btn"), Icons.Rounded.CameraAlt, onScan, Modifier.enterUp(3).fillMaxWidth(),
                fg = Palette.OnGold, brush = Palette.goldBrush, shimmer = true)
            Spacer(Modifier.height(10.dp))
            BigButton(state.t("gallery"), Icons.Rounded.PhotoLibrary, { picker.launch("image/*") },
                Modifier.enterUp(4).fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                bg = Color.White.copy(alpha = 0.08f), fg = Color.White)
            Spacer(Modifier.height(16.dp))
            Pill(state.t("offline"), Color.White.copy(alpha = 0.10f), Palette.GoldLight, Icons.Rounded.WifiOff,
                Modifier.enterUp(5))
        }

        Column(Modifier.padding(horizontal = 20.dp).offset(y = (-36).dp)) {
            // ---------- field health summary (floats over the hero) ----------
            Box(Modifier.enterUp(5, 40.dp)) { FieldHealthCard(state) }

            // ---------- samples (only if images are bundled) ----------
            val samples = remember { state.sampleNames() }
            if (samples.isNotEmpty()) {
                SectionHeader(state.t("samples"), Icons.Rounded.AutoAwesome)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(samples) { name ->
                        val bmp by produceState<Bitmap?>(null, name) {
                            value = withContext(Dispatchers.IO) { state.sampleBitmap(name) }
                        }
                        Column(Modifier.width(112.dp).clickable { bmp?.let(onPicked) }) {
                            BitmapImage(bmp, Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)))
                            Spacer(Modifier.height(6.dp))
                            Text(name.substringBeforeLast('.').replace('_', ' '), fontSize = 12.sp,
                                color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ---------- tip of the day ----------
            val tipKey = "tip${Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % 6 + 1}"
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.enterUp(6).fillMaxWidth().premiumCard(24.dp, Palette.GoldSoft).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.goldBrush),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lightbulb, null, tint = Palette.OnGold) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Eyebrow(state.t("tip_day"))
                    Spacer(Modifier.height(2.dp))
                    Text(state.t(tipKey), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                }
            }

            // ---------- recent ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader(state.t("recent"), Icons.Rounded.History) }
                if (state.history.isNotEmpty())
                    Text(state.t("see_all"), color = Palette.Leaf, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp).clickable(onClick = onSeeAll))
            }
            if (state.history.isEmpty()) EmptyNote(state.t("no_history"))
            state.history.take(3).forEachIndexed { i, s -> Box(Modifier.enterUp(7 + i)) { ScanRow(state, s, onOpenScan) } }
        }
    }
}

@Composable
private fun FieldHealthCard(state: AppState) {
    val total = state.history.size
    val healthy = state.history.count { it.severityKey == "none" && !it.uncertain }
    val care = state.history.count { it.severityKey != "none" && !it.uncertain }
    Column(
        Modifier.fillMaxWidth().premiumCard(26.dp).padding(20.dp)
    ) {
        Eyebrow(state.t("field_health"))
        Spacer(Modifier.height(14.dp))
        Row {
            Stat(total.toString(), state.t("scans"), Palette.Forest, Modifier.weight(1f))
            Stat(healthy.toString(), state.t("healthy_count"), Palette.Leaf, Modifier.weight(1f))
            Stat(care.toString(), state.t("need_care"), Palette.Terracotta, Modifier.weight(1f))
        }
        if (total > 0) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Line)) {
                if (healthy > 0) Box(Modifier.weight(healthy.toFloat()).height(8.dp).background(Palette.Mint))
                if (care > 0) Box(Modifier.weight(care.toFloat()).height(8.dp).background(Palette.Terracotta))
                val other = total - healthy - care
                if (other > 0) Box(Modifier.weight(other.toFloat()).height(8.dp).background(Palette.Sky))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, fontSize = 13.sp, color = Palette.InkSoft)
    }
}

@Composable
fun Avatar(initials: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onDark: Boolean = true) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else Palette.MintSoft)
            .border(2.dp, if (onDark) Palette.Gold.copy(alpha = 0.8f) else Palette.Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = if (onDark) Color.White else Palette.Forest, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp)
    }
}

fun localeFor(lang: String): Locale = when (lang) { "ta" -> Locale("ta", "IN"); "hi" -> Locale("hi", "IN"); else -> Locale("en", "IN") }

@Composable
fun SectionHeader(text: String, icon: ImageVector) {
    Row(Modifier.padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.GoldText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = Palette.InkSoft, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().premiumCard(20.dp).padding(18.dp))
}

@Composable
fun ScanRow(state: AppState, scan: Scan, onOpen: (Scan) -> Unit, onDelete: ((Scan) -> Unit)? = null) {
    val thumb by produceState<Bitmap?>(null, scan.id) {
        value = withContext(Dispatchers.IO) { state.loadThumb(scan.imagePath, 200) }
    }
    val sevColor = if (scan.uncertain) Palette.Sky else severityColor(scan.severityKey)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).premiumCard(22.dp).clickable { onOpen(scan) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BitmapImage(thumb, Modifier.size(66.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.conditionName(scan.label), style = MaterialTheme.typography.titleMedium, color = Palette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${cropEmoji(scan.label.substringBefore("___"))} ${state.cropName(scan.label)} · " +
                SimpleDateFormat("d MMM, h:mm a", localeFor(state.lang)).format(Date(scan.time)),
                fontSize = 12.sp, color = Palette.InkSoft)
            Spacer(Modifier.height(5.dp))
            Pill(when {
                scan.uncertain -> "?"
                scan.severityKey == "none" -> state.t("healthy")
                else -> state.t(scan.severityKey)
            }, sevColor.copy(alpha = 0.15f), sevColor)
        }
        if (onDelete != null) IconButton(onClick = { onDelete(scan) }) {
            Icon(Icons.Rounded.Delete, null, tint = Palette.InkSoft)
        } else Icon(Icons.Rounded.ChevronRight, null, tint = Palette.InkSoft)
    }
}

// =====================================================================================
//  History
// =====================================================================================
@Composable
fun HistoryScreen(state: AppState, onOpen: (Scan) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text(state.t("history"), style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            if (state.history.isEmpty()) EmptyNote(state.t("no_history"))
        }
        items(state.history.toList(), key = { it.id }) { ScanRow(state, it, onOpen, onDelete = { s -> state.delete(s) }) }
    }
}

// =====================================================================================
//  Insights (performance for judges)
// =====================================================================================
@Composable
fun DeviceScreen(state: AppState) {
    val info = remember(state.bench, state.llmStats) { state.deviceInfo() }
    val last = state.history.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp)) {
        Text(state.t("perf_title"), style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
        Text(state.t("perf_sub"), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Vision load", "${state.visionLoadMs} ms", Modifier.weight(1f))
            MetricTile("Llama load", if (state.llmReady) "${state.llmLoadMs} ms" else "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Last vision", last?.let { "${it.inferenceMs} ms" } ?: "—", Modifier.weight(1f))
            MetricTile("Preprocess", last?.let { "${it.preprocessMs} ms" } ?: "—", Modifier.weight(1f))
        }
        state.llmStats?.let { s ->
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Llama decode", "%.1f tok/s".format(s.decodeTps), Modifier.weight(1f))
                MetricTile("First token", "${s.ttftMs} ms", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Prefill", "%.0f tok/s".format(s.prefillTps), Modifier.weight(1f))
                MetricTile("Tokens in / out", "${s.promptTokens} / ${s.genTokens}", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionCard("Vision benchmark (30 runs)", Icons.Rounded.Speed) {
            val b = state.bench
            if (b != null) {
                val sorted = b.sorted()
                Text("Average %.1f ms · Median %.1f ms · Best %.1f ms · Worst %.1f ms".format(
                    b.average(), sorted[sorted.size / 2], sorted.first(), sorted.last()),
                    style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                Text("≈ %.0f images / second on CPU".format(1000 / b.average()), color = Palette.Leaf,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
            BigButton(if (state.benchRunning) state.t("running") else state.t("run_bench"), Icons.Rounded.Speed,
                { state.runBenchmark() }, Modifier.fillMaxWidth(), enabled = !state.benchRunning)
        }
        Spacer(Modifier.height(16.dp))
        SectionCard("Field-photo accuracy test", Icons.Rounded.PhotoLibrary) {
            Text("Runs every photo in the eval folder through the model on this phone and scores it.",
                color = Palette.InkSoft, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            state.evalSummary?.forEach { line ->
                Text(line, color = Palette.Ink, fontSize = 13.sp,
                    fontWeight = if (line.startsWith("Disease") || line.startsWith("Crop")) FontWeight.SemiBold else FontWeight.Normal)
            }
            if (state.evalSaved) {
                Text("Saved: eval_results.csv, eval_summary.txt", color = Palette.Leaf, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
            }
            BigButton(if (state.evalRunning) "Testing… ${state.evalProgress}" else "Run field-photo test",
                Icons.Rounded.PhotoLibrary, { state.runEval() }, Modifier.fillMaxWidth(), enabled = !state.evalRunning)
        }
        Spacer(Modifier.height(16.dp))
        SectionCard("This phone", Icons.Rounded.Memory) {
            info.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(k, color = Palette.InkSoft, modifier = Modifier.weight(0.42f), fontSize = 14.sp)
                    Text(v, color = Palette.Ink, modifier = Modifier.weight(0.58f), fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
            if (!state.llmReady) Text(state.llmError ?: "", color = Palette.Terracotta, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.premiumCard(20.dp).padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Forest)
    }
}

// =====================================================================================
//  Profile + Settings
// =====================================================================================
@Composable
fun ProfileScreen(state: AppState, onAbout: () -> Unit) {
    val user = state.user ?: return
    var langOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }       // "clear" | "delete"
    var dialog by remember { mutableStateOf<String?>(null) }        // "pw" | "question"
    if (langOpen) LanguageSheet(state) { langOpen = false }
    when (dialog) {
        "pw" -> ChangePasswordDialog(state) { dialog = null }
        "question" -> SecurityQuestionDialog(state) { dialog = null }
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(state.t(if (what == "clear") "clear_history" else "delete_profile")) },
            confirmButton = {
                TextButton({ if (what == "clear") state.clearHistory() else state.deleteProfile(); confirm = null }) {
                    Text(state.t("delete"), color = Palette.Terracotta, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("✕") } },
            containerColor = Palette.Card,
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---------- profile header ----------
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Palette.hero).statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Avatar(user.initials, 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(user.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text("+91 ${user.phone.take(2)}••••••${user.phone.takeLast(2)}", color = Palette.MintSoftFixed, fontSize = 14.sp)
            if (user.village.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Pill(user.village, Color.White.copy(alpha = 0.14f), Color.White, Icons.Rounded.Place)
            }
            Spacer(Modifier.height(8.dp))
            Text("${state.t("member_since")} ${SimpleDateFormat("MMM yyyy", localeFor(state.lang)).format(Date(user.created))} · " +
                "${state.history.size} ${state.t("scans")}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(20.dp)) {
            Text(state.t("settings"), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))

            // Appearance
            SettingsGroup(state.t("appearance")) {
                Row(Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(16.dp)).background(Palette.CardAlt).padding(4.dp)) {
                    listOf("system" to "theme_system", "light" to "theme_light", "dark" to "theme_dark").forEach { (mode, key) ->
                        val on = state.theme == mode
                        Text(state.t(key), Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) Palette.Forest else Color.Transparent)
                            .clickable { state.setThemeMode(mode) }.padding(vertical = 11.dp),
                            color = if (on) Palette.OnForest else Palette.Ink, fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                SettingRow(Icons.Rounded.Translate, state.t("language"),
                    Strings.LANGS.first { it.first == state.lang }.second) { langOpen = true }
            }

            // Voice & AI
            SettingsGroup(state.t("voice")) {
                ToggleRow(Icons.Rounded.RecordVoiceOver, state.t("auto_read"), null, state.autoRead) { state.setAutoReadOn(it) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        Text(state.t("speech_rate"), color = Palette.Ink, modifier = Modifier.weight(1f))
                        Text("%.1f×".format(state.speechRate), color = Palette.Leaf, fontWeight = FontWeight.Bold)
                    }
                    Slider(state.speechRate, { state.setRate(it) }, valueRange = 0.6f..1.4f, steps = 7,
                        colors = SliderDefaults.colors(thumbColor = Palette.Forest, activeTrackColor = Palette.Forest,
                            inactiveTrackColor = Palette.Line))
                }
                ToggleRow(Icons.Rounded.AutoAwesome, state.t("ai_toggle"), state.t("ai_toggle_sub"), state.aiOn) { state.setAi(it) }
            }

            // Data + about
            SettingsGroup(state.t("data")) {
                SettingRow(Icons.Rounded.Info, state.t("about"), state.t("about_sub"), onClick = onAbout)
                SettingRow(Icons.Rounded.Delete, state.t("clear_history"), null) { confirm = "clear" }
            }

            SettingsGroup(state.t("security")) {
                SettingRow(Icons.Rounded.Lock, state.t("change_pw"), null) { dialog = "pw" }
                SettingRow(Icons.Rounded.Shield, state.t("security_title"),
                    state.t(if (user.hasRecovery) "recovery_ok" else "recovery_missing")) { dialog = "question" }
            }

            SettingsGroup(state.t("profile")) {
                SettingRow(Icons.Rounded.SwitchAccount, state.t("switch_user"), null) { state.logout() }
                SettingRow(Icons.Rounded.Logout, state.t("logout"), null) { state.logout() }
                SettingRow(Icons.Rounded.DeleteForever, state.t("delete_profile"), null, danger = true) { confirm = "delete" }
            }
            Spacer(Modifier.height(8.dp))
            Text("${state.t("app")} · v3.0 · Bharat AI-SoC Challenge 2026–27", fontSize = 12.sp, color = Palette.InkSoft,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Eyebrow(title, Modifier.padding(start = 6.dp, top = 12.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().premiumCard(22.dp)) { content() }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = if (danger) Palette.Terracotta else Palette.Forest
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) Palette.Terracotta else Palette.Ink, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, color = Palette.InkSoft, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.InkSoft)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Forest.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = Palette.Forest, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Ink, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, color = Palette.InkSoft, fontSize = 13.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Forest,
            checkedThumbColor = Palette.OnForest, uncheckedTrackColor = Palette.CardAlt))
    }
}

// =====================================================================================
//  About (crops, how it works, how to use)
// =====================================================================================
@Composable
fun AboutScreen(state: AppState, onBack: () -> Unit) {
    val labels = state.allLabels()
    Column(Modifier.fillMaxSize().background(Palette.Cream).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Palette.hero).statusBarsPadding().padding(20.dp)
        ) {
            RoundIcon(Icons.Rounded.ArrowBack, onBack)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UzhavanLogo(60.dp, animated = true)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(state.t("app"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(state.t("tagline"), color = Palette.MintSoftFixed, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.padding(20.dp)) {
            SectionCard(state.t("how_works"), Icons.Rounded.AutoAwesome) {
                listOf("works1", "works2", "works3").forEachIndexed { i, k -> NumberedStep(i + 1, state.t(k)) }
            }
            SectionHeader(state.t("crops"), Icons.Rounded.Eco)
            listOf("Tomato", "Potato", "Pepper,_bell", "Corn_(maize)", "Rice").forEach { crop ->
                val conds = labels.filter { it.startsWith("${crop}___") }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).premiumCard(22.dp).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cropEmoji(crop), fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(Strings.cropName("${crop}___x", state.lang), style = MaterialTheme.typography.titleLarge,
                            color = Palette.Ink, modifier = Modifier.weight(1f))
                        Pill("${conds.size}", Palette.MintSoft, Palette.Forest)
                    }
                    if (conds.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(conds.joinToString("  ·  ") { state.conditionName(it) }, color = Palette.InkSoft,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionCard(state.t("help_title"), Icons.Rounded.Info) {
                listOf("help1", "help2", "help3", "help4").forEachIndexed { i, k -> NumberedStep(i + 1, state.t(k)) }
            }
            Spacer(Modifier.height(14.dp))
            SectionCard(state.t("tips_title"), Icons.Rounded.CameraAlt) {
                listOf("tip1", "tip2", "tip3", "tip4").forEach { k ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•  ", color = Palette.Leaf, fontWeight = FontWeight.Bold)
                        Text(state.t(k), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(state.t("disclaimer"), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.AmberSoft).padding(16.dp))
            Spacer(Modifier.height(14.dp))
            Text("Vision: MobileNetV3 · Advice: Llama 3.2 1B · Runtime: ExecuTorch on Arm CPU\nBharat AI-SoC Challenge 2026–27 · PS1",
                fontSize = 12.sp, color = Palette.InkSoft)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Language chooser dialog. */
@Composable
fun LanguageSheet(state: AppState, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Palette.Card).padding(20.dp)) {
            Text(state.t("choose_lang"), style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            Strings.LANGS.forEach { (code, name) ->
                val on = code == state.lang
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) Palette.MintSoft else Color.Transparent)
                        .clickable { state.setLanguage(code); state.requestAdvice(); onClose() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Palette.Forest else Palette.Line))
                    Spacer(Modifier.width(14.dp))
                    Text(name, fontSize = 18.sp, color = Palette.Ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
