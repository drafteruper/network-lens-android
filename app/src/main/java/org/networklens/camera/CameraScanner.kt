package org.networklens.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.networklens.core.*
import java.util.concurrent.Executors

/** CameraX transforms both detector outputs into PreviewView coordinates, including crop/rotation. */
@Composable
fun CameraScanner(profiles: List<Profile>, modifier: Modifier, onBadge: (BadgeObservation) -> Unit, onError: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latestProfiles by rememberUpdatedState(profiles)
    val latestTap by rememberUpdatedState(onBadge)
    val latestError by rememberUpdatedState(onError)
    var dispose by remember { mutableStateOf<(() -> Unit)?>(null) }
    AndroidView(modifier = modifier, factory = { context ->
        val preview = PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        val overlay = BadgeOverlay(context) { latestTap(it) }
        val container = FrameLayout(context).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }
        val controller = LifecycleCameraController(context)
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val faces = FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).build())
        val executor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var indexedProfiles = emptyList<Profile>()
        var badgeIndex = BadgeIndex(emptyList())
        var lastError = 0L
        var closed = false
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        controller.setImageAnalysisAnalyzer(analysisExecutor, MlKitAnalyzer(listOf(ocr, faces),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, analysisExecutor) { result ->
            if (!closed) {
                val text = result.getValue(ocr)
                val detected = result.getValue(faces)
                if (text == null && SystemClock.elapsedRealtime() - lastError > 10_000) {
                    executor.execute { if (!closed) latestError("OCR unavailable. Try restarting the scanner or use manual search.") }
                    lastError = SystemClock.elapsedRealtime()
                }
                fun android.graphics.Rect.bounds() = Bounds(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                val currentProfiles = latestProfiles
                if (indexedProfiles != currentProfiles) { indexedProfiles = currentProfiles; badgeIndex = BadgeIndex(currentProfiles) }
                val observations = FrameObservations(System.currentTimeMillis(),
                    text?.textBlocks?.mapNotNull { block -> block.boundingBox?.let {
                        BadgeObservation(it.bounds(), block.text, badgeIndex.match(block.text))
                    } } ?: emptyList(), detected?.map { it.boundingBox.bounds() } ?: emptyList())
                executor.execute { if (!closed) overlay.onFrame(observations) }
            }
        })
        preview.controller = controller
        try { controller.bindToLifecycle(owner) }
        catch (_: Exception) { latestError("Camera could not start. Check permission and whether another app is using it.") }
        controller.initializationFuture.addListener({
            try { controller.initializationFuture.get() }
            catch (_: Exception) { if (!closed) latestError("No usable rear camera. Manual attendee search is still available.") }
        }, executor)
        dispose = { closed = true; controller.clearImageAnalysisAnalyzer(); controller.unbind(); ocr.close(); faces.close(); analysisExecutor.shutdown() }
        container
    })
    DisposableEffect(owner) { onDispose { dispose?.invoke(); dispose = null } }
}

private class BadgeOverlay(context: Context, private val tap: (BadgeObservation) -> Unit) : View(context), ObservationSink {
    private var frame = FrameObservations(0, emptyList(), emptyList())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hits = mutableListOf<Pair<RectF, BadgeObservation>>()
    private val density = resources.displayMetrics.density
    private val expire = Runnable { frame = FrameObservations(0, emptyList(), emptyList()); invalidate() }
    override fun onFrame(frame: FrameObservations) { this.frame = frame; removeCallbacks(expire); postDelayed(expire, 1500); invalidate() }
    override fun onDetachedFromWindow() { removeCallbacks(expire); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); hits.clear()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density; paint.color = Color.GRAY
        frame.anonymousFaces.forEach { canvas.drawRect(it.left, it.top, it.right, it.bottom, paint) }
        frame.badges.take(16).forEach { badge ->
            val best = badge.candidates.firstOrNull()
            val color = if (best?.strong == true) when (best.profile.category) {
                "investor" -> Color.rgb(98, 230, 166); "startup" -> Color.rgb(255, 218, 104); else -> Color.LTGRAY
            } else Color.LTGRAY
            val b = badge.bounds
            paint.style = Paint.Style.STROKE; paint.color = color
            canvas.drawRoundRect(RectF(b.left, b.top, b.right, b.bottom), 8f, 8f, paint)
            val label = when {
                best == null -> "Unmatched text · tap"
                best.ambiguous -> "Ambiguous · tap to choose"
                else -> "${best.profile.name} · ${best.score}% score"
            }
            val detail = if (best?.strong == true) "${best.profile.company} · badge match" else "Possible match · confirm manually"
            paint.textSize = 13 * density
            val left = b.left.coerceIn(0f, (width - 240 * density).coerceAtLeast(0f))
            val top = (b.bottom + 4 * density).coerceIn(0f, (height - 48 * density).coerceAtLeast(0f))
            val right = (left + maxOf(paint.measureText(label), paint.measureText(detail)) + 16 * density).coerceAtMost(width.toFloat())
            val card = RectF(left, top, right, top + (if (best != null) 44 else 25) * density)
            paint.style = Paint.Style.FILL; paint.color = Color.argb(225, 12, 20, 26)
            canvas.drawRoundRect(card, 8f, 8f, paint)
            paint.color = color; canvas.drawText(label, left + 6 * density, top + 17 * density, paint)
            if (best != null) { paint.color = Color.WHITE; paint.textSize = 11 * density; canvas.drawText(detail, left + 6 * density, top + 34 * density, paint) }
            hits.add(card to badge); hits.add(RectF(b.left, b.top, b.right, b.bottom) to badge)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            hits.lastOrNull { it.first.contains(event.x, event.y) }?.let { tap(it.second); performClick(); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
