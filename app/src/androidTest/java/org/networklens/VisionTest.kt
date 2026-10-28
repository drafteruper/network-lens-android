package org.networklens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.face.FaceDetection
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class VisionTest {
    @Test fun bundledOcrReadsFictionalBadge() {
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 72f }
        canvas.drawText("Alex Example", 60f, 160f, paint)
        paint.textSize = 48f
        canvas.drawText("Example Ventures", 60f, 260f, paint)
        val detector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
            assertTrue(result.text.contains("Alex Example")); assertTrue(result.text.contains("Example Ventures"))
        } finally { detector.close(); bitmap.recycle() }
    }
    @Test fun blankFrameDoesNotProduceFaces() {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        val detector = FaceDetection.getClient()
        try { assertTrue(Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).isEmpty()) }
        finally { detector.close(); bitmap.recycle() }
    }
}
