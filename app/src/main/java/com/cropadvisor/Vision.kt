package com.cropadvisor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import org.json.JSONArray
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.exp

/**
 * ExecuTorch's XNNPACK thread-pool is shared by all models in the process; running the vision model and Llama
 * at the same time crashes natively (SIGSEGV). Every ExecuTorch call goes through this lock.
 * A scan has priority: while it waits, it keeps asking Llama to stop ([preempt]) until the runtime is free.
 */
object EtLock {
    private val lock = java.util.concurrent.locks.ReentrantLock()
    @Volatile var preempt: (() -> Unit)? = null

    fun <T> run(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }

    fun <T> runFirst(block: () -> T): T {
        val t0 = SystemClock.elapsedRealtime()
        while (!lock.tryLock(250, java.util.concurrent.TimeUnit.MILLISECONDS)) preempt?.invoke()
        val waited = SystemClock.elapsedRealtime() - t0
        if (waited > 300) android.util.Log.w("UzhavanVision", "waited $waited ms for Llama to release the runtime")
        try { return block() } finally { lock.unlock() }
    }
}

/** Vision lane: MobileNetV3 (ExecuTorch + XNNPACK on Arm CPU) + colour-based severity estimate. */
class Vision(ctx: Context) {

    data class Result(
        val label: String,
        val confidence: Float,
        val top3: List<Pair<String, Float>>,
        val severityPct: Float,
        val inferenceMs: Long,
        val preprocessMs: Long,
        val leafPct: Float,
    )

    val labels: List<String>
    private val module: Module

    init {
        // Only creates the native module. The program/XNNPACK runtime is built lazily on the first forward(),
        // which must happen AFTER Llama's LlmModule has been created (see warmUp / AppState start-up order).
        val pte = copyAsset(ctx, "crop_mnv3.pte")
        module = Module.load(pte.absolutePath)
        val arr = JSONArray(ctx.assets.open("labels.json").bufferedReader().readText())
        labels = List(arr.length()) { arr.getString(it) }
    }

    /**
     * First run: builds the XNNPACK runtime and makes the first real photo as fast as the rest.
     * ROOT-CAUSE NOTE: creating an ExecuTorch LlmModule resets the process-wide XNNPACK thread-pool. A model whose
     * runtime was built before that reset keeps a pointer to the destroyed pool → random native crashes or endless
     * hangs on later scans. So this is called only after Llama has been created and loaded.
     */
    fun warmUp() {
        EtLock.run { module.forward(EValue.from(Tensor.fromBlob(FloatArray(3 * SIZE * SIZE), SHAPE))) }
    }

    fun analyse(src: Bitmap): Result {
        val t0 = SystemClock.elapsedRealtime()
        val crop = centerCrop(src)
        // 3 views: centre crop, whole frame, mirrored centre crop -> averaged (steadier on real photos)
        val whole = Bitmap.createScaledBitmap(src, SIZE, SIZE, true)
        val mirror = Bitmap.createBitmap(crop, 0, 0, SIZE, SIZE, android.graphics.Matrix().apply { preScale(-1f, 1f) }, false)
        val inputs = listOf(crop, whole, mirror).map { toTensor(it) }
        val probs = FloatArray(labels.size)
        val (t1, t2) = EtLock.runFirst {
            val s = SystemClock.elapsedRealtime()
            for (inp in inputs) {
                val p = softmax(module.forward(EValue.from(inp))[0].toTensor().dataAsFloatArray)
                for (i in probs.indices) probs[i] += p[i] / inputs.size
            }
            s to SystemClock.elapsedRealtime()
        }

        val order = probs.indices.sortedByDescending { probs[it] }
        val top3 = order.take(3).map { labels[it] to probs[it] }
        val best = labels[order[0]]
        val (leafPct, lesionPct) = leafAndLesion(crop)
        val sev = if (best.endsWith("healthy", ignoreCase = true) || best.startsWith("Other___")) 0f else lesionPct
        return Result(best, probs[order[0]], top3, sev, t2 - t1, t1 - t0, leafPct)
    }

