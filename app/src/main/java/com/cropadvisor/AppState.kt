package com.cropadvisor

import kotlin.math.roundToInt

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class User(val phone: String, val name: String, val village: String, val created: Long,
                val hasRecovery: Boolean = false) {
    val initials get() = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    val firstName get() = name.trim().substringBefore(" ")
}

/** One finished scan (also what is stored in history). */
data class Scan(
    val id: String,
    val label: String,
    val confidence: Float,
    val severityPct: Float,
    val top3: List<Pair<String, Float>>,
    val time: Long,
    val imagePath: String,
    val inferenceMs: Long,
    val preprocessMs: Long,
    val leafPct: Float = 100f,
) {
    /** Too little leaf in the photo (wall, cup, soil…) — never give a diagnosis. */
    val noLeaf: Boolean get() = leafPct < AppState.MIN_LEAF_PCT || label == "Other___background"
    /** A leaf, but not one of the five supported crops. */
    val otherCrop: Boolean get() = !noLeaf && label == "Other___leaf"
    val severityKey: String get() = when {
        label.contains("healthy", true) || severityPct <= 0f -> "none"
        severityPct < 10f -> "mild"
        severityPct < 25f -> "moderate"
        else -> "severe"
    }
    val uncertain: Boolean get() = noLeaf || otherCrop || confidence < AppState.CONF_THRESHOLD
    /** Second crop with the same condition (e.g. potato early blight when the top answer is tomato early blight). */
    val altCropLabel: String? get() = top3.getOrNull(1)?.takeIf { (l, p) ->
        p >= 0.15f && l.substringBefore("___") != label.substringBefore("___") && !l.startsWith("Other___") &&
            l.substringAfter("___").lowercase() == label.substringAfter("___").lowercase()
    }?.first
}

class AppState private constructor(private val ctx: Context) {

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** All Compose state is written on the main thread. */
    private fun ui(block: () -> Unit) { main.post(block) }

    private val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // ONE thread runs every ExecuTorch call — vision load / scan / benchmark / field test and Llama load / answers —
    // strictly one job at a time. Both models share XNNPACK's native thread-pool, so they must never overlap, and a
    // running answer is only ever stopped from this same thread (see Advisor.stop). No locks, no cross-thread races.
    private val mlExec = Executors.newSingleThreadExecutor { r -> Thread(r, "uzhavan-ml") }
    private val visionThread = mlExec.asCoroutineDispatcher()
    private val llmThread = mlExec

    // ---- language ----
    var lang by mutableStateOf(prefs.getString("lang", "") ?: "")
        private set
    val langChosen get() = lang.isNotEmpty()
    fun setLanguage(code: String) { lang = code; prefs.edit().putString("lang", code).apply() }
    fun t(key: String) = Strings.t(key, lang.ifEmpty { "en" })

    // ---- settings ----
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system"); private set
    var autoRead by mutableStateOf(prefs.getBoolean("autoRead", false)); private set
    var speechRate by mutableStateOf(prefs.getFloat("rate", 0.95f)); private set
    var aiOn by mutableStateOf(prefs.getBoolean("aiOn", true)); private set
    fun setThemeMode(m: String) { theme = m; prefs.edit().putString("theme", m).apply() }
    fun setAutoReadOn(v: Boolean) { autoRead = v; prefs.edit().putBoolean("autoRead", v).apply() }
    fun setRate(v: Float) { speechRate = v; prefs.edit().putFloat("rate", v).apply() }
    fun setAi(v: Boolean) { aiOn = v; prefs.edit().putBoolean("aiOn", v).apply(); requestAdvice() }

    // ---- accounts: on-device SQLite database (Db.kt), nothing leaves the phone ----
    private val db = Db(ctx)
    var user by mutableStateOf<User?>(null); private set
    fun findUser(phone: String): User? = db.account(phone)?.user
    /** -1 = this account has no security question (created before v3). */
    fun recoveryQuestion(phone: String): Int = db.account(phone)?.question ?: -1
    private var recoveryVerified: String? = null           // phone whose security answer was just verified

