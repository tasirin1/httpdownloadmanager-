package com.tasirin.httpdownloadmanager.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.tasirin.httpdownloadmanager.util.Formats

/** Grafik kecepatan unduhan realtime (60 detik terakhir). */
class SpeedChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0D47A1.toInt()
        strokeWidth = 2.dp()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x220D47A1
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14000000
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF757575.toInt()
        textSize = 10.sp()
    }
    private val data = ArrayList<Long>(60)
    private var maxValue = 1L

    fun setData(points: List<Long>) {
        data.clear()
        data.addAll(points)
        maxValue = (data.maxOrNull() ?: 0L).coerceAtLeast(1L)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // garis grid + label nilai maksimum
        canvas.drawLine(0f, h - 1f, w, h - 1f, gridPaint)
        if (maxValue > 0) {
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(Formats.speed(maxValue), 4f, 12f, labelPaint)
        }

        val n = data.size
        if (n < 2) return
        val path = Path()
        val fill = Path()
        val step = w / (MAX_POINTS - 1).toFloat()
        var x = w - step * (n - 1)
        data.forEachIndexed { i, v ->
            val y = h - 2f - (h - 14f) * (v.toFloat() / maxValue.toFloat())
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
            x += step
        }
        fill.lineTo(w, h)
        fill.close()
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, linePaint)
    }

    private fun Int.dp(): Float = this * resources.displayMetrics.density
    private fun Int.sp(): Float = this * resources.displayMetrics.scaledDensity

    companion object {
        const val MAX_POINTS = 60
    }
}