    /** Runs the model n times on a fixed input; returns per-run latencies in ms. */
    fun benchmark(n: Int): List<Double> {
        val input = Tensor.fromBlob(FloatArray(3 * SIZE * SIZE) { (it % 255) / 255f }, SHAPE)
        return EtLock.run {
            List(n) {
                val t = System.nanoTime()
                module.forward(EValue.from(input))
                (System.nanoTime() - t) / 1e6
            }
        }
    }

    /** Resize shorter side to 256, centre-crop 224 (same as training eval transform). */
    private fun centerCrop(src: Bitmap): Bitmap {
        val scale = 256f / minOf(src.width, src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(SIZE)
        val h = (src.height * scale).toInt().coerceAtLeast(SIZE)
        val resized = Bitmap.createScaledBitmap(src, w, h, true)
        return Bitmap.createBitmap(resized, (w - SIZE) / 2, (h - SIZE) / 2, SIZE, SIZE)
    }

    /** ImageNet normalisation, NCHW float32. */
    private fun toTensor(bmp: Bitmap): Tensor {
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val out = FloatArray(3 * SIZE * SIZE)
        val plane = SIZE * SIZE
        for (i in px.indices) {
            val c = px[i]
            out[i] = (((c shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[plane + i] = (((c shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * plane + i] = ((c and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return Tensor.fromBlob(out, SHAPE)
    }

    /**
     * Returns (leaf % of the image, lesion % of the leaf).
     *  - green pixels  = healthy tissue (hue 65–170°)
     *  - lesion pixels = yellow / brown / dark spots
     *  - the leaf area of each row is the span between its first and last green pixel,
     *    so soil or background outside the leaf is not counted as damage.
     * Simple, explainable heuristic — documented as a limitation in the report.
     */
    private fun leafAndLesion(bmp: Bitmap): Pair<Float, Float> {
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val hsv = FloatArray(3)
        val green = BooleanArray(px.size)
        val lesion = BooleanArray(px.size)
        for (i in px.indices) {
            Color.colorToHSV(px[i], hsv)
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
            if (s >= 0.18f && v >= 0.18f && h in 65f..170f) green[i] = true
            else if ((s >= 0.15f && v >= 0.12f && h < 65f) || (v in 0.08f..0.30f && s >= 0.10f)) lesion[i] = true
        }
        var leaf = 0; var bad = 0
        for (y in 0 until SIZE) {
            var first = -1; var last = -1
            for (x in 0 until SIZE) if (green[y * SIZE + x]) { if (first < 0) first = x; last = x }
            if (first < 0) continue
            for (x in first..last) {
                val i = y * SIZE + x
                if (green[i]) leaf++ else if (lesion[i]) { leaf++; bad++ }
            }
        }
        val leafPct = 100f * leaf / px.size
        val lesionPct = if (leaf < 800) 0f else 100f * bad / leaf
        return leafPct to lesionPct
    }

    private fun softmax(x: FloatArray): FloatArray {
        val m = x.max()
        val e = FloatArray(x.size) { exp(x[it] - m) }
        val s = e.sum()
        return FloatArray(x.size) { e[it] / s }
    }

    companion object {
        const val SIZE = 224
        val SHAPE = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        fun severityLevel(pct: Float): String = when {
            pct <= 0f -> "None"
            pct < 10f -> "Mild"
            pct < 25f -> "Moderate"
            else -> "Severe"
        }

        /** "Tomato___Early_blight" -> "Tomato — Early blight" */
        fun pretty(label: String): String {
            val parts = label.split("___")
            val crop = parts[0].replace("Pepper,_bell", "Pepper").replace("Corn_(maize)", "Corn (maize)")
                .replace('_', ' ')
            val cond = parts.getOrElse(1) { "" }.replace('_', ' ').trim()
                .replaceFirstChar { it.uppercase() }
            return "$crop — $cond"
        }

        fun copyAsset(ctx: Context, name: String): File {
            val out = File(ctx.filesDir, name)
            val len = ctx.assets.openFd(name).use { it.length }
            val installed = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
            if (!out.exists() || out.length() != len || out.lastModified() < installed) {   // new model after an update
                ctx.assets.open(name).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
            return out
        }
    }
}