    /** Seconds left in a lock-out after 5 wrong tries, else 0. */
    private fun lockLeft(a: Db.Account): Long {
        val ms = a.lockedUntil - System.currentTimeMillis()
        return if (ms > 0) ms / 1000 + 1 else 0L
    }

    private fun failedTry(phone: String, a: Db.Account): String {
        val n = a.failed + 1
        db.update(phone, android.content.ContentValues().apply {
            if (n >= MAX_TRIES) { put("failed", 0); put("locked_until", System.currentTimeMillis() + LOCK_MS) }
            else put("failed", n)
        })
        return if (n >= MAX_TRIES) t("locked").format(LOCK_MS / 1000) else t("tries_left").format(MAX_TRIES - n)
    }

    private fun resetTries(phone: String) =
        db.update(phone, android.content.ContentValues().apply { put("failed", 0); put("locked_until", 0L) })

    fun register(phone: String, name: String, village: String, password: String) {
        val u = User(phone, name.trim(), village.trim(), System.currentTimeMillis())
        db.insertUser(u, Db.hashSecret(password), -1, "")
        login(u)
    }

    /** Returns null on success, else a message to show. */
    fun signIn(phone: String, password: String): String? {
        val a = db.account(phone) ?: return t("no_account")
        lockLeft(a).let { if (it > 0) return t("locked").format(it) }
        if (!Db.verify(password, a.pass, phone)) return t("wrong_password") + ". " + failedTry(phone, a)
        resetTries(phone)
        if (!a.pass.startsWith("p2$")) db.update(phone, android.content.ContentValues().apply {   // upgrade old PIN hash
            put("pass", Db.hashSecret(password)) })
        login(a.user); return null
    }

