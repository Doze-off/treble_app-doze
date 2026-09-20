package me.phh.treble.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// HSV hue/saturation wheel - angle around the circle is hue (0-360),
// distance from center is saturation (0 at center = white, 1 at the rim =
// fully saturated). "Value" (brightness) is deliberately not part of this
// view since ASUS's own Aura app keeps it as a separate slider - see
// AuraSyncActivity's brightness control.
class ColorWheelView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var hue = 0f
    private var sat = 0f
    private var value = 1f

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val selectorShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5 * resources.displayMetrics.density
        color = Color.argb(120, 0, 0, 0)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    var onColorChange: ((hue: Float, sat: Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f - selectorShadow.strokeWidth * 2

        val hues = IntArray(13)
        for (i in hues.indices) hues[i] = Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))
        huePaint.shader = SweepGradient(centerX, centerY, hues, null)
        satPaint.shader = RadialGradient(
            centerX, centerY, radius,
            Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, radius, huePaint)
        canvas.drawCircle(centerX, centerY, radius, satPaint)

        val angle = Math.toRadians(hue.toDouble())
        val dist = sat * radius
        val sx = centerX + dist * cos(angle).toFloat()
        val sy = centerY + dist * sin(angle).toFloat()
        canvas.drawCircle(sx, sy, 10 * resources.displayMetrics.density, selectorShadow)
        canvas.drawCircle(sx, sy, 10 * resources.displayMetrics.density, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = hypot(dx, dy).coerceAtMost(radius)
                var angle = Math.toDegrees(atan2(dy, dx).toDouble())
                if (angle < 0) angle += 360.0
                hue = angle.toFloat()
                sat = if (radius > 0) dist / radius else 0f
                invalidate()
                onColorChange?.invoke(hue, sat)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // "Value" isn't drawn on the wheel itself, but the wheel needs it to
    // report a correct RGB color for the currently-selected hue/sat.
    fun setValue(v: Float) {
        value = v
        invalidate()
    }

    fun setHueSat(h: Float, s: Float) {
        hue = h
        sat = s
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))
}
