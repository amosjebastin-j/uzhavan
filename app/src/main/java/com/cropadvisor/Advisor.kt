package com.cropadvisor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File

/**
 * Advisory lane: offline knowledge base (approved guidance) + Llama 3.2 1B via ExecuTorch.
 * Llama only rephrases/explains the KB facts; it never invents treatments or doses.
 */
class Advisor(private val ctx: Context) {

    data class Stats(val promptTokens: Int, val genTokens: Int, val ttftMs: Long,
                     val prefillTps: Double, val decodeTps: Double)

    data class Guidance(val type: String, val name: String, val cause: String, val symptoms: String,
                        val actions: List<String>, val treatment: String?, val prevention: String)

    private val kb = JSONObject(ctx.assets.open("kb.json").bufferedReader().readText())
    // Written just before Llama runs, deleted after. If it is still there at start-up, Llama crashed the app
    // last time -> keep Llama off for that same model file so the app never crash-loops.
    private val guard = File(ctx.filesDir, "llm_running")
    private var llm: LlmModule? = null
    var llmError: String? = null
        private set
    var modelFile: File? = null
        private set

    // Looked for in this order: app's own folder (adb push to /sdcard/Android/data/...), then /data/local/tmp
    private val dirs = listOfNotNull(ctx.getExternalFilesDir(null)?.absolutePath, "/data/local/tmp/llama")

    fun loadLlm(): Long {
        val t0 = System.currentTimeMillis()
        val dir = dirs.firstOrNull { File(it, MODEL).canRead() && File(it, TOKENIZER).canRead() }
        if (dir == null) {
            llmError = "Llama files not found in ${dirs.joinToString(" or ")}"
            return 0
        }
        val stamp = File(dir, MODEL).let { "${it.length()}:${it.lastModified()}" }
        // Only a real native crash during generation switches Llama off. A reinstall, swipe-away or
        // low-memory kill while Llama was writing also leaves the guard behind — those are ignored.
        if (guard.exists() && guard.readText() == stamp && lastExitWasNativeCrash()) {
            llmError = "Llama crashed last time — switched off (re-push the model to retry)"
            return 0
        }
        guard.delete()
        try {
            modelFile = File(dir, MODEL)
            // `llm` is published only AFTER load() finishes: nothing (stop, generate) can touch a half-loaded model
            val m = LlmModule(LlmModule.MODEL_TYPE_TEXT, "$dir/$MODEL", "$dir/$TOKENIZER", 0.0f, emptyList())
            EtLock.run { m.load() }
            llm = m
        } catch (e: Throwable) {
            llmError = "Llama load failed: ${e.message}"
            llm = null
        }
        return System.currentTimeMillis() - t0
    }