    /**
     * Forgot password (farmer-friendly, offline): the farmer confirms the name and village used at sign-up.
     * Case, spaces and dots are ignored. 5 wrong tries → 30 s lock-out, like the password.
     */
    fun checkIdentity(phone: String, name: String, village: String): String? {
        val a = db.account(phone) ?: return t("no_account")
        lockLeft(a).let { if (it > 0) return t("locked").format(it) }
        val norm = { x: String -> x.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "") }
        val okName = norm(name) == norm(a.user.name)
        val okVillage = a.user.village.isBlank() || norm(village) == norm(a.user.village)
        if (!okName || !okVillage) return t("wrong_identity") + " " + failedTry(phone, a)
        resetTries(phone); recoveryVerified = phone; return null
    }

    /** Only works right after a correct security answer for this number. */
    fun resetPassword(phone: String, newPassword: String): Boolean {
        if (recoveryVerified != phone) return false
        db.update(phone, android.content.ContentValues().apply { put("pass", Db.hashSecret(newPassword)) })
        recoveryVerified = null
        db.account(phone)?.user?.let { login(it) }
        return true
    }

    /** "Forgot everything": removes the account and its scans from this phone so the number can sign up again. */
    fun removeAccount(phone: String) {
        db.deleteUser(phone).forEach { File(it).delete() }
        File(historyDir, "index_$phone.json").delete()
        if (user?.phone == phone) logout()
    }

    fun changePassword(old: String, new: String): String? {
        val u = user ?: return null
        val a = db.account(u.phone) ?: return null
        if (!Db.verify(old, a.pass, u.phone)) return t("wrong_password")
        db.update(u.phone, android.content.ContentValues().apply { put("pass", Db.hashSecret(new)) })
        return null
    }

    private fun login(u: User) {
        user = u; prefs.edit().putString("currentUser", u.phone).apply()
        current = null; loadHistory()
    }
    fun logout() {
        advisor?.stop(); user = null; current = null; history.clear()
        prefs.edit().remove("currentUser").apply()
    }
    fun deleteProfile() { user?.let { removeAccount(it.phone) } }
    fun clearHistory() {
        val u = user ?: return
        db.clearScans(u.phone).forEach { File(it).delete() }
        history.clear()
    }

    /** One-time move of v1/v2 data (SharedPreferences users + JSON history files) into the SQLite database. */
    private fun migrateLegacy() {
        if (prefs.getBoolean("db_migrated", false)) return
        try {
            val o = JSONObject(prefs.getString("users", "{}") ?: "{}")
            o.keys().forEach { phone ->
                val j = o.getJSONObject(phone)
                if (db.account(phone) == null)
                    db.insertUser(User(phone, j.getString("name"), j.optString("village"), j.optLong("created")),
                        j.getString("pin"), -1, "")
                val f = File(historyDir, "index_$phone.json")
                if (f.exists()) { parseLegacyHistory(f).forEach { db.insertScan(phone, it) }; f.delete() }
            }
        } catch (e: Exception) { Log.e("Uzhavan", "migration", e) }
        prefs.edit().putBoolean("db_migrated", true).remove("users").apply()
    }

    // ---- engine loading ----
    var visionReady by mutableStateOf(false); private set
    var llmDone by mutableStateOf(false); private set
    var llmReady by mutableStateOf(false); private set
    var llmError by mutableStateOf<String?>(null); private set
    var visionLoadMs by mutableStateOf(0L); private set
    var llmLoadMs by mutableStateOf(0L); private set
    private var vision: Vision? = null
    private var advisor: Advisor? = null

    // ---- current scan + advice ----
    var current by mutableStateOf<Scan?>(null); private set
    var analysing by mutableStateOf(false); private set
    var advice by mutableStateOf(""); private set
    var adviceRunning by mutableStateOf(false); private set
    var adviceFailed by mutableStateOf(false); private set
    var llmStats by mutableStateOf<Advisor.Stats?>(null); private set
    @Volatile private var adviceGen = 0

    // ---- history / benchmark / voice ----
    val history = mutableStateListOf<Scan>()
    var bench by mutableStateOf<List<Double>?>(null); private set
    var benchRunning by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    private var tts: TextToSpeech? = null
    private val historyDir = File(ctx.filesDir, "history").apply { mkdirs() }
    /** The photo being analysed, shown under the scanning animation. */
    var scanningPhoto by mutableStateOf<Bitmap?>(null); private set

    init {
        migrateLegacy()
        prefs.getString("currentUser", null)?.let { findUser(it) }?.let { user = it }
        loadHistory()
        // Start-up order matters (ExecuTorch resets its shared thread-pool whenever an LlmModule is created):
        //   1. create the vision module (no inference yet)   2. create + load Llama
        //   3. only now run the vision model for the first time, so both models use the same, final thread-pool.
        // Everything runs on ONE thread, in this order, so it can never interleave.
        scope.launch {
            val t0 = System.currentTimeMillis()
            val v = try { withContext(visionThread) { Vision(ctx) } } catch (e: Throwable) { Log.e("Uzhavan", "vision", e); null }
            val a = withContext(visionThread) { Advisor(ctx) }
            val llmMs = withContext(visionThread) { a.loadLlm() }
            val t1 = System.currentTimeMillis()
            val ok = v != null && withContext(visionThread) {
                try { v.warmUp(); true } catch (e: Throwable) { Log.e("Uzhavan", "vision warm-up", e); false }
            }
            vision = if (ok) v else null
            visionLoadMs = (System.currentTimeMillis() - t1) + (t1 - t0 - llmMs).coerceAtLeast(0)
            visionReady = true
            llmLoadMs = llmMs
            advisor = a
            llmReady = a.llmReady
            llmError = a.llmError
            llmDone = true
        }
        tts = TextToSpeech(ctx) { }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { ui { speaking = true } }
                override fun onDone(id: String?) { ui { speaking = false } }
                @Deprecated("old api") override fun onError(id: String?) { ui { speaking = false } }
            })
        }
    }

    // ---------------- scanning ----------------
    fun analyse(bmp: Bitmap, onDone: (Scan) -> Unit, onError: () -> Unit) {
        val v = vision ?: return onError()
        stopAdvice()                       // free ExecuTorch from any running Llama answer before scanning
        analysing = true
        scanningPhoto = bmp
        scope.launch {
            val started = System.currentTimeMillis()
            val r = try { withContext(visionThread) { v.analyse(bmp) } } catch (e: Throwable) {
                analysing = false; scanningPhoto = null
                Toast.makeText(ctx, t("photo_error"), Toast.LENGTH_LONG).show()
                return@launch onError()
            }
            val scan = withContext(Dispatchers.IO) {
                val id = UUID.randomUUID().toString().take(8)
                val img = File(historyDir, "$id.jpg")
                val small = scaleDown(bmp, 900)
                img.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                Scan(id, r.label, sameDiseaseConfidence(r.top3, r.confidence), r.severityPct, r.top3,
                    System.currentTimeMillis(), img.absolutePath, r.inferenceMs, r.preprocessMs, r.leafPct)
            }
            val owner = user
            if (owner != null) withContext(Dispatchers.IO) { db.insertScan(owner.phone, scan) }
            history.add(0, scan)
            // keep the scanning animation on screen long enough to be read (the model itself takes ~50 ms)
            val shown = System.currentTimeMillis() - started
            if (shown < MIN_SCAN_ANIM_MS) kotlinx.coroutines.delay(MIN_SCAN_ANIM_MS - shown)
            analysing = false; scanningPhoto = null
            open(scan)
            onDone(scan)
        }
    }

    fun open(scan: Scan) {
        current = scan
        clearVoice()
        requestAdvice()
    }

    /** (Re)generates the Llama explanation for the current scan in the current language. */
    /** Short technical reason when the AI answer could not be produced (shown small under the message). */
    var adviceError by mutableStateOf<String?>(null); private set
    /** Language the current AI answer is written in ("en" for Tamil users who asked for English). */
    var adviceLang by mutableStateOf("en"); private set

    fun requestAdvice() { requestAdvice(retry = true, genLang = lang) }
    /** Tamil users: Llama 3.2 does not support Tamil, so they can ask for the AI explanation in English. */
    fun requestAdviceInEnglish() { requestAdvice(retry = true, genLang = "en") }

    private fun requestAdvice(retry: Boolean, genLang: String) {
        val scan = current ?: return
        val a = advisor
        val gen = ++adviceGen
        a?.stop()
        advice = ""; adviceFailed = false; llmStats = null; adviceError = null; adviceLang = genLang
        if (scan.uncertain || !aiOn || genLang == "ta" || a == null || !a.llmReady) {
            adviceRunning = false
            adviceFailed = aiOn && (a == null || !a.llmReady)
            if (adviceFailed) adviceError = a?.llmError ?: "Llama model not loaded"
            return
        }
        adviceRunning = true
        val sevWord = Strings.t(scan.severityKey, "en")
        val prompt = a.buildPrompt(scan.label, sevWord, scan.confidence, genLang)
        llmThread.execute {
            if (gen != adviceGen) return@execute
            val sb = StringBuilder()
            var refused = false
            a.generate(prompt,
                onToken = { tok ->
                    if (gen == adviceGen) {
                        if (!refused) {
                            sb.append(tok)
                            val text = cleanAdvice(Advisor.PREFILL + sb.toString())   // answer was pre-started with "1."
                            if (Advisor.isRefusal(sb.toString())) {      // refusal → stop, fall back to approved guidance
                                Log.w("UzhavanLLM", "refusal: $text"); refused = true; a.stop(); sb.setLength(0)
                                ui { if (gen == adviceGen) advice = "" }
                            } else ui { if (gen == adviceGen) advice = text }
                        }
                    } else {
                        // answer no longer wanted (user left / new scan / field test). A stop() sent just before
                        // generate() started is lost, so stop again here, on Llama's own thread, every token.
                        a.stop()
                    }
                },
                onStats = { s -> ui { if (gen == adviceGen) llmStats = s } },
                onFail = { msg -> ui { if (gen == adviceGen) { adviceFailed = true; adviceError = shortError(msg) } } })
            val empty = sb.isBlank()
            if (empty && gen == adviceGen) Log.w("UzhavanLLM", "no usable answer (lang=$genLang, label=${scan.label}, retry=$retry)")
            ui {
                if (gen == adviceGen) {
                    if (empty && retry && !refused) requestAdvice(retry = false, genLang = genLang)   // one silent retry
                    else {
                        adviceRunning = false
                        if (empty) {
                            adviceFailed = true
                            if (adviceError == null) adviceError = if (refused) "model declined" else "model returned no text"
                        }
                    }
                }
            }
        }
    }

    /** Stop any running Llama generation (e.g. when leaving the result screen). */
    fun stopAdvice() {
        adviceGen++
        advisor?.stop()
        adviceRunning = false
        if (speaking) { tts?.stop(); speaking = false }
    }

    /** Keeps the useful part of a native error, e.g. "Max seq length exceeded", instead of the full runner log. */
    private fun shortError(msg: String): String {
        val key = Regex("E ([^\\[]{10,160})").find(msg)?.groupValues?.get(1)?.trim()
        return (key ?: msg.substringBefore(" | nativeLog")).take(160)
    }

    private fun cleanAdvice(raw: String) = raw.replace("**", "").replace("##", "")
        .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ").replace(Regex("\n{3,}"), "\n\n").trimStart()

    fun allLabels(): List<String> = vision?.labels ?: emptyList()

    fun guidance(label: String) = advisor?.guidance(label, lang.ifEmpty { "en" })
    fun conditionName(label: String) = guidance(label)?.name ?: Strings.conditionEn(label)
    fun cropName(label: String) = Strings.cropName(label, lang.ifEmpty { "en" })

    fun sampleNames(): List<String> = try {
        ctx.assets.list("samples")?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) || it.endsWith(".jpeg", true) }
            ?.sorted() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun sampleBitmap(name: String): Bitmap = ctx.assets.open("samples/$name").use { BitmapFactory.decodeStream(it) }

    fun loadThumb(path: String, max: Int = 400): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Exception) { null }

    // ---------------- history ----------------
    /**
     * Early blight, late blight, bacterial spot and "healthy" look the same — and are treated the same — on tomato,
     * potato and pepper (same pathogen). When the top two answers are the same condition on different crops, the
     * condition itself is certain: its confidence is the sum (e.g. 54 % tomato + 35 % potato early blight = 89 %).
     */
    private fun sameDiseaseConfidence(top: List<Pair<String, Float>>, conf: Float): Float {
        if (top.size < 2) return conf
        val (l1, p1) = top[0]; val (l2, p2) = top[1]
        val cond = { l: String -> l.substringAfter("___").lowercase().replace('_', ' ').trim() }
        val sameCondition = l1.substringBefore("___") != l2.substringBefore("___") && cond(l1) == cond(l2) &&
            !l1.startsWith("Other___") && !l2.startsWith("Other___")
        return if (sameCondition) (p1 + p2).coerceAtMost(0.99f) else conf
    }

    fun delete(scan: Scan) {
        history.remove(scan)
        File(scan.imagePath).delete()
        db.deleteScan(scan.id)
    }

    private fun loadHistory() {
        history.clear()
        val u = user ?: return
        history.addAll(db.scans(u.phone))
    }

    private fun parseLegacyHistory(f: File): List<Scan> = try {
        val arr = JSONArray(f.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val t = o.getJSONArray("top3")
            Scan(o.getString("id"), o.getString("label"), o.getDouble("conf").toFloat(),
                o.getDouble("sev").toFloat(),
                List(t.length()) { k -> t.getJSONObject(k).let { j -> j.getString("l") to j.getDouble("p").toFloat() } },
                o.getLong("time"), o.getString("img"), o.optLong("inf"), o.optLong("pre"),
                o.optDouble("leaf", 100.0).toFloat())
        }
    } catch (e: Exception) { emptyList() }

    // ---------------- voice / share / call ----------------
    fun speak(text: String, toggle: Boolean = true) {
        val engine = tts ?: return
        if (speaking && toggle) { engine.stop(); speaking = false; return }
        val loc = when (lang) { "ta" -> Locale("ta", "IN"); "hi" -> Locale("hi", "IN"); else -> Locale("en", "IN") }
        engine.setSpeechRate(speechRate)
        val res = engine.setLanguage(loc)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(ctx, t("voice_missing"), Toast.LENGTH_LONG).show()
            return
        }
        engine.speak(text.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_FLUSH, null, "advice")
    }

    // ---------------- voice questions (speech → answer → read aloud) ----------------
    var voiceQuestion by mutableStateOf<String?>(null); private set
    var voiceAnswer by mutableStateOf(""); private set
    var voiceRunning by mutableStateOf(false); private set
    var voiceFromAi by mutableStateOf(false); private set

    /** BCP-47 tag for Android's speech recogniser in the app language. */
    fun speechTag() = when (lang) { "ta" -> "ta-IN"; "hi" -> "hi-IN"; else -> "en-IN" }

    /**
     * Answers a spoken question about the current scan. Safety rules:
     *  - questions about medicine / spraying → the verified treatment text, word for word (never AI-made)
     *  - Tamil (not supported by Llama 3.2) → the matching verified Tamil section
     *  - English / Hindi → Llama answers in 2–4 sentences from the verified care plan; falls back to verified text
     * The answer is then read aloud in the farmer's language.
     */
    fun askByVoice(question: String) {
        val scan = current ?: return
        val g = guidance(scan.label) ?: return
        voiceQuestion = question; voiceAnswer = ""; voiceFromAi = false
        val topic = Advisor.topicOf(question)
        val verified = when (topic) {
            "treatment" -> g.treatment ?: g.actions.joinToString(". ")
            "prevention" -> g.prevention
            "cause" -> g.cause
            "symptoms" -> g.symptoms
            else -> g.actions.joinToString(". ")
        }
        if (topic == "severity") {          // answered exactly from this scan's own measurement
            val ans = t("sev_answer").format(t(scan.severityKey), scan.severityPct.roundToInt()) +
                if (scan.severityKey == "severe") " " + t("urgent") else ""
            voiceAnswer = ans; speak(ans, toggle = false); return
        }
        val a = advisor
        if (topic == "treatment" || lang == "ta" || scan.uncertain || !aiOn || a == null || !a.llmReady || adviceRunning) {
            voiceAnswer = verified; speak(verified, toggle = false); return
        }
        voiceRunning = true
        val prompt = a.buildQuestionPrompt(scan.label, question, lang)
        val myScan = scan.id
        llmThread.execute {
            val sb = StringBuilder()
            var refused = false
            a.generate(prompt,
                onToken = { tok ->
                    if (current?.id != myScan) a.stop()
                    else if (!refused) {
                        sb.append(tok)
                        if (Advisor.isRefusal(sb.toString())) { refused = true; a.stop(); sb.setLength(0) }
                        else { val text = cleanAdvice(sb.toString()); ui { voiceAnswer = text; voiceFromAi = true } }
                    }
                },
                onStats = { },
                onFail = { msg -> Log.w("UzhavanLLM", "voice answer failed: $msg") }, short = true)
            val text = cleanAdvice(sb.toString())
            ui {
                voiceRunning = false
                if (current?.id == myScan) {
                    val answer = if (text.isBlank()) verified else text
                    voiceAnswer = answer; voiceFromAi = text.isNotBlank()
                    speak(answer, toggle = false)
                }
            }
        }
    }

    fun clearVoice() { voiceQuestion = null; voiceAnswer = ""; voiceRunning = false }

    fun shareIntent(scan: Scan, text: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(ctx, "com.cropadvisor.fileprovider", File(scan.imagePath))
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, t("share"))
    }

    fun callIntent() = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$KISAN_HELPLINE"))

    // ---------------- performance ----------------
    fun runBenchmark() {
        val v = vision ?: return
        benchRunning = true
        scope.launch {
            bench = try { withContext(visionThread) { v.benchmark(5); v.benchmark(30) } } catch (e: Throwable) { null }
            benchRunning = false
        }
    }

    // ---------------- on-device field-photo test ----------------
    // Photos go in  /sdcard/Android/data/com.cropadvisor/files/eval/<label folder>/*.jpg  (training/prepare_eval.py
    // does this). Every photo runs through the same Vision.analyse() as a real scan; results → eval_results.csv.
    var evalRunning by mutableStateOf(false); private set
    var evalProgress by mutableStateOf(""); private set
    var evalSummary by mutableStateOf<List<String>?>(null); private set
    var evalSaved by mutableStateOf(false); private set
    data class EvalStats(val n: Int, val ok: Int, val cropOk: Int, val supN: Int, val supOk: Int,
                         val conf: Int, val confOk: Int, val avgInfMs: Long, val avgPreMs: Long,
                         val perClass: List<Triple<String, Int, Int>>)      // label, correct, total
    var evalStats by mutableStateOf<EvalStats?>(null); private set
    // Android 16 can hide adb-pushed files in Android/data from the app, so /data/local/tmp/eval is a fallback
    // (same place the Llama model is read from).
    fun evalDir(): File? = listOfNotNull(ctx.getExternalFilesDir("eval"), File("/data/local/tmp/eval"))
        .firstOrNull { d -> d.listFiles()?.any { it.isDirectory && !it.list().isNullOrEmpty() } == true }
        ?: ctx.getExternalFilesDir("eval")

    fun runEval() {
        val v = vision ?: return
        val root = evalDir() ?: return
        val outDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        stopAdvice()
        evalSaved = false; evalStats = null
        evalRunning = true; evalSummary = null; evalProgress = ""
        scope.launch {
            val lines = try { withContext(visionThread) {
                val key = { s: String -> s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_') }
                val byKey = v.labels.associateBy(key)
                val exts = setOf("jpg", "jpeg", "png", "webp", "bmp")
                val skipped = mutableListOf<String>()
                val photos = (root.listFiles()?.filter { it.isDirectory } ?: emptyList()).sortedBy { it.name }.flatMap { d ->
                    val truth = byKey[key(d.name)] ?: run { skipped += d.name; return@flatMap emptyList() }
                    (d.listFiles() ?: emptyArray()).filter { it.extension.lowercase() in exts }.map { it to truth }
                }
                if (photos.isEmpty()) return@withContext listOf("No photos found in ${root.absolutePath}",
                    "Run training/prepare_eval.py on the laptop first.")
                val csv = StringBuilder("file,truth,pred,confidence,correct,crop_correct,leaf_pct,inference_ms,preprocess_ms\n")
                val per = linkedMapOf<String, IntArray>()       // truth -> [correct, total]
                var ok = 0; var cropOk = 0; var conf = 0; var confOk = 0; var inf = 0L; var pre = 0L
                photos.forEachIndexed { i, (f, truth) ->
                    ui { evalProgress = "${i + 1} / ${photos.size}" }
                    Log.i("UzhavanEval", "${i + 1} ${f.parentFile?.name}/${f.name} (${f.length() / 1024} KB)")
                    val bmp = decodeSmall(f) ?: return@forEachIndexed
                    val r = try { v.analyse(bmp) } catch (e: Throwable) {
                        Log.e("UzhavanEval", "failed on ${f.name}", e); return@forEachIndexed
                    }
                    val hit = r.label == truth
                    val cropHit = r.label.substringBefore("___") == truth.substringBefore("___")
                    if (hit) ok++; if (cropHit) cropOk++
                    if (r.confidence >= 0.60f) { conf++; if (hit) confOk++ }
                    inf += r.inferenceMs; pre += r.preprocessMs
                    per.getOrPut(truth) { IntArray(2) }.let { it[0] += if (hit) 1 else 0; it[1]++ }
                    csv.append("\"${f.parentFile?.name}/${f.name}\",$truth,${r.label},${"%.3f".format(r.confidence)},$hit,$cropHit," +
                        "${"%.1f".format(r.leafPct)},${r.inferenceMs},${r.preprocessMs}\n")
                }
                val n = per.values.sumOf { it[1] }.coerceAtLeast(1)
                val sup = per.filterKeys { !it.startsWith("Other___") }.values
                val supN = sup.sumOf { it[1] }; val supOk = sup.sumOf { it[0] }
                val stats = EvalStats(n, ok, cropOk, supN, supOk, conf, confOk, inf / n, pre / n,
                    per.map { (l, c) -> Triple(l, c[0], c[1]) })
                ui { evalStats = stats }
                val pct = { a: Int, b: Int -> "%.1f%%".format(100.0 * a / b.coerceAtLeast(1)) }
                val out = mutableListOf(
                    "Photos tested: $n",
                    "Disease correct (top-1): ${pct(ok, n)}  ($ok/$n)",
                    "Disease correct, 5 supported crops: ${pct(supOk, supN)}  ($supOk/$supN)",
                    "Crop correct: ${pct(cropOk, n)}",
                    "Confident (≥60%): ${pct(conf, n)} of photos — of these ${pct(confOk, conf)} correct",
                    "Avg time per photo: ${inf / n} ms model (3 views) + ${pre / n} ms preprocess",
                    "",
                ) + per.map { (l, c) -> "${pct(c[0], c[1]).padStart(6)}  (${c[0]}/${c[1]})  ${Vision.pretty(l)}" } +
                    (if (skipped.isNotEmpty()) listOf("", "Skipped folders (no matching label): ${skipped.joinToString()}") else emptyList())
                File(outDir, "eval_results.csv").writeText(csv.toString())
                File(outDir, "eval_summary.txt").writeText(out.joinToString("\n"))
                ui { evalSaved = true }
                listOf("Photos from: ${root.absolutePath}") + out
            } } catch (e: Throwable) { Log.e("UzhavanEval", "eval failed", e); listOf("Test stopped: ${e.message}") }
            evalSummary = lines
            evalRunning = false
        }
    }

    private fun decodeSmall(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var s = 1
        while (minOf(o.outWidth, o.outHeight) / (s * 2) >= 512) s *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) { null }

    fun deviceInfo(): List<Pair<String, String>> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val pssMb = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        val llmSize = advisor?.modelFile?.length()?.let { "%.0f MB".format(it / 1e6) } ?: "—"
        val visSize = File(ctx.filesDir, "crop_mnv3.pte").takeIf { it.exists() }?.length()?.let { "%.0f MB".format(it / 1e6) } ?: "—"
        // why the app last closed — lets a crash be diagnosed from a screenshot, without a laptop
        val lastExit = if (Build.VERSION.SDK_INT >= 30) try {
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull()?.let { e ->
                val why = when (e.reason) {
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                    android.app.ApplicationExitInfo.REASON_CRASH -> "app crash"
                    android.app.ApplicationExitInfo.REASON_ANR -> "not responding"
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
                    android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by user"
                    android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "app updated"
                    else -> "normal (${e.reason})"
                }
                why + " · " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US).format(java.util.Date(e.timestamp))
            } ?: "—"
        } catch (e: Throwable) { "—" } else "—"
        return listOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Last app exit" to lastExit,
            "SoC" to soc,
            "CPU cores" to "${Runtime.getRuntime().availableProcessors()} (Arm ${Build.SUPPORTED_ABIS.firstOrNull()})",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "RAM free / total" to "%.1f / %.1f GB".format(mi.availMem / 1e9, mi.totalMem / 1e9),
            "App memory (PSS)" to "$pssMb MB",
            "Battery" to "$level%  ·  ${"%.1f".format(temp)} °C",
            "Vision model" to "MobileNetV3-L FP32 · $visSize",
            "Llama model" to "Llama 3.2 1B Instruct 4-bit · $llmSize",
        )
    }

    fun close() {
        advisor?.stop(); tts?.shutdown()
    }

    companion object {
        const val CONF_THRESHOLD = 0.60f
        const val MIN_LEAF_PCT = 5f
        const val MAX_TRIES = 5                 // wrong passwords / answers before a short lock-out
        const val LOCK_MS = 30_000L
        const val MIN_SCAN_ANIM_MS = 1100L
        const val MIN_PASSWORD = 4              // a 4-digit PIN is fine for farmers

        @Volatile private var instance: AppState? = null
        /** One engine for the whole app process, so models are not reloaded when the screen is recreated. */
        fun get(ctx: Context): AppState = instance ?: synchronized(this) {
            instance ?: AppState(ctx.applicationContext).also { instance = it }
        }
        const val KISAN_HELPLINE = "18001801551"

        fun scaleDown(b: Bitmap, max: Int): Bitmap {
            val m = maxOf(b.width, b.height)
            if (m <= max) return b
            val s = max.toFloat() / m
            return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        }
    }
}
