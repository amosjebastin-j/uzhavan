package com.cropadvisor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cropadvisor.AppState
import com.cropadvisor.Strings

// =====================================================================================
//  Splash — animated logo while both models load
// =====================================================================================
@Composable
fun SplashScreen(state: AppState) {
    Box(Modifier.fillMaxSize().background(Palette.hero)) {
        // soft gold glow behind the logo
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Palette.Gold.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.36f), radius = size.width * 0.7f),
                radius = size.width * 0.7f, center = Offset(size.width / 2, size.height * 0.36f))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            UzhavanLogo(132.dp, animated = true)
            Spacer(Modifier.height(28.dp))
            Text(state.t("app"), color = Color.White, style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.enterUp(2))
            Text(state.t("tagline"), color = Palette.GoldLight, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.enterUp(3))
            Spacer(Modifier.height(44.dp))
            Column(Modifier.enterUp(5)) {
                LoadRow(state.t("vision_model"), state.visionReady, state.visionLoadMs)
                LoadRow(state.t("llm_model"), state.llmDone, state.llmLoadMs)
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.enterUp(7)) {
                Icon(Icons.Rounded.WifiOff, null, tint = Palette.MintSoftFixed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(state.t("offline"), color = Palette.MintSoftFixed, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LoadRow(name: String, done: Boolean, ms: Long) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Palette.Gold, modifier = Modifier.size(22.dp))
        else CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Gold, strokeWidth = 2.5.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (done && ms > 0) Text("$ms ms", color = Palette.GoldLight, fontSize = 12.sp)
    }
}

// =====================================================================================
//  Language picker
// =====================================================================================
@Composable
fun LanguageScreen(state: AppState, first: Boolean, onDone: () -> Unit) {
    var sel by remember { mutableStateOf(state.lang.ifEmpty { "en" }) }
    Column(
        Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding().padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        UzhavanLogo(60.dp, Modifier.enterUp(0))
        Spacer(Modifier.height(20.dp))
        Text(Strings.t("choose_lang", sel), style = MaterialTheme.typography.headlineMedium, color = Palette.Ink,
            modifier = Modifier.enterUp(1))
        Text(Strings.t("choose_lang_sub", sel), style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft,
            modifier = Modifier.enterUp(2))
        Spacer(Modifier.height(28.dp))
        val samples = mapOf("en" to "Aa", "ta" to "அ", "hi" to "अ")
        Strings.LANGS.forEachIndexed { i, (code, name) ->
            val on = code == sel
            Row(
                Modifier.enterUp(3 + i).fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (on) Palette.Forest else Palette.Card)
                    .border(1.5.dp, if (on) Palette.Gold else Palette.Line, RoundedCornerShape(22.dp))
                    .clickable { sel = code }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Palette.OnForest.copy(alpha = 0.15f) else Palette.MintSoft),
                    contentAlignment = Alignment.Center) {
                    Text(samples[code]!!, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = if (on) Palette.OnForest else Palette.Forest)
                }
                Spacer(Modifier.width(16.dp))
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) Palette.OnForest else Palette.Ink, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Rounded.Check, null, tint = Palette.OnForest)
            }
        }
        Spacer(Modifier.weight(1f))
        BigButton(Strings.t("continue", sel), Icons.Rounded.Translate,
            onClick = { state.setLanguage(sel); onDone() }, modifier = Modifier.fillMaxWidth(),
            fg = Palette.OnGold, brush = Palette.goldBrush)
    }
}