    private fun lastExitWasNativeCrash(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 30) return true            // no exit history: stay cautious
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull()?.reason ==
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
        } catch (e: Throwable) { true }
    }

    val llmReady get() = llm != null

    fun guidance(label: String, lang: String): Guidance? {
        val e = kb.optJSONObject(label) ?: return null
        val l = e.optJSONObject(lang) ?: e.getJSONObject("en")
        val acts = l.optJSONArray("actions") ?: JSONArray()
        return Guidance(
            type = e.optString("type", "disease"),
            name = l.optString("name", Strings.conditionEn(label)),
            cause = l.optString("cause"), symptoms = l.optString("symptoms"),
            actions = List(acts.length()) { acts.getString(it) },
            treatment = l.optString("treatment").ifBlank { null },
            prevention = l.optString("prevention"),
        )
    }

    /** Care plan in the answer language: Llama only re-explains verified text, it never translates on its own. */
    private fun facts(label: String, lang: String): String {
        val g = guidance(label, lang) ?: return ""
        // The chemical treatment is NOT given to Llama: the app shows it word-for-word from kb.json in its own card.
        // Llama only explains the non-chemical care plan, so it can never alter a product or dose
        // (and Llama 3.2's own safety training refuses to talk about pesticides anyway).
        return "Cause: ${g.cause}\nSigns: ${g.symptoms}\nDo now: ${g.actions.joinToString("; ")}\n" +
            "Prevention: ${g.prevention}"
    }

    /** Llama 3 chat-format prompt grounded on the KB entry. */
    fun buildPrompt(label: String, severity: String, confidence: Float, lang: String): String {
        val hi = lang == "hi"
        val langLine = if (hi) "Write the whole answer only in simple Hindi (Devanagari script), using the words of the care plan."
                       else "Write the answer in simple English."
        val system = "You are a friendly farm advisor in India. Talk directly to the farmer as \"you\". " +
            "Use only the facts in the farmer's care plan. Reply with at most 5 short numbered steps, " +
            "most urgent first, each one sentence explaining what to do and why. No introduction, no closing. " +
            "Do not mention any spray, product or dose. $langLine"
        val user = "My ${Strings.cropName(label, "en").lowercase()} plant has ${guidance(label, "en")?.name} " +
            "(${severity.lowercase()}).\nMy care plan${if (hi) " (in Hindi)" else ""}:\n${facts(label, if (hi) "hi" else "en")}" +
            "\n\nWhat should I do now?${if (hi) " Answer in Hindi." else ""}"
        // The answer is pre-started with "1." so the small model goes straight into the steps.
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n$PREFILL"
    }

    /** Prompt for a farmer's spoken question (English / Hindi), grounded in the verified care plan. */
    fun buildQuestionPrompt(label: String, question: String, lang: String): String {
        val hi = lang == "hi"
        val system = "You are a farm advisor in India. Answer the farmer's question directly in at most 3 short, " +
            "simple sentences, talking to the farmer as \"you\". No greeting, no reassurance, no lists, no questions " +
            "back. Use only the facts in the care plan; if the plan does not answer it, say so and suggest calling " +
            "the Kisan helpline 1800-180-1551. Do not mention any spray, product or dose. " +
            if (hi) "Answer only in simple Hindi (Devanagari script)." else "Answer in simple English."
        val user = "My ${Strings.cropName(label, "en").lowercase()} plant has ${guidance(label, "en")?.name}.\n" +
            "My care plan${if (hi) " (in Hindi)" else ""}:\n${facts(label, if (hi) "hi" else "en")}\n\n" +
            "My question: ${question.take(300)}${if (hi) " Answer in Hindi." else ""}"
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
    }

    @Volatile private var generating = false

    init {
        // a scan waiting for the runtime keeps asking a running answer to stop (never touches Llama while it loads)
        EtLock.preempt = { if (generating) stop() }
    }

    /** Blocking; call from the single LLM worker thread. */
    fun generate(prompt: String, onToken: (String) -> Unit, onStats: (Stats) -> Unit, onFail: (String) -> Unit,
                 short: Boolean = false) {
        val m = llm ?: return onFail(llmError ?: "Llama not loaded")
        modelFile?.let { f -> try { guard.writeText("${f.length()}:${f.lastModified()}") } catch (_: Exception) {} }
        // Hard cap on answer length (seq_len = prompt + new tokens), so an answer always ends in seconds even if
        // stop() or the end-of-turn token is missed. Hindi needs ~3× more tokens than English for the same text.
        // rough token estimate: ~3.5 Latin characters per token, ~1.5 Devanagari characters per token
        val promptTokens = prompt.count { it.code < 128 } / 3 + prompt.count { it.code >= 128 } * 2 / 3 + 16
        val hiOut = prompt.contains("in Hindi")
        val maxNew = if (short) (if (hiOut) 200 else 110) else (if (hiOut) MAX_NEW_HI else MAX_NEW_EN)
        val seqLen = minOf(SEQ_LEN, promptTokens + maxNew)
        var ended = false; var count = 0
        val t0 = System.currentTimeMillis()
        try {
            generating = true
            EtLock.run {
            // Each answer is independent: clear Llama's KV-cache first. Without this the runner keeps appending
            // to one long conversation and fails once 1024 tokens are used ("Max seq length exceeded").
            resetContext(m)
            m.generate(prompt, seqLen, object : LlmCallback {
                override fun onResult(result: String) {
                    count++
                    // end of the assistant's turn: stop at once, so no "assistant…" second turn leaks into the text
                    if (END_TOKENS.any { result.contains(it) }) { if (!ended) { ended = true; m.stop() }; return }
                    if (ended || result.startsWith("<|")) return    // hide any other special token
                    onToken(result)
                }
                override fun onStats(stats: String) { parseStats(stats)?.let(onStats) }
                override fun onError(errorCode: Int, message: String) {
                    android.util.Log.e("UzhavanLLM", "generate error $errorCode: $message"); onFail("$errorCode: $message")
                }
            }, false) }
        } catch (e: Throwable) {
            android.util.Log.e("UzhavanLLM", "generate failed", e)
            onFail(e.message ?: "generation failed")
        } finally {
            generating = false
            guard.delete()
            android.util.Log.i("UzhavanLLM", "answer: $count tokens in ${System.currentTimeMillis() - t0} ms " +
                "(cap $seqLen, ended=$ended)")
        }
    }

    /** LlmModule.resetContext() (ExecuTorch ≥ 0.6), called reflectively so older runtimes still build and run. */
    private fun resetContext(m: LlmModule) {
        try { m.javaClass.getMethod("resetContext").invoke(m) }
        catch (e: Throwable) { android.util.Log.w("UzhavanLLM", "resetContext not available: $e") }
    }

    /** Stops a running answer. Never touches the native runner unless an answer is actually being generated
     *  (calling stop() on an idle or loading runner crashed natively). */
    fun stop() { if (generating) try { llm?.stop() } catch (_: Throwable) {} }

    private fun parseStats(json: String): Stats? = try {
        val j = JSONObject(json)
        val start = j.getLong("inference_start_ms"); val end = j.getLong("inference_end_ms")
        val promptEnd = j.getLong("prompt_eval_end_ms"); val first = j.getLong("first_token_ms")
        val pt = j.getInt("prompt_tokens"); val gt = j.getInt("generated_tokens")
        Stats(pt, gt, first - start, pt * 1000.0 / maxOf(1, promptEnd - start),
            gt * 1000.0 / maxOf(1, end - promptEnd))
    } catch (e: Exception) { null }

    companion object {
        const val MODEL = "llama3_2_1b_instruct.pte"
        const val TOKENIZER = "tokenizer.model"
        const val SEQ_LEN = 1024                  // must be <= max_context_length used at export
        const val PREFILL = "1."
        const val MAX_NEW_EN = 260               // ≈ 5 short steps in English
        const val MAX_NEW_HI = 420               // Devanagari uses more tokens per word
        val END_TOKENS = listOf("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>", "<|start_header_id|>")
        private val REFUSAL = Regex("^\\s*(I can(no|')t|I cannot|I'm sorry|I am sorry|I'm not able|I am not able|" +
            "मैं .{0,30}(नहीं कर सकता|असमर्थ))", RegexOption.IGNORE_CASE)
        /** What a spoken question is about, from keywords in English, Tamil and Hindi. */
        fun topicOf(q: String): String {
            val s = q.lowercase()
            fun has(vararg w: String) = w.any { s.contains(it) }
            return when {
                has("medicine", "spray", "pesticide", "fungicide", "insecticide", "chemical", "dose", "cure", "treat",
                    "மருந்து", "தெளி", "பூச்சிக்கொல்லி", "சிகிச்சை", "दवा", "दवाई", "छिड़", "स्प्रे", "कीटनाशक",
                    "इलाज", "उपचार") -> "treatment"
                has("severe", "serious", "how bad", "how much", "danger", "spread how", "stage", "தீவிர", "எவ்வளவு",
                    "ஆபத்து", "गंभीर", "कितना", "खतरा") -> "severity"
                has("prevent", "avoid", "next time", "next season", "future", "தடு", "தவிர்", "அடுத்த",
                    "बचाव", "रोक", "अगली बार", "आगे से") -> "prevention"
                has("why", "cause", "reason", "how did", "ஏன்", "காரணம்", "எப்படி வந்த", "क्यों", "कारण", "वजह") -> "cause"
                has("sign", "symptom", "look like", "identify", "அறிகுறி", "எப்படி இருக்கும்", "लक्षण", "पहचान") -> "symptoms"
                else -> "actions"
            }
        }

        /** Llama 3.2's safety training sometimes refuses farm questions; such an answer is useless to a farmer. */
        fun isRefusal(text: String) = REFUSAL.containsMatchIn(text.take(120))
    }
}
