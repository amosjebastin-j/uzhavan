package com.cropadvisor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.cropadvisor.AppState
import androidx.compose.ui.layout.onSizeChanged

@Composable
fun CameraScreen(state: AppState, onBack: () -> Unit, onCaptured: (Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { safeDecode(ctx, it)?.let(onCaptured) }
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Palette.ForestDeep).statusBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.CameraAlt, null, tint = Palette.Mint, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(state.t("cam_perm"), color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))
            BigButton(state.t("allow"), Icons.Rounded.CameraAlt, { permission.launch(Manifest.permission.CAMERA) },
                bg = Palette.Mint)
            Spacer(Modifier.height(10.dp))
            BigButton(state.t("gallery"), Icons.Rounded.PhotoLibrary, { picker.launch("image/*") },
                bg = Color.White.copy(alpha = 0.12f))
        }
        return
    }

    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                androidx.camera.core.resolutionselector.ResolutionSelector.Builder().setResolutionStrategy(
                    androidx.camera.core.resolutionselector.ResolutionStrategy(android.util.Size(1280, 960),
                        androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                ).build()
            ).build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .onSizeChanged { viewSize = it }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PreviewView(c).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(this.surfaceProvider) }
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }, ContextCompat.getMainExecutor(c))
                }
            },
        )

        // dimmed surroundings + leaf frame with corner brackets
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width * FRAME_W
            val left = (size.width - side) / 2
            val top = size.height * FRAME_Y - side / 2
            val dim = Color.Black.copy(alpha = 0.45f)
            drawRect(dim, Offset.Zero, Size(size.width, top))
            drawRect(dim, Offset(0f, top + side), Size(size.width, size.height - top - side))
            drawRect(dim, Offset(0f, top), Size(left, side))
            drawRect(dim, Offset(left + side, top), Size(left, side))
            val l = side * 0.16f; val w = 5.dp.toPx(); val c = Palette.Gold
            listOf(Offset(left, top) to Pair(1f, 1f), Offset(left + side, top) to Pair(-1f, 1f),
                Offset(left, top + side) to Pair(1f, -1f), Offset(left + side, top + side) to Pair(-1f, -1f))
                .forEach { (p, d) ->
                    drawLine(c, p, Offset(p.x + l * d.first, p.y), w, StrokeCap.Round)
                    drawLine(c, p, Offset(p.x, p.y + l * d.second), w, StrokeCap.Round)
                }
        }

        // top bar
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.ArrowBack, onBack)
            Spacer(Modifier.weight(1f))
            RoundIcon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff) {
                torch = !torch; camera?.cameraControl?.enableTorch(torch)
            }
        }

        // hint + controls
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.t("frame_hint"), color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp))
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                RoundIcon(Icons.Rounded.PhotoLibrary) { picker.launch("image/*") }
                Box(
                    Modifier.size(82.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp)
                        .clip(CircleShape).background(if (busy) Palette.Gold else Color.White)
                        .clickable(enabled = !busy) {
                            busy = true
                            capture.takePicture(ContextCompat.getMainExecutor(ctx),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val rot = image.imageInfo.rotationDegrees
                                        val raw = image.toBitmap()
                                        image.close()
                                        val bmp = if (rot == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width,
                                            raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                                        onCaptured(AppState.scaleDown(
                                            cropToFrame(bmp, viewSize.width.toFloat(), viewSize.height.toFloat()), 1280))
                                    }
                                    override fun onError(exception: ImageCaptureException) { busy = false }
                                })
                        },
                    contentAlignment = Alignment.Center,
                ) { if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp) }
                Box(Modifier.size(48.dp))
            }
        }
    }
}

private const val FRAME_W = 0.82f      // gold frame: 82 % of the screen width…
private const val FRAME_Y = 0.42f      // …centred at 42 % of the screen height

/**
 * Keeps only what the farmer framed: maps the on-screen gold square (plus an 8 % margin) back onto the captured photo.
 * The preview uses FILL_CENTER, i.e. the photo is scaled to cover the screen and centred.
 */
private fun cropToFrame(bmp: Bitmap, vw: Float, vh: Float): Bitmap {
    if (vw <= 0f || vh <= 0f) return bmp
    val s = maxOf(vw / bmp.width, vh / bmp.height)
    val offX = (bmp.width * s - vw) / 2; val offY = (bmp.height * s - vh) / 2
    val side = vw * FRAME_W; val margin = side * 0.08f
    val left = (vw - side) / 2 - margin; val top = vh * FRAME_Y - side / 2 - margin
    val x0 = ((left + offX) / s).toInt().coerceIn(0, bmp.width - 1)
    val y0 = ((top + offY) / s).toInt().coerceIn(0, bmp.height - 1)
    val size = ((side + 2 * margin) / s).toInt()
    val w = minOf(size, bmp.width - x0); val h = minOf(size, bmp.height - y0)
    return if (w < 64 || h < 64) bmp else Bitmap.createBitmap(bmp, x0, y0, w, h)
}

@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
}
