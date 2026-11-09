package com.cropadvisor.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Send
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coronavirus
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cropadvisor.AppState
import com.cropadvisor.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(state: AppState, onBack: () -> Unit, onRetake: () -> Unit) {
    val scan = state.current ?: return
    val ctx = LocalContext.current
    val g = state.guidance(scan.label)
    val photo by produceState<Bitmap?>(null, scan.id) {
        value = withContext(Dispatchers.IO) { state.loadThumb(scan.imagePath, 1080) }
    }
    val healthy = g?.type == "healthy"
    val isPest = g?.type == "pest"
    val sevKey = scan.severityKey
    val accent = when {
        scan.uncertain -> Palette.Sky
        healthy -> Palette.Mint
        else -> severityColor(sevKey).let { if (sevKey == "mild") Palette.Leaf else it }
    }

    Column(Modifier.fillMaxSize().background(Palette.Cream).verticalScroll(rememberScrollState())) {
        // ---------------- photo header ----------------
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            BitmapImage(photo, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Palette.scrim))
            Row(Modifier.statusBarsPadding().padding(16.dp)) { RoundIcon(Icons.Rounded.ArrowBack, onBack) }
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val (typeText, typeIcon) = when {
                        scan.uncertain -> state.t("unsure_pill") to Icons.Rounded.HelpOutline
                        healthy -> state.t("healthy") to Icons.Rounded.CheckCircle
                        isPest -> state.t("pest") to Icons.Rounded.BugReport
                        else -> state.t("disease") to Icons.Rounded.Coronavirus
                    }
                    Pill(typeText, accent.copy(alpha = 0.92f), Color.White, typeIcon)
                    if (!scan.label.startsWith("Other___"))
                        Pill(state.cropName(scan.label) + (scan.altCropLabel?.let { " / " + state.cropName(it) } ?: ""),
                            Color.White.copy(alpha = 0.2f), Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Text(when { scan.noLeaf -> state.t("noleaf_title"); scan.otherCrop -> state.t("other_title"); scan.uncertain -> state.t("unsure_title")
                            else -> state.conditionName(scan.label) },
                    color = Color.White, style = MaterialTheme.typography.displaySmall)
            }
        }

        Column(Modifier.padding(horizontal = 18.dp).navigationBarsPadding()) {
            Spacer(Modifier.height(14.dp))
            LanguageChips(state)
            Spacer(Modifier.height(14.dp))

            if (scan.uncertain) {
                UnsureBlock(state, onRetake)
            } else {
                // ---------- overview ----------
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConfidenceRing(scan.confidence, accent, label = state.t("confidence"))
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            if (healthy) {
                                Text(state.t("healthy"), style = MaterialTheme.typography.headlineSmall, color = Palette.Leaf)
                                Text(g?.symptoms ?: "", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                            } else {
                                Text(state.t("severity"), fontSize = 12.sp, color = Palette.InkSoft)
                                Text(state.t(sevKey), style = MaterialTheme.typography.headlineSmall,
                                    color = severityColor(sevKey).let { if (sevKey == "mild") Palette.Leaf else it })
                                Text("${scan.severityPct.toInt()}% ${state.t("affected")}", fontSize = 13.sp,
                                    color = Palette.InkSoft)
                                Spacer(Modifier.height(8.dp))
                                SeverityGauge(scan.severityPct,
                                    listOf(state.t("mild"), state.t("moderate"), state.t("severe")))
                            }
                        }
                    }
                }

                if (sevKey == "severe" && !healthy) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.TerracottaSoft)
                            .border(1.dp, Palette.Terracotta.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable { ctx.startActivity(state.callIntent()) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = Palette.Terracotta)
                        Spacer(Modifier.width(12.dp))
                        Text(state.t("urgent"), color = Palette.Terracotta, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Phone, null, tint = Palette.Terracotta)
                    }
                }

                // ---------- quick actions ----------
                Spacer(Modifier.height(12.dp))
                val spoken = buildString {
                    append(state.conditionName(scan.label)).append(". ")
                    g?.actions?.forEachIndexed { i, a -> append("${i + 1}. $a. ") }
                    g?.treatment?.let { append(it) }
                }
                androidx.compose.runtime.LaunchedEffect(scan.id, state.lang) {
                    if (state.autoRead) state.speak(spoken, toggle = false)
                }
                Row(Modifier.enterUp(0), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip(if (state.speaking) state.t("stop") else state.t("listen"), Icons.Rounded.VolumeUp,
                        { state.speak(spoken) }, active = state.speaking)
                    ActionChip(state.t("share"), Icons.Rounded.Share, {
                        ctx.startActivity(state.shareIntent(scan, shareText(state, scan.label, spoken,
                            scan.confidence, state.t(sevKey))))
                    })
                    ActionChip(state.t("call_expert"), Icons.Rounded.Phone, { ctx.startActivity(state.callIntent()) })
                }

                if (g != null) {
                    // ---------- do now ----------
                    Spacer(Modifier.height(14.dp))
                    SectionCard(state.t("do_now"), Icons.Rounded.TaskAlt, accent = accent, modifier = Modifier.enterUp(1)) {
                        g.actions.forEachIndexed { i, a -> NumberedStep(i + 1, a, accent) }
                    }
                    // ---------- AI advisor ----------
                    if (!healthy) {
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.enterUp(2)) { AiCard(state) }
                        Box(Modifier.enterUp(3)) { VoiceCard(state) }
                    }
                    // ---------- what is happening ----------
                    Spacer(Modifier.height(14.dp))
                    SectionCard(state.t("whats_happening"), Icons.Rounded.Search, accent = Palette.Sky, modifier = Modifier.enterUp(3)) {
                        LabeledText(state.t("cause"), g.cause)
                        Spacer(Modifier.height(8.dp))
                        LabeledText(state.t("symptoms"), g.symptoms)
                    }
                    if (!healthy && g.treatment != null) {
                        Spacer(Modifier.height(14.dp))
                        SectionCard(state.t("treatment"), Icons.Rounded.MedicalServices, accent = Palette.Amber, modifier = Modifier.enterUp(4)) {
                            Text(g.treatment, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SectionCard(state.t("prevention"), Icons.Rounded.Shield, accent = Palette.Leaf, modifier = Modifier.enterUp(5)) {
                        Text(g.prevention, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    }
                }

                // ---------- alternatives ----------
                Spacer(Modifier.height(14.dp))
                SectionCard(state.t("other_possible"), modifier = Modifier.enterUp(6)) {
                    scan.top3.drop(1).filter { !it.first.startsWith("Other___") }.forEach { (label, p) -> AltRow(state, label, p) }
                }
            }

            Spacer(Modifier.height(18.dp))
            BigButton(state.t("scan_again"), Icons.Rounded.CameraAlt, onRetake, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Vision ${scan.inferenceMs} ms · on-device · ExecuTorch + XNNPACK (Arm CPU)",
                fontSize = 11.sp, color = Palette.InkSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageChips(state: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Strings.LANGS.forEach { (code, name) ->
            val on = state.lang == code
            Text(name, color = if (on) Palette.OnForest else Palette.Forest, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Forest else Palette.CardAlt)
                    .clickable { if (!on) { state.setLanguage(code); state.requestAdvice() } }
                    .padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun AiCard(state: AppState) {
    SectionCard(state.t("ai_title"), Icons.Rounded.AutoAwesome, accent = Palette.GoldText) {
        Pill(state.t("ai_badge"), Palette.GoldSoft, Palette.GoldText, Icons.Rounded.Verified)
        Spacer(Modifier.height(12.dp))
        when {
            // Tamil: Llama 3.2 does not support Tamil → verified Tamil guidance + optional AI explanation in English
            state.lang == "ta" && state.advice.isBlank() && !state.adviceRunning && !state.adviceFailed -> Column {
                Text(state.t("ai_tamil"), color = Palette.InkSoft, style = MaterialTheme.typography.bodyMedium)
                if (state.llmReady) Text(state.t("ai_explain_en"), color = Palette.Leaf, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { state.requestAdviceInEnglish() }.padding(vertical = 8.dp, horizontal = 4.dp))
            }
            state.adviceFailed && state.advice.isBlank() -> Column {
                Text(state.t("ai_unavailable"), color = Palette.InkSoft, style = MaterialTheme.typography.bodyMedium)
                if (state.llmReady) Text("↻  " + state.t("try_again"), color = Palette.Leaf, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { if (state.lang == "ta") state.requestAdviceInEnglish() else state.requestAdvice() }
                        .padding(vertical = 8.dp, horizontal = 4.dp))
                state.adviceError?.let {
                    Text("Details: $it", fontSize = 11.sp, color = Palette.InkSoft.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            state.advice.isBlank() && state.adviceRunning -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Leaf, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(state.t("ai_thinking"), color = Palette.InkSoft)
            }
            else -> {
                val blink by rememberInfiniteTransition(label = "c").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b")
                Row {
                    Text(state.advice, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink,
                        modifier = Modifier.weight(1f, fill = false))
                    if (state.adviceRunning) Text("▍", color = Palette.Leaf, modifier = Modifier.alpha(blink))
                }
            }
        }
        state.llmStats?.let { s ->
            Spacer(Modifier.height(10.dp))
            Text("Llama 3.2 1B · %.1f tok/s · first word in %d ms · on this phone".format(s.decodeTps, s.ttftMs),
                fontSize = 11.sp, color = Palette.InkSoft)
        }
    }
}

/**
 * Voice questions for farmers who prefer speaking to reading: the phone's speech recogniser (offline when the
 * language pack is installed) turns speech into text in English, Tamil or Hindi; the answer comes from the verified
 * guidance (or Llama, grounded in it, for English / Hindi) and is read aloud.
 */
@Composable
private fun VoiceCard(state: AppState) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val q = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!q.isNullOrBlank()) state.askByVoice(q)
    }
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, state.speechTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, state.speechTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, state.t("voice_prompt"))
        }
        try { launcher.launch(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, state.t("voice_unavailable"), Toast.LENGTH_LONG).show()
        }
    }
    SectionCard(state.t("voice_title"), Icons.Rounded.Mic, accent = Palette.Leaf) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Palette.goldBrush).clickable { listen() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, state.t("voice_title"), tint = Palette.OnGold, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.t("voice_tap"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(state.t("voice_sub"), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
        }
        // typed question: works on every phone, even without a speech recogniser
        var typed by remember { mutableStateOf("") }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthField(typed, { typed = it.take(200) }, state.t("type_question"), Icons.Rounded.Edit,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(52.dp).clip(CircleShape).background(Palette.Forest)
                .clickable { if (typed.isNotBlank()) { state.askByVoice(typed.trim()); typed = "" } },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Send, state.t("type_question"), tint = Palette.OnForest, modifier = Modifier.size(22.dp))
            }
        }
        state.voiceQuestion?.let { q ->
            Spacer(Modifier.height(14.dp))
            Text("“$q”", style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, fontStyle = FontStyle.Italic,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.CardAlt).padding(12.dp))
            Spacer(Modifier.height(8.dp))
            if (state.voiceAnswer.isBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Leaf, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(state.t("ai_thinking"), color = Palette.InkSoft)
            } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.MintSoft).padding(12.dp)) {
                Text(state.voiceAnswer, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.t(if (state.voiceFromAi) "voice_src_ai" else "voice_src_verified"), fontSize = 11.sp,
                        color = Palette.InkSoft, modifier = Modifier.weight(1f))
                    if (!state.voiceRunning) Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { state.speak(state.voiceAnswer) }
                        .padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VolumeUp, null, tint = Palette.Leaf, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(state.t("listen_again"), color = Palette.Leaf, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AltRow(state: AppState, label: String, p: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CropBadge(label.substringBefore("___"), 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(state.conditionName(label),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
        Box(Modifier.width(70.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) {
            Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).height(6.dp).background(Palette.Sky))
        }
        Spacer(Modifier.width(8.dp))
        Text("${(p * 100).toInt()}%", fontSize = 13.sp, color = Palette.InkSoft, modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun UnsureBlock(state: AppState, onRetake: () -> Unit) {
    val ctx = LocalContext.current
    val scan = state.current ?: return
    val key = when { scan.noLeaf -> "noleaf"; scan.otherCrop -> "other"; else -> "unsure" }
    SectionCard(state.t("${key}_title"), Icons.Rounded.HelpOutline, accent = Palette.Sky) {
        Text(state.t("${key}_sub"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
        val maybe = scan.top3.filter { !it.first.startsWith("Other___") }
        if (key == "unsure" && maybe.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Eyebrow(state.t("maybe"))
            maybe.forEach { (label, p) -> AltRow(state, label, p) }
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionCard(state.t("tips_title"), Icons.Rounded.CameraAlt, accent = Palette.Leaf) {
        listOf("tip1", "tip2", "tip3", "tip4").forEachIndexed { i, k -> NumberedStep(i + 1, state.t(k), Palette.Leaf) }
    }
    Spacer(Modifier.height(14.dp))
    BigButton(state.t("retake"), Icons.Rounded.CameraAlt, onRetake, Modifier.fillMaxWidth(), bg = Palette.Leaf)
    Spacer(Modifier.height(10.dp))
    BigButton(state.t("call_expert"), Icons.Rounded.Phone, { ctx.startActivity(state.callIntent()) },
        Modifier.fillMaxWidth(), bg = Palette.SkySoft, fg = Palette.Sky)
}

private fun shareText(state: AppState, label: String, steps: String, conf: Float, sev: String) = buildString {
    append("${state.t("app")} — ${state.cropName(label)}\n")
    append("${state.conditionName(label)} · ${state.t("confidence")} ${(conf * 100).toInt()}% · ${state.t("severity")} $sev\n\n")
    append(steps)
    append("\n\n(Offline AI report — please confirm with your agriculture officer / KVK)")
}
