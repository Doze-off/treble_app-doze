package me.phh.treble.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// Full-circle hue ring with a soft glow, a segmented/dashed rim, and a
// small notch (with two tick marks) at the top - matching the look of
// ASUS's own real Aura Sync ring control, not a plain flat pastel wheel.
// The drawing code itself is original.
//
// Saturation is set externally via setSaturation() (see the
// "Saturation" slider in AuraSyncActivity) rather than by dragging
// within this view, so the ring only has to represent one dimension
// (hue) and can stay a clean thin ring instead of a filled disc.
class ColorWheelView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var hue = 0f
    private var sat = 0f
    private var value = 1f

    private val density = resources.displayMetrics.density
    private val ringStrokeWidth = 20 * density
    private val dashLen = 10 * density
    private val dashGap = 4 * density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStrokeWidth
        pathEffect = DashPathEffect(floatArrayOf(dashLen, dashGap), 0f)
    }
    private val glowPaints = listOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringStrokeWidth + 26 * density; alpha = 25 },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringStrokeWidth + 14 * density; alpha = 45 }
    )
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val selectorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = Color.argb(160, 0, 0, 0)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private val ringBounds = RectF()
    private var dragging = false

    var onColorChange: ((hue: Float, sat: Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f - (ringStrokeWidth + 26 * density)
        ringBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        val hues = IntArray(13)
        for (i in hues.indices) hues[i] = Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))
        val shader = SweepGradient(centerX, centerY, hues, null)
        ringPaint.shader = shader
        glowPaints.forEach { it.shader = shader }
    }

    override fun onDraw(canvas: Canvas) {
        for (glow in glowPaints) {
            canvas.drawArc(ringBounds, GAP_START, GAP_SWEEP, false, glow)
        }
        canvas.drawArc(ringBounds, GAP_START, GAP_SWEEP, false, ringPaint)

        drawTick(canvas, -90f - GAP_HALF, Color.WHITE)
        drawTick(canvas, -90f + GAP_HALF, Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        val angleRad = Math.toRadians(hue.toDouble())
        val sx = centerX + radius * cos(angleRad).toFloat()
        val sy = centerY + radius * sin(angleRad).toFloat()
        canvas.drawCircle(sx, sy, 13 * density, selectorPaint)
        canvas.drawCircle(sx, sy, 13 * density, selectorBorderPaint)
    }

    private fun drawTick(canvas: Canvas, angleDeg: Float, color: Int) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val inner = radius - ringStrokeWidth / 2f - 4 * density
        val outer = radius + ringStrokeWidth / 2f + 4 * density
        val x1 = centerX + inner * cos(rad).toFloat()
        val y1 = centerY + inner * sin(rad).toFloat()
        val x2 = centerX + outer * cos(rad).toFloat()
        val y2 = centerY + outer * sin(rad).toFloat()
        tickPaint.color = color
        canvas.drawLine(x1, y1, x2, y2, tickPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - centerX
        val dy = event.y - centerY
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dist = hypot(dx, dy)
                dragging = dist in (radius - TOUCH_BAND_DP * density)..(radius + TOUCH_BAND_DP * density)
                if (!dragging) return super.onTouchEvent(event)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                dragging = false
                return if (wasDragging) true else super.onTouchEvent(event)
            }
            else -> return super.onTouchEvent(event)
        }

        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0) angle += 360f
        hue = angle.coerceIn(0f, 360f)
        invalidate()
        onColorChange?.invoke(hue, sat)
        return true
    }

    // "Value" isn't drawn on the ring itself; it's needed to report a
    // correct RGB color for the currently-selected hue/saturation.
    fun setValue(v: Float) {
        value = v
        invalidate()
    }

    fun setHueSat(h: Float, s: Float) {
        hue = h
        sat = s
        invalidate()
    }

    fun setSaturation(s: Float) {
        sat = s
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))

    companion object {
        private const val GAP_HALF = 6f
        private const val GAP_START = -90f + GAP_HALF
        private const val GAP_SWEEP = 360f - 2 * GAP_HALF
        private const val TOUCH_BAND_DP = 28f
    }
}