// =====================================================================================
//  Analysing — the farmer's own photo under a scanning light
// =====================================================================================
@Composable
fun AnalysingScreen(state: AppState) {
    val tr = rememberInfiniteTransition(label = "scan")
    val y by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    val dots by tr.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "d")
    Column(
        Modifier.fillMaxSize().background(Palette.ForestDeep).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.enterUp(0).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp))
            .border(1.5.dp, Palette.Gold.copy(alpha = 0.5f), RoundedCornerShape(32.dp))) {
            val photo = state.scanningPhoto
            if (photo != null) BitmapImage(photo, Modifier.fillMaxSize())
            else UzhavanLogo(120.dp, Modifier.align(Alignment.Center), badge = false)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.25f))
                val ly = size.height * y
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.Gold.copy(alpha = 0.35f)),
                    startY = ly - 160f, endY = ly), Offset(0f, ly - 160f), androidx.compose.ui.geometry.Size(size.width, 160f))
                drawLine(Palette.GoldLight, Offset(0f, ly), Offset(size.width, ly), 5f)
                // viewfinder corners
                val a = size.minDimension * 0.12f; val m = size.minDimension * 0.07f; val w = 7f
                listOf(Offset(m, m) to Offset(1f, 1f), Offset(size.width - m, m) to Offset(-1f, 1f),
                    Offset(m, size.height - m) to Offset(1f, -1f),
                    Offset(size.width - m, size.height - m) to Offset(-1f, -1f)).forEach { (c, d) ->
                    drawLine(Palette.Gold, c, Offset(c.x + d.x * a, c.y), w, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Palette.Gold, c, Offset(c.x, c.y + d.y * a), w, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(state.t("analysing") + ".".repeat(dots.toInt() + 1), color = Color.White,
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        listOf("step_detect", "step_severity", "step_advice").forEachIndexed { i, k ->
            Row(Modifier.enterUp(2 + i * 2).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Gold, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(state.t(k), color = Palette.MintSoftFixed, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// =====================================================================================
//  Sign in / create account / reset password  (all offline, on-device database)
// =====================================================================================
private enum class Auth { PHONE, SIGNIN, CREATE, SECURITY, RECOVER, NEWPW, NORECOVERY }

@Composable
fun LoginScreen(state: AppState) {
    var step by remember { mutableStateOf(Auth.PHONE) }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var question by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun go(s: Auth) {
        error = null; pw = ""; pw2 = ""
        if (s == Auth.PHONE || s == Auth.SIGNIN || s == Auth.RECOVER) answer = ""
        step = s
    }
    fun fail(msg: String) { error = msg; shake++ }
    fun pwProblem(): String? = when {
        pw.length < AppState.MIN_PASSWORD -> state.t("pw_short")
        pw != pw2 -> state.t("pw_mismatch")
        else -> null
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = Palette.Terracotta) },
        title = { Text(state.t("remove_confirm_title")) },
        text = { Text(state.t("remove_acc_sub")) },
        confirmButton = {
            TextButton({
                state.removeAccount(phone); confirmRemove = false
                name = ""; village = ""; go(Auth.CREATE)
            }) { Text(state.t("remove"), color = Palette.Terracotta, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton({ confirmRemove = false }) { Text(state.t("cancel"), color = Palette.Ink) } },
        containerColor = Palette.Card, titleContentColor = Palette.Ink, textContentColor = Palette.InkSoft,
    )

    Column(Modifier.fillMaxSize().background(Palette.Cream).verticalScroll(rememberScrollState()).imePadding()) {
        // ---------- hero ----------
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)).background(Palette.hero)) {
            UzhavanLogo(240.dp, Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-20).dp).alpha(0.08f), badge = false)
            Column(Modifier.statusBarsPadding().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp)) {
                UzhavanLogo(68.dp, animated = true)
                Spacer(Modifier.height(20.dp))
                Text(state.t("login_title"), color = Color.White, style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.enterUp(1))
                Spacer(Modifier.height(8.dp))
                Text(state.t("login_sub"), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.enterUp(2))
            }
        }

        // ---------- step content ----------
        AnimatedContent(
            targetState = step, label = "auth",
            transitionSpec = {
                val fwd = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(380)) { w -> if (fwd) w / 3 else -w / 3 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> if (fwd) -w / 3 else w / 3 } + fadeOut(tween(250)))
            },
        ) { s ->
            Column(Modifier.fillMaxWidth().padding(24.dp).shakeOn(shake)) {
                when (s) {
                    Auth.PHONE -> {
                        StepTitle(state.t("mobile"), null)
                        AuthField(phone, { v -> phone = v.filter(Char::isDigit).take(10); error = null },
                            state.t("mobile"), Icons.Rounded.Phone, keyboard = KeyboardType.Phone, prefix = "+91  ",
                            isError = error != null)
                    }
                    Auth.SIGNIN -> {
                        val u = state.findUser(phone)
                        StepTitle("${state.t("welcome_back")}, ${u?.firstName ?: ""} 👋", "+91 $phone")
                        AuthField(pw, { pw = it; error = null }, state.t("password_enter"), Icons.Rounded.Lock,
                            password = true, isError = error != null)
                        Text(state.t("forgot"), color = Palette.Leaf, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp))
                                .clickable { go(if (state.recoveryQuestion(phone) >= 0) Auth.RECOVER else Auth.NORECOVERY) }
                                .padding(8.dp))
                    }
                    Auth.CREATE -> {
                        Pill(state.t("step_of").format(1), Palette.GoldSoft, Palette.GoldText)
                        Spacer(Modifier.height(12.dp))
                        StepTitle(state.t("create_account"), "+91 $phone")
                        AuthField(name, { name = it.take(40); error = null }, state.t("name"), Icons.Rounded.Person)
                        Spacer(Modifier.height(10.dp))
                        AuthField(village, { village = it.take(40) }, state.t("village"), Icons.Rounded.Place)
                        Spacer(Modifier.height(10.dp))
                        AuthField(pw, { pw = it; error = null }, state.t("password_new"), Icons.Rounded.Lock,
                            password = true, hint = state.t("pw_hint"))
                        Spacer(Modifier.height(4.dp))
                        AuthField(pw2, { pw2 = it; error = null }, state.t("password_confirm"), Icons.Rounded.Lock,
                            password = true, isError = error != null && pw != pw2)
                        StrengthBar(pw)
                    }
                    Auth.SECURITY -> {
                        Pill(state.t("step_of").format(2), Palette.GoldSoft, Palette.GoldText)
                        Spacer(Modifier.height(12.dp))
                        StepTitle(state.t("security_title"), state.t("security_sub"))
                        QuestionPicker(state, question) { question = it }
                        Spacer(Modifier.height(12.dp))
                        AuthField(answer, { answer = it.take(40); error = null }, state.t("your_answer"),
                            Icons.Rounded.VpnKey, isError = error != null)
                    }
                    Auth.RECOVER -> {
                        StepTitle(state.t("recover_title"), state.t("recover_sub"))
                        val q = state.recoveryQuestion(phone)
                        Row(Modifier.fillMaxWidth().premiumCard(18.dp, Palette.GoldSoft).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, null, tint = Palette.GoldText)
                            Spacer(Modifier.width(12.dp))
                            Text(state.t("q$q"), color = Palette.Ink, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(14.dp))
                        AuthField(answer, { answer = it.take(40); error = null }, state.t("your_answer"),
                            Icons.Rounded.VpnKey, isError = error != null)
                    }
                    Auth.NEWPW -> {
                        StepTitle(state.t("new_pw_title"), "+91 $phone")
                        AuthField(pw, { pw = it; error = null }, state.t("password_new"), Icons.Rounded.Lock,
                            password = true, hint = state.t("pw_hint"))
                        Spacer(Modifier.height(4.dp))
                        AuthField(pw2, { pw2 = it; error = null }, state.t("password_confirm"), Icons.Rounded.Lock,
                            password = true, isError = error != null)
                        StrengthBar(pw)
                    }
                    Auth.NORECOVERY -> {
                        StepTitle(state.t("recover_title"), null)
                        Row(Modifier.fillMaxWidth().premiumCard(18.dp, Palette.SkySoft).padding(16.dp)) {
                            Icon(Icons.Rounded.HelpOutline, null, tint = Palette.Sky)
                            Spacer(Modifier.width(12.dp))
                            Text(state.t("no_recovery"), color = Palette.Ink, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(error ?: "", color = Palette.Terracotta, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(22.dp))

                // ---------- main button ----------
                val (label, icon) = when (s) {
                    Auth.PHONE -> state.t("next") to Icons.Rounded.ArrowForward
                    Auth.SIGNIN -> state.t("sign_in") to Icons.Rounded.Check
                    Auth.CREATE -> state.t("next") to Icons.Rounded.ArrowForward
                    Auth.SECURITY -> state.t("create") to Icons.Rounded.Check
                    Auth.RECOVER -> state.t("verify") to Icons.Rounded.Shield
                    Auth.NEWPW -> state.t("save_signin") to Icons.Rounded.Check
                    Auth.NORECOVERY -> state.t("remove_acc") to Icons.Rounded.DeleteForever
                }
                BigButton(label, icon, modifier = Modifier.fillMaxWidth(),
                    bg = if (s == Auth.NORECOVERY) Palette.Terracotta else Palette.Forest,
                    fg = if (s == Auth.NORECOVERY) Color.White else Palette.OnGold,
                    brush = if (s == Auth.NORECOVERY) null else Palette.goldBrush,
                    onClick = {
                        when (s) {
                            Auth.PHONE ->
                                if (phone.length != 10 || phone[0] !in "6789") fail(state.t("invalid_mobile"))
                                else go(if (state.findUser(phone) != null) Auth.SIGNIN else Auth.CREATE)
                            Auth.SIGNIN ->
                                if (pw.isEmpty()) fail(state.t("password_enter"))
                                else state.signIn(phone, pw)?.let { fail(it); pw = "" }
                            Auth.CREATE -> when {
                                name.isBlank() -> fail(state.t("need_name"))
                                else -> pwProblem()?.let { fail(it) } ?: run { error = null; step = Auth.SECURITY }
                            }
                            Auth.SECURITY ->
                                if (answer.trim().length < 2) fail(state.t("need_answer"))
                                else state.register(phone, name, village, pw, question, answer)
                            Auth.RECOVER ->
                                if (answer.trim().length < 2) fail(state.t("need_answer"))
                                else state.checkAnswer(phone, answer)?.let { fail(it) } ?: go(Auth.NEWPW)
                            Auth.NEWPW -> pwProblem()?.let { fail(it) } ?: run { state.resetPassword(phone, pw) }
                            Auth.NORECOVERY -> confirmRemove = true
                        }
                    },
                )

                // ---------- secondary links ----------
                if (s == Auth.RECOVER) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.t("remove_acc"), color = Palette.Terracotta, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp))
                            .clickable { confirmRemove = true }.padding(10.dp))
                }
                if (s != Auth.PHONE) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        when (s) {
                            Auth.SECURITY -> { error = null; step = Auth.CREATE }       // keep typed passwords
                            Auth.RECOVER, Auth.NORECOVERY, Auth.NEWPW -> go(Auth.SIGNIN)
                            else -> go(Auth.PHONE)
                        }
                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Palette.Leaf, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(state.t("back"), color = Palette.Leaf, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ---------- footer ----------
        Row(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Palette.MintSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = Palette.Leaf, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(state.t("db_note"), color = Palette.InkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StepTitle(title: String, sub: String?) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
    if (sub != null) Text(sub, color = Palette.InkSoft, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(18.dp))
}

/** Security-question choice (also used in Profile → Account security). */
@Composable
fun QuestionPicker(state: AppState, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().premiumCard(18.dp)) {
        repeat(AppState.QUESTIONS) { i ->
            val on = i == selected
            Row(
                Modifier.fillMaxWidth().background(if (on) Palette.GoldSoft else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape)
                    .border(2.dp, if (on) Palette.GoldText else Palette.Line, CircleShape)
                    .background(if (on) Palette.GoldText else Color.Transparent),
                    contentAlignment = Alignment.Center) {
                    if (on) Icon(Icons.Rounded.Check, null, tint = Palette.Card, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(state.t("q$i"), color = Palette.Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Four-segment password strength bar. */
@Composable
fun StrengthBar(pw: String) {
    if (pw.isEmpty()) return
    val score = listOf(pw.length >= 6, pw.length >= 10, pw.any(Char::isDigit) && pw.any(Char::isLetter),
        pw.any { !it.isLetterOrDigit() } || (pw.any(Char::isUpperCase) && pw.any(Char::isLowerCase))).count { it }
    val color = when (score) { 0, 1 -> Palette.Terracotta; 2 -> Palette.Amber; else -> Palette.Mint }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50))
                .background(if (i < score.coerceAtLeast(1)) color else Palette.Line))
        }
    }
}
