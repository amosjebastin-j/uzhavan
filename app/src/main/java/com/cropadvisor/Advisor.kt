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
class Advisor(ctx: Context) {

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
        if (guard.exists() && guard.readText() == stamp) {
            llmError = "Llama crashed last time — switched off (re-push the model to retry)"
            return 0
        }
        guard.delete()
        try {
            modelFile = File(dir, MODEL)
            llm = LlmModule(LlmModule.MODEL_TYPE_TEXT, "$dir/$MODEL", "$dir/$TOKENIZER", 0.0f, emptyList())
            synchronized(ET_LOCK) { llm!!.load() }
        } catch (e: Throwable) {
            llmError = "Llama load failed: ${e.message}"
            llm = null
        }
        return System.currentTimeMillis() - t0
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

    private fun factsEn(label: String): String {
        val g = guidance(label, "en") ?: return ""
        return buildString {
            append("Cause: ${g.cause}\nSigns: ${g.symptoms}\nDo now: ${g.actions.joinToString("; ")}\n")
            g.treatment?.let { append("Treatment: $it\n") }
            append("Prevention: ${g.prevention}")
        }
    }

    /** Llama 3 chat-format prompt grounded on the KB entry. */
    fun buildPrompt(label: String, severity: String, confidence: Float, lang: String): String {
        val langLine = if (lang == "hi") "Write the whole answer in simple Hindi (Devanagari script)."
                       else "Write the answer in simple English."
        val system = "You are a friendly agricultural extension assistant for small farmers in India. " +
            "Use ONLY the approved guidance provided. Never add a chemical name or dose that is not in it. " +
            "Answer in at most 5 short numbered steps, starting with the most urgent. $langLine"
        val user = "Crop: ${Strings.cropName(label, "en")}\nCondition: ${guidance(label, "en")?.name}\n" +
            "Severity: $severity\nApproved guidance:\n${factsEn(label)}\n\nWhat should the farmer do now?"
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
    }

    /** Blocking; call from the single LLM worker thread. */
    fun generate(prompt: String, onToken: (String) -> Unit, onStats: (Stats) -> Unit, onFail: (String) -> Unit) {
        val m = llm ?: return onFail(llmError ?: "Llama not loaded")
        modelFile?.let { f -> try { guard.writeText("${f.length()}:${f.lastModified()}") } catch (_: Exception) {} }
        try {
            synchronized(ET_LOCK) { m.generate(prompt, SEQ_LEN, object : LlmCallback {
                override fun onResult(result: String) {
                    if (result.startsWith("<|")) return            // hide special tokens like <|eot_id|>
                    onToken(result)
                }
                override fun onStats(stats: String) { parseStats(stats)?.let(onStats) }
                override fun onError(errorCode: Int, message: String) { onFail("$errorCode: $message") }
            }, false) }
        } catch (e: Throwable) {
            onFail(e.message ?: "generation failed")
        } finally {
            guard.delete()
        }
    }

    fun stop() { try { llm?.stop() } catch (_: Throwable) {} }

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
    }
}
